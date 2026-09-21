-- SnapHere local sample data for the application Flyway schema (V1-V18).
-- Adapted from Downloads/02_snaphere_dummy_data.sql.
-- Safe to run repeatedly: deterministic IDs and ON CONFLICT DO NOTHING are used.
-- This is a developer seed, not a Flyway migration.

begin;

set local timezone = 'UTC';

-- Standalone bigint user IDs are mapped to deterministic UUIDs.
insert into users (
    id, google_subject, email, nickname, profile_image_url, bio, locale,
    role, status, onboarding_completed, terms_version, terms_agreed_at,
    upload_blocked_until, push_like_enabled, push_follow_enabled,
    push_badge_enabled, badge_count, follower_count, following_count,
    post_count, withdrawn_at, purge_scheduled_at, restore_key,
    created_at, updated_at
) values
    ('11111111-1111-4111-8111-111111111111', 'google-admin-001',
     'admin@example.invalid', '관리자', 'https://example.invalid/profiles/admin.jpg',
     'SnapHere 운영 계정', 'ko-KR', 'ADMIN', 'ACTIVE', true, 'local-v1',
     now() - interval '365 days', null, true, true, true, 0, 1, 0, 0,
     null, null, null, now() - interval '365 days', now()),
    ('22222222-2222-4222-8222-222222222222', 'google-mina-002',
     'mina@example.invalid', '여행하는미나', 'https://example.invalid/profiles/mina.jpg',
     '사진으로 여행을 기록해요.', 'ko-KR', 'USER', 'ACTIVE', true, 'local-v1',
     now() - interval '180 days', null, true, true, true, 2, 1, 2, 2,
     null, null, null, now() - interval '180 days', now()),
    ('33333333-3333-4333-8333-333333333333', 'google-joon-003',
     'joon@example.invalid', '서울산책러', 'https://example.invalid/profiles/joon.jpg',
     '주말마다 골목 산책', 'ko-KR', 'USER', 'ACTIVE', true, 'local-v1',
     now() - interval '120 days', null, true, false, true, 1, 1, 1, 2,
     null, null, null, now() - interval '120 days', now()),
    ('44444444-4444-4444-8444-444444444444', 'google-sora-004',
     'sora@example.invalid', '바다좋아', null, '바다 사진을 모읍니다.',
     'ko-KR', 'USER', 'SUSPENDED', true, 'local-v1',
     now() - interval '60 days', now() + interval '12 hours', false, true, true,
     0, 0, 0, 1, null, null, null, now() - interval '60 days', now()),
    ('55555555-5555-4555-8555-555555555555', 'withdrawn-local-005',
     'withdrawn@example.invalid', '탈퇴사용자', null, null, 'ko-KR', 'USER',
     'WITHDRAWN', true, 'local-v1', now() - interval '90 days', null,
     false, false, false, 0, 0, 0, 0, now() - interval '3 days',
     now() + interval '27 days', repeat('e', 64),
     now() - interval '90 days', now() - interval '3 days')
on conflict do nothing;

insert into user_devices (
    id, user_id, device_identifier, fcm_token, platform, app_version, updated_at
) values
    ('00000000-0000-4000-8000-000000000101',
     '22222222-2222-4222-8222-222222222222', 'sample-device-android-mina',
     'sample-fcm-token-android-mina', 'ANDROID', '1.0.0', now()),
    ('00000000-0000-4000-8000-000000000102',
     '33333333-3333-4333-8333-333333333333', 'sample-device-ios-joon',
     'sample-fcm-token-ios-joon', 'IOS', '1.0.0', now()),
    ('00000000-0000-4000-8000-000000000103',
     '44444444-4444-4444-8444-444444444444', 'sample-device-android-sora',
     null, 'ANDROID', '0.9.5', now() - interval '7 days')
on conflict do nothing;

insert into refresh_tokens (
    id, token_hash, user_id, device_id, expires_at, revoked_at, created_at
) values
    ('00000000-0000-4000-8000-000000000201', repeat('a', 64),
     '22222222-2222-4222-8222-222222222222',
     '00000000-0000-4000-8000-000000000101', now() + interval '30 days', null, now()),
    ('00000000-0000-4000-8000-000000000202', repeat('b', 64),
     '33333333-3333-4333-8333-333333333333',
     '00000000-0000-4000-8000-000000000102', now() + interval '30 days', null, now()),
    ('00000000-0000-4000-8000-000000000203', repeat('c', 64),
     '44444444-4444-4444-8444-444444444444',
     '00000000-0000-4000-8000-000000000103', now() + interval '20 days',
     now() - interval '1 day', now() - interval '10 days')
