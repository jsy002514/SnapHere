package com.snaphere.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SnapHereApplication {

    public static void main(String[] args) {
        SpringApplication.run(SnapHereApplication.class, args);
    }
}
