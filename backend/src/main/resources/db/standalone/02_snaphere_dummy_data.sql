-- SnapHere standalone sample data
-- Run after 01_snaphere_schema.sql.
-- The rows use reserved ID ranges and ON CONFLICT DO NOTHING so this file can
-- be executed repeatedly without duplicating its own sample data.

begin;

set local timezone = 'UTC';

-- ---------------------------------------------------------------------------
-- Accounts and devices
-- ---------------------------------------------------------------------------

insert into users (
    user_id, provider, provider_user_id, email, nickname, profile_image_url, bio,
    locale, role, status, upload_blocked_until,
    push_like_enabled, push_follow_enabled, push_badge_enabled,
    badge_count, follower_count, following_count, post_count,
    withdrawn_at, purge_scheduled_at, restore_key, created_at, updated_at
) values
    (1, 'GOOGLE', 'google-admin-001', 'admin@example.invalid', '관리자',
     'https://example.invalid/profiles/admin.jpg', 'SnapHere 운영 계정',
     'ko', 'ADMIN', 'ACTIVE', null, true, true, true,
     0, 1, 0, 0, null, null, null, now() - interval '365 days', now()),
    (2, 'GOOGLE', 'google-mina-002', 'mina@example.invalid', '여행하는미나',
     'https://example.invalid/profiles/mina.jpg', '사진으로 여행을 기록해요.',
     'ko', 'USER', 'ACTIVE', null, true, true, true,
     2, 1, 2, 2, null, null, null, now() - interval '180 days', now()),
    (3, 'GOOGLE', 'google-joon-003', 'joon@example.invalid', '서울산책러',
     'https://example.invalid/profiles/joon.jpg', '주말마다 골목 산책',
     'ko', 'USER', 'ACTIVE', null, true, false, true,
     1, 1, 1, 2, null, null, null, now() - interval '120 days', now()),
    (4, 'GOOGLE', 'google-sora-004', 'sora@example.invalid', '바다좋아',
     null, '바다 사진을 모읍니다.',
     'ko', 'USER', 'SUSPENDED', now() + interval '12 hours', false, true, true,
     0, 0, 0, 1, null, null, null, now() - interval '60 days', now()),
    (5, 'GOOGLE', null, null, '탈퇴사용자', null, null,
     'ko', 'USER', 'WITHDRAWN', null, false, false, false,
     0, 0, 0, 0, now() - interval '3 days', now() + interval '27 days',
     repeat('e', 64), now() - interval '90 days', now() - interval '3 days')
on conflict do nothing;

insert into user_devices (device_id, user_id, fcm_token, platform, app_version, updated_at) values
    (101, 2, 'sample-fcm-token-android-mina', 'ANDROID', '1.0.0', now()),
    (102, 3, 'sample-fcm-token-ios-joon', 'IOS', '1.0.0', now()),
    (103, 4, null, 'ANDROID', '0.9.5', now() - interval '7 days')
on conflict do nothing;

insert into refresh_tokens (token_hash, user_id, device_id, expires_at, revoked_at) values
    (repeat('a', 64), 2, 101, now() + interval '30 days', null),
    (repeat('b', 64), 3, 102, now() + interval '30 days', null),
    (repeat('c', 64), 4, 103, now() + interval '20 days', now() - interval '1 day')
on conflict do nothing;

insert into account_deletion_logs (
    log_id, user_id, reason, content_action, deleted_at, purged_at
) values
    (1001, 5, '서비스를 잠시 쉬고 싶어요.', 'KEEP_ANONYMIZED', now() - interval '3 days', null)
on conflict do nothing;

insert into follows (follower_id, following_id, created_at) values
    (2, 3, now() - interval '30 days'),
    (2, 1, now() - interval '20 days'),
    (3, 2, now() - interval '25 days')
on conflict do nothing;

-- ---------------------------------------------------------------------------
-- Region and place master data
-- ---------------------------------------------------------------------------

