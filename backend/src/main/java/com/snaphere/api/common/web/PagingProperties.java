package com.snaphere.api.common.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 커서 페이징 크기 규약. (SYS-003)
 *
 * @param defaultSize {@code size} 를 보내지 않았을 때 쓰는 값
 * @param maxSize     넘겨도 이 값으로 자른다. 한 번에 큰 목록을 요청해 서버를 밀어내지 못하게 한다
 */
@ConfigurationProperties(prefix = "snaphere.paging")
public record PagingProperties(int defaultSize, int maxSize) {

    public PagingProperties {
        if (defaultSize <= 0) {
            defaultSize = 20;
        }
        if (maxSize <= 0) {
            maxSize = 50;
        }
    }

    /**
     * 요청 크기를 규약 안으로 맞춘다. 잘못된 값을 400 으로 돌려주지 않고 조용히 자른다 —
     * 목록 조회가 크기 때문에 실패하면 앱이 화면을 아예 못 그린다.
     */
    public int resolve(Integer requested) {
        if (requested == null || requested <= 0) {
            return defaultSize;
        }
        return Math.min(requested, maxSize);
    }
}