on conflict do nothing;

insert into account_deletion_logs (
    log_id, user_id, reason, content_action, deleted_at, purged_at
) values
    (1001, '55555555-5555-4555-8555-555555555555',
     '서비스를 잠시 쉬고 싶어요.', 'KEEP_ANONYMIZED', now() - interval '3 days', null)
on conflict do nothing;

insert into follows (follower_id, following_id, created_at) values
    ('22222222-2222-4222-8222-222222222222',
     '33333333-3333-4333-8333-333333333333', now() - interval '30 days'),
    ('22222222-2222-4222-8222-222222222222',
     '11111111-1111-4111-8111-111111111111', now() - interval '20 days'),
    ('33333333-3333-4333-8333-333333333333',
     '22222222-2222-4222-8222-222222222222', now() - interval '25 days')
on conflict do nothing;

-- Flyway V4 already creates all 17 regions. Enrich those rows for the demo.
insert into regions (
    area_code, name_ko, name_en, representative_image_url,
    default_event_verify_radius_m
) values
    (1,  '서울특별시', 'Seoul', 'https://example.invalid/regions/seoul.jpg', 2000),
    (2,  '인천광역시', 'Incheon', 'https://example.invalid/regions/incheon.jpg', 2000),
    (3,  '대전광역시', 'Daejeon', 'https://example.invalid/regions/daejeon.jpg', 2000),
    (4,  '대구광역시', 'Daegu', 'https://example.invalid/regions/daegu.jpg', 2000),
    (5,  '광주광역시', 'Gwangju', 'https://example.invalid/regions/gwangju.jpg', 2000),
    (6,  '부산광역시', 'Busan', 'https://example.invalid/regions/busan.jpg', 2000),
    (7,  '울산광역시', 'Ulsan', 'https://example.invalid/regions/ulsan.jpg', 2000),
    (8,  '세종특별자치시', 'Sejong', 'https://example.invalid/regions/sejong.jpg', 2000),
    (31, '경기도', 'Gyeonggi', 'https://example.invalid/regions/gyeonggi.jpg', 2000),
    (32, '강원특별자치도', 'Gangwon', 'https://example.invalid/regions/gangwon.jpg', 2000),
    (33, '충청북도', 'Chungbuk', 'https://example.invalid/regions/chungbuk.jpg', 2000),
    (34, '충청남도', 'Chungnam', 'https://example.invalid/regions/chungnam.jpg', 2000),
    (35, '경상북도', 'Gyeongbuk', 'https://example.invalid/regions/gyeongbuk.jpg', 2000),
    (36, '경상남도', 'Gyeongnam', 'https://example.invalid/regions/gyeongnam.jpg', 2000),
    (37, '전북특별자치도', 'Jeonbuk', 'https://example.invalid/regions/jeonbuk.jpg', 2000),
    (38, '전라남도', 'Jeonnam', 'https://example.invalid/regions/jeonnam.jpg', 2000),
    (39, '제주특별자치도', 'Jeju', 'https://example.invalid/regions/jeju.jpg', 2000)
on conflict (area_code) do update set
    representative_image_url = excluded.representative_image_url,
    default_event_verify_radius_m = excluded.default_event_verify_radius_m,
    updated_at = now();

insert into sigungu (area_code, sigungu_code, name_ko, name_en) values
    (1, 1, '종로구', 'Jongno-gu'),
    (1, 13, '마포구', 'Mapo-gu'),
    (6, 16, '해운대구', 'Haeundae-gu'),
    (39, 1, '제주시', 'Jeju-si'),
    (39, 2, '서귀포시', 'Seogwipo-si')
on conflict do nothing;

