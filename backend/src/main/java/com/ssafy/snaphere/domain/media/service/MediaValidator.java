package com.ssafy.snaphere.domain.media.service;

import com.ssafy.snaphere.domain.media.dto.MediaDtos.FileRequest;
import com.ssafy.snaphere.global.error.BusinessException;
import com.ssafy.snaphere.global.error.ErrorCode;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 업로드 파일 형식·용량 검증과 저장 키 생성. */
@Component
public class MediaValidator {

    private static final Set<String> IMAGE_TYPES =
            Set.of("image/jpeg", "image/jpg", "image/png", "image/heic", "image/heif", "image/webp");
    private static final Set<String> VIDEO_TYPES =
            Set.of("video/mp4", "video/quicktime");

    @Value("${app.post.max-image-bytes}")   private long maxImageBytes;
    @Value("${app.post.max-video-bytes}")   private long maxVideoBytes;
    @Value("${app.post.max-video-seconds}") private int maxVideoSeconds;
    @Value("${app.post.max-image-count}")   private int maxCount;

    public enum Kind { IMAGE, VIDEO }

    public Kind validate(FileRequest f) {
        String ct = f.contentType() == null ? "" : f.contentType().toLowerCase();

        if (IMAGE_TYPES.contains(ct)) {
            if (f.fileSize() > maxImageBytes) throw new BusinessException(ErrorCode.MEDIA_002, "fileSize");
            return Kind.IMAGE;
        }
        if (VIDEO_TYPES.contains(ct)) {
            if (f.fileSize() > maxVideoBytes) throw new BusinessException(ErrorCode.MEDIA_002, "fileSize");
            // 길이를 안 보내면 검사할 수 없다. 클라이언트가 생략하면 통과시키되 등록 단계에서 다시 본다.
            if (f.durationSec() != null && f.durationSec() > maxVideoSeconds) {
                throw new BusinessException(ErrorCode.MEDIA_003, "durationSec");
            }
            return Kind.VIDEO;
        }
        throw new BusinessException(ErrorCode.MEDIA_001, "contentType");
    }

    public void validateCount(int count) {
        if (count > maxCount) throw new BusinessException(ErrorCode.MEDIA_002, "files");
    }

    /**
     * 저장 키는 서버가 만든다. 클라이언트가 준 파일명을 그대로 쓰면
     * 경로 탈출·덮어쓰기·한글 파일명 인코딩 문제가 한꺼번에 생긴다.
     */
    public String newMediaKey(Kind kind, String originalFileName) {
        LocalDate today = LocalDate.now();
        String ext = extensionOf(originalFileName, kind);
        return "posts/%d/%02d/%02d/%s%s".formatted(
                today.getYear(), today.getMonthValue(), today.getDayOfMonth(),
                UUID.randomUUID().toString().replace("-", ""), ext);
    }

    private static String extensionOf(String fileName, Kind kind) {
        if (fileName != null) {
            int dot = fileName.lastIndexOf('.');
            if (dot > 0 && dot < fileName.length() - 1) {
                String ext = fileName.substring(dot).toLowerCase();
                if (ext.matches("\\.[a-z0-9]{2,5}")) return ext;
            }
        }
        return kind == Kind.VIDEO ? ".mp4" : ".jpg";
    }
}
