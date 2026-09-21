package com.snaphere.api.report;

import com.snaphere.api.common.error.ApiException;
import com.snaphere.api.common.error.ErrorCode;
import com.snaphere.api.post.PostStatus;
import com.snaphere.api.post.entity.PostEntity;
import com.snaphere.api.post.repository.PostRepository;
import com.snaphere.api.post.tier.PhotoSource;
import com.snaphere.api.post.tier.TrustTier;
import com.snaphere.api.report.dto.CreateReportRequest;
import com.snaphere.api.report.dto.ReportReceiptResponse;
import com.snaphere.api.report.repository.ReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 게시글 신고 — PST-043, PST-044, PST-045 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PostReportServiceTest {

    private static final long POST_ID = 7L;
    private static final UUID AUTHOR = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID REPORTER = UUID.fromString("99999999-8888-7777-6666-555555555555");

    @Mock private ReportRepository reports;
    @Mock private PostRepository posts;

    private PostReportService service;

    @BeforeEach
    void setUp() {
        service = new PostReportService(reports, posts);
        when(posts.findById(POST_ID)).thenReturn(Optional.of(activePost()));
        when(reports.existsByReporterIdAndTargetTypeAndTargetId(any(), any(), anyLong()))
                .thenReturn(false);
        when(reports.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        when(reports.countByTargetTypeAndTargetId(any(), anyLong())).thenReturn(1L);
    }

    private static PostEntity activePost() {
        return PostEntity.create(AUTHOR, 1L, null, 1, "내용", TrustTier.HIGH,
                37.5, 127.0, null, PhotoSource.ALBUM);
    }

    private static CreateReportRequest request(ReportReason reason) {
        return new CreateReportRequest(reason, "실제 위치와 다릅니다");
    }

    private static ErrorCode codeOf(Throwable t) {
        return ((ApiException) t).errorCode();
    }

    @Test
    @DisplayName("신고하면 접수증을 준다 — 상태는 PENDING")
    void 접수() {
        ReportReceiptResponse receipt =
                service.report(POST_ID, REPORTER, request(ReportReason.PLACE_MISMATCH));

        assertThat(receipt.status()).isEqualTo("PENDING");
        assertThat(receipt.createdAt()).isNotNull();
        verify(reports).saveAndFlush(any());
    }

    @Test
    @DisplayName("접수증에 처리 결과를 담지 않는다 — 3건 모으는 방법을 알려 주는 셈이 된다")
    void 접수증은_결과를_숨긴다() {
        ReportReceiptResponse receipt =
                service.report(POST_ID, REPORTER, request(ReportReason.SPAM));

        assertThat(receipt.status()).isEqualTo("PENDING");
        assertThat(ReportReceiptResponse.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactly("reportId", "status", "createdAt");
    }

    @Test
    @DisplayName("같은 사람이 같은 대상을 두 번 신고하면 REPORT_DUPLICATE")
    void 중복_신고() {
        when(reports.existsByReporterIdAndTargetTypeAndTargetId(
                REPORTER, ReportTargetType.POST, POST_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.report(POST_ID, REPORTER, request(ReportReason.SPAM)))
                .satisfies(t -> assertThat(codeOf(t)).isEqualTo(ErrorCode.REPORT_DUPLICATE));
        verify(reports, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("사전 검사를 지나쳐도 UNIQUE 가 막고 같은 코드를 준다")
    void 경합_중복() {
        when(reports.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("dup"));

        assertThatThrownBy(() -> service.report(POST_ID, REPORTER, request(ReportReason.SPAM)))
                .satisfies(t -> assertThat(codeOf(t)).isEqualTo(ErrorCode.REPORT_DUPLICATE));
    }

    @Test
    @DisplayName("없는 게시글은 POST_NOT_FOUND")
    void 없는_게시글() {
        when(posts.findById(POST_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.report(POST_ID, REPORTER, request(ReportReason.SPAM)))
                .satisfies(t -> assertThat(codeOf(t)).isEqualTo(ErrorCode.POST_NOT_FOUND));
    }

    @Test
    @DisplayName("삭제된 게시글은 신고할 수 없다 — 되돌아오지 않는다")
    void 삭제된_게시글() {
        PostEntity deleted = activePost();
        deleted.softDelete();
        when(posts.findById(POST_ID)).thenReturn(Optional.of(deleted));

        assertThatThrownBy(() -> service.report(POST_ID, REPORTER, request(ReportReason.SPAM)))
                .satisfies(t -> assertThat(codeOf(t)).isEqualTo(ErrorCode.POST_NOT_FOUND));
    }

    @Test
    @DisplayName("이미 가려진 게시글도 신고를 받는다 — 복구될 수 있어 누적 근거를 버리면 안 된다")
    void 가려진_게시글도_접수() {
        service.report(POST_ID, REPORTER, request(ReportReason.INAPPROPRIATE));
        verify(reports).saveAndFlush(any());
    }

    @Test
    @DisplayName("사유 5종을 모두 받는다")
    void 사유_전체() {
        for (ReportReason reason : ReportReason.values()) {
            assertThat(service.report(POST_ID, REPORTER, request(reason)).status())
                    .isEqualTo("PENDING");
        }
        assertThat(ReportReason.values()).extracting(Enum::name).containsExactly(
                "INAPPROPRIATE", "COPYRIGHT", "PLACE_MISMATCH", "SPAM", "OTHER");
    }

    @Test
    @DisplayName("상세 설명은 선택이다 — 필수로 만들면 신고를 포기한다")
    void 상세_선택() {
        ReportReceiptResponse receipt = service.report(POST_ID, REPORTER,
                new CreateReportRequest(ReportReason.OTHER, null));

        assertThat(receipt.status()).isEqualTo("PENDING");
    }

    // ───────────────────────────────────────────── 자동 블라인드 (PST-045)

    @Test
    @DisplayName("신고 3건에 도달하면 게시글을 가린다")
    void 자동_블라인드() {
        PostEntity post = activePost();
        when(posts.findById(POST_ID)).thenReturn(Optional.of(post));
        when(reports.countByTargetTypeAndTargetId(ReportTargetType.POST, POST_ID)).thenReturn(3L);

        service.report(POST_ID, REPORTER, request(ReportReason.SPAM));

        assertThat(post.getStatus()).isEqualTo(PostStatus.HIDDEN);
    }

    @Test
    @DisplayName("2건까지는 가리지 않는다")
    void 기준_미달() {
        PostEntity post = activePost();
        when(posts.findById(POST_ID)).thenReturn(Optional.of(post));
        when(reports.countByTargetTypeAndTargetId(ReportTargetType.POST, POST_ID)).thenReturn(2L);

        service.report(POST_ID, REPORTER, request(ReportReason.SPAM));

        assertThat(post.getStatus()).isEqualTo(PostStatus.ACTIVE);
    }

    @Test
    @DisplayName("가려도 접수증 상태는 PENDING 이다 — 운영자 검토가 남아 있다")
    void 가려도_접수증은_PENDING() {
        when(reports.countByTargetTypeAndTargetId(any(), anyLong())).thenReturn(5L);

        assertThat(service.report(POST_ID, REPORTER, request(ReportReason.SPAM)).status())
                .isEqualTo("PENDING");
    }

    @Test
    @DisplayName("가리기는 조용히 한다 — 언제 가려지는지 응답으로 알 수 없다")
    void 가리기는_응답에_없음() {
        PostEntity post = activePost();
        when(posts.findById(POST_ID)).thenReturn(Optional.of(post));
        when(reports.countByTargetTypeAndTargetId(any(), anyLong())).thenReturn(3L);

        ReportReceiptResponse under = service.report(POST_ID, REPORTER, request(ReportReason.SPAM));

        assertThat(post.getStatus()).isEqualTo(PostStatus.HIDDEN);
        assertThat(under.status()).isEqualTo("PENDING");
    }
}
