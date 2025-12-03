package top.stellarium.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import top.stellarium.common.constant.RedisConstant;
import top.stellarium.common.exception.BusinessException;
import top.stellarium.pojo.dto.CreateRoomDTO;
import top.stellarium.pojo.entity.RoomInfo;
import top.stellarium.pojo.vo.CreateRoomVO;
import top.stellarium.service.BangumiService;
import top.stellarium.service.TogetherService;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class TogetherServiceImpl implements TogetherService {

    private static final String ID_KEY = RedisConstant.TOGETHER + "::next_room_id";
    private static final String ROOM = RedisConstant.TOGETHER + "::room";
    private static final String ROOM_LIST_KEY = RedisConstant.TOGETHER + "::room:list";

    @Autowired
    private RedisTemplate redisTemplate;
    @Autowired
    private BangumiService bangumiService;
    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public CreateRoomVO createRoom(CreateRoomDTO createRoomDTO) {
        Long roomId = redisTemplate.opsForValue().increment(ID_KEY);
        Mono<String> mono = bangumiService.getSpecificAnime(createRoomDTO.getAnimeId());
        String jsonBody = mono.block();
        String nameCn= null, image= null;
        try {
            if (jsonBody != null) {
                JsonNode rootNode = objectMapper.readTree(jsonBody);
                nameCn = rootNode.get("name_cn").asText();
                image = rootNode.get("images").get("large").asText();
            }
        } catch (JsonProcessingException e) {
            throw new BusinessException(e.getMessage());
        }
        RoomInfo roomInfo = RoomInfo
                .builder()
                .animeId(createRoomDTO.getAnimeId())
                .cover(image)
                .currentEpisode(0)
                .onlineCount(0)
                .ownerId(createRoomDTO.getUserId())
                .id(roomId)
                .status(1)
                .title(createRoomDTO.getTitle()==null?nameCn:createRoomDTO.getTitle())
                .build();

        redisTemplate.opsForValue().set(ROOM+roomId, roomInfo);
        // 3. 将 roomId 加入活跃列表 (Set)
        redisTemplate.opsForSet().add(ROOM_LIST_KEY, roomId);

        // 4. 给房间详情设置过期时间，防止僵尸房间
        redisTemplate.expire(ROOM + roomId, 24, TimeUnit.HOURS);
        return CreateRoomVO.builder()
                .animeId(createRoomDTO.getAnimeId())
                .id(roomId)
                .build();
    }

    @Override
    public List<RoomInfo> getRooms() {
        // 1. 先从 Set 中获取所有活跃的 roomId
        // members 返回的是 Set<Object> (取决于你的泛型配置)
        Set<Object> roomIds = redisTemplate.opsForSet().members(ROOM_LIST_KEY);

        if (roomIds == null || roomIds.isEmpty()) {
            return new ArrayList<>();
        }

        // 2. 拼接出所有的 Key (together:room:1, together:room:2 ...)
        List<String> keys = roomIds.stream()
                .map(id -> ROOM + id) // 拼接前缀
                .collect(Collectors.toList());

        // 3. 使用 multiGet 批量查询 (性能远高于循环调用 get)
        List<Object> results = redisTemplate.opsForValue().multiGet(keys);

        // 4. 过滤空值 (防止某些房间过期了但 ID 还在 Set 里) 并强转类型
        List<RoomInfo> activeRooms = new ArrayList<>();

        // 这一步是为了同步清理无效的 ID
        List<Object> invalidIds = new ArrayList<>();

        for (int i = 0; i < results.size(); i++) {
            Object obj = results.get(i);
            if (obj instanceof RoomInfo) {
                activeRooms.add((RoomInfo) obj);
            } else {
                String invalidKey = keys.get(i);
            }
        }
        return activeRooms;
    }
}
