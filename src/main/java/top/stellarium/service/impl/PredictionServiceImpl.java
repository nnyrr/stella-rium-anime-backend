package top.stellarium.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import top.stellarium.common.constant.RedisConstant;
import top.stellarium.common.exception.BusinessException;
import top.stellarium.mapper.AnimeInfoMapper;
import top.stellarium.mapper.UserMapper;
import top.stellarium.pojo.dto.CollectionDTO;
import top.stellarium.pojo.entity.AnimeInfo;
import top.stellarium.pojo.entity.User;
import top.stellarium.pojo.vo.CollectionAnimeVO;
import top.stellarium.pojo.vo.ListVO;
import top.stellarium.service.BangumiService;
import top.stellarium.service.PredictionService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class PredictionServiceImpl implements PredictionService {

    private final WebClient webClient;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private BangumiService bangumiService;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private RedisTemplate redisTemplate;
    @Autowired
    private AnimeInfoMapper animeInfoMapper;

    private static final String ANIME = "anime";
    private static final String CHARACTER = "character";


    PredictionServiceImpl(@Value("${spring.stella-rium.prediction.url}") String baseUrl) {
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    /**
     * 获取预测的动漫
     *
     * @param userId
     * @return
     */
    @Override
    public ListVO<CollectionAnimeVO> getPredictionAnime(Integer userId) throws Exception {
        // 先检查是否存在推荐缓存
        String key = RedisConstant.PREDICTION + "::" + userId + ":anime";
        if (redisTemplate.hasKey(key) && redisTemplate.opsForList().size(key) >= 3) {

        } else {
            // 先通过用户表获得用户id
            User user = userMapper.selectById(userId);
            // 再从bangumi读取用户的动漫收藏列表（只取前50个）
            String bangumiUsername = user.getBangumiId();
            List<Integer> collectionList = getCollectionList(bangumiUsername, ANIME);
            // 调用预测模型的接口
            List<Integer> predictionList = getAnimePredictionList(collectionList);
            redisTemplate.opsForList().rightPushAll(key, predictionList);
            redisTemplate.expire(key, 30, TimeUnit.MINUTES);
            // body参数: user_id -> -1, history_anime_ids -> collectionList, top_k->15
        }
        ListVO<CollectionAnimeVO> listVO = new ListVO<>();
        listVO.setTotal(3);
        List<CollectionAnimeVO> list = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Integer animeId = (Integer) redisTemplate.opsForList().leftPop(key);
            AnimeInfo animeInfo = animeInfoMapper.selectById(animeId);
            if(animeInfo == null){
                Mono<String> mono = bangumiService.getSpecificAnime(animeId);
                String jsonBody = mono.block();
                if(jsonBody != null){
                    JsonNode rootNode = objectMapper.readTree(jsonBody);
                    String name = null, nameCn = null, image = null, year = null, summary = null, tag = null;
                    Long bangumiId = Long.valueOf(animeId);
                    Double rating = null;
                    name = rootNode.get("name").asText();
                    nameCn = rootNode.get("name_cn").asText();
                    image = rootNode.get("images").get("large").asText();
                    year = rootNode.get("date").asText().split("-")[0];
                    summary = rootNode.get("summary").asText();
                    tag = rootNode.get("tags").get(0).get("name").asText();
                    rating = rootNode.get("rating").get("score").asDouble();
                    animeInfo = AnimeInfo
                            .builder()
                            .summary(summary)
                            .tag(tag)
                            .year(year)
                            .image(image)
                            .name(name)
                            .nameCn(nameCn)
                            .bangumiId(bangumiId)
                            .rating(rating)
                            .build();
                    animeInfoMapper.insert(animeInfo);
                }
            }
            CollectionAnimeVO collectionAnimeVO = new CollectionAnimeVO();
            BeanUtils.copyProperties(animeInfo, collectionAnimeVO);
            list.add(collectionAnimeVO);
        }
        listVO.setList(list);
        // 从缓存里面取3个数据出来，并pop
        // 然后再从数据库 || bangumi的api获取到这些东西的信息，
        // 我可以数据库里有的直接拿，没有的去用api获取。
        return listVO;
    }

    private List<Integer> getAnimePredictionList(List<Integer> history) {
        List<Integer> list = new ArrayList<>();
        // 1. 准备参数
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("user_id", -1);
        requestBody.put("history_anime_ids", history);
        requestBody.put("top_k", 15);

// 2. 发送请求
        Mono<String> mono = webClient.post()
                .uri("/recommend/anime")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody) // 直接传入 Map
                .retrieve()
                .bodyToMono(String.class);
        String jsonBody = mono.block();
        try {
            if (jsonBody != null) {
                JsonNode rootNode = objectMapper.readTree(jsonBody);
                JsonNode dataNode = rootNode.get("recommendations");
                if (dataNode != null && dataNode.isArray()) {
                    for (int i = 0; i < 15 && i < dataNode.size(); i++) {
                        JsonNode node = dataNode.get(i);
                        list.add(node.asInt());
                    }
                }
            }
        } catch (JsonProcessingException e) {
            throw new BusinessException("获取预测失败！");
        }
        return list;
    }

    private List<Integer> getCollectionList(String bangumiUsername, String type) throws Exception {
        // 根据bangumiUsername调用请求
        List<Integer> list = new ArrayList<>();
        String jsonBody = null;
        CollectionDTO collectionDTO = CollectionDTO.builder().bangumiId(bangumiUsername).page(1).limit(50).build();
        switch (type) {
            case ANIME -> {
                jsonBody = bangumiService.getAnimeCollection(collectionDTO).block();
            }
            case CHARACTER -> {
                jsonBody = bangumiService.getCharacterCollection(collectionDTO).block();
            }
        }
        try {
            if (jsonBody != null) {
                JsonNode rootNode = objectMapper.readTree(jsonBody);
                int total = rootNode.get("total").asInt();
                JsonNode dataNode = rootNode.get("data");
                if (dataNode != null && dataNode.isArray() && !dataNode.isEmpty()) {
                    for (int i = 0; i < total && i < dataNode.size(); i++) {
                        JsonNode node = dataNode.get(i);
                        list.add(node.get("subject").get("id").asInt());
                    }
                }
            }
        } catch (Exception e) {
            throw new BusinessException("获取收藏列表失败");
        }
        return list;
    }
}
