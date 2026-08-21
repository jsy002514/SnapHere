-- =====================================================================
-- 관광데이터랩 공모전 — 백엔드 스키마 v2 (MySQL 8.0)
-- 작성일: 2026-08-20
--
-- v1 대비 주요 변경
--  1) 익명(ANONYMOUS) 계정 제거 → 구글 로그인 필수
--  2) photos + posts 통합 → posts 하나 + post_images (0~N장)
--  3) attractions → places 로 통합. 관광지(OFFICIAL) + 사용자 장소(USER) 를 한 테이블로
--  4) 팔로우 / 알림(FCM) / 방문 기록 / 북마크 / 댓글 / 해시태그 추가
--  5) 히트맵 집계 테이블 추가 (메인 랜딩 지도)
--  6) 프론트 화면 변경 반영: 실시간(1시간) 히트맵 레이어, 시도별 트래픽 집계,
--     기간별 인기 게시물 조회 인덱스
--  7) 와이어프레임 반영 (nav 5탭: 홈·커뮤니티·업로드·이벤트·마이)
--     · 일반 로그인(아이디/비밀번호) 추가 — auth_type, login_id, password_hash
--     · 영상 업로드 — post_images 를 post_media 로 확장
--     · 이벤트(지자체 행사) — places 에 행사 기간·주최 컬럼
--     · 자동 태그 추천 — tags.tag_type, post_tags.source
--     · 인기 지수/등급/개인 SNS — users.popularity_score, grade, sns_links
--     · masonry 레이아웃 — posts.thumbnail_ratio
--
-- ⚠️ 타입 규약 (2026-08-21 확정 — 어기면 Hibernate ddl-auto=validate 가 기동을 막는다)
--   · 상태·구분값은 MySQL ENUM 을 쓰지 않는다. **VARCHAR(30)** + 허용값을 /* A|B|C */ 주석으로 남긴다.
--     이유: ENUM 은 JDBC 가 CHAR 로 보고하는데 JPA @Enumerated(STRING) 은 VARCHAR 를 기대해서 검증이 실패한다.
--     값 추가 때마다 ALTER TABLE 이 필요한 문제도 사라진다. 유효성은 Java enum 이 보장한다.
--   · 해시 컬럼은 CHAR(64) 가 아니라 **VARCHAR(64)**.
--   · boolean 은 TINYINT(1) (Connector/J 가 BIT 으로 보고 → Hibernate boolean 과 일치).
--     그 외 작은 정수도 TINYINT 를 쓰지 말고 **INT**.
--   · 엔티티 쪽 대응: enum 필드는 반드시 @Column(length = 30) 을 명시한다 (기본값 255 라서 불일치).
-- ⚠️ 좌표 축 순서 (MySQL 8 최대 함정 — 8.0.46 에서 직접 검증)
--    · POINT() 함수 : POINT(경도, 위도)   → ST_SRID(POINT(126.9770, 37.5796), 4326)
--    · WKT  문자열  : 'POINT(위도 경도)'  → ST_GeomFromText('POINT(37.5796 126.9770)', 4326)
--    · ST_X() = 위도, ST_Y() = 경도
--    두 표기가 서로 반대다. 코드에서는 POINT(lng, lat) 한 가지로만 통일한다.
--    TourAPI 는 mapx = 경도, mapy = 위도 (이름이 헷갈리게 지어져 있음).
-- =====================================================================

CREATE DATABASE IF NOT EXISTS tourlab
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE tourlab;
SET NAMES utf8mb4;
SET time_zone = '+09:00';


-- =====================================================================
-- 1. 사용자 / 인증
-- =====================================================================

CREATE TABLE users (
    user_id              BIGINT       NOT NULL AUTO_INCREMENT,
    -- 인증 방식. LOCAL(아이디/비밀번호) 과 GOOGLE 두 가지를 지원한다.
    auth_type            VARCHAR(30) /* LOCAL|GOOGLE */ NOT NULL DEFAULT 'LOCAL',
    login_id             VARCHAR(30)  NULL COMMENT 'LOCAL 로그인 아이디. GOOGLE 계정은 NULL',
    password_hash        VARCHAR(100) NULL COMMENT 'BCrypt 해시. 평문·SHA 계열 저장 절대 금지',
    provider             VARCHAR(30) /* GOOGLE */ NULL COMMENT 'GOOGLE 계정만. LOCAL 은 NULL',
    provider_user_id     VARCHAR(191) NULL COMMENT 'Google ID Token 의 sub. 탈퇴 시 NULL 처리',
    email                VARCHAR(255) NULL COMMENT 'LOCAL 가입은 선택. 없으면 비밀번호 찾기 불가',
    nickname             VARCHAR(30)  NOT NULL,
    profile_image_url    VARCHAR(500) NULL,
    bio                  VARCHAR(150) NULL COMMENT '프로필 소개글',
    locale               VARCHAR(10)  NOT NULL DEFAULT 'ko',
    role                 VARCHAR(30) /* USER|ADMIN */ NOT NULL DEFAULT 'USER',
    status               VARCHAR(30) /* ACTIVE|SUSPENDED|WITHDRAWN */ NOT NULL DEFAULT 'ACTIVE',

    terms_agreed_at      DATETIME(6)  NULL,
    upload_blocked_until DATETIME(6)  NULL COMMENT '신고 누적에 의한 업로드 정지 만료시각',

    -- 알림 설정 (F-08). 개별 스위치를 users 에 두면 알림 발송 시 조인 1회로 끝난다.
    push_like_enabled    TINYINT(1)   NOT NULL DEFAULT 1,
    push_comment_enabled TINYINT(1)   NOT NULL DEFAULT 1,
    push_follow_enabled  TINYINT(1)   NOT NULL DEFAULT 1,
    push_post_enabled    TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '내가 팔로우한 사람의 새 게시글',

    -- 비정규화 카운터 (매일 새벽 보정 배치 필수)
    follower_count       INT          NOT NULL DEFAULT 0,
    following_count      INT          NOT NULL DEFAULT 0,
    post_count           INT          NOT NULL DEFAULT 0,
    visit_count          INT          NOT NULL DEFAULT 0 COMMENT '방문한 고유 장소 수',

    -- 인기 지수 / 등급 (프로필 화면)
    popularity_score     INT          NOT NULL DEFAULT 0 COMMENT '활동량 기반 누적 점수. 배치로 갱신',
    grade                VARCHAR(30) /* SEED|SPROUT|TREE|FOREST|LEGEND */ NOT NULL DEFAULT 'SEED'
                         COMMENT '점수 구간으로 산출. 조회 편의를 위해 저장한다',
    -- 개인 SNS 링크. 플랫폼이 늘어날 수 있어 JSON 으로 둔다.
    -- 예: {"instagram":"https://...","youtube":"https://...","blog":"https://..."}
    sns_links            JSON         NULL,

    -- 계정 삭제 (F-01-05)
    withdraw_reason      VARCHAR(50)  NULL,
    withdrawn_at         DATETIME(6)  NULL,
    purge_scheduled_at   DATETIME(6)  NULL COMMENT '완전 파기 예정 시각 (탈퇴 + 30일)',
    restore_key          VARCHAR(64)     NULL COMMENT 'sha256(provider + sub). 탈퇴 후 복구 매칭용. 원본 sub 는 즉시 파기',

    created_at           DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at           DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (user_id),
    UNIQUE KEY uk_users_login_id (login_id),
    UNIQUE KEY uk_users_provider (provider, provider_user_id),
    KEY idx_users_status (status),
    KEY idx_users_popularity (popularity_score DESC),
    KEY idx_users_purge (status, purge_scheduled_at),
    KEY idx_users_restore (restore_key),
    KEY idx_users_nickname (nickname)
) ENGINE=InnoDB COMMENT='회원 (구글 로그인)';


