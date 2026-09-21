package com.snaphere.api.post.dto;

import com.snaphere.api.post.entity.PostImageEntity;

import java.math.BigDecimal;

/**
 * 명세: 3. 응답 스키마 &gt; PostImage
 *
 * <p>이 DTO는 후처리가 끝난 공개 이미지에만 사용한다. 비공개 원본 URL로 폴백하지 않는다.
 */
public record PostImageResponse(
        String postImageId,
        String imageUrl,
        String thumbnailUrl,
        BigDecimal aspectRatio,
        int sortOrder
) {
    /** 후처리 전 기본 비율. 세로형 사진이 흔해 4:5 를 쓴다 (PST-021). */
    public static final BigDecimal DEFAULT_ASPECT_RATIO = new BigDecimal("0.8000");

    public static PostImageResponse from(PostImageEntity image, String imageUrl) {
        String thumbnail = image.getThumbnailUrl();
        BigDecimal ratio = image.getAspectRatio() == null ? DEFAULT_ASPECT_RATIO : image.getAspectRatio();
        return new PostImageResponse(
                String.valueOf(image.getPostImageId()), imageUrl, thumbnail, ratio, image.getSortOrder());
    }
}
