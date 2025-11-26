package top.stellarium.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.units.qual.C;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import top.stellarium.common.constant.RedisConstant;
import top.stellarium.common.exception.BusinessException;
import top.stellarium.mapper.UserMapper;
import top.stellarium.pojo.dto.CollectionDTO;
import top.stellarium.pojo.entity.User;
import top.stellarium.pojo.vo.CollectionAnimeVO;
import top.stellarium.pojo.vo.CollectionCharacterVO;
import top.stellarium.pojo.vo.IndexAnimeVO;
import top.stellarium.pojo.vo.ListVO;
import top.stellarium.service.BangumiService;
import top.stellarium.service.CollectionService;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class CollectionServiceImpl implements CollectionService {

    @Autowired
    private BangumiService bangumiService;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private RedisTemplate redisTemplate;
    @Autowired
    private UserMapper userMapper;

    /**
     * 获取收藏的动漫
     *
     * @param collectionDTO
     * @return
     */
    @Override
    public ListVO<CollectionAnimeVO> getCollectedAnime(CollectionDTO collectionDTO) {
        // 调用api
        // 如果缓存存在，则直接通过缓存获取数据，ZSET映射状态，否则先api获取首页
        ListVO<CollectionAnimeVO> listVO = new ListVO<>();
        List<CollectionAnimeVO> list;
        String key = RedisConstant.COLLECTION + "::" + collectionDTO.getUserId() + ":anime";//根据用户筛选 collectionCache::username:anime
        if (redisTemplate.hasKey(key)) {
            // 现在根据limit page status获取 status即zset的分数
            long offset = (long) (collectionDTO.getPage() - 1) * collectionDTO.getLimit();
            long status = collectionDTO.getStatus();
            long limit = collectionDTO.getLimit();
            Set<CollectionAnimeVO> animeVOSet;
            double min = (status == 0) ? 1.0D : status * 1.0D;
            double max = (status == 0) ? 5.0D : status * 1.0D; // 最大状态是5
            animeVOSet = redisTemplate.opsForZSet().rangeByScore(key, min, max, offset, limit);
            list = new ArrayList<>(animeVOSet);
            listVO.setTotal(list.size());
            listVO.setList(list);
            return listVO;
        }
        log.info("不存在缓存: {}", collectionDTO);
        User user = userMapper.selectById(collectionDTO.getUserId());
        collectionDTO.setBangumiId(user.getBangumiId());
        log.info("获取收藏首页: {}", collectionDTO);
        Mono<String> mono = bangumiService.getAnimeCollection(collectionDTO);
        String jsonBody = mono.block();
        //log.info(jsonBody);
        list = new ArrayList<>();
        // 先获取首页并返回
        try {
            // 解析 JSON
            if (jsonBody != null) {
                JsonNode rootNode = objectMapper.readTree(jsonBody);
                JsonNode dataNode = rootNode.get("data");
                if (dataNode != null && dataNode.isArray()) {
                    // 现在遍历dataNode
                    int limit = collectionDTO.getLimit();
                    for (int i = 0; i < limit && i < dataNode.size(); i++) {
                        /*
                        *
                        * private String name;
                        * private String nameCn;
                        * private Double rating;
                        * private String image;
                        * private Long bangumiId;
                        * private String tag; // main tag most upvoted
                        * private String year;
                        * */
                        JsonNode node = dataNode.get(i);
                        if (node == null) continue;
                        CollectionAnimeVO collectionAnimeVO = getCollectionAnimeVO(node);
                        list.add(collectionAnimeVO);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new BusinessException(500, "获取用户收藏失败");
        }
        listVO.setList(list);
        listVO.setTotal(list.size());
        // 异步获取全部内容，直到获取的数目 == total
        // 如果异步获取的时候用户又查询了怎么办？？？ ：用redis setnx一个key当作锁来用
        getAnimeCache(collectionDTO);

        return listVO;
    }

    /**
     * 获取CollectionAnimeVO封装复用
     * @param node data的子节点
     * @return
     */
    private static CollectionAnimeVO getCollectionAnimeVO(JsonNode node) {
        JsonNode subject = node.get("subject");
        String name = subject.get("name").asText();
        String nameCn = subject.get("name_cn").asText();
        Double rating = subject.get("score").asDouble();
        String image = subject.get("images").get("large").asText();
        Long bangumiId = subject.get("id").asLong();
        String tag = subject.get("tags").get(0).get("name").asText();
        String year = subject.get("date").asText().split("-")[0];
        CollectionAnimeVO collectionAnimeVO = CollectionAnimeVO.builder()
                .tag(tag)
                .year(year)
                .name(name)
                .nameCn(nameCn)
                .rating(rating)
                .image(image)
                .bangumiId(bangumiId)
                .build();
        return collectionAnimeVO;
    }

    /**
     * 异步获取该用户的全部内容，并缓存1h
     * @param collectionDTO
     */
    @Async("taskExecutor")
    protected void getAnimeCache(CollectionDTO collectionDTO){
        // 1. 使用独立的锁 Key，不要用数据 Key
        String lockKey = "lock:loading:" + collectionDTO.getUserId() + ":anime";
        // 2. 抢锁 (SETNX)，10分钟自动过期防止死锁
        Boolean isLocked = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", 10, TimeUnit.MINUTES);
        if (Boolean.FALSE.equals(isLocked)) {
            return; // 已经有线程在跑了，直接退出
        }
        try {
            String key = RedisConstant.COLLECTION + "::" + collectionDTO.getUserId() + ":anime";//根据用户筛选 collectionCache::username:anime
            // 全部内容score存两种，一种是状态，一种是0
            ZSetOperations zSetOperations = redisTemplate.opsForZSet();

            int total = Integer.MAX_VALUE, added = 0;
            collectionDTO.setLimit(50); // 直接设置到最大，减少获取次数
            collectionDTO.setPage(1);
            while(added < total){
                Mono<String> mono = bangumiService.getAnimeCollection(collectionDTO);
                String jsonBody = mono.block();
                try {
                    if(jsonBody!=null){
                        JsonNode rootNode = objectMapper.readTree(jsonBody);
                        if(total == Integer.MAX_VALUE) total = rootNode.get("total").asInt();
                        JsonNode dataNode = rootNode.get("data");
                        if(dataNode!= null && dataNode.isArray()){
                            // type = 1,2,3,4,5
                            for(int i = 0; i<dataNode.size();i++){
                                JsonNode node = dataNode.get(i);
                                added++;
                                if(node == null) continue;
                                int type = node.get("type").asInt();
                                CollectionAnimeVO collectionAnimeVO = getCollectionAnimeVO(node);
                                zSetOperations.add(key, collectionAnimeVO, type*1.0); // 添加到对应分类
                            }
                        }
                        else{
                            break;
                        }
                    }
                } catch (Exception e) {
                    log.error("异步缓存用户[{}]收藏失败", collectionDTO.getUserId(), e);
                    // 确保清理脏数据
                    redisTemplate.delete(key);
                }
                collectionDTO.setPage(collectionDTO.getPage()+1);
            }
            redisTemplate.expire(key, RedisConstant.COLLECTION_EXPIRE, TimeUnit.MINUTES);
        } catch (BusinessException e) {
            throw new RuntimeException(e);
        } finally {
            log.info("获取缓存完成");
            redisTemplate.delete(lockKey);
        }
    }



    /**
     * 获取收藏的角色
     * @param collectionDTO
     * @return
     */
    @Override
    public ListVO<CollectionCharacterVO> getCollectedCharacter(CollectionDTO collectionDTO) {
        // 首先检查缓存中有没有
        ListVO<CollectionCharacterVO> listVO = new ListVO<>();
        List<CollectionCharacterVO> list;
        String key = RedisConstant.COLLECTION + "::" + collectionDTO.getUserId() + ":character";//根据用户筛选 collectionCache::username:character
        if (redisTemplate.hasKey(key)) {
            long offset = (long) (collectionDTO.getPage() - 1) * collectionDTO.getLimit();
            long limit = collectionDTO.getLimit();
            Set<CollectionCharacterVO> characterVOSet;
            characterVOSet = redisTemplate.opsForZSet().rangeByScore(key, 0.0, 1.0, offset, limit);
            // 只有0
            list = new ArrayList<>(characterVOSet);
            listVO.setTotal(list.size());
            listVO.setList(list);
            return listVO;
        }
        log.info("不存在缓存: {}", collectionDTO);
        User user = userMapper.selectById(collectionDTO.getUserId());
        collectionDTO.setBangumiId(user.getBangumiId());
        log.info("获取收藏首页: {}", collectionDTO);
        Mono<String> mono = bangumiService.getCharacterCollection(collectionDTO);
        String jsonBody = mono.block();
        //log.info(jsonBody);
        list = new ArrayList<>();
        // 先获取首页并返回
        try {
            // 解析 JSON
            if (jsonBody != null) {
                JsonNode rootNode = objectMapper.readTree(jsonBody);
                JsonNode dataNode = rootNode.get("data");
                if (dataNode != null && dataNode.isArray()) {
                    // 现在遍历dataNode
                    int limit = collectionDTO.getLimit();
                    for (int i = 0; i < limit && i < dataNode.size(); i++) {
                        JsonNode node = dataNode.get(i);
                        if (node == null) continue;
                        CollectionCharacterVO collectionCharacterVO = getCollectionCharacterVO(node);
                        list.add(collectionCharacterVO);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new BusinessException(500, "获取用户收藏失败");
        }
        listVO.setList(list);
        listVO.setTotal(list.size());
        // 异步获取全部内容，直到获取的数目 == total
        getCharacterCache(collectionDTO);
        return listVO;
    }

    private CollectionCharacterVO getCollectionCharacterVO(JsonNode node) {
        /*
         *
         * private String name;
         * private String nameCn;
         * private String image;
         * private Long bangumiId;
         * private String tag; // main tag most upvoted
         * private String from;
         * */
        Long bangumiId = node.get("id").asLong();
        String name = null, nameCn = null, image = null, tag = null, from = null;
        try{
            Mono<String> mono = bangumiService.getCharacter(bangumiId);
            String jsonBody = mono.block();
            Mono<String> subjectMono = bangumiService.getCharacterSubject(bangumiId);
            String subjectJsonBody = subjectMono.block();
            if(jsonBody!=null && subjectJsonBody!=null){
                JsonNode rootNode = objectMapper.readTree(jsonBody);
                name = rootNode.get("name").asText();
                image = rootNode.get("images").get("large").asText();
                JsonNode infoboxNode = node.path("infobox");
                if (infoboxNode.isArray()) {
                    for (JsonNode info : infoboxNode) {
                        String key = info.path("key").asText();
                        // 解析 Value：Bangumi 的 value 可能是字符串，也可能是数组
                        JsonNode valNode = info.path("value");
                        String valueStr = "";
                        if (valNode.isArray()) {
                            // 如果是数组，通常取第一个或者拼接，这里简化处理取第一个v
                            valueStr = valNode.get(0).path("v").asText();
                        } else {
                            valueStr = valNode.asText();
                        }

                        if ("简体中文名".equals(key)) {
                            nameCn = valueStr;
                        }

                        if("性别".equals(key)){
                            tag = valueStr;
                        }
                    }
                }

                JsonNode subjectRootNode = objectMapper.readTree(subjectJsonBody);
                from = subjectRootNode.get(0).get("name_cn").asText();

            }
        }
        catch (Exception e){
            e.printStackTrace();
        }
        return CollectionCharacterVO.builder()
                .from(from)
                .tag(tag)
                .bangumiId(bangumiId)
                .image(image)
                .nameCn(nameCn)
                .name(name)
                .build();
    }

    /**
     * 获取该用户全部的角色收藏并缓存1h
     * @param collectionDTO
     */
    @Async
    protected void getCharacterCache(CollectionDTO collectionDTO){
        // 1. 使用独立的锁 Key，不要用数据 Key
        String lockKey = "lock:loading:" + collectionDTO.getUserId() + ":character";
        // 2. 抢锁 (SETNX)，10分钟自动过期防止死锁
        Boolean isLocked = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", 10, TimeUnit.MINUTES);
        if (Boolean.FALSE.equals(isLocked)) {
            return; // 已经有线程在跑了，直接退出
        }
        try {
            String key = RedisConstant.COLLECTION + "::" + collectionDTO.getUserId() + ":character";//根据用户筛选 collectionCache::username:anime
            // 全部内容score存0
            ZSetOperations zSetOperations = redisTemplate.opsForZSet();
            int total = Integer.MAX_VALUE, added = 0;
            collectionDTO.setLimit(50); // 直接设置到最大，减少获取次数
            collectionDTO.setPage(1);
            while(added < total){
                Mono<String> mono = bangumiService.getCharacterCollection(collectionDTO);
                String jsonBody = mono.block();
                try {
                    // 解析 JSON
                    if (jsonBody != null) {
                        JsonNode rootNode = objectMapper.readTree(jsonBody);
                        if(total == Integer.MAX_VALUE){
                            total = rootNode.get("total").asInt();
                        }
                        JsonNode dataNode = rootNode.get("data");
                        if (dataNode != null && dataNode.isArray()) {
                            // 现在遍历dataNode
                            int limit = collectionDTO.getLimit();
                            for (int i = 0; i < limit && i < dataNode.size(); i++) {
                                JsonNode node = dataNode.get(i);
                                if (node == null) continue;
                                added++;
                                CollectionCharacterVO collectionCharacterVO = getCollectionCharacterVO(node);
                                zSetOperations.add(key, collectionCharacterVO, 0);
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    redisTemplate.delete(key);
                    break;
                }
                collectionDTO.setPage(collectionDTO.getPage()+1);
            }
            redisTemplate.expire(key, RedisConstant.COLLECTION_EXPIRE, TimeUnit.MINUTES);
        } catch (BusinessException e) {
            throw new RuntimeException(e);
        } finally {
            log.info("获取缓存完成");
            redisTemplate.delete(lockKey);
        }
    }
}