insert into regions (
    area_code, name_ko, name_en, representative_image_url, default_event_verify_radius_m
) values
    (1,  '서울특별시', 'Seoul',     'https://example.invalid/regions/seoul.jpg', 2000),
    (2,  '인천광역시', 'Incheon',   'https://example.invalid/regions/incheon.jpg', 2000),
    (3,  '대전광역시', 'Daejeon',   'https://example.invalid/regions/daejeon.jpg', 2000),
    (4,  '대구광역시', 'Daegu',     'https://example.invalid/regions/daegu.jpg', 2000),
    (5,  '광주광역시', 'Gwangju',   'https://example.invalid/regions/gwangju.jpg', 2000),
    (6,  '부산광역시', 'Busan',     'https://example.invalid/regions/busan.jpg', 2000),
    (7,  '울산광역시', 'Ulsan',     'https://example.invalid/regions/ulsan.jpg', 2000),
    (8,  '세종특별자치시', 'Sejong', 'https://example.invalid/regions/sejong.jpg', 2000),
    (31, '경기도',     'Gyeonggi',   'https://example.invalid/regions/gyeonggi.jpg', 2000),
    (32, '강원특별자치도', 'Gangwon','https://example.invalid/regions/gangwon.jpg', 2000),
    (33, '충청북도',   'Chungbuk',   'https://example.invalid/regions/chungbuk.jpg', 2000),
    (34, '충청남도',   'Chungnam',   'https://example.invalid/regions/chungnam.jpg', 2000),
    (35, '경상북도',   'Gyeongbuk',  'https://example.invalid/regions/gyeongbuk.jpg', 2000),
    (36, '경상남도',   'Gyeongnam',  'https://example.invalid/regions/gyeongnam.jpg', 2000),
    (37, '전북특별자치도', 'Jeonbuk','https://example.invalid/regions/jeonbuk.jpg', 2000),
    (38, '전라남도',   'Jeonnam',    'https://example.invalid/regions/jeonnam.jpg', 2000),
    (39, '제주특별자치도', 'Jeju',   'https://example.invalid/regions/jeju.jpg', 2000)
on conflict do nothing;

insert into sigungu (area_code, sigungu_code, name_ko, name_en) values
    (1, 1, '종로구', 'Jongno-gu'),
    (1, 13, '마포구', 'Mapo-gu'),
    (6, 16, '해운대구', 'Haeundae-gu'),
    (39, 1, '제주시', 'Jeju-si'),
    (39, 2, '서귀포시', 'Seogwipo-si')
on conflict do nothing;

insert into places (
    place_id, place_type, content_id, content_type_id, title, addr1, geom,
    verify_radius_m, area_code, sigungu_code, has_coordinate,
    post_count, visit_count, view_count, created_by, created_at
) values
    (201, 'OFFICIAL', 126508, 12, '경복궁', '서울특별시 종로구 사직로 161',
     ST_SetSRID(ST_MakePoint(126.9770162, 37.5796170), 4326)::geography,
     500, 1, 1, true, 2, 2, 145, null, now() - interval '2 years'),
    (202, 'USER', null, null, '홍대 벽화 골목', '서울특별시 마포구 와우산로 일대',
     ST_SetSRID(ST_MakePoint(126.9237070, 37.5546800), 4326)::geography,
     100, 1, 13, true, 1, 1, 78, 2, now() - interval '40 days'),
    (203, 'OFFICIAL', 126081, 12, '해운대해수욕장', '부산광역시 해운대구 해운대해변로 264',
     ST_SetSRID(ST_MakePoint(129.1603842, 35.1586975), 4326)::geography,
     500, 6, 16, true, 1, 1, 203, null, now() - interval '2 years'),
    (204, 'OFFICIAL', 126435, 12, '성산일출봉', '제주특별자치도 서귀포시 성산읍 일출로 284-12',
     ST_SetSRID(ST_MakePoint(126.9405375, 33.4580560), 4326)::geography,
     500, 39, 2, true, 1, 1, 321, null, now() - interval '2 years'),
    (205, 'OFFICIAL', 999999, 14, '좌표 확인 중인 관광지', '제주특별자치도',
     null, 500, 39, null, false, 1, 0, 3, null, now() - interval '10 days')
on conflict do nothing;

