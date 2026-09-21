package com.snaphere.api.post.tier;

/**
 * 위치 신뢰 등급. (PST-022 ~ PST-026)
 *
 * <p>이름만으로 서열이 보이도록 높음·보통·낮음으로 정했다 (용어 표준화 2026-08-31).
 * 등급이 결정하는 것은 <b>랭킹 가중치</b>와 <b>혜택 포함 여부</b> 두 가지다.
 *
 * <p>낮음(LOW)도 게시와 랭킹 반영은 허용한다. 0점을 주면 EXIF 가 없는 기기 사용자가
 * 전부 배제되기 때문이다. 대신 뱃지·방문 기록·히트맵에서는 뺀다 (PST-025, PST-026).
 */
public enum TrustTier {

    HIGH(3.0, true, true, true),
    MEDIUM(1.8, true, true, true),
    LOW(0.5, false, false, false);

    private final double rankingWeight;
    private final boolean eligibleForBadge;
    private final boolean countsForVisit;
    private final boolean countsForHeatmap;

    TrustTier(double rankingWeight, boolean eligibleForBadge,
              boolean countsForVisit, boolean countsForHeatmap) {
        this.rankingWeight = rankingWeight;
        this.eligibleForBadge = eligibleForBadge;
        this.countsForVisit = countsForVisit;
        this.countsForHeatmap = countsForHeatmap;
    }

    /** 장소 랭킹 점수 계산에 쓰는 게시글 가중치. (RNK-001) */
    public double rankingWeight() {
        return rankingWeight;
    }

    /** 뱃지 지급 대상인가. (PST-026, BDG-005) */
    public boolean eligibleForBadge() {
        return eligibleForBadge;
    }

    /** 방문 기록을 남기는가. (PST-026, VST-001) */
    public boolean countsForVisit() {
        return countsForVisit;
    }

    /** 히트맵 집계에 들어가는가. (PST-026, MAP-008) */
    public boolean countsForHeatmap() {
        return countsForHeatmap;
    }

    /** i18n 키. 서버는 완성 문장을 만들지 않는다. (SYS-010) */
    public String messageKey() {
        return "tier." + name().toLowerCase();
    }
}
