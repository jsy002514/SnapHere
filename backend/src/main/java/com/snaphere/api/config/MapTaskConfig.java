package com.snaphere.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class MapTaskConfig {
    public static final String MAP_TASK_EXECUTOR = "mapTaskExecutor";

    @Bean(name = MAP_TASK_EXECUTOR)
    public Executor mapTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("map-refresh-");
        executor.initialize();
        return executor;
    }
}