insert into place_details (
    place_id, language_code, overview, tel, homepage, use_time, rest_date
) values
    (201, 'ko', '조선 왕조의 대표적인 법궁입니다.', '02-3700-3900',
     'https://example.invalid/places/gyeongbokgung', '09:00~18:00', '화요일'),
    (201, 'en', 'A representative royal palace of the Joseon dynasty.', null,
     'https://example.invalid/en/places/gyeongbokgung', '09:00-18:00', 'Tuesday'),
    (202, 'ko', '사용자가 등록한 홍대의 사진 명소입니다.', null, null, '상시', '없음'),
    (203, 'ko', '부산을 대표하는 해수욕장입니다.', '051-749-5700',
     'https://example.invalid/places/haeundae', '상시', '없음'),
    (204, 'ko', '제주 동쪽의 대표적인 일출 명소입니다.', '064-783-0959',
     'https://example.invalid/places/seongsan', '일출 1시간 전~20:00', '없음')
on conflict do nothing;

insert into events (
    event_id, content_id, title, overview, area_code, place_id,
    start_date, end_date, thumbnail_url, fixed_tags,
    participant_count, source, verify_radius_m
) values
    (301, null, '홍대 봄 사진 산책', '골목을 걸으며 봄 풍경을 촬영하는 행사',
     1, 202, current_date - 3, current_date + 7,
     'https://example.invalid/events/hongdae-spring.jpg', '["서울", "홍대봄산책"]'::jsonb,
     2, 'MANUAL', 1500),
    (302, 20260001, '해운대 모래축제', '해운대 해변에서 열리는 모래 작품 축제',
     6, 203, current_date - 1, current_date + 14,
     'https://example.invalid/events/sand-festival.jpg', '["부산", "해운대모래축제"]'::jsonb,
     1, 'TOURAPI', null)
on conflict do nothing;

-- ---------------------------------------------------------------------------
-- Posts, images, comments and community actions
-- ---------------------------------------------------------------------------

insert into posts (
    post_id, user_id, place_id, event_id, content, original_language_code,
    tier, lat, lng, taken_at, source, area_code,
    like_count, comment_count, view_count, status, created_at, updated_at, deleted_at
) values
    (1001, 2, 201, null, '비 온 뒤의 경복궁은 색이 더 선명해요. #경복궁', 'ko',
     'HIGH', 37.5796200, 126.9770200, now() - interval '35 minutes', 'CAMERA', 1,
     2, 2, 31, 'ACTIVE', now() - interval '30 minutes', now() - interval '30 minutes', null),
    (1002, 3, 201, null, '오늘 광화문 산책 사진입니다.', 'ko',
     'MEDIUM', 37.5798000, 126.9773000, now() - interval '3 hours', 'ALBUM', 1,
     1, 1, 18, 'ACTIVE', now() - interval '2 hours', now() - interval '2 hours', null),
    (1003, 2, 202, 301, '홍대 봄 사진 산책에 참여했어요.', 'ko',
     'MEDIUM', 37.5547000, 126.9237200, now() - interval '1 day', 'ALBUM', 1,
     0, 0, 7, 'ACTIVE', now() - interval '23 hours', now() - interval '23 hours', null),
    (1004, 3, 204, null, '성산일출봉에서 본 아침', 'ko',
     'HIGH', 33.4581000, 126.9405000, now() - interval '9 days', 'CAMERA', 39,
     0, 0, 42, 'ACTIVE', now() - interval '9 days', now() - interval '9 days', null),
    (1005, 4, 203, 302, '해운대 모래축제 준비 현장', 'ko',
     'MEDIUM', 35.1587000, 129.1604000, now() - interval '5 hours', 'CAMERA', 6,
     0, 0, 5, 'BLINDED', now() - interval '5 hours', now() - interval '1 hour', null),
    (1006, 4, 205, null, '좌표가 없어 낮음 등급으로 등록된 게시글', 'ko',
     'LOW', null, null, now() - interval '5 hours', 'ALBUM', 39,
     0, 0, 2, 'ACTIVE', now() - interval '4 hours', now() - interval '4 hours', null),
    (1007, 3, 204, null, '삭제된 게시글의 예시', 'ko',
     'HIGH', 33.4580500, 126.9405500, now() - interval '20 days', 'CAMERA', 39,
     0, 0, 3, 'DELETED', now() - interval '20 days', now() - interval '2 days', now() - interval '2 days')
