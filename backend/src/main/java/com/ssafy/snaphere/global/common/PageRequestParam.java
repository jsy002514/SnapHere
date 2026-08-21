package com.ssafy.snaphere.global.common;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@Getter
@Setter
public class PageRequestParam {

    @Min(1)
    @Schema(description = "페이지 번호 (1부터)", defaultValue = "1")
    private int page = 1;

    @Min(1) @Max(100)
    @Schema(description = "페이지 크기 (최대 100)", defaultValue = "20")
    private int size = 20;

    public Pageable toPageable() {
        return PageRequest.of(page - 1, size);
    }

    /**
     * 정렬 기준 뒤에 항상 PK 를 붙인다.
     * tie-breaker 가 없으면 동점 구간에서 페이지 경계가 흔들려 데이터가 중복·누락된다.
     */
    public Pageable toPageable(Sort sort, String pkProperty) {
        return PageRequest.of(page - 1, size, sort.and(Sort.by(Sort.Direction.DESC, pkProperty)));
    }
}
