package com.snaphere.api.post.share;

import com.snaphere.api.auth.ExternalIds;
import com.snaphere.api.common.web.ApiResponse;
import com.snaphere.api.common.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * API-PST-014 — 공유 메타데이터 (공개).
 *
 * <p>기능 명세: 5.4 공유
 * <p>요구사항: CMU-019, CMU-020, CMU-021, CMU-022
 *
 * <p>경로가 {@code /api/v1/public/**} 이라 토큰이 없다. 공유 링크는 앱이 없는 사람도 여는
 * 주소이므로 인증을 요구하면 기능 자체가 성립하지 않는다 (CMU-019).
 *
 * <p>비회원이 볼 수 있는 값만 담는다 — 작성자 닉네임·좋아요 수처럼 화면에 더 필요한 것들은
 * 공개 페이지가 API-PST-006 으로 따로 가져온다.
 */
@RestController
@RequestMapping("/api/v1/public/posts/{postId}")
public class ShareController {

    /** 명세의 캐시 5m. 링크 하나가 단체 대화방에 뿌려지면 같은 요청이 수십 번 들어온다. */
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final ShareMetadataService shareMetadataService;

    public ShareController(ShareMetadataService shareMetadataService) {
        this.shareMetadataService = shareMetadataService;
    }

    @GetMapping("/share-metadata")
    public ResponseEntity<ApiResponse<ShareMetadataResponse>> shareMetadata(
            @PathVariable String postId,
            HttpServletRequest httpRequest) {

        ShareMetadataResponse metadata = shareMetadataService.metadata(ExternalIds.parsePost(postId));

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(CACHE_TTL).cachePublic())
                .body(ApiResponse.ok(metadata, TraceIdFilter.currentTraceId(httpRequest)));
    }
}
