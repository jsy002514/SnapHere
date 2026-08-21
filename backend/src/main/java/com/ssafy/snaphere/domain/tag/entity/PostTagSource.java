package com.ssafy.snaphere.domain.tag.entity;

/** 태그가 어디서 왔는지. 자동 추천 태그와 사용자 입력을 구분해 통계에 다르게 쓴다. */
public enum PostTagSource { USER, AUTO_REGION, AUTO_CATEGORY, AUTO_EVENT }
