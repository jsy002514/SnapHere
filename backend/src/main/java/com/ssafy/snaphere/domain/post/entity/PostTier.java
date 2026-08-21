package com.ssafy.snaphere.domain.post.entity;

import java.math.BigDecimal;

/**
 * 위치 신뢰도. 이 프로젝트의 차별점이자 랭킹·방문기록·히트맵의 기준이다.
 *
 * ⚠️ 서버만 판정한다. 프론트가 보낸 tier 값은 절대 신뢰하지 않는다.
 *    클라이언트가 tier 를 정할 수 있으면 "현장 인증" 배지가 아무 의미가 없어진다.
 */
public enum PostTier {

    /** 현장 인증 — 앱 카메라로 즉시 촬영, 장소 인증 반경 안 */
    ON_SITE("3.0", true, true),

    /** 위치 확인 — 좌표는 맞지만 즉시 촬영은 아님(앨범 업로드 등) */
    LOCATION_CONFIRMED("1.8", true, true),

    /** 위치 미확인 — 랭킹에 반영하지 않고 방문으로도 인정하지 않는다 */
    NO_LOCATION("0", false, false);

    private final BigDecimal rankingWeight;
    private final boolean countsForRanking;
    private final boolean createsVisit;

    PostTier(String weight, boolean countsForRanking, boolean createsVisit) {
        this.rankingWeight = new BigDecimal(weight);
        this.countsForRanking = countsForRanking;
        this.createsVisit = createsVisit;
    }

    public BigDecimal rankingWeight() { return rankingWeight; }
    public boolean countsForRanking() { return countsForRanking; }
    public boolean createsVisit() { return createsVisit; }

    /** 히트맵 반영 여부. 위치가 없는 게시물은 좌표가 없으니 당연히 제외된다. */
    public boolean countsForHeatmap() { return this != NO_LOCATION; }

    /**
     * 프론트가 다국어 매핑에 쓰는 키. 서버가 완성된 문장을 만들지 않는다.
     * 외국인 대상 서비스라 문구는 앱이 i18n 으로 처리한다.
     */
    public String messageKey() {
        return "tier." + name().toLowerCase();
    }
}