-- geom and has_coordinate are generated from lat/lng in the Flyway schema.
insert into places (
    place_id, place_type, content_id, content_type_id, title, normalized_title,
    addr1, lat, lng, verify_radius_m, area_code, sigungu_code, status,
    post_count, visit_count, view_count, created_by, image_url, created_at, updated_at
) values
    (201, 'OFFICIAL', 126508, 12, '경복궁', '경복궁',
     '서울특별시 종로구 사직로 161', 37.5796170, 126.9770162,
     500, 1, 1, 'ACTIVE', 2, 2, 145, null,
     'https://example.invalid/places/gyeongbokgung.jpg', now() - interval '2 years', now()),
    (202, 'USER', null, null, '홍대 벽화 골목', '홍대 벽화 골목',
     '서울특별시 마포구 와우산로 일대', 37.5546800, 126.9237070,
     100, 1, 13, 'ACTIVE', 1, 1, 78,
     '22222222-2222-4222-8222-222222222222',
     'https://example.invalid/places/hongdae.jpg', now() - interval '40 days', now()),
    (203, 'OFFICIAL', 126081, 12, '해운대해수욕장', '해운대해수욕장',
     '부산광역시 해운대구 해운대해변로 264', 35.1586975, 129.1603842,
     500, 6, 16, 'ACTIVE', 1, 1, 203, null,
     'https://example.invalid/places/haeundae.jpg', now() - interval '2 years', now()),
    (204, 'OFFICIAL', 126435, 12, '성산일출봉', '성산일출봉',
     '제주특별자치도 서귀포시 성산읍 일출로 284-12', 33.4580560, 126.9405375,
     500, 39, 2, 'ACTIVE', 1, 1, 321, null,
     'https://example.invalid/places/seongsan.jpg', now() - interval '2 years', now()),
    (205, 'OFFICIAL', 999999, 14, '좌표 확인 중인 관광지', '좌표 확인 중인 관광지',
     '제주특별자치도', null, null, 500, 39, null, 'ACTIVE', 1, 0, 3,
     null, null, now() - interval '10 days', now())
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
    event_id, content_id, title, overview, area_code, place_id, start_date,
    end_date, thumbnail_url, fixed_tags, participant_count, source,
    verify_radius_m, status
) values
    (301, null, '홍대 봄 사진 산책', '골목을 걸으며 봄 풍경을 촬영하는 행사',
     1, 202, current_date - 3, current_date + 7,
     'https://example.invalid/events/hongdae-spring.jpg',
     '["서울", "홍대봄산책"]'::jsonb, 2, 'MANUAL', 1500, 'ACTIVE'),
    (302, '20260001', '해운대 모래축제', '해운대 해변에서 열리는 모래 작품 축제',
     6, 203, current_date - 1, current_date + 14,
     'https://example.invalid/events/sand-festival.jpg',
     '["부산", "해운대모래축제"]'::jsonb, 1, 'TOURAPI', null, 'ACTIVE')
on conflict do nothing;

-- BLINDED from the standalone schema maps to HIDDEN in Flyway.
insert into posts (
    post_id, user_id, place_id, event_id, content, original_language_code,
    tier, lat, lng, taken_at, source, area_code, like_count, comment_count,
    view_count, status, created_at, updated_at, deleted_at
) values
    (1001, '22222222-2222-4222-8222-222222222222', 201, null,
     '비 온 뒤의 경복궁은 색이 더 선명해요. #경복궁', 'ko', 'HIGH',
     37.5796200, 126.9770200, now() - interval '35 minutes', 'CAMERA', 1,
     2, 2, 31, 'ACTIVE', now() - interval '30 minutes', now() - interval '30 minutes', null),
    (1002, '33333333-3333-4333-8333-333333333333', 201, null,
     '오늘 광화문 산책 사진입니다.', 'ko', 'MEDIUM', 37.5798000, 126.9773000,
     now() - interval '3 hours', 'ALBUM', 1, 1, 1, 18, 'ACTIVE',
     now() - interval '2 hours', now() - interval '2 hours', null),
    (1003, '22222222-2222-4222-8222-222222222222', 202, 301,
     '홍대 봄 사진 산책에 참여했어요.', 'ko', 'MEDIUM', 37.5547000, 126.9237200,
     now() - interval '1 day', 'ALBUM', 1, 0, 0, 7, 'ACTIVE',
     now() - interval '23 hours', now() - interval '23 hours', null),
    (1004, '33333333-3333-4333-8333-333333333333', 204, null,
     '성산일출봉에서 본 아침', 'ko', 'HIGH', 33.4581000, 126.9405000,
     now() - interval '9 days', 'CAMERA', 39, 0, 0, 42, 'ACTIVE',
     now() - interval '9 days', now() - interval '9 days', null),
    (1005, '44444444-4444-4444-8444-444444444444', 203, 302,
     '해운대 모래축제 준비 현장', 'ko', 'MEDIUM', 35.1587000, 129.1604000,
     now() - interval '5 hours', 'CAMERA', 6, 0, 0, 5, 'HIDDEN',
     now() - interval '5 hours', now() - interval '1 hour', null),
    (1006, '44444444-4444-4444-8444-444444444444', 205, null,
     '좌표가 없어 낮음 등급으로 등록된 게시글', 'ko', 'LOW', null, null,
     now() - interval '5 hours', 'ALBUM', 39, 0, 0, 2, 'ACTIVE',
     now() - interval '4 hours', now() - interval '4 hours', null),
    (1007, '33333333-3333-4333-8333-333333333333', 204, null,
     '삭제된 게시글의 예시', 'ko', 'HIGH', 33.4580500, 126.9405500,
     now() - interval '20 days', 'CAMERA', 39, 0, 0, 3, 'DELETED',
     now() - interval '20 days', now() - interval '2 days', now() - interval '2 days')
