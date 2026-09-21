package com.snaphere.api.post.event;

import java.util.UUID;

/**
 * 게시글이 만들어졌다. (PST-016)
 *
 * <p>이미지 후처리처럼 등록 응답과 분리해야 하는 작업(PST-019)이 이 이벤트를 듣는다. 서비스가
 * 후처리를 직접 호출하면 응답 시간에 딸려 들어가고, 후처리 실패가 게시 실패로 번진다.
 *
 * <p>커밋 이후에 처리해야 한다. 트랜잭션이 끝나기 전에 다른 스레드가 이 게시글을 조회하면
 * 행이 아직 없다.
 */
public record PostCreatedEvent(long postId, UUID userId) {
}
