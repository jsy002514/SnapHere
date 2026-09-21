package com.snaphere.api.common.web;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TraceIdFilterTest {
    private final TraceIdFilter filter=new TraceIdFilter();

    @Test
    void 요청_응답_MDC가_같은_traceId를_쓴다() throws Exception {
        MockHttpServletRequest request=new MockHttpServletRequest();
        request.addHeader(TraceIdFilter.HEADER,"trace-client-1");
        MockHttpServletResponse response=new MockHttpServletResponse();
        AtomicReference<String> inside=new AtomicReference<>();

        filter.doFilter(request,response,(req,res) -> inside.set(MDC.get(TraceIdFilter.MDC_KEY)));

        assertThat(inside.get()).isEqualTo("trace-client-1");
        assertThat(response.getHeader(TraceIdFilter.HEADER)).isEqualTo("trace-client-1");
        assertThat(TraceIdFilter.currentTraceId(request)).isEqualTo("trace-client-1");
        assertThat(MDC.get(TraceIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void 헤더가_없으면_서버가_traceId를_만든다() throws Exception {
        MockHttpServletRequest request=new MockHttpServletRequest();
        MockHttpServletResponse response=new MockHttpServletResponse();
        filter.doFilter(request,response,(req,res) -> { });
        assertThat(response.getHeader(TraceIdFilter.HEADER)).startsWith("tr_");
    }
}
