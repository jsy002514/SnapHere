# SnapHere — 백엔드 개발 지침

Claude Code가 매 세션 자동으로 읽는 파일입니다.
패키지 루트는 `com.ssafy.snaphere`, 프로젝트는 `backend/` 아래에 있습니다.

---

## 프로젝트 한 줄 요약

**사진으로 채우는 대한민국 실시간 여행 지도.**
사용자가 GPS가 담긴 사진을 올리면 서버가 위치 신뢰도를 판정하고, 그 데이터로 지도 히트맵·장소 랭킹·지역 커뮤니티가 굴러간다.

담당: 백엔드(API만). 앱 화면은 프론트가 만든다. **우리는 화면이 아니라 JSON을 만든다.**

---

## 스택 (확정 — 바꾸지 말 것)

| 영역 | 선택 |
|---|---|
| 언어/프레임워크 | Java 17, Spring Boot 3.3.x |
| 빌드 | Gradle (Wrapper 사용, 로컬 Gradle 설치 금지) |
| DB | MySQL 8.0 — 공간 인덱스 사용 |
| ORM | Spring Data JPA (복잡한 목록은 nativeQuery 또는 QueryDSL) |
| 인증 | 자체 JWT. 아이디/비밀번호(BCrypt) + 구글 OIDC |
| 이미지·영상 | AWS S3 Presigned URL |
| 푸시 | FCM (firebase-admin) |
| API 문서 | springdoc-openapi (Swagger UI) |
| 외부 데이터 | 한국관광공사 TourAPI — **배치로만 호출** |

---

## 확정된 설계 결정 (뒤집지 말 것)

1. **소셜 로그인은 구글 단독.** 카카오·네이버·애플은 만들지 않는다.
2. **익명 계정 없음.** 조회는 비로그인 허용, 쓰기(업로드·좋아요·댓글·팔로우)는 로그인 필수.
3. **사진과 게시글은 하나의 엔티티(`posts`).** 이미지 0~N개(`post_media`). 이미지 0개면 텍스트 글.
4. **장소는 `places` 한 테이블.** `place_type = OFFICIAL`(TourAPI) / `USER`(사용자가 만든 숨은 명소).
5. **위치 신뢰도 3단계** — `ON_SITE`(현장 인증) / `LOCATION_CONFIRMED`(위치 확인) / `NO_LOCATION`(위치 미확인).
   **판정은 100% 서버가 한다. 프론트가 보낸 tier 값은 절대 신뢰하지 않는다.**
6. **랭킹·히트맵은 조회 시 계산하지 않는다.** 배치가 미리 만든 스냅샷 테이블만 읽는다.
7. **모든 삭제는 논리 삭제**(`status` 컬럼). 물리 삭제는 배치로만.
8. **실시간 = 1분 캐시 + 앱 60초 폴링.** 웹소켓·SSE 사용하지 않는다.
9. **에러는 코드로 응답한다.** 서버가 완성된 사용자 문구를 만들지 않는다(외국인 대상 다국어).
10. **모든 스키마는 `docs/03_schema.sql` 이 원본.** JPA `ddl-auto`는 `validate`로 고정.

---

## 반드시 알아야 할 함정 (실제로 겪고 검증한 것들)

### 1. MySQL 8 좌표 축 순서 — 가장 많이 틀린다

`POINT()` 함수와 WKT 문자열의 좌표 순서가 **서로 반대**다. (MySQL 8.0.46에서 직접 검증)

```sql
-- ✅ POINT() 함수: (경도, 위도)
ST_SRID(POINT(126.9770, 37.5796), 4326)
-- ✅ WKT 문자열: (위도 경도)
ST_GeomFromText('POINT(37.5796 126.9770)', 4326)
-- 접근자
ST_X(g) = 위도,  ST_Y(g) = 경도
```

**TourAPI는 `mapx = 경도`, `mapy = 위도`** 로 준다(이름이 헷갈리게 지어져 있다).
→ 좌표에서 geom을 만드는 코드는 **`GeoUtils` 한 곳에만** 두고, 다른 곳에서 직접 만들지 못하게 한다.

### 2. 공간 인덱스를 실제로 태우려면 `MBRContains` 가 필요하다

```sql
-- ❌ 풀스캔 (EXPLAIN type=ALL)
WHERE ST_Distance_Sphere(geom, :pt) <= 500
-- ✅ 인덱스 사용 (EXPLAIN type=range, key=spx_places_geom)
WHERE MBRContains(ST_Buffer(:pt, 500), geom)
  AND ST_Distance_Sphere(geom, :pt) <= 500
```

위치 관련 쿼리를 새로 쓸 때마다 **`EXPLAIN` 을 찍어 `type=range` 인지 확인**할 것.

### 2-1. `ST_Buffer` 의 거리 단위는 SRID 4326 에서 **미터**다

