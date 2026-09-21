package com.snaphere.api.post.media;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 후처리 테스트용 이미지 픽스처.
 *
 * <p>두 테스트가 같은 JPEG 을 만들어야 해서 여기 모았다. 특히 {@link #withExif} 는
 * "EXIF 가 지워진다"를 검증하는 근거이므로 한 곳에만 두어야 한다.
 */
final class JpegFixtures {

    /** APP1 Exif 세그먼트의 시작 표식. 'E' 'x' 'i' 'f' 그리고 NUL 두 개. */
    static final byte[] EXIF_MARKER = {'E', 'x', 'i', 'f', 0, 0};

    /** 좌표가 실려 있던 자리를 흉내 낸 값. 결과에 남아 있으면 안 된다. */
    static final byte[] FAKE_GPS = "GPSLatitudeRef-37.5796".getBytes(StandardCharsets.ISO_8859_1);

    private JpegFixtures() {
    }

    static BufferedImage solid(int width, int height, Color color) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, width, height);
        g.dispose();
        return image;
    }

    static byte[] jpeg(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "jpg", out)) {
            throw new IOException("jpg 인코더를 찾을 수 없다");
        }
        return out.toByteArray();
    }

    static byte[] jpeg(int width, int height) throws IOException {
        return jpeg(solid(width, height, Color.RED));
    }

    /**
     * SOI(FF D8) 바로 뒤에 APP1 Exif 세그먼트를 끼워 넣는다. 실제 카메라 사진의 좌표·기기
     * 정보가 들어 있는 자리가 여기다.
     */
    static byte[] withExif(byte[] plainJpeg) throws IOException {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        payload.write(EXIF_MARKER);
        payload.write(FAKE_GPS);
        byte[] data = payload.toByteArray();
        int segmentLength = data.length + 2; // 길이 필드 2바이트를 포함한다

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(plainJpeg, 0, 2);
        out.write(0xFF);
        out.write(0xE1);
        out.write((segmentLength >> 8) & 0xFF);
        out.write(segmentLength & 0xFF);
        out.write(data);
        out.write(plainJpeg, 2, plainJpeg.length - 2);
        return out.toByteArray();
    }

    /** EXIF 를 심은 JPEG 을 한 번에 만든다. */
    static byte[] jpegWithExif(int width, int height) throws IOException {
        return withExif(jpeg(width, height));
    }

    static boolean contains(byte[] haystack, byte[] pattern) {
        outer:
        for (int i = 0; i <= haystack.length - pattern.length; i++) {
            for (int j = 0; j < pattern.length; j++) {
                if (haystack[i + j] != pattern[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }
}
