package com.ssafy.snaphere.domain.post.entity;

/**
 * 영상은 업로드 직후 바로 재생 가능한 상태가 아니다(썸네일 추출·검증 필요).
 * READY 가 아닌 미디어는 목록·상세에서 "처리중" 으로 표시한다.
 */
public enum ProcessStatus { UPLOADING, PROCESSING, READY, FAILED }