-- FCM 푸시 토큰. 한 사용자가 여러 기기를 쓸 수 있으므로 별도 테이블.
CREATE TABLE user_devices (
    device_row_id  BIGINT       NOT NULL AUTO_INCREMENT,
    user_id        BIGINT       NOT NULL,
    device_id      VARCHAR(100) NOT NULL COMMENT '앱이 생성한 기기 UUID',
    fcm_token      VARCHAR(255) NULL,
    platform       VARCHAR(30) /* IOS|ANDROID|WEB */ NOT NULL,
    app_version    VARCHAR(20)  NULL,
    locale         VARCHAR(10)  NULL,
    push_enabled   TINYINT(1)   NOT NULL DEFAULT 1 COMMENT 'OS 레벨 알림 권한 허용 여부',
    last_active_at DATETIME(6)  NULL,
    created_at     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (device_row_id),
    UNIQUE KEY uk_user_devices (user_id, device_id),
    KEY idx_user_devices_token (fcm_token),
    CONSTRAINT fk_user_devices_user FOREIGN KEY (user_id) REFERENCES users(user_id)
) ENGINE=InnoDB COMMENT='FCM 토큰 · 기기 정보';


CREATE TABLE refresh_tokens (
    token_id     BIGINT       NOT NULL AUTO_INCREMENT,
    user_id      BIGINT       NOT NULL,
    token_hash   VARCHAR(64)     NOT NULL COMMENT 'SHA-256. 원문은 저장하지 않는다',
    device_id    VARCHAR(100) NULL,
    expires_at   DATETIME(6)  NOT NULL,
    revoked_at   DATETIME(6)  NULL,
    replaced_by  VARCHAR(64)     NULL COMMENT '로테이션으로 대체된 다음 토큰 해시',
    created_at   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (token_id),
    UNIQUE KEY uk_refresh_token_hash (token_hash),
    KEY idx_refresh_user (user_id, expires_at),
    CONSTRAINT fk_refresh_user FOREIGN KEY (user_id) REFERENCES users(user_id)
) ENGINE=InnoDB;


CREATE TABLE account_deletion_logs (
    log_id            BIGINT       NOT NULL AUTO_INCREMENT,
    user_id           BIGINT       NOT NULL COMMENT 'FK 를 걸지 않는다 — users 행이 파기돼도 로그는 남아야 함',
    withdraw_reason   VARCHAR(50)  NULL,
    content_action    VARCHAR(30) /* KEEP_ANONYMIZED|DELETE_ALL */ NOT NULL,
    deleted_posts     INT          NOT NULL DEFAULT 0,
    deleted_comments  INT          NOT NULL DEFAULT 0,
    deleted_images    INT          NOT NULL DEFAULT 0,
    requested_at      DATETIME(6)  NOT NULL,
    purged_at         DATETIME(6)  NULL,
    PRIMARY KEY (log_id),
    KEY idx_deletion_logs_user (user_id),
    KEY idx_deletion_logs_requested (requested_at)
) ENGINE=InnoDB COMMENT='계정 삭제 감사 로그 (개인 식별 정보 저장 금지)';


-- =====================================================================
-- 2. 팔로우
-- =====================================================================

CREATE TABLE follows (
    follower_id  BIGINT      NOT NULL COMMENT '팔로우를 건 사람',
    following_id BIGINT      NOT NULL COMMENT '팔로우 당한 사람',
    created_at   DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (follower_id, following_id),
    -- 팔로워 목록(나를 팔로우한 사람) 조회용. PK 만으로는 이 방향을 못 탄다.
    KEY idx_follows_following (following_id, created_at DESC),
    CONSTRAINT fk_follows_follower  FOREIGN KEY (follower_id)  REFERENCES users(user_id),
    CONSTRAINT fk_follows_following FOREIGN KEY (following_id) REFERENCES users(user_id)
) ENGINE=InnoDB COMMENT='팔로우 (자기 자신 팔로우는 애플리케이션에서 차단)';


-- =====================================================================
-- 3. 지역 마스터
-- =====================================================================

