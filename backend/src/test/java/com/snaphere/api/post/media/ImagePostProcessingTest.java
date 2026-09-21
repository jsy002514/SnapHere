package com.snaphere.api.post.media;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 이미지 후처리 — PST-019, PST-020, PST-021, PST-031
 *
 * <p>핵심은 PST-020 이다. "EXIF 를 지운다"는 주장을 그냥 믿지 않고, EXIF 를 실제로 심은
 * JPEG 을 만들어 결과 바이트에서 그 표식이 사라졌는지 확인한다.
 */
class ImagePostProcessingTest {

    private static final byte[] EXIF_MARKER = JpegFixtures.EXIF_MARKER;
    private static final byte[] FAKE_GPS = JpegFixtures.FAKE_GPS;

    // ───────────────────────────────────────────── EXIF 제거 (PST-020)

    @Test
    @DisplayName("픽스처 검증 — 심은 EXIF 가 원본에는 실제로 들어 있다")
    void 픽스처가_유효하다() throws IOException {
        byte[] original = JpegFixtures.jpegWithExif(80, 60);
        assertThat(JpegFixtures.contains(original, EXIF_MARKER)).isTrue();
        assertThat(JpegFixtures.contains(original, FAKE_GPS)).isTrue();
    }

    @Test
    @DisplayName("공개용 이미지에서 EXIF·좌표 표식이 사라진다")
    void EXIF_제거() throws IOException {
        byte[] original = JpegFixtures.jpegWithExif(800, 600);

        ProcessedImage processed = ImagePostProcessing.process(original);

        assertThat(JpegFixtures.contains(processed.sanitized(), EXIF_MARKER)).isFalse();
        assertThat(JpegFixtures.contains(processed.sanitized(), FAKE_GPS)).isFalse();
        assertThat(JpegFixtures.contains(processed.thumbnail(), FAKE_GPS)).isFalse();
    }

    @Test
    @DisplayName("메타데이터를 지워도 픽셀 크기는 그대로다")
    void 크기_유지() throws IOException {
        byte[] original = JpegFixtures.jpegWithExif(800, 600);

        ProcessedImage processed = ImagePostProcessing.process(original);
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(processed.sanitized()));

