-- =====================================================================
-- 마이그레이션 2026-08-22
--   기존 DB 를 지우지 않고 적용한다. 이미 적재한 places 데이터를 보존하기 위함.
--   신규 설치는 03_schema.sql 에 이미 반영되어 있으므로 이 파일을 실행하지 않아도 된다.
--
-- 적용:
--   Get-Content docs\06_migration_20260822.sql -Encoding UTF8 | docker exec -i snaphere-mysql mysql -uroot -psnaphere1234 --default-character-set=utf8mb4 tourlab
--
-- 왜 필요한가:
--   API 명세서의 GET /map/regions 응답에 labelLat/labelLng 가 있는데 regions 테이블에 컬럼이 없었다.
--   얇고 긴 지역(충청북도 등)의 지도 탭 타겟을 앱이 놓을 수 없어 실사용에서 문제가 된다.
-- =====================================================================
SET NAMES utf8mb4;

-- 1) regions 에 라벨 좌표 추가 (이미 있으면 오류가 나므로 존재 여부를 먼저 확인)
SET @exists := (SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'regions' AND column_name = 'label_lat');
SET @sql := IF(@exists = 0,
    'ALTER TABLE regions ADD COLUMN label_lat DECIMAL(10,7) NULL AFTER default_zoom,
                         ADD COLUMN label_lng DECIMAL(10,7) NULL AFTER label_lat',
    'SELECT ''regions.label_lat 이미 존재 — 건너뜀'' AS note');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2) 라벨 좌표 초기값. 대부분은 중심점과 같지만, 얇고 긴 지역은 개별 조정했다.
UPDATE regions SET label_lat = center_lat, label_lng = center_lng
WHERE label_lat IS NULL;

UPDATE regions SET label_lat = 36.8000000, label_lng = 127.8500000 WHERE area_code = 33; -- 충청북도: 청주 동쪽
UPDATE regions SET label_lat = 37.8500000, label_lng = 128.3000000 WHERE area_code = 32; -- 강원도: 내륙 중앙
UPDATE regions SET label_lat = 34.9000000, label_lng = 126.7000000 WHERE area_code = 38; -- 전라남도: 해안 제외 내륙

SELECT area_code, name_ko, center_lat, center_lng, label_lat, label_lng FROM regions ORDER BY sort_order;
