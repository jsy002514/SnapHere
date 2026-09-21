package com.snaphere.api.post.media;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 이미지 후처리 계산. (PST-019, PST-020, PST-021, PST-031)
 *
 * <p>네트워크도 DB 도 쓰지 않는 순수 함수라 바이트 배열만 넣어 그대로 시험할 수 있다.
 *
 * <p><b>EXIF 제거 방식.</b> 태그를 하나씩 지우지 않는다. {@link ImageIO} 로 픽셀만 읽어
 * {@link BufferedImage} 로 만든 뒤 다시 인코딩하면 EXIF·GPS·기기 정보가 애초에 옮겨지지 않는다
 * (PST-020). 지울 태그 목록을 관리할 필요가 없어 새 태그 종류가 생겨도 안전하고, 라이브러리를
 * 더 붙이지 않아도 된다.
 *
 * <p><b>해시는 원본 바이트로 계산한다.</b> 재인코딩 결과로 계산하면 JDK 인코더 구현이 바뀔 때
 * 같은 사진의 해시가 달라져 중복 판정(PST-031)이 무너진다.
 */
public final class ImagePostProcessing {

    /** 공개용 이미지 형식. 재인코딩하면서 메타데이터가 떨어진다. */
    public static final String OUTPUT_CONTENT_TYPE = "image/jpeg";
    private static final String OUTPUT_FORMAT = "jpg";

    /** 썸네일 긴 변 기준 크기. 목록 카드가 이보다 크게 쓰이지 않는다. */
    public static final int THUMBNAIL_MAX_EDGE = 480;

    private ImagePostProcessing() {
    }

    public static ProcessedImage process(byte[] original) throws IOException {
        BufferedImage source = decode(original);
        BufferedImage opaque = flatten(source);

        return new ProcessedImage(
                encode(opaque),
                encode(resizeToMaxEdge(opaque, THUMBNAIL_MAX_EDGE)),
                sha256(original),
                aspectRatio(source.getWidth(), source.getHeight()),
                OUTPUT_CONTENT_TYPE);
    }

    /** 가로/세로. 소수점 4자리는 {@code post_images.aspect_ratio numeric(6,4)} 에 맞춘 값이다. */
    public static BigDecimal aspectRatio(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("이미지 크기가 0 이하다: " + width + "x" + height);
        }
        return BigDecimal.valueOf(width)
                .divide(BigDecimal.valueOf(height), 4, RoundingMode.HALF_UP);
    }

    /** 소문자 16진수 64자. {@code post_images.image_hash varchar(64)} 와 맞춘다. */
    public static String sha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content);
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException impossible) {
            // SHA-256 은 모든 JDK 구현이 반드시 제공한다.
            throw new IllegalStateException(impossible);
        }
    }

    private static BufferedImage decode(byte[] content) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(content));
        if (image == null) {
            throw new IOException("이미지를 해석할 수 없다. 업로드된 바이트가 이미지가 아니다");
        }
        return image;
    }

    /**
     * 투명도를 흰 배경으로 눌러 RGB 로 만든다. PNG 의 알파 채널을 그대로 JPEG 으로 쓰면
     * 인코더가 실패하거나 색이 뒤집힌다.
     */
    private static BufferedImage flatten(BufferedImage source) {
        if (source.getType() == BufferedImage.TYPE_INT_RGB) {
            return source;
        }
        BufferedImage rgb = new BufferedImage(
                source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
            g.drawImage(source, 0, 0, null);
        } finally {
            g.dispose();
        }
        return rgb;
    }

    private static BufferedImage resizeToMaxEdge(BufferedImage source, int maxEdge) {
        int width = source.getWidth();
        int height = source.getHeight();
        if (width <= maxEdge && height <= maxEdge) {
            return source;
        }
        double scale = (double) maxEdge / Math.max(width, height);
        int targetWidth = Math.max(1, (int) Math.round(width * scale));
        int targetHeight = Math.max(1, (int) Math.round(height * scale));

        BufferedImage resized = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        } finally {
            g.dispose();
        }
        return resized;
    }

    private static byte[] encode(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(image, OUTPUT_FORMAT, out)) {
            throw new IOException(OUTPUT_FORMAT + " 인코더를 찾을 수 없다");
        }
        return out.toByteArray();
    }
}