on conflict do nothing;

insert into post_images (
    post_image_id, post_id, image_key, thumbnail_url, aspect_ratio, sort_order
) values
    (2001, 1001, 'posts/1001/original-1.jpg', 'https://example.invalid/thumbs/1001-1.jpg', 1.333, 0),
    (2002, 1001, 'posts/1001/original-2.jpg', 'https://example.invalid/thumbs/1001-2.jpg', 0.750, 1),
    (2003, 1002, 'posts/1002/original-1.jpg', 'https://example.invalid/thumbs/1002-1.jpg', 1.000, 0),
    (2004, 1003, 'posts/1003/original-1.jpg', null, 1.500, 0),
    (2005, 1004, 'posts/1004/original-1.jpg', 'https://example.invalid/thumbs/1004-1.jpg', 1.333, 0),
    (2006, 1005, 'posts/1005/original-1.jpg', 'https://example.invalid/thumbs/1005-1.jpg', 1.777, 0),
    (2007, 1006, 'posts/1006/original-1.jpg', null, 1.333, 0),
    (2008, 1007, 'posts/1007/original-1.jpg', 'https://example.invalid/thumbs/1007-1.jpg', 1.333, 0)
on conflict do nothing;

insert into comments (
    comment_id, post_id, user_id, parent_id, content, like_count, status, created_at
) values
    (4001, 1001, 3, null, '색감이 정말 좋네요!', 1, 'ACTIVE', now() - interval '20 minutes'),
    (4002, 1001, 2, 4001, '감사합니다. 비가 그치자마자 찍었어요.', 0, 'ACTIVE', now() - interval '15 minutes'),
    (4003, 1002, 2, null, '삭제된 댓글의 예시입니다.', 0, 'DELETED', now() - interval '1 hour')
on conflict do nothing;

insert into likes (user_id, target_type, target_id, created_at) values
    (3, 'POST', 1001, now() - interval '18 minutes'),
    (1, 'POST', 1001, now() - interval '10 minutes'),
    (2, 'POST', 1002, now() - interval '40 minutes'),
    (2, 'COMMENT', 4001, now() - interval '12 minutes')
on conflict do nothing;

insert into bookmarks (user_id, target_type, target_id, created_at) values
    (2, 'PLACE', 204, now() - interval '8 days'),
    (3, 'PLACE', 201, now() - interval '10 days'),
    (3, 'POST', 1001, now() - interval '15 minutes')
on conflict do nothing;

insert into tags (tag_id, name, normalized_name, theme_code, usage_count) values
    (501, '경복궁', '경복궁', 'HERITAGE', 2),
    (502, '서울여행', '서울여행', 'SEOUL', 3),
    (503, '홍대봄산책', '홍대봄산책', 'EVENT', 1),
    (504, '제주일출', '제주일출', 'JEJU', 1),
    (505, '해운대', '해운대', 'BUSAN', 1)
on conflict do nothing;

insert into post_tags (post_id, tag_id, is_locked, is_suggested) values
    (1001, 501, false, false),
    (1001, 502, false, true),
    (1002, 501, false, false),
    (1003, 502, true, false),
    (1003, 503, true, false),
    (1004, 504, false, false),
    (1005, 505, true, false),
    (1006, 504, false, true)
on conflict do nothing;

-- ---------------------------------------------------------------------------
-- Badges and visits
-- ---------------------------------------------------------------------------

insert into badges (
    badge_id, code, type, name_ko, name_en, description, icon_url,
    condition_json, event_id, area_code, is_obtainable, available_from, available_to
) values
    (601, 'EVENT_HONGDAE_SPRING', 'EVENT', '홍대 봄 산책', 'Hongdae Spring Walk',
     '홍대 봄 사진 산책에 참여하면 획득', 'https://example.invalid/badges/hongdae.png',
     '{"type":"EVENT_PARTICIPATE"}'::jsonb, 301, null, true,
     now() - interval '3 days', now() + interval '7 days'),
    (602, 'AREA_SEOUL_5', 'AREA', '서울 기록가', 'Seoul Recorder',
     '서울에서 게시글 5개를 등록하면 획득', 'https://example.invalid/badges/seoul.png',
     '{"type":"AREA_POST_COUNT","threshold":5}'::jsonb, null, 1, true, null, null),
    (603, 'VISIT_ALL_17', 'COMPLETION', '전국 완주', 'All Regions',
     '17개 시도를 모두 방문하면 획득', 'https://example.invalid/badges/all-regions.png',
     '{"type":"VISITED_AREA_COUNT","threshold":17}'::jsonb, null, null, true, null, null),
    (604, 'POST_COUNT_10', 'RECORD', '첫 열 장', 'First Ten Posts',
     '게시글 10개를 등록하면 획득', 'https://example.invalid/badges/ten-posts.png',
     '{"type":"TOTAL_POST_COUNT","threshold":10}'::jsonb, null, null, true, null, null)