-- ⚠️ area_code 는 TourAPI 코드를 그대로 PK 로 쓴다. 1~8, 31~39 로 연속되지 않는다.
CREATE TABLE regions (
    area_code     INT           NOT NULL,
    name_ko       VARCHAR(50)   NOT NULL,
    name_en       VARCHAR(50)   NOT NULL,
    name_ja       VARCHAR(50)   NULL,
    name_zh       VARCHAR(50)   NULL,
    center_lat    DECIMAL(10,7) NOT NULL,
    center_lng    DECIMAL(10,7) NOT NULL,
    default_zoom  INT       NOT NULL DEFAULT 11,
    -- 터치 타겟용 좌표. 충청북도처럼 얇고 긴 지역은 영역 탭이 어려워서 앱이 이 지점에 핀을 놓는다.
    -- 중심점(center_lat/lng)과 다를 수 있다. 값이 없으면 중심점을 그대로 쓴다.
    label_lat     DECIMAL(10,7) NULL,
    label_lng     DECIMAL(10,7) NULL,
    thumbnail_url VARCHAR(500)  NULL,
    sort_order    INT           NOT NULL DEFAULT 0,
    PRIMARY KEY (area_code)
) ENGINE=InnoDB COMMENT='17개 시도';


CREATE TABLE sigungu (
    area_code    INT         NOT NULL,
    sigungu_code INT         NOT NULL,
    name_ko      VARCHAR(50) NOT NULL,
    name_en      VARCHAR(50) NULL,
    PRIMARY KEY (area_code, sigungu_code),
    CONSTRAINT fk_sigungu_region FOREIGN KEY (area_code) REFERENCES regions(area_code)
) ENGINE=InnoDB;


CREATE TABLE region_stats (
    area_code         INT         NOT NULL,
    place_count       INT         NOT NULL DEFAULT 0,
    user_place_count  INT         NOT NULL DEFAULT 0 COMMENT '사용자가 만든 숨은 명소 수',
    post_count        INT         NOT NULL DEFAULT 0,
    image_count       INT         NOT NULL DEFAULT 0,
    contributor_count INT         NOT NULL DEFAULT 0,
    -- 메인 지도의 시도별 농도 표시용 (축소 상태에서는 격자 대신 시도 단위로 칠한다)
    recent_post_1h    INT         NOT NULL DEFAULT 0 COMMENT '최근 1시간 게시물 수',
    recent_post_24h   INT         NOT NULL DEFAULT 0 COMMENT '최근 24시간 게시물 수',
    recent_user_1h    INT         NOT NULL DEFAULT 0 COMMENT '최근 1시간 고유 기여자 수',
    traffic_intensity DECIMAL(5,4) NOT NULL DEFAULT 0 COMMENT '0.0~1.0 정규화. 지도 색 농도로 그대로 사용',
    last_post_at      DATETIME(6) NULL COMMENT '이 지역의 마지막 게시 시각',
    updated_at        DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (area_code),
    CONSTRAINT fk_region_stats_region FOREIGN KEY (area_code) REFERENCES regions(area_code)
) ENGINE=InnoDB;


-- =====================================================================
-- 4. 장소 (관광지 + 비관광지 통합)  ★ v2 핵심 변경
-- =====================================================================
--
-- place_type = 'OFFICIAL' : TourAPI 로 적재한 공식 관광지
-- place_type = 'USER'     : 사용자가 GPS 로 새로 만든 장소 (카페 · 골목 · 뷰포인트 등)
--
-- 왜 한 테이블인가
--   · 주변 검색 · 지도 · 히트맵 · 피드가 두 종류를 항상 함께 다룬다. 나누면 UNION 지옥이 된다.
--   · "숨은 명소" 랭킹을 place_type 필터 하나로 만들 수 있다 (공모전 차별점)
CREATE TABLE places (
    place_id           BIGINT        NOT NULL AUTO_INCREMENT,
    place_type         VARCHAR(30) /* OFFICIAL|USER */ NOT NULL DEFAULT 'OFFICIAL',

    -- OFFICIAL 전용 (TourAPI)
    content_id         BIGINT        NULL COMMENT 'TourAPI contentid. USER 장소는 NULL',
    content_type_id    INT           NULL COMMENT '12관광지 14문화시설 15축제 25코스 28레포츠 32숙박 38쇼핑 39음식점',
    cat1               VARCHAR(10)   NULL,
    cat2               VARCHAR(10)   NULL,
    cat3               VARCHAR(10)   NULL,
    tour_modified_at   DATETIME(6)   NULL COMMENT 'TourAPI modifiedtime — UPSERT 판단 기준',
    -- 축제·행사(content_type_id = 15) 전용. 이벤트 탭과 자동 태그 추천이 이 기간을 사용한다.
    event_start_date   DATE          NULL,
    event_end_date     DATE          NULL,
    event_place        VARCHAR(255)  NULL COMMENT '행사 장소명',
    organizer          VARCHAR(255)  NULL COMMENT '주최·주관 (지자체)',
    overview           MEDIUMTEXT    NULL COMMENT 'detailCommon 으로 지연 적재',
    overview_synced_at DATETIME(6)   NULL,
    homepage           TEXT          NULL,
    tel                VARCHAR(100)  NULL,
    zipcode            VARCHAR(20)   NULL,

    -- USER 전용
    created_by_user_id BIGINT        NULL,
    merged_into_place_id BIGINT      NULL COMMENT '중복 장소를 운영자가 병합했을 때 대상 place_id',

    -- 공통
    title              VARCHAR(255)  NOT NULL,
    addr1              VARCHAR(255)  NULL,
    addr2              VARCHAR(255)  NULL,
    area_code          INT           NOT NULL,
    sigungu_code       INT           NULL,
    lat                DECIMAL(10,7) NULL,
    lng                DECIMAL(10,7) NULL,
    -- SPATIAL INDEX 는 NOT NULL 컬럼에만 걸린다. 좌표 없는 TourAPI 데이터는 POINT(0,0) + has_coordinate=0
    geom               POINT         NOT NULL SRID 4326,
    has_coordinate     TINYINT(1)    NOT NULL DEFAULT 1,
    verify_radius_m    INT           NOT NULL DEFAULT 500 COMMENT '현장 인증 인정 반경. 넓은 명소는 개별 조정',

    first_image_url    VARCHAR(500)  NULL,
    first_image_thumb  VARCHAR(500)  NULL,

    post_count         INT           NOT NULL DEFAULT 0,
    image_count        INT           NOT NULL DEFAULT 0,
    like_count         INT           NOT NULL DEFAULT 0,
    visit_count        INT           NOT NULL DEFAULT 0,
    view_count         INT           NOT NULL DEFAULT 0,
    bookmark_count     INT           NOT NULL DEFAULT 0,
    is_featured        TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '데이터 부족 시 추천 fallback 대상',

    status             VARCHAR(30) /* ACTIVE|HIDDEN|MERGED */ NOT NULL DEFAULT 'ACTIVE',
    created_at         DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at         DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (place_id),
    -- content_id 가 NULL 인 행은 UNIQUE 제약을 받지 않는다 → USER 장소를 얼마든지 넣을 수 있다
    UNIQUE KEY uk_places_content_id (content_id),
    KEY idx_places_area_type (area_code, place_type, status),
    KEY idx_places_content_type (area_code, content_type_id, status),
    KEY idx_places_title (title(50)),
    KEY idx_places_popular (area_code, post_count DESC),
    KEY idx_places_creator (created_by_user_id),
    -- 이벤트 탭: 진행중·예정 행사 조회
    KEY idx_places_event (content_type_id, event_end_date, event_start_date),
    KEY idx_places_event_area (area_code, content_type_id, event_start_date),
    SPATIAL KEY spx_places_geom (geom),
    FULLTEXT KEY ft_places_title (title, addr1) WITH PARSER ngram,
    CONSTRAINT fk_places_region  FOREIGN KEY (area_code) REFERENCES regions(area_code),
    CONSTRAINT fk_places_creator FOREIGN KEY (created_by_user_id) REFERENCES users(user_id)
) ENGINE=InnoDB COMMENT='관광지(OFFICIAL) + 사용자 장소(USER) 통합';