MySQL 8.0.46 에서 실측 확인 (기준점에서 24m 떨어진 장소로 검증).

| 호출 | 결과 |
|---|---|
| `ST_Buffer(pt, 1000)` | 잡힘 (24m < 1000m) |
| `ST_Buffer(pt, 100)` | 잡힘 (24m < 100m) |
| `ST_Buffer(pt, 10)` | **안 잡힘** (24m > 10m) |
| `ST_Buffer(pt, 0.01)` | 안 잡힘 → 도(degree) 단위가 아니다 |

**반경(m)을 그대로 넣는다.** 도 단위로 착각해 `radius / 111320` 을 넣으면
500m 검색이 0.0045m 가 되어 **결과가 항상 0건인데 에러도 나지 않는다.** 조용히 전부 실패하는 종류의 버그다.

### 2-2. InnoDB FULLTEXT — `DELETE` 로 지운 문서 ID 가 새 행을 가린다

`ngram FULLTEXT` 로 장소 이름을 검색한다(`ft_places_title`). 그런데 행을 **하드 삭제**하면
지워진 문서 ID 가 `INNODB_FT_DELETED` 에 쌓이고, 이후 삽입된 행이 그 ID 를 재사용하면
**검색 결과에서 조용히 누락된다.** (2026-08-22 실측: 5건을 넣었는데 3건만 검색됨)

- 우리 설계는 전부 **소프트 삭제**(`status`)이므로 평소에는 발생하지 않는다. 하드 삭제하지 말 것.
- 계정 파기 배치처럼 하드 삭제가 필요한 곳을 만들었다면,
  **월 1회 `SET GLOBAL innodb_optimize_fulltext_only=ON; OPTIMIZE TABLE places;`** 를 배치에 넣어야 한다.
- `TRUNCATE` 는 FULLTEXT 보조 테이블까지 초기화하므로 이 문제가 없다.

### 2-3. `.ps1` 스크립트는 **UTF-8 BOM** 으로 저장해야 한다

Windows PowerShell 5.1 은 BOM 이 없는 `.ps1` 을 시스템 코드페이지(한국어 Windows = CP949)로 읽는다.
한글 주석·문자열이 깨지면서 인용이 무너지고, 실제 원인과 무관한 구문 오류가 쏟아진다.

```
식 또는 문에서 예기치 않은 'labelLat' 토큰입니다.
앰퍼샌드(&) 문자를 사용할 수 없습니다.        ← URL 안의 & 는 잘못이 없다. 인코딩 때문에 파서가 문자열을 놓친 것
```

- 파일 저장: **UTF-8 with BOM**
- 스크립트 첫머리에 `[Console]::OutputEncoding = [System.Text.Encoding]::UTF8` 을 넣어 출력 한글도 지킨다
- `.sql` 파일을 파이프로 넘길 때도 `Get-Content -Encoding UTF8` 을 반드시 붙인다

### 2-4. PowerShell 파이프는 한글을 **`?` 로 파괴한다** — 가장 조용한 사고

```powershell
Get-Content file.sql -Encoding UTF8 | docker exec -i mysql ...   # ❌ 한글이 전부 ? 가 된다
```

`-Encoding UTF8` 은 파일을 **읽을 때만** 적용된다.
PowerShell 이 네이티브 프로그램(`docker`, `mysql` 등)으로 파이프할 때 쓰는 인코딩은 `$OutputEncoding` 이고,
**Windows PowerShell 5.1 의 기본값은 ASCII** 다. 한글은 표현할 수 없어 전부 `?` 로 치환되고 **오류도 나지 않는다.**

2026-08-22 실제 사고: `04_seed_regions.sql` 을 이 방식으로 넣어 `regions.name_ko` 가 `??`(HEX `3F3F`)로 저장됐다.
자동 태그 추천과 지도 라벨이 조용히 깨졌고, 원인을 TourAPI 로 잘못 의심하느라 시간을 썼다.

**반드시 `docker cp` 로 복사한 뒤 컨테이너 안에서 실행한다.** 바이트가 그대로 옮겨진다.

```powershell
powershell -ExecutionPolicy Bypass -File scripts\apply-sql.ps1 docs\07_fix_region_names.sql
```

검증은 눈으로 하지 말고 `HEX()` 로 한다. 콘솔 출력은 콘솔 인코딩 때문에 따로 깨질 수 있어 신뢰할 수 없다.

```sql
SELECT area_code, name_ko, HEX(name_ko) FROM regions WHERE area_code = 1;
-- 정상: EC849CEC9AB8   깨짐: 3F3F
```

**파일 인코딩 규칙 정리**

