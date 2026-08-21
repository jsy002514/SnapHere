package com.ssafy.snaphere.global.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * docs/03_API명세서.md 의 에러 코드 표와 1:1 로 대응한다.
 * ★ enum 이름이 곧 API 응답의 code 다. 이름을 바꾸면 프론트가 깨진다.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 공통
    COMMON_400(HttpStatus.BAD_REQUEST,           "잘못된 요청입니다."),
    COMMON_401(HttpStatus.UNAUTHORIZED,          "인증이 필요합니다."),
    COMMON_403(HttpStatus.FORBIDDEN,             "권한이 없습니다."),
    COMMON_404(HttpStatus.NOT_FOUND,             "요청한 리소스를 찾을 수 없습니다."),
    COMMON_429(HttpStatus.TOO_MANY_REQUESTS,     "요청이 너무 많습니다."),
    COMMON_500(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),

    // 인증
    AUTH_001(HttpStatus.CONFLICT,     "이미 사용 중인 아이디입니다."),
    AUTH_002(HttpStatus.BAD_REQUEST,  "비밀번호는 8자 이상이며 영문과 숫자를 포함해야 합니다."),
    AUTH_003(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다."),
    AUTH_004(HttpStatus.UNAUTHORIZED, "구글 토큰 검증에 실패했습니다."),
    AUTH_005(HttpStatus.UNAUTHORIZED, "리프레시 토큰이 유효하지 않습니다."),
    AUTH_006(HttpStatus.UNAUTHORIZED, "로그인이 필요한 기능입니다."),
    AUTH_007(HttpStatus.TOO_MANY_REQUESTS, "로그인 시도가 너무 많습니다. 잠시 후 다시 시도해주세요."),

    // 사용자
    USER_001(HttpStatus.BAD_REQUEST, "닉네임은 2~20자여야 합니다."),
    USER_002(HttpStatus.FORBIDDEN,   "이용 약관에 동의해야 합니다."),
    USER_003(HttpStatus.FORBIDDEN,   "정지된 계정입니다."),
    USER_004(HttpStatus.CONFLICT,    "이미 탈퇴 처리된 계정입니다."),
    USER_005(HttpStatus.BAD_REQUEST, "복구 가능 기간이 지났습니다."),
    USER_006(HttpStatus.BAD_REQUEST, "허용되지 않은 링크 주소입니다."),

    // 팔로우
    FOLLOW_001(HttpStatus.BAD_REQUEST,      "자기 자신은 팔로우할 수 없습니다."),
    FOLLOW_002(HttpStatus.TOO_MANY_REQUESTS,"하루 팔로우 한도를 초과했습니다."),

    // 지역 · 장소
    REGION_001(HttpStatus.NOT_FOUND,        "존재하지 않는 지역 코드입니다."),
    PLACE_001(HttpStatus.NOT_FOUND,         "존재하지 않는 장소입니다."),
    PLACE_002(HttpStatus.TOO_MANY_REQUESTS, "하루에 만들 수 있는 장소 수를 초과했습니다."),
    PLACE_003(HttpStatus.BAD_REQUEST,       "서비스 범위를 벗어난 좌표입니다."),
    PLACE_004(HttpStatus.FORBIDDEN,         "장소에 더 가까이 가야 체크인할 수 있습니다."),

    // 미디어
    MEDIA_001(HttpStatus.BAD_REQUEST,       "지원하지 않는 파일 형식입니다."),
    MEDIA_002(HttpStatus.PAYLOAD_TOO_LARGE, "파일 용량이 너무 큽니다."),
    MEDIA_003(HttpStatus.BAD_REQUEST,       "영상 길이가 너무 깁니다."),

    // 게시물
    POST_001(HttpStatus.BAD_REQUEST,       "제목 또는 본문 중 하나는 입력해야 합니다."),
    POST_002(HttpStatus.NOT_FOUND,         "삭제되었거나 존재하지 않는 게시물입니다."),
    POST_003(HttpStatus.TOO_MANY_REQUESTS, "오늘 업로드 가능한 수를 초과했습니다."),
    POST_004(HttpStatus.TOO_MANY_REQUESTS, "이 장소에 오늘 올릴 수 있는 수를 초과했습니다."),
    POST_005(HttpStatus.CONFLICT,          "이미 업로드한 파일입니다."),
    POST_006(HttpStatus.FORBIDDEN,         "신고 누적으로 업로드가 제한된 상태입니다."),

    // 댓글 · 신고
    COMMENT_001(HttpStatus.BAD_REQUEST, "댓글은 1~1000자여야 합니다."),
    COMMENT_002(HttpStatus.BAD_REQUEST, "대댓글은 1단계까지만 작성할 수 있습니다."),
    REPORT_001(HttpStatus.CONFLICT,     "이미 신고한 대상입니다."),

    // TourAPI 연동 (관리자 전용 경로에서만 노출된다)
    TOUR_001(HttpStatus.SERVICE_UNAVAILABLE, "관광정보 API 호출에 실패했습니다."),
    TOUR_002(HttpStatus.SERVICE_UNAVAILABLE, "관광정보 API 인증에 실패했습니다. 서비스키를 확인하세요."),
    TOUR_003(HttpStatus.TOO_MANY_REQUESTS,   "관광정보 API 일 호출 한도를 초과했습니다.");

    private final HttpStatus status;
    private final String message;
}
