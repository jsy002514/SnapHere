package com.snaphere.api.post.share;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 공유 링크 규약. (CMU-019, CMU-021)
 *
 * @param webBaseUrl        공개 웹 페이지의 호스트. 앱이 설치돼 있으면 같은 주소가 앱으로 열린다
 *                          (유니버설 링크·앱 링크) — 그래서 커스텀 스킴을 따로 두지 않는다
 *                          (CMU-021)
 * @param pathPrefix        게시글 공개 페이지 경로 접두어
 * @param defaultTitle      장소가 없는 게시글의 공유 제목. 제목이 빈 미리보기는 링크가 깨진 것처럼 보인다
 * @param defaultDescription 캡션이 없는 게시글의 공유 설명
 *
 *                          <p>서버가 문장을 만드는 유일한 자리다. OG 태그를 읽는 카톡·DM 크롤러는
 *                          Accept-Language 를 보내지 않고 메시지 키를 해석하지도 못하므로, 완성된
 *                          문장이 아니면 미리보기가 빈칸으로 뜬다 — SYS-010 의 예외이며 그래서
 *                          코드가 아니라 설정으로 뺐다.
 */
@ConfigurationProperties(prefix = "snaphere.share")
public record ShareProperties(String webBaseUrl,
                              String pathPrefix,
                              String defaultTitle,
                              String defaultDescription) {

    public ShareProperties {
        if (webBaseUrl == null) {
            webBaseUrl = "";
        }
        if (pathPrefix == null || pathPrefix.isBlank()) {
            pathPrefix = "/p/";
        }
        if (defaultTitle == null || defaultTitle.isBlank()) {
            defaultTitle = "SnapHere";
        }
        if (defaultDescription == null || defaultDescription.isBlank()) {
            defaultDescription = "SnapHere에서 사진을 확인하세요";
        }
    }

    /** 게시글 공개 페이지 주소. (CMU-019) */
    public String shareUrl(long postId) {
        String base = webBaseUrl.endsWith("/")
                ? webBaseUrl.substring(0, webBaseUrl.length() - 1)
                : webBaseUrl;
        String prefix = pathPrefix.startsWith("/") ? pathPrefix : "/" + pathPrefix;
        String path = prefix.endsWith("/") ? prefix : prefix + "/";
        return base + path + postId;
    }
}
