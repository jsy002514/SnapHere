package com.snaphere.api.report;

import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * 신고 도메인이 들어오기 전까지 쓰는 구현. 아무도 정지 상태가 아니다.
 *
 * <p><b>실제 구현을 추가할 때 이 파일을 지운다.</b> 조건부 등록을 걸지 않았으므로 구현이 하나 더
 * 생기면 애플리케이션이 뜨지 않고 중복 빈을 알려 준다.
 */
@Component
public class NoOpUploadSuspensionReader implements UploadSuspensionReader {

    @Override
    public Optional<OffsetDateTime> suspendedUntil(UUID userId) {
        return Optional.empty();
    }
}
