package com.snaphere.api.place;

import java.util.Optional;

/**
 * TourAPI 콘텐츠 유형. 장소의 종류를 나타내는 번호다. (PLC-003, CMU-027)
 *
 * <p>번호와 이름의 대응은 용어 사전(docs/06-glossary.md &gt; contentTypeId)을 그대로 옮겼다.
 * 코드에 숫자를 흩어 놓지 않고 여기 한 곳에 모은 이유는, 카테고리 태그 추천과 테마 랭킹이 같은
 * 대응을 봐야 하기 때문이다 (RNK-005).
 *
 * <p>목록에 없는 번호는 태그로 만들지 않는다. TourAPI 가 유형을 늘리면 여기 없는 값이 들어오는데,
 * 그때 "25" 같은 숫자가 해시태그로 붙는 것보다 태그가 하나 적은 편이 낫다.
 */
public enum ContentType {

    TOURIST_SPOT(12, "관광지"),
    CULTURAL_FACILITY(14, "문화시설"),
    FESTIVAL(15, "축제공연행사"),
    LEISURE_SPORTS(28, "레포츠"),
    SHOPPING(38, "쇼핑"),
    RESTAURANT(39, "음식점");

    private final int code;
    private final String tagName;

    ContentType(int code, String tagName) {
        this.code = code;
        this.tagName = tagName;
    }

    public int code() {
        return code;
    }

    /** 카테고리 태그로 쓸 이름. (CMU-027) */
    public String tagName() {
        return tagName;
    }

    /** @return 모르는 번호면 빈 값 */
    public static Optional<ContentType> of(Integer code) {
        if (code == null) {
            return Optional.empty();
        }
        for (ContentType type : values()) {
            if (type.code == code) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
