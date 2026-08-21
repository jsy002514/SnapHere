package com.ssafy.snaphere;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@ConfigurationPropertiesScan
@EnableJpaAuditing
@EnableScheduling
@EnableAsync
@SpringBootApplication
public class SnaphereApplication {
    public static void main(String[] args) {
        SpringApplication.run(SnaphereApplication.class, args);
    }
}
