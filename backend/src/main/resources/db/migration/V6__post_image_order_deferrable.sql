-- SnapHere V6 — 사진 순서 변경을 가능하게 하는 제약 완화
-- 요구사항: PST-036 (게시글 수정 — 사진 순서)
--
-- V3 의 (post_id, sort_order) UNIQUE 는 즉시 검사라서 순서를 바꿀 수 없다.
-- 1번과 2번을 맞바꾸려면 한쪽을 먼저 2번으로 써야 하고, 그 순간 2번이 둘이 된다.
--
-- sort_order 에 CHECK (1~4) 가 걸려 있어 임시로 5·6 같은 값을 거쳐 갈 수도 없다.
-- 그래서 제약을 트랜잭션 끝에 검사하도록 미룬다. 커밋 시점에는 여전히 유일해야 하므로
-- 중복이 남는 실수는 그대로 막힌다.

alter table post_images drop constraint uk_post_images_order;

alter table post_images
    add constraint uk_post_images_order unique (post_id, sort_order)
    deferrable initially deferred;

comment on table post_images is
'게시글당 1~4장 (PST-001). (post_id, sort_order) UNIQUE 는 DEFERRABLE — 순서 변경(PST-036)이 트랜잭션 중간에 값을 겹치게 지나가기 때문이다. 커밋 시점 유일성은 그대로 보장된다';
