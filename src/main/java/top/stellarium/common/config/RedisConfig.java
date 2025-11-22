package top.stellarium.common.config;

import com.alibaba.fastjson.support.spring.GenericFastJsonRedisSerializer;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import tools.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.*;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class RedisConfig {

    /**
     * 自定义 RedisTemplate：解决序列化乱码、泛型反序列化问题
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(redisConnectionFactory);
        // 给序列化器设置 ObjectMapper（核心步骤，之前的双参数错误由此替换）

        // ======================== String 序列化（key 专用）========================
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        GenericFastJsonRedisSerializer genericFastJsonRedisSerializer = new GenericFastJsonRedisSerializer();

        // ======================== 配置 RedisTemplate 序列化方式 ========================
        // key 序列化：String（避免乱码）
        redisTemplate.setKeySerializer(stringSerializer);
        // hash 的 key 序列化：String
        redisTemplate.setHashKeySerializer(stringSerializer);
        // value 序列化：Jackson（JSON 格式，保留类型信息）
        redisTemplate.setValueSerializer(genericFastJsonRedisSerializer);
        // hash 的 value 序列化：Jackson
        redisTemplate.setHashValueSerializer(genericFastJsonRedisSerializer);

        // 初始化模板
        redisTemplate.afterPropertiesSet();
        return redisTemplate;
    }

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory redisConnectionFactory) {
        // 复用 RedisTemplate 的 Jackson 配置（避免重复代码）
        ObjectMapper objectMapper = JsonMapper.builder().build();
        GenericFastJsonRedisSerializer genericFastJsonRedisSerializer = new GenericFastJsonRedisSerializer();
// ======================== 缓存基础配置 ========================
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(1)) // 默认缓存过期 1 小时
                .disableCachingNullValues() // 不缓存 null 值
                // key 序列化：String
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                // value 序列化：Jackson（和 RedisTemplate 一致）
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(genericFastJsonRedisSerializer));

        // ======================== 特定缓存配置（按需添加）=======================
        Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();
        cacheConfigs.put("calendarCache", defaultConfig.entryTtl(Duration.ofHours(24)));
        cacheConfigs.put("todayCache", defaultConfig.entryTtl(Duration.ofHours(24)));
        cacheConfigs.put("popularCache", defaultConfig.entryTtl(Duration.ofDays(31)));

        // ======================== 创建 CacheManager ========================
        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(defaultConfig) // 全局默认配置
                .withInitialCacheConfigurations(cacheConfigs) // 自定义缓存配置
                .build();
    }
}