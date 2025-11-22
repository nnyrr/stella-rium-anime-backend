package top.stellarium.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import top.stellarium.pojo.vo.IndexAnimeVO;
import top.stellarium.pojo.vo.ListVO;
import top.stellarium.pojo.vo.TodaysPickVO;
import top.stellarium.service.BangumiService;
import top.stellarium.service.IndexService;

import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 主页接口服务
 */
@Service
@Slf4j
public class IndexServiceImpl implements IndexService {

    @Autowired
    private BangumiService bangumiService;
    @Autowired
    private ObjectMapper objectMapper;

    private static final String todayString = "T(java.time.LocalDate).now().format(T(java.time.format.DateTimeFormatter).ofPattern('yyyyMMdd'))";
    private static final String monthString = "T(java.time.LocalDate).now().format(T(java.time.format.DateTimeFormatter).ofPattern('yyyyMM'))";

    /**
     * 获取每日放送
     *
     * @return
     */
    @Cacheable(value = "calendarCache", key = todayString)
    public ListVO<IndexAnimeVO> getCalendar() {
        log.info("今日缓存不存在");
        // 1. 调用 WebClient 获取 JSON 字符串
        Mono<String> mono = bangumiService.getCalendar();
        // 注意：因为方法返回值不是 Mono，这里必须阻塞(block)以获取结果
        String jsonBody = mono.block();
        List<IndexAnimeVO> resultList = new ArrayList<>();
        try {
            // 2. 解析 JSON
            if (jsonBody != null) {
                JsonNode rootNode = objectMapper.readTree(jsonBody);
                // 3. 获取当天
                if (rootNode.isArray()) {
                    DayOfWeek dayOfWeek = LocalDateTime.now().getDayOfWeek();
                    JsonNode dayNode = rootNode.get(dayOfWeek.getValue()-1);

                    // 获取当天的 items 数组
                    JsonNode itemsNode = dayNode.get("items");
                    if (itemsNode != null && itemsNode.isArray()) {
                        for (JsonNode item : itemsNode) {
                            // 4. 提取并转换数据
                            IndexAnimeVO vo = new IndexAnimeVO();
                            // 提取 ID
                            vo.setBangumiId(item.get("id").asLong());
                            // 提取名称 (优先使用中文名，如果没有则使用原名)
                            String nameCn = item.hasNonNull("name_cn") && !item.get("name_cn").asText().isEmpty()
                                    ? item.get("name_cn").asText()
                                    : item.get("name").asText("");
                            vo.setNameCn(nameCn);
                            // 提取图片 (注意判空)
                            if (item.hasNonNull("images") && item.get("images").hasNonNull("large")) {
                                vo.setImage(item.get("images").get("large").asText());
                            }

                            resultList.add(vo);
                        }
                    }

                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            // 这里可以抛出自定义异常或返回空列表
        }

        // 5. 封装返回
        ListVO<IndexAnimeVO> listVO = new ListVO<>();
        listVO.setList(resultList);
        listVO.setTotal(resultList.size());

        return listVO;
    }

    /**
     * 获取今日推荐
     *
     * @return
     */
    @Override
    @Cacheable(value = "todayCache", key = todayString)
    public TodaysPickVO getTodaysPick() {
        // 随机获取500名以内的动漫
        int rank = new Random().nextInt(500) + 1;
        Mono<String> mono = bangumiService.getAnimeAtRank(rank);
        String name = null, nameCn = null, image = null, brief = null;
        Long bangumiId = null;
        String jsonBody = mono.block();
        try {
            if (jsonBody != null) {
                JsonNode rootNode = objectMapper.readTree(jsonBody);
                JsonNode dataNode = rootNode.get("data");
                JsonNode node = dataNode.get(0);
                name = node.get("name").asText();
                nameCn = node.get("name_cn").asText();
                image = node.get("images").get("large").asText();
                brief = node.get("summary").asText();
                bangumiId = node.get("id").asLong();
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        return new TodaysPickVO(name, nameCn, image, bangumiId, brief);
    }

    /**
     * 获得热门动漫
     *
     * @return
     */
    @Override
    @Cacheable(value = "popularCache", key = monthString)
    public ListVO<IndexAnimeVO> getPopular() {
        // 不存在就去爬虫获取bangumi首页有的动漫，获取六个，然后请求api来获得详情
        ListVO<IndexAnimeVO> list = new ListVO<>();
        try {
            log.info("开始爬取 Bangumi 首页...");
            List<Long> bangumiIds = new ArrayList<>();
            Document doc = Jsoup.connect("https://bgm.tv/")
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    .timeout(5000) // 设置超时时间
                    .get();
            Elements targetLinks = doc.select("li.anime.clearit > div.mainItem > a");
            for (Element targetLink : targetLinks) {
                String[] split = targetLink.attr("href").split("/");
                bangumiIds.add(Long.valueOf(split[split.length - 1]));
            }
            Elements targetSubitems = doc.select("li.anime.clearit > div.subitem.clearit > a");
            for (Element targetSubitem : targetSubitems) {
                if (bangumiIds.size() >= 6) break;
                String[] split = targetSubitem.attr("href").split("/");
                bangumiIds.add(Long.valueOf(split[split.length - 1]));
            }

            list.setTotal(bangumiIds.size());
            List<IndexAnimeVO> indexAnimeVOList = new ArrayList<>();
            for (Long bangumiId : bangumiIds) {
                Mono<String> mono = bangumiService.getSpecificAnime(bangumiId);
                String jsonBody = mono.block();
                String name = null, nameCn= null, image= null, tag= null, year= null;
                Double rating= null;
                if (jsonBody != null) {
                    JsonNode rootNode = objectMapper.readTree(jsonBody);
                    name = rootNode.get("name").asText();
                    nameCn = rootNode.get("name_cn").asText();
                    image = rootNode.get("images").get("large").asText();
                    tag = rootNode.get("tags").get(0).get("name").asText();
                    year = rootNode.get("date").asText().split("-")[0];
                    rating = rootNode.get("rating").get("score").asDouble();
                }
                IndexAnimeVO indexAnimeVO = IndexAnimeVO.builder()
                        .bangumiId(bangumiId)
                        .tag(tag)
                        .rating(rating)
                        .year(year)
                        .name(name)
                        .nameCn(nameCn)
                        .image(image)
                        .build();
                indexAnimeVOList.add(indexAnimeVO);
            }
            list.setList(indexAnimeVOList);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return list;
    }
}

