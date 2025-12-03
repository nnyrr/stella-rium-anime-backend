package top.stellarium.common.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import top.stellarium.common.constant.RedisConstant;
import top.stellarium.common.constant.WebsocketConstant;
import top.stellarium.pojo.entity.RoomInfo;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@ServerEndpoint("/together/ws/{roomId}")
public class TogetherServer {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    // --- Redis 相关配置 ---
    // 保持与 TogetherServiceImpl 中的 Key 定义一致
    private static final String ROOM_PREFIX = RedisConstant.TOGETHER + "::room";
    private static final String ROOM_LIST_KEY = RedisConstant.TOGETHER + "::room:list";

    // 静态注入 RedisTemplate
    private static RedisTemplate<String, Object> redisTemplate;

    @Autowired
    public void setRedisTemplate(RedisTemplate<String, Object> redisTemplate) {
        TogetherServer.redisTemplate = redisTemplate;
    }

    // --- 内存存储结构 ---
    // 房间ID -> {会话ID -> Session}
    private static final Map<Long, Map<String, Session>> ROOM_SESSIONS = new ConcurrentHashMap<>();
    // 会话ID -> 房间ID
    private static final Map<String, Long> SESSION_ROOM_MAP = new ConcurrentHashMap<>();

    @Data
    public static class WsMessage {
        private String type;
        private Object payload;
    }

    @OnOpen
    public void onOpen(Session session, @PathParam("roomId") Long roomId) {
        // 1. 建立物理连接映射
        ROOM_SESSIONS.computeIfAbsent(roomId, k -> new ConcurrentHashMap<>());
        Map<String, Session> room = ROOM_SESSIONS.get(roomId);
        room.put(session.getId(), session);
        SESSION_ROOM_MAP.put(session.getId(), roomId);

        log.info("WS连接建立(匿名): room={}, session={}, 当前人数={}", roomId, session.getId(), room.size());

        // 2. 【Redis 同步】刷新过期时间 & 更新在线人数
        // 只要有人进来，就给房间“续命” 24小时
        redisTemplate.expire(ROOM_PREFIX + roomId, 24, TimeUnit.HOURS);
        // 更新 Redis 中的在线人数 (以内存中的实际人数为准)
        updateRedisOnlineCount(roomId, room.size());
    }

    @OnMessage
    public void onMessage(Session session, String jsonMessage) {
        try {
            WsMessage msg = objectMapper.readValue(jsonMessage, WsMessage.class);
            Long roomId = SESSION_ROOM_MAP.get(session.getId());
            if (roomId == null) return;

            Long currentUserId = (Long) session.getUserProperties().get("userId");

            switch (msg.getType()) {
                case WebsocketConstant.HEARTBEAT:
                    handleHeartbeat(session, msg, currentUserId);
                    // 收到心跳时，也可以顺便续期一下 Redis Key (可选)
                    // redisTemplate.expire(ROOM_PREFIX + roomId, 24, TimeUnit.HOURS);
                    break;

                case WebsocketConstant.CHAT_SEND:
                    if (currentUserId == null) return;
                    Map<String, Object> chatPayload = (Map<String, Object>) msg.getPayload();
                    chatPayload.put("userId", currentUserId);
                    broadcastToRoom(roomId, null, WebsocketConstant.CHAT_NEW, chatPayload);
                    break;

                case WebsocketConstant.VIDEO_PAUSE:
                case WebsocketConstant.VIDEO_PLAY:
                case WebsocketConstant.VIDEO_SEEK:
                case WebsocketConstant.VIDEO_RATE:
                case WebsocketConstant.EPISODE_CHANGE:
                    String syncType = convertToSyncType(msg.getType());
                    broadcastToRoom(roomId, session.getId(), syncType, msg.getPayload());
                    break;
            }
        } catch (Exception e) {
            log.error("消息处理异常", e);
        }
    }

