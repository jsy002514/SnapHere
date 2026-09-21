package com.snaphere.api.post.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 업로드가 끝난 사진 한 장. 명세: 2. 요청 파라미터 &gt; API-PST-003 &gt; images
 *
 * @param imageKey    {@code POST /media/presigned-urls} 가 발급한 객체 키. 클라이언트가 임의로
 *                    만든 키는 받지 않는다 — 서버가 키 접두어로 소유자를 확인한다
 * @param sortOrder   1~4. 사용자가 고른 순서 그대로 보여준다
 * @param aspectRatio 가로/세로 비율. 후처리 배치(JOB-003)가 계산하기 전에도 메이슨리가 카드 높이를
 *                    잡아야 해서 클라이언트가 알고 있는 값을 함께 받는다 (PST-021).
 *                    비워도 게시되고, 그때는 배치가 채울 때까지 목록에서 기본 비율로 그린다
 * @param imageHash   원본 SHA-256 (소문자 16진수 64자). 중복 업로드를 등록 응답 전에 되돌리기 위한
 *                    값이다 (PST-031). 서버가 이 시점에 원본을 내려받아 계산하면 후처리를 응답과
 *                    분리하라는 PST-019 와 어긋난다. 비워도 게시되고, 그때는 후처리 배치가
 *                    실제 해시를 채울 때 중복이 드러난다
 */
public record PostImageRequest(

        @NotBlank
        @Size(max = 1024)
        String imageKey,

        @NotNull
        Integer sortOrder,

        @DecimalMin(value = "0", inclusive = false)
        BigDecimal aspectRatio,

        @Pattern(regexp = "^[0-9a-f]{64}$", message = "SHA-256 소문자 16진수 64자")
        String imageHash
) {
}
