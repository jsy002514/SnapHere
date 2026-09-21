package com.snaphere.api.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.snaphere.api.common.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigTest {
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void 미인증_응답은_완전한_JSON과_traceId를_반환한다() throws Exception {
        SecurityConfig security = new SecurityConfig(objectMapper);
        TraceIdFilter traceIdFilter = new TraceIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/admin/reports");
        request.addHeader(TraceIdFilter.HEADER, "postman-trace-401");
        MockHttpServletResponse response = new MockHttpServletResponse();

        traceIdFilter.doFilter(request, response, (req, res) -> security.unauthorized(
                (HttpServletRequest) req,
                (HttpServletResponse) res,
                new BadCredentialsException("test")));

        JsonNode body = objectMapper.readTree(response.getContentAsByteArray());
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader(TraceIdFilter.HEADER)).isEqualTo("postman-trace-401");
        assertThat(body.path("success").asBoolean()).isFalse();
        assertThat(body.path("error").path("code").asText()).isEqualTo("AUTH_REQUIRED");
        assertThat(body.path("traceId").asText()).isEqualTo("postman-trace-401");
        assertThat(body.path("timestamp").asText()).endsWith("+09:00");
    }

    @Test
    void traceId_필터는_Spring_Security보다_먼저_실행된다() {
        Order order = TraceIdFilter.class.getAnnotation(Order.class);
        assertThat(order).isNotNull();
        assertThat(order.value()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
    }
}
