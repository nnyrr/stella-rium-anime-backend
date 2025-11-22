package top.stellarium.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                // 文档基础信息（标题、版本、描述等，会显示在文档首页）
                .info(new Info()
                        .title("Stella-rium Anime API 文档")
                        .version("1.0.0")
                        .description("基于 Knife4j + OpenAPI3 的接口文档，支持调试、导出等功能")
                        // 可选：添加联系人信息
                        .contact(new Contact().name("开发者").email("xxx@xxx.com")));
    }
}
