package com.snaphere.api.report;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * 업로드 정지 조회 포트. (PST-032)
 *
 * <p>신고가 누적된 사용자는 24시간 업로드를 막는다. 정지를 걸고 푸는 쪽은 신고 검토(SYS-017,
 * REP 도메인)이고 게시글 등록은 남은 시각만 읽는다.
 *
 * <p><b>{@code reports} 테이블과 {@code users.upload_blocked_until} 컬럼은 아직 없다.</b>
 * 신고 도메인이 다른 담당 범위여서 지금은 {@link NoOpUploadSuspensionReader} 가 항상 비어 있는
 * 값을 준다. {@code users} 는 인증 담당의 테이블이라 이 브랜치에서 컬럼을 추가하지 않는다.
 */
public interface UploadSuspensionReader {

    /** @return 업로드 정지가 걸려 있으면 해제 시각. 정지가 아니면 비어 있음 */
    Optional<OffsetDateTime> suspendedUntil(UUID userId);
}
