package top.stellarium.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import top.stellarium.pojo.dto.CollectionDTO;
import top.stellarium.pojo.dto.LibraryDTO;

@Slf4j
@Service
public class BangumiService {

    private final WebClient webClient;

    // 1. 使用 @Value 注入配置的 URL
    private final String bangumiBaseUrl;

    // 2. 使用 @Value 注入配置的 User-Agent
    private final String userAgent;

    // 构造函数用于依赖注入和WebClient初始化
    public BangumiService(
            @Value("${stella-rium.bangumi.url}") String bangumiBaseUrl,
            @Value("${stella-rium.bangumi.user-agent}") String userAgent) {

        this.bangumiBaseUrl = bangumiBaseUrl;
        this.userAgent = userAgent;

        this.webClient = WebClient.builder()
                .baseUrl(this.bangumiBaseUrl)
                // 必须添加 User-Agent
                .defaultHeader(HttpHeaders.USER_AGENT, this.userAgent)
                .build();
    }

    /**
     * 获取每日放送
     *
     * @return
     */
    public Mono<String> getCalendar() {
        return webClient.get()
                .uri("/calendar") // 使用相对路径
                .retrieve()
                // 示例：返回原始字符串
                .bodyToMono(String.class);
    }

    /**
     * 获取特定排名的动漫
     * Bangumi API 逻辑: Rank 1 = Offset 0, Limit 1
     *
     * @param rank 排名 (从1开始)
     * @return Mono<String> 原始JSON
     */
    public Mono<String> getAnimeAtRank(int rank) {
        // 防止 rank < 1 导致报错
        int offset = Math.max(0, rank - 1);

        return webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/v0/subjects")
                        .queryParam("type", 2)       // 2 = Anime
                        .queryParam("sort", "rank")  // 按排名排序
                        .queryParam("limit", 1)      // 只取1条
                        .queryParam("offset", offset) // 跳过前 n-1 条
                        .build())
                .retrieve()
                .bodyToMono(String.class);
    }

    /**
     * 获取特定的动漫
     *
     * @param id bangumi的唯一id
     * @return Mono<String> 原始JSON
     */
    public Mono<String> getSpecificAnime(long id) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/v0/subjects/{id}")
                        .build(id))
                .retrieve()
                .bodyToMono(String.class);
    }

    /**
     * 获取排行榜
     *
     * @param
     * @return Mono<String> 原始JSON
     */
    public Mono<String> getLibrary(LibraryDTO libraryDTO) {

        return webClient.get()
                .uri(uriBuilder -> {
                    // 先添加必传参数
                    uriBuilder.path("/v0/subjects")
                            .queryParam("type", 2)
                            .queryParam("limit", libraryDTO.getLimit())
                            .queryParam("offset", (libraryDTO.getPage() - 1) * libraryDTO.getLimit());
                    // 非 null 参数才添加为 query 参数
                    if (libraryDTO.getSort() != null) {
                        uriBuilder.queryParam("sort", libraryDTO.getSort());
                    }
                    if (libraryDTO.getYear() != null) {
                        uriBuilder.queryParam("year", Integer.parseInt(libraryDTO.getYear()));
                    }
                    return uriBuilder.build();
                })
                .retrieve()
                .bodyToMono(String.class);
    }

    /**
     * 获取动漫收藏
     *
     * @param collectionDTO
     * @return
     */
    public Mono<String> getAnimeCollection(CollectionDTO collectionDTO) {

        return webClient.get()
                .uri(uriBuilder -> {
                    // 1. 设置路径和固定参数
                    uriBuilder.path("/v0/users/{username}/collections")
                            .queryParam("subject_type", 2)
                            .queryParam("limit", collectionDTO.getLimit())
                            .queryParam("offset", (collectionDTO.getPage() - 1) * collectionDTO.getLimit());
                    // 2. 动态添加参数
                    if (collectionDTO.getStatus() != null && collectionDTO.getStatus() != 0) {
                        uriBuilder.queryParam("type", collectionDTO.getStatus());
                    }

                    // 3. 最后一步：生成 URI 并填充 {username}
                    return uriBuilder.build(collectionDTO.getBangumiId());
                })
                .retrieve()
                .bodyToMono(String.class);
    }

    /**
     * 获取角色收藏
     * @param collectionDTO
     * @return
     */
    public Mono<String> getCharacterCollection(CollectionDTO collectionDTO){
        log.info("获取角色收藏: {}", collectionDTO);
        return webClient.get()
                .uri(uriBuilder -> {
                    // 1. 设置路径和固定参数
                    uriBuilder.path("/v0/users/{username}/collections/-/characters")
                            .queryParam("limit", collectionDTO.getLimit())
                            .queryParam("offset", (collectionDTO.getPage() - 1) * collectionDTO.getLimit());
                    // 3. 最后一步：生成 URI 并填充 {username}
                    return uriBuilder.build(collectionDTO.getBangumiId());
                })
                .retrieve()
                .bodyToMono(String.class);
    }

    /**
     * 获取特定角色
     * @param id
     * @return
     */
    public Mono<String> getCharacter(Long id){
        return webClient.get()
                .uri(uriBuilder -> {
                    uriBuilder.path("/v0/characters/{id}");
                    return uriBuilder.build(id);
                })
                .retrieve()
                .bodyToMono(String.class);
    }

    /**
     * 获取特定角色关联的条目
     * @param id
     * @return
     */
    public Mono<String> getCharacterSubject(Long id){
        return webClient.get()
                .uri(uriBuilder -> {
                    uriBuilder.path("/v0/characters/{id}/subjects");
                    return uriBuilder.build(id);
                })
                .retrieve()
                .bodyToMono(String.class);
    }
}