on conflict do nothing;

insert into user_badges (user_id, badge_id, earned_at, source_post_id) values
    (2, 601, now() - interval '23 hours', 1003),
    (2, 602, now() - interval '10 days', 1001),
    (3, 604, now() - interval '20 days', null)
on conflict do nothing;

insert into visits (visit_id, user_id, place_id, post_id, visited_on) values
    (701, 2, 201, 1001, current_date),
    (702, 3, 201, 1002, current_date),
    (703, 2, 202, 1003, current_date - 1),
    (704, 3, 204, null, current_date - 9),
    (705, 4, 203, 1005, current_date)
on conflict do nothing;

-- ---------------------------------------------------------------------------
-- Aggregate snapshots
-- ---------------------------------------------------------------------------

insert into heatmap_cells (
    cell_id, grid_level, lat, lng, period, post_count, visit_count,
    user_count, top_place_id, sample_post_ids, last_posted_at, calculated_at
) values
    (801, 2, 37.5750000, 126.9750000, 'LAST_1H', 1, 1, 1, 201,
     '[1001]'::jsonb, now() - interval '30 minutes', now()),
    (802, 2, 37.5750000, 126.9750000, 'LAST_24H', 2, 2, 2, 201,
     '[1001,1002]'::jsonb, now() - interval '30 minutes', now()),
    (803, 2, 37.5550000, 126.9250000, 'WEEKLY', 1, 1, 1, 202,
     '[1003]'::jsonb, now() - interval '23 hours', now()),
    (805, 0, 33.5000000, 126.5000000, 'MONTHLY', 1, 1, 1, 204,
     '[1004]'::jsonb, now() - interval '9 days', now())
on conflict do nothing;

insert into post_rankings (post_id, period, score, rank_no, calculated_at) values
    (1001, 'HOURS_24', 12.5500, 1, now()),
    (1002, 'HOURS_24', 7.4000, 2, now()),
    (1003, 'HOURS_24', 1.1500, 3, now()),
    (1001, 'WEEKLY', 14.2000, 1, now()),
    (1002, 'WEEKLY', 8.1000, 2, now()),
    (1006, 'WEEKLY', 0.5000, 3, now()),
    (1004, 'MONTHLY', 6.3000, 3, now()),
    (1001, 'ALL', 16.5000, 1, now())
on conflict do nothing;

insert into region_stats (area_code, period, post_count, contributor_count) values
    (1, 'LAST_1H', 1, 1),
    (1, 'LAST_24H', 3, 2),
    (1, 'WEEKLY', 3, 2),
    (39, 'MONTHLY', 1, 1)
on conflict do nothing;

insert into place_rankings (
    ranking_id, place_id, area_code, period, theme,
    score, rank_no, previous_rank, calculated_at
) values
    (901, 201, 1, 'DAILY', 'ALL', 18.2500, 1, 2, now()),
    (902, 202, 1, 'DAILY', 'ALL', 7.5000, 2, 1, now()),
    (903, 203, 6, 'WEEKLY', 'ALL', 11.0000, 1, 1, now()),
    (904, 204, 39, 'MONTHLY', 'JEJU', 15.8000, 1, null, now())
on conflict do nothing;

-- ---------------------------------------------------------------------------
-- Notifications and operations
-- ---------------------------------------------------------------------------

