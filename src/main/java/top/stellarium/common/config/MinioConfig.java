package top.stellarium.common.config;


import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import top.stellarium.common.properties.MinioProperties;
import top.stellarium.common.utils.MinioUtil;

/**
 * Configuration类把Bean注入IOC容器，也就是与IOC容器互动
 */
@Configuration
@Slf4j
public class MinioConfig {

    @Bean
    @ConditionalOnMissingBean // 防止多次创建
    public MinioUtil minioUtil(MinioProperties minioProperties){
        log.info("创建minioUtil");
        return new MinioUtil(minioProperties.getUrl(),
                minioProperties.getAccessKey(),
                minioProperties.getSecretKey(),
                minioProperties.getBucketName());
    }

}