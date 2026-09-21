package com.snaphere.api.post.tier;

/** 사진 출처. 카메라 경로만 높음 등급 후보가 된다. (PST-005, PST-006, PST-023) */
public enum PhotoSource {

    /** 그 자리에서 촬영 (PST-006) */
    CAMERA,

    /** 앨범에서 선택 — 기본 업로드 경로 (PST-005) */
    ALBUM
}