| 파일 | BOM | 이유 |
|---|---|---|
| `.ps1` | **있어야 함** | 없으면 PowerShell 5.1 이 CP949 로 읽어 문자열이 깨진다 |
| `.sql` | **없어야 함** | MySQL 이 BOM 을 구문으로 읽어 첫 문장에서 오류를 낸다 |
| `.java` `.md` `.yml` | 없음 | Gradle·Spring 이 UTF-8 로 읽는다 |

### 3. `SPATIAL INDEX` 는 NOT NULL 컬럼에만 걸린다

좌표 없는 TourAPI 데이터는 `geom = POINT(0,0)` + `has_coordinate = 0` 으로 저장하고,
위치 쿼리에서 `has_coordinate = 1` 로 걸러낸다.

### 4. TourAPI serviceKey 이중 인코딩

발급받은 키는 이미 URL 인코딩되어 있다. 한 번 더 인코딩하면 `SERVICE_KEY_IS_NOT_REGISTERED_ERROR` 가 난다.
`UriComponentsBuilder ... .build(true)` 로 넘길 것.

### 5. TourAPI 응답의 `items` 가 빈 문자열로 온다

결과가 0건이면 `response.body.items` 가 `{}` 가 아니라 `""` 로 온다. 파싱에 방어 코드 필수.

### 6. 페이지네이션 정렬에 tie-breaker가 없으면 데이터가 새거나 겹친다

`ORDER BY like_count DESC` 만 쓰면 동점 구간에서 페이지 경계가 흔들린다.
**정렬 마지막에 항상 `post_id DESC`(또는 PK)를 붙인다.**

### 7. N+1 쿼리

`application.yml` 의 `hibernate.default_batch_fetch_size: 100` 을 지우지 말 것.
목록 API를 만들 때는 반드시 실행된 SQL 개수를 로그로 확인한다.

### 8. 비정규화 카운터는 어긋난다

`like_count`, `post_count` 등은 조회 성능용 중복 컬럼이다.
**매일 새벽 실제 COUNT로 덮어쓰는 보정 배치를 반드시 만든다.**

---

## 패키지 구조 (도메인형)

```
com.ssafy.snaphere
├── global/                  공통 인프라 — 도메인 로직 금지
│   ├── common/              ApiResponse, PageResponse, BaseTimeEntity
│   ├── error/               ErrorCode, BusinessException, GlobalExceptionHandler
│   ├── security/            JwtTokenProvider, JwtAuthenticationFilter, @AuthUser
│   ├── config/              Security, CORS, Swagger, S3, Async, Scheduling
│   ├── infra/               s3/, fcm/, tourapi/
│   └── util/                GeoUtils  ← 좌표 변환은 반드시 여기만 통과
└── domain/
    ├── auth/     인증 (아이디·비밀번호 + 구글)
    ├── user/     프로필, 인기지수·등급, 계정 삭제
    ├── follow/   팔로우
    ├── region/   17개 시도
    ├── place/    관광지 + 사용자 장소, TourAPI 적재
    ├── post/     게시물, 미디어, 좋아요, ⭐ 위치 신뢰도 판정
    ├── comment/  댓글
    ├── tag/      해시태그, 자동 추천
    ├── visit/    방문 기록
    ├── notification/ 알림 + FCM
    ├── ranking/  랭킹·추천 배치
    ├── map/      히트맵
    ├── event/    지자체 행사
    └── search/   통합 검색
```

계층은 `controller → service → repository` **단방향**. 컨트롤러가 리포지토리를 직접 부르지 않는다.

---

## 코드 규약

- **엔티티에 `@Setter` 금지.** 상태 변경은 의도가 드러나는 메서드로 (`user.withdraw(...)`).
- **엔티티를 컨트롤러 응답으로 직접 내보내지 않는다.** 반드시 DTO(record)로 감싼다.
- **목록 조회에서 장문 컬럼(`overview`, `content`)을 조회하지 않는다.** DTO Projection 사용.
- 응답은 전부 `ApiResponse<T>` 로 감싼다. 페이지는 `PageResponse<T>`.
- 예외는 `throw new BusinessException(ErrorCode.XXX)`. 컨트롤러에서 try-catch 하지 않는다.
- 에러 코드는 `ErrorCode` enum 이름이 곧 API 응답의 `code` 다. 이름을 함부로 바꾸면 프론트가 깨진다.
- 날짜는 전부 ISO-8601 with offset. 서버 타임존 `Asia/Seoul` 고정.
- 페이지 번호는 **API 규약상 1부터**. Spring Data는 0부터이므로 `PageResponse`에서 변환한다.
- **DB 타입 규약 — 어기면 서버가 안 뜬다.** `ddl-auto: validate` 가 엔티티와 테이블을 대조한다.
  - 상태·구분값: DDL 은 `VARCHAR(30)`, 엔티티는 `@Enumerated(EnumType.STRING) @Column(length = 30)`.
    **MySQL `ENUM` 금지** — JDBC 가 CHAR 로 보고해서 검증이 깨진다.
  - 해시(SHA-256) 컬럼: `VARCHAR(64)`. `CHAR(64)` 금지.
  - boolean: `TINYINT(1)`. 그 외 작은 정수는 `TINYINT` 대신 `INT`.
  - 새 엔티티를 쓸 때마다 `docs/03_schema.sql` 의 해당 CREATE TABLE 과 컬럼 타입·길이·null 여부를 한 줄씩 대조할 것.

