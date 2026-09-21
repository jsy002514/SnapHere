-- SnapHere V5 — 게시글 인기 집계
-- 요구사항: PST-035, CMU-002, CMU-008, CMU-009 · 배치: JOB-013
-- 설계: docs/05-erd-reference.md > post_rankings
--
-- 기간별 인기 게시글은 이 테이블만 읽는다. 조회 시 점수를 계산하지 않는다 —
-- 좋아요·댓글·조회수를 매 요청마다 집계하면 무한 스크롤이 버티지 못한다.

create table post_rankings (
    post_id       bigint        not null,
    period        varchar(20)   not null,
    score         numeric(18, 4) not null,
    rank_no       integer       not null,
    calculated_at timestamptz   not null default now(),

    primary key (post_id, period),
    constraint ck_post_rankings_period check (period in ('HOURS_24', 'WEEKLY', 'MONTHLY', 'ALL')),
    constraint ck_post_rankings_rank   check (rank_no > 0),
    constraint uk_post_rankings_rank   unique (period, rank_no),
    constraint fk_post_rankings_post   foreign key (post_id) references posts (post_id) on delete cascade
);

comment on table post_rankings is
'기간별 게시글 인기 점수·순위. JOB-013 이 10분마다 기간 단위로 전체 재계산한다. 팔로잉 가중치는 사용자별이라 이 score 를 기준값으로 두고 조회 시 보정한다 (CMU-009)';
comment on column post_rankings.period is '조회 시점 기준 롤링 윈도우. WEEKLY 는 이번 주가 아니라 지금부터 7일 전까지다';
comment on column post_rankings.rank_no is
'1부터. (period, rank_no) UNIQUE 라 부분 갱신이 아니라 기간별 전체 삭제 후 재삽입으로 채운다 — 갱신 도중 두 게시글이 같은 순위를 갖는 상태가 없어야 한다';

-- 인기 목록은 순위 순으로 페이지를 넘긴다.
create index idx_post_rankings_period_rank on post_rankings (period, rank_no);
