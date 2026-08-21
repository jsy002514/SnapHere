-- =====================================================================
-- 지역 이름 복구 (2026-08-22)
--
-- 문제: /admin/tour-sync/areas 가 regions.name_ko 를 TourAPI 값으로 덮어썼고,
--       그 과정에서 한글이 '??' 로 유실됐다. 자동 태그 추천과 지도 라벨이 깨진다.
--
-- 원인 제거: TourUpsertRepository.upsertRegionNames 를 verifyRegions 로 바꿔
--            이름을 덮어쓰지 않게 했다. 이 스크립트는 이미 깨진 데이터를 되돌린다.
--
-- 적용:
--   Get-Content docs\07_fix_region_names.sql -Encoding UTF8 | docker exec -i snaphere-mysql mysql -uroot -psnaphere1234 --default-character-set=utf8mb4 tourlab
--
-- ⚠️ places·posts 는 건드리지 않는다. 지역 이름만 되돌린다.
-- =====================================================================
SET NAMES utf8mb4;

UPDATE regions SET name_ko = '서울'       WHERE area_code = 1;
UPDATE regions SET name_ko = '인천'       WHERE area_code = 2;
UPDATE regions SET name_ko = '대전'       WHERE area_code = 3;
UPDATE regions SET name_ko = '대구'       WHERE area_code = 4;
UPDATE regions SET name_ko = '광주'       WHERE area_code = 5;
UPDATE regions SET name_ko = '부산'       WHERE area_code = 6;
UPDATE regions SET name_ko = '울산'       WHERE area_code = 7;
UPDATE regions SET name_ko = '세종'       WHERE area_code = 8;
UPDATE regions SET name_ko = '경기도'     WHERE area_code = 31;
UPDATE regions SET name_ko = '강원도'     WHERE area_code = 32;
UPDATE regions SET name_ko = '충청북도'   WHERE area_code = 33;
UPDATE regions SET name_ko = '충청남도'   WHERE area_code = 34;
UPDATE regions SET name_ko = '경상북도'   WHERE area_code = 35;
UPDATE regions SET name_ko = '경상남도'   WHERE area_code = 36;
UPDATE regions SET name_ko = '전라북도'   WHERE area_code = 37;
UPDATE regions SET name_ko = '전라남도'   WHERE area_code = 38;
UPDATE regions SET name_ko = '제주도'     WHERE area_code = 39;

-- 시군구 이름도 같은 경로로 들어왔으므로 깨진 것이 있는지 확인한다.
SELECT '--- 시군구 중 물음표가 섞인 행 (있으면 재적재 필요) ---' AS check_point;
SELECT area_code, sigungu_code, name_ko FROM sigungu WHERE name_ko LIKE '%?%' LIMIT 20;

SELECT '--- 복구 결과 ---' AS check_point;
SELECT area_code, name_ko, name_en, HEX(name_ko) AS hex_check FROM regions ORDER BY sort_order;
