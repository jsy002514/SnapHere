package com.snaphere.api.post.tier;

/** 낮음·보통일 때 어떻게 하면 등급이 올라가는지 알려주는 코드. (PST-049) */
public enum TierImprovementHint {

    /** 위치 정보가 켜진 상태로 찍은 사진을 고르면 등급이 올라간다 */
    ENABLE_LOCATION_ON_CAMERA,

    /** 장소에 더 가까이에서 찍은 사진을 고르면 된다 */
    TAKE_CLOSER_TO_PLACE,

    /** 최근에 찍은 사진을 고르면 된다 */
    USE_RECENT_PHOTO,

    /** 지금 그 자리에서 카메라로 찍으면 가장 높은 등급이 된다 */
    SHOOT_NOW_WITH_CAMERA;

    public String messageKey() {
        return "tier.hint." + name().toLowerCase();
    }
}
