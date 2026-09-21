-- SnapHere V8 — 저장함
-- 요구사항: CMU-023, CMU-024, PLC-015
-- 설계: docs/05-erd-reference.md > bookmarks
--
-- 게시글과 장소를 한 테이블에 담는다. 나누면 마이페이지 저장함이 UNION 이 되고,
-- 저장 대상이 늘 때마다 테이블이 하나씩 생긴다. 대신 target_id 에 외래키를 걸 수 없어
-- 대상 존재 확인을 애플리케이션이 한다 (CMU-024).

create table bookmarks (
    user_id     uuid        not null,
    target_type varchar(20) not null,
    target_id   bigint      not null,
    created_at  timestamptz not null default now(),

    primary key (user_id, target_type, target_id),
    constraint ck_bookmarks_target_type check (target_type in ('POST', 'PLACE')),
    constraint fk_bookmarks_user foreign key (user_id) references users (id) on delete cascade
);

comment on table bookmarks is
'저장함. 복합 PK 로 중복을 막는다. POST/PLACE 다형 대상이라 target_id 에 외래키가 없다 (CMU-024)';
comment on column bookmarks.target_id is
'POST 면 posts.post_id, PLACE 면 places.place_id';

-- 마이페이지 저장함은 최근 저장 순으로 본다 (CMU-023, PLC-015).
create index idx_bookmarks_user_created on bookmarks (user_id, target_type, created_at desc);
