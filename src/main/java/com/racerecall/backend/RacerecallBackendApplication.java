package com.racerecall.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;

@SpringBootApplication
@EnableCaching
public class RacerecallBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(RacerecallBackendApplication.class, args);
    }

    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }

    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager(
                "sessionsByYear",
                "sessionByKey",
                "resultsBySession",
                "gridBySession",
                "weatherBySession",
                "driversBySession",
                "driversByMeeting"
        );
    }
}
