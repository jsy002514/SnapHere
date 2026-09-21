-- SnapHere V7 — 좋아요
-- 요구사항: PST-040, PST-041, CMU-018
-- 설계: docs/05-erd-reference.md > likes
--
-- 대상이 게시글과 댓글 두 종류라 target_id 에 외래키를 걸 수 없다.
-- 대상 존재 확인은 애플리케이션이 한다 (CMU-024 와 같은 이유).

create table likes (
    user_id     uuid        not null,
    target_type varchar(20) not null,
    target_id   bigint      not null,
    created_at  timestamptz not null default now(),

    primary key (user_id, target_type, target_id),
    constraint ck_likes_target_type check (target_type in ('POST', 'COMMENT')),
    constraint fk_likes_user foreign key (user_id) references users (id) on delete cascade
);

comment on table likes is
'좋아요. 복합 PK 로 중복을 막는다 — 사용자당 1회 토글이므로 애플리케이션에서 조회 후 삽입하면 동시 요청에 두 번 들어간다 (PST-040)';
comment on column likes.target_id is
'POST 면 posts.post_id, COMMENT 면 comments.comment_id. 다형 참조라 외래키를 걸 수 없고 대상 존재는 애플리케이션이 확인한다';

-- 대상별 좋아요 수 집계와 자기 좋아요 판정(PST-041)에 쓴다. PK 는 user_id 부터라 이 방향을 못 탄다.
create index idx_likes_target on likes (target_type, target_id);