-- TourAPI detailImage 로 적재한 공식 이미지. 사용자 사진(post_images)과 저작권이 다르므로 분리한다.
CREATE TABLE place_images (
    image_id      BIGINT       NOT NULL AUTO_INCREMENT,
    place_id      BIGINT       NOT NULL,
    image_url     VARCHAR(500) NOT NULL,
    thumbnail_url VARCHAR(500) NULL,
    source        VARCHAR(30) /* TOUR_API|ADMIN */ NOT NULL DEFAULT 'TOUR_API',
    sort_order    INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (image_id),
    KEY idx_place_images (place_id, sort_order),
    CONSTRAINT fk_place_images FOREIGN KEY (place_id) REFERENCES places(place_id)
) ENGINE=InnoDB COMMENT='TourAPI 공식 이미지';


-- =====================================================================
-- 5. 게시물 (사진 + 글 통합)  ★ v2 핵심 변경
-- =====================================================================
--
-- v1 의 photos 와 posts 를 하나로 합쳤다.
--   · 이미지 0장  → 순수 텍스트 글 (질문 · 자유글)
--   · 이미지 1~N장 → 사진 게시물. GPS·Tier 판정 대상
--   · place_id NULL → 특정 장소에 묶이지 않은 지역 글
CREATE TABLE posts (
    post_id       BIGINT       NOT NULL AUTO_INCREMENT,
    user_id       BIGINT       NOT NULL,
    place_id      BIGINT       NULL COMMENT 'NULL 이면 장소에 묶이지 않은 글',
    area_code     INT          NOT NULL COMMENT '지역 커뮤니티 분류. 좌표가 있으면 자동, 없으면 사용자 선택',

    category      VARCHAR(30) /* PHOTO|REVIEW|QUESTION|COURSE|FREE */ NOT NULL DEFAULT 'PHOTO',
    title         VARCHAR(100) NULL COMMENT '사진 위주 게시물은 제목 없이도 등록 가능',
    content       TEXT         NULL COMMENT 'title 과 content 중 최소 하나는 있어야 한다 (앱에서 검증)',

    media_count   INT          NOT NULL DEFAULT 0 COMMENT '이미지 + 영상 개수',
    video_count   INT          NOT NULL DEFAULT 0,
    thumbnail_url VARCHAR(500) NULL COMMENT '대표 미디어 썸네일 캐시. 목록 조회에서 조인을 없앤다',
    -- 지역 게시판이 masonry(가변 높이) 레이아웃이라 목록 단계에서 대표 이미지 비율이 필요하다.
    -- 이게 없으면 앱이 이미지를 다 받은 뒤에야 높이를 알아 레이아웃이 튄다.
    thumbnail_ratio DECIMAL(5,3) NULL COMMENT '대표 미디어의 가로/세로 비율 (1.000 = 정사각, 1.778 = 16:9)',

    -- 위치 · 신뢰도 (이미지가 있는 게시물에 대해서만 의미가 있다)
    has_location  TINYINT(1)   NOT NULL DEFAULT 0,
    lat           DECIMAL(10,7) NULL,
    lng           DECIMAL(10,7) NULL,
    geom          POINT        NOT NULL SRID 4326 COMMENT '위치 없으면 POINT(0,0)',
    distance_m    INT          NULL COMMENT 'place 중심과 촬영지점 거리(m)',
    source        VARCHAR(30) /* CAMERA|GALLERY|NONE */ NOT NULL DEFAULT 'NONE',
    tier          VARCHAR(30) /* ON_SITE|LOCATION_CONFIRMED|NO_LOCATION */ NOT NULL DEFAULT 'NO_LOCATION',
    taken_at      DATETIME(6)  NULL COMMENT 'EXIF 촬영시각',

    view_count    INT          NOT NULL DEFAULT 0,
    like_count    INT          NOT NULL DEFAULT 0,
    comment_count INT          NOT NULL DEFAULT 0,
    bookmark_count INT         NOT NULL DEFAULT 0,
    report_count  INT          NOT NULL DEFAULT 0,
    -- 기간별 인기순 정렬용 사전 계산 점수 (24시간/일주일/한달/전체 탭)
    popularity_score DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '좋아요·댓글·조회를 시간 감쇠와 함께 합산. 배치 갱신',

    status        VARCHAR(30) /* ACTIVE|BLINDED|DELETED */ NOT NULL DEFAULT 'ACTIVE',
    created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (post_id),
    KEY idx_posts_area_created   (area_code, status, created_at DESC),
    KEY idx_posts_area_category  (area_code, category, status, created_at DESC),
    KEY idx_posts_area_popular   (area_code, status, popularity_score DESC, post_id DESC),
    -- '이번주 인기 사진' — 기간으로 좁힌 뒤 정렬한다. 사진 있는 글만 대상이라 image_count 포함
    KEY idx_posts_area_period    (area_code, status, media_count, created_at DESC),
    KEY idx_posts_place          (place_id, status, created_at DESC),
    KEY idx_posts_user           (user_id, status, created_at DESC),
    KEY idx_posts_ranking        (place_id, tier, status, created_at),
    KEY idx_posts_feed           (status, created_at DESC) COMMENT '팔로잉 피드 조인용',
    SPATIAL KEY spx_posts_geom   (geom),
    FULLTEXT KEY ft_posts_text   (title, content) WITH PARSER ngram,
    CONSTRAINT fk_posts_user   FOREIGN KEY (user_id)   REFERENCES users(user_id),
    CONSTRAINT fk_posts_place  FOREIGN KEY (place_id)  REFERENCES places(place_id),
    CONSTRAINT fk_posts_region FOREIGN KEY (area_code) REFERENCES regions(area_code)
) ENGINE=InnoDB COMMENT='게시물 (사진 + 글 통합)';


