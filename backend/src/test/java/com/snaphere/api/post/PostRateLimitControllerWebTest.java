package com.snaphere.api.post;

import com.snaphere.api.common.error.ApiException;
import com.snaphere.api.common.error.ErrorCode;
import com.snaphere.api.common.error.GlobalExceptionHandler;
import com.snaphere.api.common.security.CurrentUser;
import com.snaphere.api.common.security.CurrentUserProvider;
import com.snaphere.api.common.web.TraceIdFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 업로드 남용 방어 회귀 테스트. (PST-029, PST-030)
 *
 * <p>{@link UploadLimitCheckerTest} 는 한도 로직이 올바른 {@link ApiException} 을 던지는지
 * 서비스 단위에서 검증한다. 이 테스트는 그 예외가 실제 HTTP 응답에서 <b>429 Too Many Requests</b>
 * 와 {@code error.retryAfterSec} 로 나가는지를 컨트롤러 경계에서 확인한다 — 클라이언트가 재시도
 * 시점을 계산할 수 있어야 한다.
 */
class PostRateLimitControllerWebTest {

    private MockMvc mvc;
    private PostCreateService postCreateService;

    /** @Valid 를 통과하는 최소 본문. 개수 제약은 서비스에서 보므로 형식만 맞추면 서비스까지 도달한다. */
    private static final String VALID_BODY = """
            {"originalLanguageCode":"ko","source":"CAMERA"}
            """;

    @BeforeEach
    void setUp() {
        postCreateService = mock(PostCreateService.class);
        PostEditService postEditService = mock(PostEditService.class);
        CurrentUserProvider users = mock(CurrentUserProvider.class);
        when(users.require(any())).thenReturn(new CurrentUser(UUID.randomUUID()));

        mvc = MockMvcBuilders
                .standaloneSetup(new PostController(postCreateService, postEditService, users))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new TraceIdFilter())
                .build();
    }

    @Test
    @DisplayName("하루 한도를 채운 뒤 또 올리면 429 와 retryAfterSec 를 준다")
    void 하루_한도_초과는_429() throws Exception {
        when(postCreateService.create(any(), any()))
                .thenThrow(new ApiException(ErrorCode.POST_DAILY_LIMIT, Map.of(), 3600));

        mvc.perform(post("/api/v1/posts").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("POST_DAILY_LIMIT"))
                .andExpect(jsonPath("$.error.retryAfterSec").value(3600));
    }

    @Test
    @DisplayName("같은 장소 한도를 채운 뒤 또 올리면 429 와 retryAfterSec 를 준다")
    void 장소_한도_초과는_429() throws Exception {
        when(postCreateService.create(any(), any()))
                .thenThrow(new ApiException(ErrorCode.POST_PLACE_DAILY_LIMIT, Map.of(), 7200));

        mvc.perform(post("/api/v1/posts").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("POST_PLACE_DAILY_LIMIT"))
                .andExpect(jsonPath("$.error.retryAfterSec").value(7200));
    }
}