---

## 작업 순서 (이 순서를 지킬 것)

각 단계는 **"확인 방법"을 통과해야** 다음으로 넘어간다.

| 단계 | 내용 | 확인 방법 |
|---|---|---|
| **S1** | 프로젝트 세팅 · DB 연결 · Swagger | `./gradlew bootRun` 후 `/swagger-ui/index.html` 열림 |
| **S2** | 공통 응답·예외·JWT·CORS | 더미 컨트롤러가 `{"success":true,...}` 반환 |
| **S3** | 회원가입·로그인·토큰 재발급 | Swagger에서 가입 → 로그인 → 토큰으로 `/users/me` 호출 성공 |
| **S4** | 지역·장소 조회 + **주변 검색** | 좌표 넣으면 거리순 결과. `EXPLAIN` 이 `type=range` |
| **S5** | TourAPI 적재 배치 | `places` 에 수천 건, `has_coordinate=1` 비율 확인 |
| **S6** | S3 Presigned URL | 발급된 URL로 실제 업로드 성공 |
| **S7** | ⭐ 게시물 등록 + **위치 신뢰도 판정** | 좌표·시각을 바꿔가며 T1/T2/T3가 의도대로 나오는지 |
| **S8** | 게시물 목록·상세·좋아요 | 가변 높이 격자용 비율 필드 포함 |
| **S9** | 댓글·태그(자동 추천 포함) | 업로드 시 지역·분류·행사 태그가 자동으로 붙는지 |
| **S10** | 팔로우 + 팔로잉 가중치 정렬 | 첫 페이지만 개인화되는지 |
| **S11** | 방문 기록 · 알림(FCM) | 좋아요 시 상대에게 푸시 도착 |
| **S12** | 랭킹·히트맵 배치 | 스냅샷 테이블이 채워지고 조회 API가 그것만 읽는지 |
| **S13** | 이벤트(행사) | 진행중·예정 필터 동작 |
| **S14** | 계정 삭제 + 파기 배치 | 개인정보 즉시 제거, 30일 후 파기 예약 |

**S7이 이 프로젝트의 핵심이다.** 다른 걸 줄이더라도 이건 끝까지 붙잡는다.

---

## 지금 미결정인 것 (임의로 정하지 말고 물어볼 것)

1. 회원가입에 이메일을 받는지 — 안 받으면 비밀번호 찾기가 원리적으로 불가능
2. 영상 업로드를 전체 게시물에 허용할지, 이벤트에서만 허용할지
3. 등급(SEED~LEGEND) 구간 수치
4. 개인 SNS 링크 허용 도메인 목록
5. 인기 지수 산정 가중치
6. 방문자 목록 공개 범위

---

## 참고 문서 (`docs/`)

| 파일 | 내용 |
|---|---|
| `01_요구사항명세서.md` | 요구사항 201개. 구현 대상은 이 목록이 기준 |
| `02_기능명세서.md` | 화면별 동작·전환. 프론트와 합의된 흐름 |
| `03_API명세서.md` | **프론트엔드에 전달하는 계약.** 엔드포인트를 바꾸면 이 문서를 같이 고친다 |
| `03_schema.sql` | **DB 원본.** MySQL 8.0.46에서 실행 검증됨 (27 테이블) |
| `04_ERD.md` | 테이블 관계도 |
| `05_모바일목업.html` | 화면 19개 목업. 어떤 데이터가 화면에 필요한지 확인용 |
| `요구사항_기능_명세서.xlsx` | 요구사항·기능명세 원본 시트 |

## 현재 진행 상황

| 단계 | 상태 |
|---|---|
| S1 프로젝트 세팅 · DB · Swagger | 코드 작성 완료 — 로컬 실행 확인 대기 |
| S2 공통 응답 · 예외 · JWT · CORS | 완료 |
| S3 회원가입 · 로그인 · 토큰 재발급 | 완료 (구글 로그인은 스텁) |
| S4~S14 | 미착수 |

실행 방법과 확인 명령은 `backend/SETUP.md` 를 보세요.

---

## 나(사람)에게 보고할 때

- 단계가 끝나면 **무엇이 동작하는지**를 확인 명령과 함께 보여줄 것
- 스펙과 다르게 구현했으면 **먼저 말할 것** (나중에 발견되면 프론트까지 영향)
- 막히면 추측으로 진행하지 말고 **질문**할 것