-- 이미지와 영상을 한 테이블로 관리한다. 정렬·개수·삭제 로직이 완전히 같기 때문.
CREATE TABLE post_media (
    media_id      BIGINT       NOT NULL AUTO_INCREMENT,
    post_id       BIGINT       NOT NULL,
    media_type    VARCHAR(30) /* IMAGE|VIDEO */ NOT NULL DEFAULT 'IMAGE',
    media_key     VARCHAR(500) NOT NULL COMMENT 'S3 object key',
    media_url     VARCHAR(500) NOT NULL,
    thumbnail_url VARCHAR(500) NULL COMMENT '영상은 첫 프레임 추출 이미지',
    media_hash    VARCHAR(64)     NULL COMMENT 'SHA-256. 중복 업로드 차단',
    width         INT          NULL,
    height        INT          NULL,
    duration_sec  INT          NULL COMMENT '영상 길이(초). 이미지는 NULL',
    file_size     BIGINT       NULL,
    -- 영상은 업로드 직후 바로 재생 가능한 상태가 아니다(썸네일 추출·검증 필요).
    -- READY 가 아닌 미디어는 목록·상세에서 제외하거나 처리중으로 표시한다.
    process_status VARCHAR(30) /* UPLOADING|PROCESSING|READY|FAILED */ NOT NULL DEFAULT 'READY',
    sort_order    INT          NOT NULL DEFAULT 0,
    created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (media_id),
    KEY idx_post_media (post_id, sort_order),
    KEY idx_post_media_hash (media_hash),
    KEY idx_post_media_status (process_status, created_at),
    CONSTRAINT fk_post_media FOREIGN KEY (post_id) REFERENCES posts(post_id)
) ENGINE=InnoDB COMMENT='게시물 미디어 0~N개 (이미지 + 영상)';


CREATE TABLE post_likes (
    post_id    BIGINT      NOT NULL,
    user_id    BIGINT      NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (post_id, user_id),
    KEY idx_post_likes_user (user_id, created_at DESC) COMMENT '"내가 좋아요한 글" 조회',
    CONSTRAINT fk_post_likes_post FOREIGN KEY (post_id) REFERENCES posts(post_id),
    CONSTRAINT fk_post_likes_user FOREIGN KEY (user_id) REFERENCES users(user_id)
) ENGINE=InnoDB;


CREATE TABLE comments (
    comment_id        BIGINT        NOT NULL AUTO_INCREMENT,
    post_id           BIGINT        NOT NULL,
    user_id           BIGINT        NOT NULL,
    parent_comment_id BIGINT        NULL COMMENT '대댓글. 1단계까지만 허용',
    content           VARCHAR(1000) NOT NULL,
    like_count        INT           NOT NULL DEFAULT 0,
    report_count      INT           NOT NULL DEFAULT 0,
    status            VARCHAR(30) /* ACTIVE|BLINDED|DELETED */ NOT NULL DEFAULT 'ACTIVE',
    created_at        DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at        DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (comment_id),
    KEY idx_comments_post (post_id, status, created_at),
    KEY idx_comments_parent (parent_comment_id),
    KEY idx_comments_user (user_id, created_at DESC),
    CONSTRAINT fk_comments_post   FOREIGN KEY (post_id) REFERENCES posts(post_id),
    CONSTRAINT fk_comments_user   FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT fk_comments_parent FOREIGN KEY (parent_comment_id) REFERENCES comments(comment_id)
) ENGINE=InnoDB;


CREATE TABLE comment_likes (
    comment_id BIGINT      NOT NULL,
    user_id    BIGINT      NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (comment_id, user_id),
    CONSTRAINT fk_comment_likes_comment FOREIGN KEY (comment_id) REFERENCES comments(comment_id),
    CONSTRAINT fk_comment_likes_user    FOREIGN KEY (user_id)    REFERENCES users(user_id)
) ENGINE=InnoDB;


-- 북마크(저장). 게시물과 장소 둘 다 저장할 수 있다.
CREATE TABLE bookmarks (
    user_id     BIGINT      NOT NULL,
    target_type VARCHAR(30) /* POST|PLACE */ NOT NULL,
    target_id   BIGINT      NOT NULL,
    created_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (user_id, target_type, target_id),
    KEY idx_bookmarks_target (target_type, target_id),
    KEY idx_bookmarks_recent (user_id, created_at DESC),
    CONSTRAINT fk_bookmarks_user FOREIGN KEY (user_id) REFERENCES users(user_id)
) ENGINE=InnoDB COMMENT='폴리모픽. FK 를 걸 수 없으므로 애플리케이션에서 존재 검증';


