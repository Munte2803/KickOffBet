package com.munte.KickOffBet.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestClient;

import java.util.concurrent.Executor;

@Configuration
public class FootballDataConfig {

    @Value("${football.data.api.url}")
    private String baseUrl;

    @Value("${football.data.api.token}")
    private String apiToken;

    @Bean
    public RestClient footballDataRestClient() {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("X-Auth-Token", apiToken)
                .defaultHeader("Accept", "application/json")
                .build();
    }

    @Bean(name = "threadPoolTaskExecutor")
    public Executor threadPoolTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("SyncThread-");
        executor.initialize();
        return executor;
    }
}