        assertThat(decoded.getWidth()).isEqualTo(800);
        assertThat(decoded.getHeight()).isEqualTo(600);
    }

    // ───────────────────────────────────────────── 해시 (PST-031)

    @Test
    @DisplayName("해시는 원본 바이트로 계산한다 — 재인코딩 결과가 아니다")
    void 해시는_원본_기준() throws IOException {
        byte[] original = JpegFixtures.jpegWithExif(800, 600);

        ProcessedImage processed = ImagePostProcessing.process(original);

        assertThat(processed.sha256()).isEqualTo(ImagePostProcessing.sha256(original));
        assertThat(processed.sha256())
                .isNotEqualTo(ImagePostProcessing.sha256(processed.sanitized()));
    }

    @Test
    @DisplayName("해시는 소문자 16진수 64자 — image_hash varchar(64) 에 맞는다")
    void 해시_형식() {
        assertThat(ImagePostProcessing.sha256(new byte[]{1, 2, 3}))
                .hasSize(64)
                .matches("^[0-9a-f]{64}$");
    }

    @Test
    @DisplayName("같은 바이트는 같은 해시, 다른 바이트는 다른 해시")
    void 해시_일관성() {
        assertThat(ImagePostProcessing.sha256("가".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo(ImagePostProcessing.sha256("가".getBytes(StandardCharsets.UTF_8)))
                .isNotEqualTo(ImagePostProcessing.sha256("나".getBytes(StandardCharsets.UTF_8)));
    }

    // ───────────────────────────────────────────── 비율 (PST-021)

    @Test
    @DisplayName("가로/세로 비율을 소수점 4자리로 준다")
    void 비율_계산() {
        assertThat(ImagePostProcessing.aspectRatio(800, 600))
                .isEqualByComparingTo(new BigDecimal("1.3333"));
        assertThat(ImagePostProcessing.aspectRatio(1080, 1350))
                .isEqualByComparingTo(new BigDecimal("0.8000"));
        assertThat(ImagePostProcessing.aspectRatio(500, 500))
                .isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    @DisplayName("크기가 0 이하면 계산하지 않는다")
    void 비율_잘못된_크기() {
        assertThatThrownBy(() -> ImagePostProcessing.aspectRatio(0, 600))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ImagePostProcessing.aspectRatio(800, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("후처리 결과의 비율은 원본 픽셀 기준이다")
    void 결과_비율() throws IOException {
        ProcessedImage processed = ImagePostProcessing.process(JpegFixtures.jpeg(JpegFixtures.solid(1200, 800, Color.BLUE)));
        assertThat(processed.aspectRatio()).isEqualByComparingTo(new BigDecimal("1.5000"));
    }

    // ───────────────────────────────────────────── 썸네일

    @Test
    @DisplayName("썸네일 긴 변을 480 으로 줄이고 비율을 지킨다")
    void 썸네일_축소() throws IOException {
        ProcessedImage processed = ImagePostProcessing.process(JpegFixtures.jpeg(1600, 1200));
        BufferedImage thumb = ImageIO.read(new ByteArrayInputStream(processed.thumbnail()));

        assertThat(Math.max(thumb.getWidth(), thumb.getHeight()))
                .isEqualTo(ImagePostProcessing.THUMBNAIL_MAX_EDGE);
        assertThat(thumb.getWidth()).isEqualTo(480);
        assertThat(thumb.getHeight()).isEqualTo(360);
    }

    @Test
    @DisplayName("세로가 긴 사진은 세로를 480 에 맞춘다")
    void 썸네일_세로형() throws IOException {
        ProcessedImage processed = ImagePostProcessing.process(JpegFixtures.jpeg(720, 1280));
        BufferedImage thumb = ImageIO.read(new ByteArrayInputStream(processed.thumbnail()));

        assertThat(thumb.getHeight()).isEqualTo(480);
        assertThat(thumb.getWidth()).isEqualTo(270);
    }

    @Test
    @DisplayName("이미 작은 사진은 늘리지 않는다")
    void 썸네일_확대_안함() throws IOException {
        ProcessedImage processed = ImagePostProcessing.process(JpegFixtures.jpeg(300, 200));
        BufferedImage thumb = ImageIO.read(new ByteArrayInputStream(processed.thumbnail()));

        assertThat(thumb.getWidth()).isEqualTo(300);
        assertThat(thumb.getHeight()).isEqualTo(200);
    }

    // ───────────────────────────────────────────── 알파 채널 · 잘못된 입력

    @Test
    @DisplayName("투명한 PNG 도 처리한다 — 알파를 흰 배경으로 누른다")
    void 투명_PNG() throws IOException {
        BufferedImage argb = new BufferedImage(200, 100, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = argb.createGraphics();
        g.setColor(new Color(255, 0, 0, 128));
        g.fillRect(0, 0, 200, 100);
        g.dispose();

        ByteArrayOutputStream png = new ByteArrayOutputStream();
        assertThat(ImageIO.write(argb, "png", png)).isTrue();

        ProcessedImage processed = ImagePostProcessing.process(png.toByteArray());

        assertThat(processed.contentType()).isEqualTo(ImagePostProcessing.OUTPUT_CONTENT_TYPE);
        assertThat(processed.aspectRatio()).isEqualByComparingTo(new BigDecimal("2.0000"));
        assertThat(ImageIO.read(new ByteArrayInputStream(processed.sanitized()))).isNotNull();
    }

    @Test
    @DisplayName("이미지가 아닌 바이트는 IOException")
    void 이미지가_아님() {
        byte[] notImage = "not an image".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> ImagePostProcessing.process(notImage))
                .isInstanceOf(IOException.class);
    }
}
