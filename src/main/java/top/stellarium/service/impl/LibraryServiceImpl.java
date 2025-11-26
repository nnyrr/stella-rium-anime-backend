package top.stellarium.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import top.stellarium.common.exception.BusinessException;
import top.stellarium.pojo.dto.LibraryDTO;
import top.stellarium.pojo.vo.LibraryAnimeVO;
import top.stellarium.pojo.vo.ListVO;
import top.stellarium.service.BangumiService;
import top.stellarium.service.LibraryService;

import java.util.ArrayList;
import java.util.List;

@Service
public class LibraryServiceImpl implements LibraryService {

    @Autowired
    private BangumiService bangumiService;
    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 获得排行榜
     * @param libraryDTO
     * @return
     */
    @Override
    @Cacheable(value = "libraryCache", key="#p0")
    public ListVO<LibraryAnimeVO> getLibrary(LibraryDTO libraryDTO) {
        // 从bangumi查询
        Mono<String> mono = bangumiService.getLibrary(libraryDTO);
        String jsonBody = mono.block();

        // 初始化返回结果
        List<LibraryAnimeVO> animeList = new ArrayList<>();
        ListVO<LibraryAnimeVO> result = new ListVO<>();

        try {
            if (jsonBody != null) {
                JsonNode rootNode = objectMapper.readTree(jsonBody);
                // 假设 API 返回的是 { "data": [ ... ] } 结构
                // 如果返回的直接是数组，请用 rootNode 替代 dataNode
                JsonNode dataNode = rootNode.path("data");

                if (dataNode.isMissingNode()) {
                    // 兼容逻辑：如果直接返回的是数组结构（某些API端点可能是这样）
                    dataNode = rootNode;
                }

                int limit = libraryDTO.getLimit();

                // 遍历数据
                for (int i = 0; i < limit && i < dataNode.size(); i++) {
                    JsonNode node = dataNode.get(i);
                    if (node == null) continue;

                    // 1. 基础字段提取 (使用 path 防止 NPE)
                    long bangumiId = node.path("id").asLong();
                    String name = node.path("name").asText();
                    String nameCn = node.path("name_cn").asText();
                    // 如果中文名为空，回退使用原名
                    if (nameCn == null || nameCn.isEmpty()) {
                        nameCn = name;
                    }

                    String image = node.path("images").path("large").asText();
                    String date = node.path("date").asText();
                    int episodes = node.path("eps").asInt();

                    // 2. 评分相关
                    JsonNode ratingNode = node.path("rating");
                    Double rating = ratingNode.path("score").asDouble(0.0);
                    long rank = ratingNode.path("rank").asLong(0);
                    long ratingCount = ratingNode.path("total").asLong(0);

                    // 3. 解析 Infobox (获取导演、制作公司)
                    String director = null;
                    String productionCompany = null;
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

                            if ("导演".equals(key) || "监督".equals(key)) {
                                director = valueStr;
                            }
                            // 优先找动画制作，其次找制作/製作
                            if (productionCompany == null) { // 只取第一个匹配的
                                if ("动画制作".equals(key) || "制作".equals(key) || "製作".equals(key)) {
                                    productionCompany = valueStr;
                                }
                            }
                        }
                    }

                    // 4. 解析 Tags (提取前几个标签)
                    List<String> tagList = new ArrayList<>();
                    JsonNode tagsNode = node.path("tags");
                    if (tagsNode.isArray()) {
                        // 最多取前7个标签，避免太长
                        int maxTags = 7;
                        for (int j = 0; j < Math.min(maxTags, tagsNode.size()); j++) {
                            if(tagsNode.get(j).path("name").asText().contains(date.substring(0, 4))){
                                maxTags++;
                                continue;
                            }
                            tagList.add(tagsNode.get(j).path("name").asText());
                        }
                    }
                    String[] tagsArray = tagList.toArray(new String[0]);

                    // 5. 构建对象
                    LibraryAnimeVO libraryAnimeVO = LibraryAnimeVO.builder()
                            .bangumiId(String.valueOf(bangumiId)) // VO里定义的是String
                            .name(name)
                            .nameCn(nameCn)
                            .image(image)
                            .date(date)
                            .rating(rating)
                            .rank(rank)
                            .ratingCount(ratingCount)
                            .episodes(episodes)
                            .director(director)
                            .productionCompany(productionCompany)
                            .tags(tagsArray)
                            .build();

                    animeList.add(libraryAnimeVO);
                }
            }
        } catch (JsonProcessingException e) {
            // 建议使用 log 打印堆栈，而不是直接抛出 RuntimeException，根据你的业务需求调整
            e.printStackTrace();
            throw new BusinessException("json解析错误");
        }

        result.setList(animeList);
        // 可以在这里设置 result.setTotal(...) 如果 API 返回了总数
        return result;
    }
}