on conflict do nothing;

-- Flyway uses one-based image order (1..4), unlike the standalone sample.
insert into post_images (
    post_image_id, post_id, image_key, thumbnail_url, aspect_ratio, sort_order
) values
    (2001, 1001, 'posts/1001/original-1.jpg', 'https://example.invalid/thumbs/1001-1.jpg', 1.333, 1),
    (2002, 1001, 'posts/1001/original-2.jpg', 'https://example.invalid/thumbs/1001-2.jpg', 0.750, 2),
    (2003, 1002, 'posts/1002/original-1.jpg', 'https://example.invalid/thumbs/1002-1.jpg', 1.000, 1),
    (2004, 1003, 'posts/1003/original-1.jpg', null, 1.500, 1),
    (2005, 1004, 'posts/1004/original-1.jpg', 'https://example.invalid/thumbs/1004-1.jpg', 1.333, 1),
    (2006, 1005, 'posts/1005/original-1.jpg', 'https://example.invalid/thumbs/1005-1.jpg', 1.777, 1),
    (2007, 1006, 'posts/1006/original-1.jpg', null, 1.333, 1),
    (2008, 1007, 'posts/1007/original-1.jpg', 'https://example.invalid/thumbs/1007-1.jpg', 1.333, 1)
on conflict (post_image_id) do nothing;

-- Deleted comments must have a NULL body in the Flyway schema.
insert into comments (
    comment_id, post_id, user_id, parent_id, content, like_count, status,
    created_at, updated_at
) values
    (4001, 1001, '33333333-3333-4333-8333-333333333333', null,
     '색감이 정말 좋네요!', 1, 'ACTIVE', now() - interval '20 minutes', now() - interval '20 minutes'),
    (4002, 1001, '22222222-2222-4222-8222-222222222222', 4001,
     '감사합니다. 비가 그치자마자 찍었어요.', 0, 'ACTIVE',
     now() - interval '15 minutes', now() - interval '15 minutes'),
    (4003, 1002, '22222222-2222-4222-8222-222222222222', null,
     null, 0, 'DELETED', now() - interval '1 hour', now() - interval '1 hour')
on conflict do nothing;

insert into likes (user_id, target_type, target_id, created_at) values
    ('33333333-3333-4333-8333-333333333333', 'POST', 1001, now() - interval '18 minutes'),
    ('11111111-1111-4111-8111-111111111111', 'POST', 1001, now() - interval '10 minutes'),
    ('22222222-2222-4222-8222-222222222222', 'POST', 1002, now() - interval '40 minutes'),
    ('22222222-2222-4222-8222-222222222222', 'COMMENT', 4001, now() - interval '12 minutes')
on conflict do nothing;

insert into bookmarks (user_id, target_type, target_id, created_at) values
    ('22222222-2222-4222-8222-222222222222', 'PLACE', 204, now() - interval '8 days'),
    ('33333333-3333-4333-8333-333333333333', 'PLACE', 201, now() - interval '10 days'),
    ('33333333-3333-4333-8333-333333333333', 'POST', 1001, now() - interval '15 minutes')
