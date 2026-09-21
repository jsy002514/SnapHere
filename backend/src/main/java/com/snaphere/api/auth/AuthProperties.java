package com.snaphere.api.auth;
import org.springframework.boot.context.properties.ConfigurationProperties;
@ConfigurationProperties(prefix="snaphere.auth") public record AuthProperties(String googleClientId, String jwtSecret, String termsVersion) {}
