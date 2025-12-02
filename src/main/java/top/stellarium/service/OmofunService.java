package top.stellarium.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import top.stellarium.pojo.vo.PlayerInfoVO;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class OmofunService {

    private static final String BASE_URL = "https://omofun.icu";
    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    /**
     * 核心方法：根据标题获取资源列表
     */
    public List<PlayerInfoVO.episode> getEpisodesByTitle(String fullTitle) {
        try {
            // 1. 提取搜索关键词 (取第一个词)
            String keyword = extractFirstWord(fullTitle);
            if (!StringUtils.hasText(keyword)) return new ArrayList<>();

            // 2. 搜索并获取最佳匹配的详情页链接
            String detailUrl = searchAndFindBestMatch(keyword, fullTitle);
            if (detailUrl == null) return new ArrayList<>();

            if(detailUrl.endsWith(".html")){
                detailUrl = detailUrl.replace(".html", "");
            }

            // 3. 解析详情页获取剧集
            return parseEpisodes(detailUrl);

        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>(); // 发生异常返回空列表，不要阻断主流程
        }
    }

    /**
     * 步骤1：简单的关键词提取
     */
    private String extractFirstWord(String title) {
        if (title == null) return "";
        // 替换掉特殊符号，只保留文字和空格，防止搜索出错
        log.info("获取标题: {}", title);
        String clean = title.replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fa5\\s]", " ");
        String[] parts = clean.trim().split("\\s+");
        String ttl =  parts.length > 0 ? parts[0] : "";
        if(ttl == "剧场版")ttl= parts[1];
        log.info("获取到标题: {}", ttl);
        return ttl;
    }

    /**
     * 步骤2：搜索并计算编辑距离
     */
    private String searchAndFindBestMatch(String keyword, String originalTitle) throws IOException {
        String searchUrl = BASE_URL + "/vod/search?wd=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8);

        Document doc = Jsoup.connect(searchUrl)
                .userAgent(UA)
                .timeout(5000)
                .get();

        // 根据 css1.json: div.module-card-item-title > a
        Elements items = doc.select("div.module-card-item-title > a");

        String bestUrl = null;
        int minDistance = Integer.MAX_VALUE;

        for (Element item : items) {
            String resultTitle = item.text();
            String resultHref = item.attr("href");

            // 计算编辑距离
            int distance = calculateLevenshteinDistance(originalTitle, resultTitle);

            // 简单的优选逻辑：距离越小越好。
            // 如果完全包含（比如 "进击的巨人" 和 "进击的巨人最终季"），也可以加分
            if (distance < minDistance) {
                minDistance = distance;
                bestUrl = resultHref;
            }
        }

        if (bestUrl != null && !bestUrl.startsWith("http")) {
            bestUrl = BASE_URL + bestUrl;
        }
        return bestUrl;
    }

    /**
     * 步骤3：解析详情页
     */
    private List<PlayerInfoVO.episode> parseEpisodes(String detailUrl) throws IOException {
        Document doc = Jsoup.connect(detailUrl)
                .userAgent(UA)
                .timeout(5000)
                .get();

        List<PlayerInfoVO.episode> episodeList = new ArrayList<>();
        log.info("开始解析详情页: {}", detailUrl);
        // 1. 寻找播放列表容器
        // Omofun 通常有多个线路，我们要找 "module-play-list-content"
        // 这里的逻辑是：优先找包含 "高清" 或 "独家" 的 Tab，或者直接取第一个列表

        // 获取所有 Tab 名称
        Elements tabs = doc.select(".module-tab-items-box .module-tab-item span");
        // 获取所有 列表容器
        Elements contentLists = doc.select(".module-play-list-content");

        int targetIndex = 0;
        // 尝试匹配最佳线路 (参考 JSON 配置的正则)
        for (int i = 0; i < tabs.size(); i++) {
            String tabName = tabs.get(i).text();
            if (tabName.contains("独家") || tabName.contains("高清")) {
                targetIndex = i;
                break;
            }
        }

        if (targetIndex >= contentLists.size()) {
            return episodeList; // 没有找到资源
        }

        // 2. 提取该线路下的所有集数
        Element targetList = contentLists.get(targetIndex);
        Elements links = targetList.select("a");

        int sort = 1;
        for (Element link : links) {
            PlayerInfoVO.episode ep = new PlayerInfoVO.episode();
            ep.setSort(sort++);
            ep.setTitle(link.text());

            String href = link.attr("href");
            if (!href.startsWith("http")) {
                href = BASE_URL + href;
            }
            // 注意：这里存的是播放页 URL (如 /vod/play/id/123.html)
            // 在这一步不解析出 m3u8，以保证性能
            ep.setUrl(href);

            episodeList.add(ep);
        }

        return episodeList;
    }

    /**
     * 工具：计算编辑距离 (Levenshtein Distance)
     * 也可以使用 Apache Commons Text 库的 LevenshteinDistance
     */
    private int calculateLevenshteinDistance(String x, String y) {
        int[][] dp = new int[x.length() + 1][y.length() + 1];

        for (int i = 0; i <= x.length(); i++) {
            for (int j = 0; j <= y.length(); j++) {
                if (i == 0) {
                    dp[i][j] = j;
                } else if (j == 0) {
                    dp[i][j] = i;
                } else {
                    dp[i][j] = min(
                            dp[i - 1][j - 1] + (x.charAt(i - 1) == y.charAt(j - 1) ? 0 : 1),
                            dp[i - 1][j] + 1,
                            dp[i][j - 1] + 1
                    );
                }
            }
        }
        return dp[x.length()][y.length()];
    }

    private int min(int... numbers) {
        int min = Integer.MAX_VALUE;
        for (int num : numbers) {
            if (num < min) min = num;
        }
        return min;
    }
}