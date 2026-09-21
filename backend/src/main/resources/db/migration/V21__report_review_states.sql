-- SYS-017: 최신 ERD의 신고 상태(PENDING/RESOLVED/REJECTED)로 통일한다.
alter table reports drop constraint ck_reports_reviewed;
alter table reports drop constraint ck_reports_status;

update reports set status = 'RESOLVED' where status = 'REVIEWED';

alter table reports add constraint ck_reports_status
    check (status in ('PENDING', 'RESOLVED', 'REJECTED'));
alter table reports add constraint ck_reports_reviewed
    check ((status = 'PENDING' and reviewed_at is null)
        or (status in ('RESOLVED', 'REJECTED') and reviewed_at is not null));

drop index if exists idx_reports_pending;
create index idx_reports_review_queue
    on reports (status, target_type, report_id desc);
