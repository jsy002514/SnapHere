package com.ssafy.snaphere.global.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AsyncConfig {

    /** 썸네일 생성·해시 계산·푸시 발송처럼 응답을 막으면 안 되는 작업용. */
    @Bean(name = "mediaExecutor")
    public Executor mediaExecutor() {
        var e = new ThreadPoolTaskExecutor();
        e.setCorePoolSize(4); e.setMaxPoolSize(8); e.setQueueCapacity(200);
        e.setThreadNamePrefix("media-"); e.initialize();
        return e;
    }

    @Bean(name = "notificationExecutor")
    public Executor notificationExecutor() {
        var e = new ThreadPoolTaskExecutor();
        e.setCorePoolSize(2); e.setMaxPoolSize(4); e.setQueueCapacity(500);
        e.setThreadNamePrefix("notify-"); e.initialize();
        return e;
    }
}