-- =====================================================================
-- 6. 해시태그
-- =====================================================================

CREATE TABLE tags (
    tag_id      BIGINT      NOT NULL AUTO_INCREMENT,
    name        VARCHAR(50) NOT NULL COMMENT '# 제외한 순수 문자열. 소문자 정규화',
    tag_type    VARCHAR(30) /* FREE|REGION|CATEGORY|EVENT */ NOT NULL DEFAULT 'FREE'
                COMMENT '지역·카테고리·행사 태그는 자동 추천 대상',
    ref_id      BIGINT      NULL COMMENT 'REGION=area_code, EVENT=place_id 등 원본 참조',
    usage_count INT         NOT NULL DEFAULT 0,
    created_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (tag_id),
    UNIQUE KEY uk_tags_name (name),
    KEY idx_tags_popular (usage_count DESC)
) ENGINE=InnoDB;


CREATE TABLE post_tags (
    post_id BIGINT NOT NULL,
    tag_id  BIGINT NOT NULL,
    -- 자동 추천 태그와 사용자가 직접 넣은 태그를 구분한다.
    -- 추천 태그의 채택률을 측정해야 추천 로직을 개선할 수 있다.
    source  VARCHAR(30) /* USER|AUTO_REGION|AUTO_CATEGORY|AUTO_EVENT */ NOT NULL DEFAULT 'USER',
    PRIMARY KEY (post_id, tag_id),
    KEY idx_post_tags_tag (tag_id, post_id DESC) COMMENT '태그로 게시물 검색',
    CONSTRAINT fk_post_tags_post FOREIGN KEY (post_id) REFERENCES posts(post_id),
    CONSTRAINT fk_post_tags_tag  FOREIGN KEY (tag_id)  REFERENCES tags(tag_id)
) ENGINE=InnoDB;


-- 지역 게시판 '태그별' 탭에서 쓰는 집계. 매 요청 GROUP BY 하면 무거워서 배치로 미리 만든다.
CREATE TABLE region_tag_stats (
    area_code   INT         NOT NULL,
    tag_id      BIGINT      NOT NULL,
    post_count  INT         NOT NULL DEFAULT 0,
    rank_no     INT         NOT NULL DEFAULT 0,
    updated_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (area_code, tag_id),
    KEY idx_region_tag_rank (area_code, rank_no),
    CONSTRAINT fk_region_tag_region FOREIGN KEY (area_code) REFERENCES regions(area_code),
    CONSTRAINT fk_region_tag_tag    FOREIGN KEY (tag_id)    REFERENCES tags(tag_id)
) ENGINE=InnoDB COMMENT='지역별 인기 태그';


-- =====================================================================
-- 7. 방문 기록
-- =====================================================================
--
-- T1(현장 인증) 또는 T2(위치 확인) 게시물을 올리면 자동으로 방문 기록이 생긴다.
-- 사진 없이 "여기 왔어요"만 누르는 수동 체크인도 같은 테이블을 쓴다.
CREATE TABLE visits (
    visit_id     BIGINT      NOT NULL AUTO_INCREMENT,
    user_id      BIGINT      NOT NULL,
    place_id     BIGINT      NOT NULL,
    area_code    INT         NOT NULL,
    post_id      BIGINT      NULL COMMENT '게시물로부터 자동 생성된 경우',
    source       VARCHAR(30) /* AUTO_FROM_POST|MANUAL_CHECKIN */ NOT NULL,
    tier         VARCHAR(30) /* ON_SITE|LOCATION_CONFIRMED */ NOT NULL COMMENT 'T3 는 방문으로 인정하지 않는다',
    visited_on   DATE        NOT NULL COMMENT '같은 날 같은 장소는 1회만 기록',
    visited_at   DATETIME(6) NOT NULL,
    PRIMARY KEY (visit_id),
    UNIQUE KEY uk_visits_daily (user_id, place_id, visited_on),
    KEY idx_visits_user (user_id, visited_at DESC),
    KEY idx_visits_place (place_id, visited_at DESC),
    KEY idx_visits_area (area_code, visited_at DESC),
    CONSTRAINT fk_visits_user   FOREIGN KEY (user_id)  REFERENCES users(user_id),
    CONSTRAINT fk_visits_place  FOREIGN KEY (place_id) REFERENCES places(place_id),
    CONSTRAINT fk_visits_region FOREIGN KEY (area_code) REFERENCES regions(area_code)
) ENGINE=InnoDB COMMENT='방문 기록 (히트맵 · 마이페이지 · 배지)';


-- =====================================================================
-- 8. 알림 (FCM)
-- =====================================================================

CREATE TABLE notifications (
    notification_id BIGINT       NOT NULL AUTO_INCREMENT,
    recipient_id    BIGINT       NOT NULL COMMENT '알림을 받는 사람',
    actor_id        BIGINT       NULL     COMMENT '행동한 사람. 시스템 알림이면 NULL',
    type            VARCHAR(30) /* POST_LIKE|COMMENT|COMMENT_REPLY|FOLLOW|FOLLOWEE_POST|EVENT_NEARBY|GRADE_UP|SYSTEM */ NOT NULL,
    target_type     VARCHAR(30) /* POST|COMMENT|USER|PLACE|EVENT */ NULL,
    target_id       BIGINT       NULL,
    -- 문구는 서버가 만들지 않는다. 프론트가 code + params 로 다국어 문구를 조립한다.
    message_key     VARCHAR(50)  NOT NULL COMMENT '예: notification.post_like',
    message_params  JSON         NULL     COMMENT '예: {"nickname":"Mina","title":"경복궁 야간개장"}',
    thumbnail_url   VARCHAR(500) NULL,
    is_read         TINYINT(1)   NOT NULL DEFAULT 0,
    read_at         DATETIME(6)  NULL,
    push_sent_at    DATETIME(6)  NULL COMMENT 'FCM 발송 성공 시각. NULL 이면 인앱 알림만',
    created_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (notification_id),
    KEY idx_notifications_recipient (recipient_id, created_at DESC),
    KEY idx_notifications_unread (recipient_id, is_read, created_at DESC),
    -- 같은 사람이 같은 대상에 좋아요를 껐다 켰다 할 때 알림이 반복 생성되는 것을 막는다
    UNIQUE KEY uk_notifications_dedup (recipient_id, actor_id, type, target_type, target_id),
    CONSTRAINT fk_notifications_recipient FOREIGN KEY (recipient_id) REFERENCES users(user_id),
    CONSTRAINT fk_notifications_actor     FOREIGN KEY (actor_id)     REFERENCES users(user_id)
) ENGINE=InnoDB COMMENT='인앱 알림 + FCM 푸시 발송 이력';


