package com.ssafy.snaphere.domain.place.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** TourAPI 공식 이미지 */
@Getter
@Entity
@Table(name = "place_images")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlaceImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "image_id")
    private Long id;

    @Column(name = "place_id", nullable = false) private Long placeId;
    @Column(name = "image_url", nullable = false, length = 500) private String imageUrl;
    @Column(name = "thumbnail_url", length = 500) private String thumbnailUrl;

    @Column(nullable = false, length = 30) private String source;
    @Column(name = "sort_order", nullable = false) private int sortOrder;
}
