package com.ssafy.snaphere.domain.media.service;

import com.ssafy.snaphere.global.error.BusinessException;
import com.ssafy.snaphere.global.error.ErrorCode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * 개발·시연용 로컬 파일 저장소. AWS 키 없이 업로드 흐름 전체를 돌릴 수 있게 한다.
 *
 * ⚠️ 운영에서는 쓰지 말 것. 서버 인스턴스가 여러 대면 파일이 한 대에만 남는다.
 *    운영 전환 시 S3MediaStorage 를 추가하면 이 빈은 자동으로 비활성화된다.
 */
@Slf4j
@Component
@ConditionalOnMissingBean(name = "s3MediaStorage")
public class LocalMediaStorage implements MediaStorage {

    private final Path root;
    private final String baseUrl;
    private final int expiresIn;

    public LocalMediaStorage(@Value("${app.media.local-dir:./media-store}") String dir,
                             @Value("${app.media.base-url:http://localhost:8080/media}") String baseUrl,
                             @Value("${app.media.upload-url-ttl-seconds:300}") int expiresIn) {
        this.root = Paths.get(dir).toAbsolutePath().normalize();
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.expiresIn = expiresIn;
        try {
            Files.createDirectories(root);
            log.info("로컬 미디어 저장소 = {} (운영에서는 S3 로 교체할 것)", root);
        } catch (IOException e) {
            throw new IllegalStateException("미디어 저장 디렉터리를 만들 수 없습니다: " + root, e);
        }
    }

    @Override
    public Upload issueUpload(String mediaKey, String contentType) {
        // 로컬에서는 서명이 필요 없다. 같은 서버의 PUT 엔드포인트로 보낸다.
        return new Upload(baseUrl + "/" + mediaKey, mediaKey, expiresIn);
    }

    @Override
    public String publicUrl(String mediaKey) { return baseUrl + "/" + mediaKey; }

    @Override
    public int expiresInSeconds() { return expiresIn; }

    // ── 로컬 전용 입출력 ──

    public void store(String mediaKey, InputStream in) {
        Path target = resolveSafe(mediaKey);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.COMMON_500);
        }
    }

    public Path load(String mediaKey) {
        Path p = resolveSafe(mediaKey);
        if (!Files.exists(p)) throw new BusinessException(ErrorCode.COMMON_404);
        return p;
    }

    /**
     * 경로 탈출 방어. mediaKey 에 ".." 이 들어오면 저장 디렉터리 밖의 파일을 읽거나 덮어쓸 수 있다.
     * 키는 서버가 만들지만, PUT 엔드포인트는 클라이언트가 임의 경로로 호출할 수 있으므로 반드시 검증한다.
     */
    private Path resolveSafe(String mediaKey) {
        Path p = root.resolve(mediaKey).normalize();
        if (!p.startsWith(root)) throw new BusinessException(ErrorCode.COMMON_400, "mediaKey");
        return p;
    }
}