-- =====================================================================
-- 9. 신고
-- =====================================================================

CREATE TABLE reports (
    report_id        BIGINT       NOT NULL AUTO_INCREMENT,
    reporter_user_id BIGINT       NOT NULL,
    target_type      VARCHAR(30) /* POST|COMMENT|USER|PLACE */ NOT NULL,
    target_id        BIGINT       NOT NULL,
    reason           VARCHAR(30) /* INAPPROPRIATE|COPYRIGHT|NOT_THE_PLACE|SPAM|DUPLICATE_PLACE|OTHER */ NOT NULL,
    detail           VARCHAR(500) NULL,
    status           VARCHAR(30) /* PENDING|ACCEPTED|REJECTED */ NOT NULL DEFAULT 'PENDING',
    handled_at       DATETIME(6)  NULL,
    created_at       DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (report_id),
    UNIQUE KEY uk_reports_once (reporter_user_id, target_type, target_id),
    KEY idx_reports_target (target_type, target_id, status),
    CONSTRAINT fk_reports_user FOREIGN KEY (reporter_user_id) REFERENCES users(user_id)
) ENGINE=InnoDB COMMENT='신고 3회 누적 시 자동 BLINDED';


-- =====================================================================
-- 10. 랭킹
-- =====================================================================
--
-- 현재 랭킹 스냅샷만 보관 (배치가 UPSERT). 조회 API 는 이 테이블만 읽는다.
-- area_code = 0 은 전국. NULL 을 쓰면 UNIQUE 제약이 동작하지 않는다.
-- theme 으로 테마별 랭킹(추가기능 5)과 "숨은 명소" 랭킹을 스키마 변경 없이 확장한다.
CREATE TABLE place_rankings (
    ranking_id    BIGINT        NOT NULL AUTO_INCREMENT,
    area_code     INT           NOT NULL DEFAULT 0,
    period        VARCHAR(30) /* DAILY|WEEKLY|MONTHLY|ALL_TIME */ NOT NULL,
    theme         VARCHAR(30)   NOT NULL DEFAULT 'ALL'
                  COMMENT 'ALL | HIDDEN_GEM | NATURE | CULTURE | FOOD | FESTIVAL | KCULTURE ...',
    place_id      BIGINT        NOT NULL,
    rank_no       INT           NOT NULL,
    previous_rank INT           NULL COMMENT 'NULL 이면 신규 진입(NEW)',
    score         DECIMAL(12,2) NOT NULL,
    post_count    INT           NOT NULL DEFAULT 0,
    like_count    INT           NOT NULL DEFAULT 0,
    visit_count   INT           NOT NULL DEFAULT 0,
    calculated_at DATETIME(6)   NOT NULL,
    PRIMARY KEY (ranking_id),
    UNIQUE KEY uk_ranking_slot (area_code, period, theme, place_id),
    KEY idx_ranking_lookup (area_code, period, theme, rank_no),
    CONSTRAINT fk_ranking_place FOREIGN KEY (place_id) REFERENCES places(place_id)
) ENGINE=InnoDB;


CREATE TABLE ranking_history (
    history_id    BIGINT        NOT NULL AUTO_INCREMENT,
    area_code     INT           NOT NULL DEFAULT 0,
    period        VARCHAR(30) /* DAILY|WEEKLY|MONTHLY|ALL_TIME */ NOT NULL,
    theme         VARCHAR(30)   NOT NULL DEFAULT 'ALL',
    place_id      BIGINT        NOT NULL,
    rank_no       INT           NOT NULL,
    score         DECIMAL(12,2) NOT NULL,
    snapshot_date DATE          NOT NULL,
    PRIMARY KEY (history_id),
    UNIQUE KEY uk_history_slot (snapshot_date, area_code, period, theme, place_id),
    KEY idx_history_place (place_id, snapshot_date)
) ENGINE=InnoDB COMMENT='순위 변동 그래프용. 90일 후 삭제';


