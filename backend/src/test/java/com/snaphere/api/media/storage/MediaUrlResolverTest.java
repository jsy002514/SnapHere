package com.snaphere.api.media.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** 객체 키 → 공개 주소 변환과 소유자 확인 — PST-013, PST-014 */
class MediaUrlResolverTest {

    private static final UUID USER = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID OTHER = UUID.fromString("99999999-8888-7777-6666-555555555555");

    private static MediaUrlResolver resolver(String publicBaseUrl) {
        return new MediaUrlResolver(new MediaStorageProperties(
                "stub", "b", "ap-northeast-2", publicBaseUrl, Duration.ofMinutes(5), 1));
    }

    @Test
    @DisplayName("CDN 주소가 있으면 키를 이어 붙인다")
    void CDN_주소_조립() {
        assertThat(resolver("https://cdn.test").publicUrl("posts/a/b.webp"))
                .isEqualTo("https://cdn.test/posts/a/b.webp");
    }

    @Test
    @DisplayName("CDN 주소 끝의 슬래시가 겹치지 않는다")
    void 슬래시_중복_없음() {
        assertThat(resolver("https://cdn.test/").publicUrl("posts/a/b.webp"))
                .isEqualTo("https://cdn.test/posts/a/b.webp");
    }

    @Test
    @DisplayName("CDN 주소가 없으면 상대 경로를 준다")
    void 로컬_개발_상대경로() {
        assertThat(resolver("").publicUrl("posts/a/b.webp")).isEqualTo("/posts/a/b.webp");
        assertThat(resolver(null).publicUrl("posts/a/b.webp")).isEqualTo("/posts/a/b.webp");
    }

    @Test
    @DisplayName("본인에게 발급된 게시글 사진 키만 통과한다")
    void 본인_키만_허용() {
        MediaUrlResolver r = resolver("https://cdn.test");
        assertThat(r.isOwnedPostImageKey("originals/posts/" + USER + "/abc.webp", USER)).isTrue();
        assertThat(r.isOwnedPostImageKey("originals/posts/" + OTHER + "/abc.webp", USER)).isFalse();
    }

    @Test
    @DisplayName("프로필 키·경로 조작·빈 파일명은 거부한다")
    void 잘못된_키_거부() {
        MediaUrlResolver r = resolver("https://cdn.test");
        assertThat(r.isOwnedPostImageKey("profile/" + USER + "/abc.webp", USER)).isFalse();
        assertThat(r.isOwnedPostImageKey("originals/posts/" + USER + "/../" + OTHER + "/a.webp", USER)).isFalse();
        assertThat(r.isOwnedPostImageKey("originals/posts/" + USER + "/", USER)).isFalse();
        assertThat(r.isOwnedPostImageKey(null, USER)).isFalse();
    }

    @Test
    @DisplayName("비공개 원본 키는 URL로 노출하지 않는다")
    void 비공개_원본_차단() {
        assertThat(resolver("https://cdn.test").publicUrl("originals/posts/a/b.webp")).isNull();
    }
}