on conflict do nothing;

insert into tags (tag_id, name, normalized_name, theme_code, usage_count) values
    (501, '경복궁', '경복궁', 'HERITAGE', 2),
    (502, '서울여행', '서울여행', 'SEOUL', 3),
    (503, '홍대봄산책', '홍대봄산책', 'EVENT', 1),
    (504, '제주일출', '제주일출', 'JEJU', 1),
    (505, '해운대', '해운대', 'BUSAN', 1)
on conflict do nothing;

insert into post_tags (post_id, tag_id, is_locked, is_suggested) values
    (1001, 501, false, false), (1001, 502, false, true),
    (1002, 501, false, false), (1003, 502, true, false),
    (1003, 503, true, false), (1004, 504, false, false),
    (1005, 505, true, false), (1006, 504, false, true)
on conflict do nothing;

insert into badges (
    badge_id, code, type, name_ko, name_en, description, icon_url,
    condition_json, event_id, area_code, is_obtainable, available_from, available_to
) values
    (601, 'EVENT_HONGDAE_SPRING', 'EVENT', '홍대 봄 산책', 'Hongdae Spring Walk',
     '홍대 봄 사진 산책에 참여하면 획득', 'https://example.invalid/badges/hongdae.png',
     '{"type":"EVENT_PARTICIPATE"}'::jsonb, 301, null, true,
     current_date - 3, current_date + 7),
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
    ('22222222-2222-4222-8222-222222222222', 601, now() - interval '23 hours', 1003),
    ('22222222-2222-4222-8222-222222222222', 602, now() - interval '10 days', 1001),
    ('33333333-3333-4333-8333-333333333333', 604, now() - interval '20 days', null)
on conflict do nothing;

-- visits.post_id is mandatory in Flyway, so visit 704 points to its source post 1004.
insert into visits (visit_id, user_id, place_id, post_id, visited_on) values
    (701, '22222222-2222-4222-8222-222222222222', 201, 1001, current_date),
    (702, '33333333-3333-4333-8333-333333333333', 201, 1002, current_date),
    (703, '22222222-2222-4222-8222-222222222222', 202, 1003, current_date - 1),
    (704, '33333333-3333-4333-8333-333333333333', 204, 1004, current_date - 9),
    (705, '44444444-4444-4444-8444-444444444444', 203, 1005, current_date)
on conflict do nothing;

-- JSON sample arrays become paired bigint[] and text[] arrays in Flyway V12.
insert into heatmap_cells (
    cell_id, grid_level, lat_index, lng_index, lat, lng, period,
    post_count, visit_count, user_count, top_place_id,
    sample_post_ids, sample_thumbnail_urls, last_posted_at, calculated_at
) values
    (801, 2, 3757, 12697, 37.575, 126.975, 'LAST_1H', 1, 1, 1, 201,
     array[1001]::bigint[], array['https://example.invalid/thumbs/1001-1.jpg']::text[],
     now() - interval '30 minutes', now()),
    (802, 2, 3757, 12697, 37.575, 126.975, 'LAST_24H', 2, 2, 2, 201,
     array[1001, 1002]::bigint[],
     array['https://example.invalid/thumbs/1001-1.jpg',
           'https://example.invalid/thumbs/1002-1.jpg']::text[],
     now() - interval '30 minutes', now()),
    (803, 2, 3755, 12692, 37.555, 126.925, 'WEEKLY', 1, 1, 1, 202,
     array[1003]::bigint[], array['https://example.invalid/thumbs/1003-1.jpg']::text[],
     now() - interval '23 hours', now()),
    (805, 0, 33, 126, 33.5, 126.5, 'MONTHLY', 1, 1, 1, 204,
     array[1004]::bigint[], array['https://example.invalid/thumbs/1004-1.jpg']::text[],
     now() - interval '9 days', now())
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

insert into region_stats (
    area_code, period, post_count, contributor_count,
    representative_post_id, calculated_at
) values
    (1, 'LAST_1H', 1, 1, 1001, now()),
    (1, 'LAST_24H', 3, 2, 1001, now()),
    (1, 'WEEKLY', 3, 2, 1001, now()),
    (39, 'MONTHLY', 1, 1, 1004, now())
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