-- =====================================================================
-- 11. 히트맵 (메인 랜딩 지도)  ★ 신규
-- =====================================================================
--
-- 지도에 "지금 어디에 사람이 몰려 있는지"를 격자로 보여준다.
-- 원본(posts · visits)을 매 요청마다 GROUP BY 하면 지도를 드래그할 때마다 무거운 쿼리가 나간다.
-- → 배치가 격자 단위로 미리 집계해두고, 조회 API 는 사각 범위(bbox)로 이 테이블만 읽는다.
--
-- grid_level 별 격자 크기 (위경도 반올림 자릿수)
--   0 : 1.0도    (~110km)  전국 뷰      zoom  1~6
--   1 : 0.1도    (~11km)   광역시도 뷰   zoom  7~9
--   2 : 0.01도   (~1.1km)  시군구 뷰    zoom 10~12
--   3 : 0.001도  (~110m)   동네 뷰      zoom 13+
CREATE TABLE heatmap_cells (
    cell_id       BIGINT        NOT NULL AUTO_INCREMENT,
    grid_level    INT       NOT NULL,
    -- REALTIME = 최근 1시간. 메인 지도의 기본 레이어이며 1분 주기로 갱신한다.
    period        VARCHAR(30) /* REALTIME|DAY|WEEK|MONTH|ALL */ NOT NULL DEFAULT 'REALTIME',
    cell_lat      DECIMAL(10,7) NOT NULL COMMENT '격자 중심 위도 (반올림된 값)',
    cell_lng      DECIMAL(10,7) NOT NULL COMMENT '격자 중심 경도',
    geom          POINT         NOT NULL SRID 4326,
    area_code     INT           NULL,
    post_count    INT           NOT NULL DEFAULT 0,
    visit_count   INT           NOT NULL DEFAULT 0,
    user_count    INT           NOT NULL DEFAULT 0 COMMENT '고유 기여자 수',
    place_count   INT           NOT NULL DEFAULT 0,
    intensity     DECIMAL(5,4)  NOT NULL DEFAULT 0 COMMENT '0.0~1.0 정규화 값. 프론트가 색상 강도로 그대로 사용',
    top_place_id  BIGINT        NULL COMMENT '이 격자에서 가장 인기 있는 장소 (툴팁용)',
    top_post_id   BIGINT        NULL COMMENT '이 격자의 대표 게시물. 홈 지도 "인기 사진" 레이어의 썸네일',
    top_post_thumb VARCHAR(500) NULL COMMENT '대표 게시물 썸네일 URL 캐시 (마커에 바로 그린다)',
    last_post_at  DATETIME(6)   NULL COMMENT '이 격자의 마지막 게시 시각. 앱이 "지금 올라오는 중" 연출에 사용',
    calculated_at DATETIME(6)   NOT NULL,
    next_refresh_at DATETIME(6) NULL COMMENT '다음 갱신 예정 시각. 앱의 폴링 주기를 서버가 제어한다',
    PRIMARY KEY (cell_id),
    UNIQUE KEY uk_heatmap_cell (grid_level, period, cell_lat, cell_lng),
    KEY idx_heatmap_lookup (grid_level, period, intensity DESC),
    SPATIAL KEY spx_heatmap_geom (geom)
) ENGINE=InnoDB COMMENT='히트맵 격자 집계 (배치가 갱신)';


-- =====================================================================
-- 12. 검색 · 배치 로그
-- =====================================================================

CREATE TABLE search_logs (
    search_id   BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NULL,
    keyword     VARCHAR(100) NOT NULL,
    result_count INT         NOT NULL DEFAULT 0,
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (search_id),
    KEY idx_search_keyword (keyword, created_at DESC)
) ENGINE=InnoDB COMMENT='인기 검색어 집계용';


CREATE TABLE sync_logs (
    sync_id       BIGINT       NOT NULL AUTO_INCREMENT,
    sync_type     VARCHAR(30) /* TOUR_API_AREA|TOUR_API_DETAIL|TOUR_API_IMAGE|TOUR_API_FESTIVAL|RANKING|HEATMAP|STATS|COUNTER_FIX|PURGE|POPULARITY|TAG_STATS */ NOT NULL,
    target        VARCHAR(100) NULL COMMENT '예: areaCode=1,contentTypeId=12',
    status        VARCHAR(30) /* RUNNING|SUCCESS|FAILED */ NOT NULL DEFAULT 'RUNNING',
    created_count INT          NOT NULL DEFAULT 0,
    updated_count INT          NOT NULL DEFAULT 0,
    failed_count  INT          NOT NULL DEFAULT 0,
    message       TEXT         NULL,
    started_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    finished_at   DATETIME(6)  NULL,
    PRIMARY KEY (sync_id),
    KEY idx_sync_logs (sync_type, started_at DESC)
) ENGINE=InnoDB;


-- =====================================================================
-- 13. 초기 데이터 — 17개 시도
-- =====================================================================

INSERT INTO regions (area_code, name_ko, name_en, name_ja, name_zh, center_lat, center_lng, sort_order) VALUES
(1,  '서울',     'Seoul',        'ソウル',     '首尔',   37.5665000, 126.9780000, 1),
(2,  '인천',     'Incheon',      '仁川',       '仁川',   37.4563000, 126.7052000, 2),
(3,  '대전',     'Daejeon',      '大田',       '大田',   36.3504000, 127.3845000, 3),
(4,  '대구',     'Daegu',        '大邱',       '大邱',   35.8714000, 128.6014000, 4),
(5,  '광주',     'Gwangju',      '光州',       '光州',   35.1595000, 126.8526000, 5),
(6,  '부산',     'Busan',        '釜山',       '釜山',   35.1796000, 129.0756000, 6),
(7,  '울산',     'Ulsan',        '蔚山',       '蔚山',   35.5384000, 129.3114000, 7),
(8,  '세종',     'Sejong',       '世宗',       '世宗',   36.4800000, 127.2890000, 8),
(31, '경기도',   'Gyeonggi-do',  '京畿道',     '京畿道', 37.4138000, 127.5183000, 9),
(32, '강원특별자치도','Gangwon-do','江原道',    '江原道', 37.8228000, 128.1555000, 10),
(33, '충청북도', 'Chungcheongbuk-do','忠清北道','忠清北道',36.6357000,127.4917000, 11),
(34, '충청남도', 'Chungcheongnam-do','忠清南道','忠清南道',36.5184000,126.8000000, 12),
(35, '경상북도', 'Gyeongsangbuk-do','慶尚北道','庆尚北道',36.4919000,128.8889000, 13),
(36, '경상남도', 'Gyeongsangnam-do','慶尚南道','庆尚南道',35.4606000,128.2132000, 14),
(37, '전북특별자치도','Jeonbuk-do','全羅北道','全罗北道',35.7175000,127.1530000, 15),
(38, '전라남도', 'Jeollanam-do', '全羅南道',   '全罗南道',34.8679000,126.9910000, 16),
(39, '제주도',   'Jeju-do',      '済州島',     '济州岛', 33.4996000, 126.5312000, 17);

INSERT INTO region_stats (area_code) SELECT area_code FROM regions;

-- 탈퇴 사용자의 콘텐츠를 이관받는 시스템 계정 (KEEP_ANONYMIZED 처리용)
INSERT INTO users (user_id, auth_type, login_id, provider, provider_user_id, nickname, status, role)
VALUES (1, 'LOCAL', NULL, NULL, NULL, '탈퇴한 사용자', 'SUSPENDED', 'USER');
