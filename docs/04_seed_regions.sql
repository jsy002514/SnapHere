-- 17개 시도 기준 데이터. area_code 는 TourAPI 의 areaCode 를 그대로 쓴다 (1~8, 31~39, 연속 아님).
-- 적용:
--   Get-Content docs\04_seed_regions.sql -Encoding UTF8 | docker exec -i snaphere-mysql mysql -uroot -psnaphere1234 --default-character-set=utf8mb4 tourlab
SET NAMES utf8mb4;

INSERT INTO regions
    (area_code, name_ko, name_en, name_ja, name_zh, center_lat, center_lng, default_zoom, sort_order)
VALUES
    ( 1, '서울',       'Seoul',              'ソウル',     '首尔',     37.5665000, 126.9780000, 11,  1),
    ( 6, '부산',       'Busan',              '釜山',       '釜山',     35.1796000, 129.0756000, 11,  2),
    ( 4, '대구',       'Daegu',              '大邱',       '大邱',     35.8714000, 128.6014000, 11,  3),
    ( 2, '인천',       'Incheon',            '仁川',       '仁川',     37.4563000, 126.7052000, 11,  4),
    ( 5, '광주',       'Gwangju',            '光州',       '光州',     35.1595000, 126.8526000, 11,  5),
    ( 3, '대전',       'Daejeon',            '大田',       '大田',     36.3504000, 127.3845000, 11,  6),
    ( 7, '울산',       'Ulsan',              '蔚山',       '蔚山',     35.5384000, 129.3114000, 11,  7),
    ( 8, '세종',       'Sejong',             '世宗',       '世宗',     36.4800000, 127.2890000, 11,  8),
    (31, '경기도',     'Gyeonggi-do',        '京畿道',     '京畿道',   37.4138000, 127.5183000,  9,  9),
    (32, '강원도',     'Gangwon-do',         '江原道',     '江原道',   37.8228000, 128.1555000,  9, 10),
    (33, '충청북도',   'Chungcheongbuk-do',  '忠清北道',   '忠清北道', 36.8000000, 127.7000000,  9, 11),
    (34, '충청남도',   'Chungcheongnam-do',  '忠清南道',   '忠清南道', 36.5184000, 126.8000000,  9, 12),
    (37, '전라북도',   'Jeollabuk-do',       '全羅北道',   '全罗北道', 35.7175000, 127.1530000,  9, 13),
    (38, '전라남도',   'Jeollanam-do',       '全羅南道',   '全罗南道', 34.8679000, 126.9910000,  9, 14),
    (35, '경상북도',   'Gyeongsangbuk-do',   '慶尚北道',   '庆尚北道', 36.4919000, 128.8889000,  9, 15),
    (36, '경상남도',   'Gyeongsangnam-do',   '慶尚南道',   '庆尚南道', 35.4606000, 128.2132000,  9, 16),
    (39, '제주도',     'Jeju-do',            '済州道',     '济州道',   33.4890000, 126.4983000, 10, 17)
ON DUPLICATE KEY UPDATE
    name_ko = VALUES(name_ko), name_en = VALUES(name_en),
    name_ja = VALUES(name_ja), name_zh = VALUES(name_zh),
    center_lat = VALUES(center_lat), center_lng = VALUES(center_lng),
    default_zoom = VALUES(default_zoom), sort_order = VALUES(sort_order);

-- 지도 탭 타겟용 라벨 좌표. 기본은 중심점과 동일하고, 얇고 긴 지역만 따로 옮긴다.
UPDATE regions SET label_lat = center_lat, label_lng = center_lng WHERE label_lat IS NULL;
UPDATE regions SET label_lat = 36.8000000, label_lng = 127.8500000 WHERE area_code = 33; -- 충청북도
UPDATE regions SET label_lat = 37.8500000, label_lng = 128.3000000 WHERE area_code = 32; -- 강원도
UPDATE regions SET label_lat = 34.9000000, label_lng = 126.7000000 WHERE area_code = 38; -- 전라남도
