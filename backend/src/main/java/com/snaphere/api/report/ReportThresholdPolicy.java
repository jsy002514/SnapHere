package com.snaphere.api.report;

/**
 * 신고 누적 기준. (PST-045)
 *
 * <p>DB 도 스프링도 쓰지 않는 순수 판정이라 건수만 넣어 시험할 수 있다. 기준을 상수 하나로
 * 모아 두는 것은 나중에 대상별로 달라질 수 있기 때문이다 — 장소 숨김(PLC-023)은 게시글보다
 * 무거운 조치라 같은 3건으로 처리하지 않을 여지가 있다.
 */
public final class ReportThresholdPolicy {

    /** 게시글 자동 블라인드 기준. 이 건수에 도달하면 가린다. (PST-045) */
    public static final int POST_BLIND_THRESHOLD = 3;

    private ReportThresholdPolicy() {
    }

    /**
     * @param reportCount 이번 신고를 <b>포함한</b> 누적 건수
     * @return 자동으로 가려야 하는가
     */
    public static boolean shouldBlindPost(long reportCount) {
        return reportCount >= POST_BLIND_THRESHOLD;
    }
}