insert into notifications (
    notification_id, recipient_id, actor_id, type, target_type, target_id,
    message_key, message_params, is_read, created_at
) values
    (1101, 2, 3, 'POST_LIKE', 'POST', 1001,
     'notification.post-like', '{"actorNickname":"서울산책러"}'::jsonb, false, now() - interval '18 minutes'),
    (1102, 3, 2, 'FOLLOW', 'USER', 2,
     'notification.follow', '{"actorNickname":"여행하는미나"}'::jsonb, true, now() - interval '30 days'),
    (1103, 2, null, 'BADGE_EARNED', 'BADGE', 601,
     'notification.badge-earned', '{"badgeName":"홍대 봄 산책"}'::jsonb, false, now() - interval '23 hours'),
    (1104, 4, null, 'SYSTEM', 'NONE', null,
     'notification.upload-blocked', '{"hours":24}'::jsonb, false, now() - interval '12 hours')
on conflict do nothing;

insert into reports (
    report_id, reporter_id, target_type, target_id, reason, status, created_at
) values
    (1201, 2, 'POST', 1005, '장소와 관련 없는 사진입니다.', 'PENDING', now() - interval '2 hours'),
    (1202, 3, 'PLACE', 202, '장소 이름을 확인해주세요.', 'RESOLVED', now() - interval '7 days')
on conflict do nothing;

insert into sync_logs (
    sync_id, job_type, area_code, content_type_id, result, count, message, created_at
) values
    (1301, 'PLACE', 1, 12, 'SUCCESS', 2, '서울 관광지 동기화 완료', now() - interval '1 day'),
    (1302, 'EVENT', 6, 15, 'PARTIAL', 1, '일부 상세 정보 재시도 필요', now() - interval '6 hours'),
    (1303, 'HEATMAP', null, null, 'SUCCESS', 4, '샘플 히트맵 집계 완료', now())
on conflict do nothing;

insert into search_logs (log_id, keyword, area_code, searched_at) values
    (1401, '경복궁', 1, now() - interval '50 minutes'),
    (1402, '해운대', 6, now() - interval '30 minutes'),
    (1403, '제주 일출', 39, now() - interval '10 minutes'),
    (1404, '봄 축제', null, now() - interval '5 minutes')
on conflict do nothing;

-- Explicit IDs above make the sample deterministic. Move identity sequences
-- past the sample ranges so subsequent manual inserts do not collide.
select setval(pg_get_serial_sequence('users', 'user_id'), (select max(user_id) from users), true);
select setval(pg_get_serial_sequence('user_devices', 'device_id'), (select max(device_id) from user_devices), true);
select setval(pg_get_serial_sequence('account_deletion_logs', 'log_id'), (select max(log_id) from account_deletion_logs), true);
select setval(pg_get_serial_sequence('places', 'place_id'), (select max(place_id) from places), true);
select setval(pg_get_serial_sequence('events', 'event_id'), (select max(event_id) from events), true);
select setval(pg_get_serial_sequence('posts', 'post_id'), (select max(post_id) from posts), true);
select setval(pg_get_serial_sequence('post_images', 'post_image_id'), (select max(post_image_id) from post_images), true);
select setval(pg_get_serial_sequence('comments', 'comment_id'), (select max(comment_id) from comments), true);
select setval(pg_get_serial_sequence('tags', 'tag_id'), (select max(tag_id) from tags), true);
select setval(pg_get_serial_sequence('badges', 'badge_id'), (select max(badge_id) from badges), true);
select setval(pg_get_serial_sequence('visits', 'visit_id'), (select max(visit_id) from visits), true);
select setval(pg_get_serial_sequence('heatmap_cells', 'cell_id'), (select max(cell_id) from heatmap_cells), true);
select setval(pg_get_serial_sequence('place_rankings', 'ranking_id'), (select max(ranking_id) from place_rankings), true);
select setval(pg_get_serial_sequence('notifications', 'notification_id'), (select max(notification_id) from notifications), true);
select setval(pg_get_serial_sequence('reports', 'report_id'), (select max(report_id) from reports), true);
select setval(pg_get_serial_sequence('sync_logs', 'sync_id'), (select max(sync_id) from sync_logs), true);
select setval(pg_get_serial_sequence('search_logs', 'log_id'), (select max(log_id) from search_logs), true);

commit;