    @OnClose
    public void onClose(Session session) {
        Long roomId = SESSION_ROOM_MAP.remove(session.getId());
        if (roomId != null) {
            Map<String, Session> room = ROOM_SESSIONS.get(roomId);
            if (room != null) {
                room.remove(session.getId());
                Long userId = (Long) session.getUserProperties().get("userId");
                log.info("WS断开: room={}, userId={}, session={}", roomId, userId, session.getId());

                if (room.isEmpty()) {
                    // 3. 【Redis 同步】房间没人了，清理 Redis
                    ROOM_SESSIONS.remove(roomId);

                    // A. 从活跃列表中移除 ID
                    redisTemplate.opsForSet().remove(ROOM_LIST_KEY, roomId);
                    // B. 删除房间详情 Key
                    redisTemplate.delete(ROOM_PREFIX + roomId);

                    log.info("房间 {} 已空，从 Redis 中移除", roomId);
                } else {
                    // 4. 【Redis 同步】房间还有人，更新在线人数
                    broadcastToRoom(roomId, null, WebsocketConstant.ROOM_UPDATE,
                            Map.of("onlineCount", room.size()));
                    updateRedisOnlineCount(roomId, room.size());
                }
            }
        }
    }

    @OnError
    public void onError(Session session, Throwable error) {
        log.error("WS错误: session={}", session.getId(), error);
    }

    // --- 辅助方法 ---

    /**
     * 更新 Redis 中存储的 RoomInfo 的在线人数
     */
    private void updateRedisOnlineCount(Long roomId, int count) {
        String key = ROOM_PREFIX + roomId;
        try {
            // 1. 获取当前 Redis 中的对象
            Object obj = redisTemplate.opsForValue().get(key);
            if (obj instanceof RoomInfo) {
                RoomInfo info = (RoomInfo) obj;
                // 2. 修改人数
                info.setOnlineCount(count);
                // 3. 写回 Redis (保持原有过期时间不变，或者重新设置)
                redisTemplate.opsForValue().set(key, info, 24, TimeUnit.HOURS);
            }
        } catch (Exception e) {
            log.warn("更新Redis在线人数失败: room={}", roomId, e);
        }
    }

    private void handleHeartbeat(Session session, WsMessage msg, Long currentUserId) {
        if (currentUserId == null && msg.getPayload() instanceof Map) {
            Map<?, ?> payload = (Map<?, ?>) msg.getPayload();
            Object userIdObj = payload.get("userId");

            if (userIdObj != null) {
                try {
                    Long newUserId = Long.valueOf(userIdObj.toString());
                    session.getUserProperties().put("userId", newUserId);
                    log.info("用户身份绑定成功: session={}, userId={}", session.getId(), newUserId);
                } catch (NumberFormatException e) {
                    log.warn("心跳 UserId 格式错误: {}", userIdObj);
                }
            }
        }
    }

    private void broadcastToRoom(Long roomId, String excludeSessionId, String type, Object payload) {
        Map<String, Session> room = ROOM_SESSIONS.get(roomId);
        if (room == null) return;

        String messageJson;
        try {
            WsMessage resp = new WsMessage();
            resp.setType(type);
            resp.setPayload(payload);
            messageJson = objectMapper.writeValueAsString(resp);
        } catch (JsonProcessingException e) {
            log.error("序列化失败", e);
            return;
        }

        room.values().forEach(session -> {
            if (session.isOpen() && !session.getId().equals(excludeSessionId)) {
                try {
                    session.getAsyncRemote().sendText(messageJson);
                } catch (Exception e) {
                    log.error("发送消息失败", e);
                }
            }
        });
    }

    private String convertToSyncType(String clientType) {
        return switch (clientType) {
            case WebsocketConstant.VIDEO_PAUSE -> WebsocketConstant.SYNC_PAUSE;
            case WebsocketConstant.VIDEO_PLAY -> WebsocketConstant.SYNC_PLAY;
            case WebsocketConstant.VIDEO_SEEK -> WebsocketConstant.SYNC_SEEK;
            case WebsocketConstant.VIDEO_RATE -> WebsocketConstant.SYNC_RATE;
            case WebsocketConstant.EPISODE_CHANGE -> WebsocketConstant.SYNC_EPISODE;
            default -> clientType;
        };
    }
}