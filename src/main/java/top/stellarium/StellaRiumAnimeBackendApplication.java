package top.stellarium;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class StellaRiumAnimeBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(StellaRiumAnimeBackendApplication.class, args);
    }

}
