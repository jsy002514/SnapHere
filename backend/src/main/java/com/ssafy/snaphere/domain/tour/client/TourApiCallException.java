package com.ssafy.snaphere.domain.tour.client;

/** TourAPI 호출 자체가 실패했을 때. 배치는 이 예외를 조합 단위로 잡아 다음 조합을 계속 진행한다. */
public class TourApiCallException extends RuntimeException {

    private final String resultCode;
    private final boolean retryable;

    public TourApiCallException(String resultCode, String message, boolean retryable) {
        super("[" + resultCode + "] " + message);
        this.resultCode = resultCode;
        this.retryable = retryable;
    }

    public String getResultCode() { return resultCode; }

    /** 재시도해도 의미가 있는 오류인가 (타임아웃·5xx = true / 인증 실패·한도 초과 = false) */
    public boolean isRetryable() { return retryable; }
}
