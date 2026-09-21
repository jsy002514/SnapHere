package com.snaphere.api.post.dto;

import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 게시글 수정 요청. 명세: 2. 요청 파라미터 &gt; API-PST-007
 *
 * <p><b>장소·좌표·등급·촬영 시각은 받지 않는다.</b> 필드를 두고 무시하는 것보다 없는 편이 낫다 —
 * 게시 후에 그 값들을 바꿀 수 있으면 위치 신뢰 체계가 무너진다 (PST-037). 다른 장소에서 찍은
 * 사진을 올린 뒤 장소만 바꿔치기하면 높음 등급을 얻는다.
 *
 * <p>세 필드 모두 선택이다. 보내지 않은 필드는 건드리지 않는다 — PATCH 이므로 부분 수정이다.
 * {@code content} 를 비우려면 빈 문자열을 보낸다.
 *
 * @param tagNames   보내면 <b>전체 교체</b>다. 차이 계산 방식은 버그를 부른다 (CMU-032)
 * @param imageOrder 기존 사진 ID 전체를 새 순서로. 일부만 보내면 나머지 순서가 불명확해진다
 */
public record UpdatePostRequest(

        @Size(max = 5000)
        String content,

        List<String> tagNames,

        List<Long> imageOrder
) {
    public boolean hasContent() {
        return content != null;
    }

    public boolean hasTagNames() {
        return tagNames != null;
    }

    public boolean hasImageOrder() {
        return imageOrder != null && !imageOrder.isEmpty();
    }
}
