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
import top.stellarium.mapper.AnimeInfoMapper;
import top.stellarium.pojo.entity.AnimeInfo;
import top.stellarium.pojo.vo.PlayerInfoVO;
import top.stellarium.service.BangumiService;
import top.stellarium.service.OmofunService;
import top.stellarium.service.PlayerService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class PlayerServiceImpl implements PlayerService {

    @Autowired
    private AnimeInfoMapper animeInfoMapper;
    @Autowired
    private BangumiService bangumiService;
    @Autowired
    private RedisTemplate redisTemplate;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private OmofunService omofunService;

    /**
     * 获取播放资源信息
     * @param id
     * @return
     */
    @Override
    public PlayerInfoVO getPlayerInfo(Integer id) {
        /**
         * private String title;
         *     private String synopsis;
         *     private List<String> tags;
         *     private String year;
         *     private String cover;
         *     private Long bangumiId;
         *     private List<episode> episodes;
         *
         *     private class episode{
         *         Integer sort;
         *         String title;
         *         String url;
         *     }
         */
//        String key = RedisConstant.PLAYER + "::" + id + ":info";
//        if(redisTemplate.hasKey(key)){
//            return (PlayerInfoVO) redisTemplate.opsForValue().get(key);
//        }
        // 先根据动漫id获取前面所需要的内容。
        // 看看存不存在，存在就直接读取，不存在就去获取，缓存一个小时
        String title = null;
        String synopsis = null;
        List<String> tags = new ArrayList<>();
        String year = null;
        String cover = null;
        Long bangumiId = null;
        Integer eps = null; // 有多少集
        List<PlayerInfoVO.episode> episodes = new ArrayList<>();
        Mono<String> mono = bangumiService.getSpecificAnime(id);
        String jsonBody = mono.block();
        if(jsonBody!=null){
            try {
                JsonNode rootNode = objectMapper.readTree(jsonBody);
                synopsis = rootNode.get("summary").asText();
                title = rootNode.get("name_cn").asText();
                if(title == null)title = rootNode.get("name").asText();
                JsonNode tagNode = rootNode.get("tag");
                if(tagNode != null && tagNode.isArray()){
                    for(int i = 0;i<tagNode.size();i++){
                        JsonNode node = tagNode.get(i);
                        tags.add(node.get("name").asText());
                    }
                }
                year = rootNode.get("date").asText().split("-")[0];
                cover = rootNode.get("images").get("large").asText();
                bangumiId = Long.valueOf(id);
                eps = rootNode.get("eps").asInt();
            } catch (JsonProcessingException e) {
                throw new BusinessException(e.getMessage());
            }
        }
        // 然后查询在线资源库获取播放的内容
        // 1. 如果 title 不为空，尝试去 Omofun 搜索
        if (title != null) {
            // 调用我们刚写的 Service
            List<PlayerInfoVO.episode> onlineEpisodes = omofunService.getEpisodesByTitle(title);

            if (!onlineEpisodes.isEmpty()) {
                episodes = onlineEpisodes;
            } else {
                // 如果没搜到，这里可以构建一个"空"列表或者占位
                // 或者保留 Bangumi 的集数信息但 url 为 null
                for(int i=1; i<=eps; i++){
                    PlayerInfoVO.episode ep = new PlayerInfoVO.episode();
                    ep.setSort(i);
                    ep.setTitle("第" + i + "话");
                    ep.setUrl(null); // 没资源
                    episodes.add(ep);
                }
            }
        }

        // 组装 VO 并返回
        PlayerInfoVO vo = new PlayerInfoVO();
        vo.setTitle(title);
        vo.setSynopsis(synopsis);
        vo.setTags(tags);
        vo.setYear(year);
        vo.setCover(cover);
        vo.setBangumiId(bangumiId);
        vo.setEpisodes(episodes);

        // 写入缓存 (记得处理序列化)
//        redisTemplate.opsForValue().set(key, vo, 1, TimeUnit.HOURS);

        return vo;
    }
}
