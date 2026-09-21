package com.snaphere.api.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class WebConfigTest {
    @Test
    void 허용목록_origin만_CORS를_통과한다() {
        var source=new WebConfig().corsConfigurationSource("https://app.example, https://admin.example");
        MockHttpServletRequest request=new MockHttpServletRequest("OPTIONS","/api/v1/places");
        var config=source.getCorsConfiguration(request);
        assertThat(config).isNotNull();
        assertThat(config.getAllowedOrigins()).containsExactly("https://app.example","https://admin.example");
        assertThat(config.getAllowedOrigins()).doesNotContain("*");
        assertThat(config.getExposedHeaders()).contains("X-Trace-Id");
    }

    @Test
    void 빈_설정은_모든_origin을_허용하지_않는다() {
        var source=new WebConfig().corsConfigurationSource("");
        MockHttpServletRequest request=new MockHttpServletRequest("OPTIONS","/api/v1/places");
        assertThat(source.getCorsConfiguration(request).getAllowedOrigins()).isNullOrEmpty();
    }
}
