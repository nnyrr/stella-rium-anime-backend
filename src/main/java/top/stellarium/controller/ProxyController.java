package top.stellarium.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import top.stellarium.common.result.Result;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/proxy")
@Slf4j
@Tag(name = "代理与解析接口")
public class ProxyController {

    // 默认 Referer (Omofun 等 MacCMS 站点通常需要)
    private static final String TARGET_REFERER = "https://omofun.icu/";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    /**
     * 接口三：[新增] 解析接口
     * 作用：接收前端传来的 HTML/视频链接，解析出真实地址和类型
     * 前端根据返回的 type 决定是直连(mp4)还是走代理(m3u8)
     */
    @GetMapping("/resolve")
    public Result<String> resolveUrl(@RequestParam String url) {
        Map<String, String> result = new HashMap<>();
        String realUrl = url;

        // 1. 如果是 HTML 页面，先尝试提取内部视频链接
        if (url.contains(".html")) {
            String extracted = extractM3u8FromHtml(url);
            if (extracted != null) {
                realUrl = extracted;
            }
        }

        return Result.success(realUrl);
    }

    /**
     * 接口一：代理 M3U8 文件
     * 仅当 /resolve 返回 type=m3u8 时，前端才会调用此接口
     */
    @GetMapping("/m3u8")
    public void proxyM3u8(@RequestParam String url, HttpServletRequest request, HttpServletResponse response) {
        try {
            // 双重保障：万一前端没调用 resolve 直接调这个，也尝试提取一下
            if (url.contains(".html")) {
                String extracted = extractM3u8FromHtml(url);
                if (extracted != null) url = extracted;
            }

            // 针对小红书等图床去除 Referer，防止 403
            String currentReferer = TARGET_REFERER;
            if (url.contains("xhscdn.com") || url.contains("v.weishi.qq.com")) {
                currentReferer = "";
            }

            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("GET");
            if (!currentReferer.isEmpty()) {
                connection.setRequestProperty("Referer", currentReferer);
            }
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            // 如果源站直接返回了视频流(MP4)，直接透传 (容错处理)
            String contentType = connection.getContentType();
            if (contentType != null && !contentType.contains("mpegurl") && (contentType.contains("video") || contentType.contains("stream"))) {
                response.setContentType(contentType);
                transferStream(connection, response);
                return;
            }

            // 标准 M3U8 处理：读取文本 -> 替换 TS 路径 -> 返回
            response.setContentType("application/vnd.apple.mpegurl");

            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder modifiedContent = new StringBuilder();
            String line;
            String baseUrl = getBaseUrl(request) + "/proxy/ts?url=";

            while ((line = reader.readLine()) != null) {
                if (!StringUtils.hasText(line) || line.startsWith("#")) {
                    modifiedContent.append(line).append("\n");
                    continue;
                }
                String tsUrl = resolveUrl(url, line.trim());
                String newLink = baseUrl + URLEncoder.encode(tsUrl, StandardCharsets.UTF_8);
                modifiedContent.append(newLink).append("\n");
            }
            response.getWriter().write(modifiedContent.toString());
            reader.close();

        } catch (Exception e) {
            log.error("代理 M3U8 失败: {}", url, e);
            response.setStatus(500);
        }
    }

    /**
     * 接口二：代理 TS 视频流
     */
    @GetMapping("/ts")
    public void proxyTs(@RequestParam String url, HttpServletResponse response) {
        try {
            url = url.replace(" ", "%20");
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Referer", TARGET_REFERER);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setConnectTimeout(10000);

            response.setContentType("video/mp2t");
            transferStream(connection, response);

        } catch (Exception e) {
            // TS 请求断开很正常，不打印堆栈
            response.setStatus(500);
        }
    }

    // --- 辅助方法 ---

    private void transferStream(HttpURLConnection connection, HttpServletResponse response) throws Exception {
        if (connection.getContentLength() > 0) {
            response.setContentLength(connection.getContentLength());
        }
        try (InputStream in = connection.getInputStream();
             OutputStream out = response.getOutputStream()) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
            out.flush();
        }
    }

    private String extractM3u8FromHtml(String pageUrl) {
        try {
            Document doc = Jsoup.connect(pageUrl)
                    .userAgent(USER_AGENT)
                    .timeout(5000)
                    .get();
            String html = doc.html();
            Set<String> candidates = new LinkedHashSet<>();

            // 策略1: JSON 变量 "url":"..."
            Pattern pJson = Pattern.compile("\"url\"\\s*:\\s*\"(http[^\"]+)\"");
            Matcher mJson = pJson.matcher(html);
            while (mJson.find()) candidates.add(mJson.group(1).replace("\\/", "/"));

            // 策略2: 源码直链
            Pattern pRaw = Pattern.compile("http[s]?://(?:[a-zA-Z]|[0-9]|[$-_@.&+]|[!*\\\\(\\\\),]|(?:%[0-9a-fA-F][0-9a-fA-F]))+");
            Matcher mRaw = pRaw.matcher(html);
            while (mRaw.find()) {
                String raw = mRaw.group().replace("\\/", "/");
                if (isVideoUrl(raw)) candidates.add(raw);
            }

            // 优先 M3U8
            for (String cand : candidates) {
                if (cand.contains(".m3u8")) return cand;
            }
            if (!candidates.isEmpty()) return candidates.iterator().next();

            return null;
        } catch (Exception e) {
            log.error("解析 HTML 失败: {}", pageUrl);
            return null;
        }
    }

    private boolean isVideoUrl(String url) {
        return url.contains(".m3u8") || url.contains(".mp4") || url.contains(".mkv")
                || url.contains("akamaized") || url.contains("bilivideo.com");
    }

    private String resolveUrl(String baseUrl, String line) {
        if (line.startsWith("http")) return line;
        try {
            URI base = new URI(baseUrl);
            return base.resolve(line).toString();
        } catch (Exception e) {
            return line;
        }
    }

    private String getBaseUrl(HttpServletRequest request) {
        String scheme = request.getScheme();
        String serverName = request.getServerName();
        int serverPort = request.getServerPort();
        String contextPath = request.getContextPath();
        StringBuilder url = new StringBuilder();
        url.append(scheme).append("://").append(serverName);
        if (serverPort != 80 && serverPort != 443) url.append(":").append(serverPort);
        url.append(contextPath);
        return url.toString();
    }
}