-- Standalone report text maps to Flyway reason codes plus optional detail.
insert into reports (
    report_id, reporter_id, target_type, target_id, reason, detail,
    status, action, reviewed_at, created_at
) values
    (1201, '22222222-2222-4222-8222-222222222222', 'POST', 1005,
     'PLACE_MISMATCH', '장소와 관련 없는 사진입니다.', 'PENDING', null, null,
     now() - interval '2 hours'),
    (1202, '33333333-3333-4333-8333-333333333333', 'PLACE', 202,
     'OTHER', '장소 이름을 확인해주세요.', 'REVIEWED', 'KEEP',
     now() - interval '6 days', now() - interval '7 days')
on conflict do nothing;

-- Flyway sync_logs require a parent batch_runs row.
insert into batch_runs (
    run_id, job_type, status, processed_count, failed_count,
    started_at, finished_at, created_at
) values
    (1301, 'PLACE', 'SUCCESS', 2, 0, now() - interval '1 day 5 minutes',
     now() - interval '1 day', now() - interval '1 day 5 minutes'),
    (1302, 'EVENT', 'SUCCESS', 1, 1, now() - interval '6 hours 5 minutes',
     now() - interval '6 hours', now() - interval '6 hours 5 minutes'),
    (1303, 'HEATMAP', 'SUCCESS', 4, 0, now() - interval '5 minutes',
     now(), now() - interval '5 minutes')
on conflict do nothing;

insert into sync_logs (
    sync_id, run_id, job_type, area_code, content_type_id, result,
    count, message, started_at, finished_at
) values
    (1301, 1301, 'PLACE', 1, 12, 'SUCCESS', 2,
     '서울 관광지 동기화 완료', now() - interval '1 day 5 minutes', now() - interval '1 day'),
    (1302, 1302, 'EVENT', 6, 15, 'PARTIAL', 1,
     '일부 상세 정보 재시도 필요', now() - interval '6 hours 5 minutes', now() - interval '6 hours'),
    (1303, 1303, 'HEATMAP', null, null, 'SUCCESS', 4,
     '샘플 히트맵 집계 완료', now() - interval '5 minutes', now())
on conflict do nothing;

-- Notifications and search_logs are intentionally omitted: they do not exist in V1-V18.

-- Explicit IDs make the sample deterministic. Advance identity sequences afterward.
select setval(pg_get_serial_sequence('account_deletion_logs', 'log_id'),
              greatest((select max(log_id) from account_deletion_logs), 1), true);
select setval(pg_get_serial_sequence('places', 'place_id'),
              greatest((select max(place_id) from places), 1), true);
select setval(pg_get_serial_sequence('events', 'event_id'),
              greatest((select max(event_id) from events), 1), true);
select setval(pg_get_serial_sequence('posts', 'post_id'),
              greatest((select max(post_id) from posts), 1), true);
select setval(pg_get_serial_sequence('post_images', 'post_image_id'),
              greatest((select max(post_image_id) from post_images), 1), true);
select setval(pg_get_serial_sequence('comments', 'comment_id'),
              greatest((select max(comment_id) from comments), 1), true);
select setval(pg_get_serial_sequence('tags', 'tag_id'),
              greatest((select max(tag_id) from tags), 1), true);
select setval(pg_get_serial_sequence('badges', 'badge_id'),
              greatest((select max(badge_id) from badges), 1), true);
select setval(pg_get_serial_sequence('visits', 'visit_id'),
              greatest((select max(visit_id) from visits), 1), true);
select setval(pg_get_serial_sequence('heatmap_cells', 'cell_id'),
              greatest((select max(cell_id) from heatmap_cells), 1), true);
select setval(pg_get_serial_sequence('place_rankings', 'ranking_id'),
              greatest((select max(ranking_id) from place_rankings), 1), true);
select setval(pg_get_serial_sequence('reports', 'report_id'),
              greatest((select max(report_id) from reports), 1), true);
select setval(pg_get_serial_sequence('batch_runs', 'run_id'),
              greatest((select max(run_id) from batch_runs), 1), true);
select setval(pg_get_serial_sequence('sync_logs', 'sync_id'),
              greatest((select max(sync_id) from sync_logs), 1), true);

commit;
