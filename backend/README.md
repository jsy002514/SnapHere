# SnapHere API (backend)

Spring Boot 3.5 · Java 21 · Gradle (Kotlin DSL) 기반 백엔드 API 서버.

명세는 저장소 문서를 정본으로 삼는다.

| 문서 | 위치 |
| --- | --- |
| 요구사항 · 기능 명세서 v1.1.3 | `docs/specs/snaphere-requirements-spec-v1.1.3.xlsx` |
| API 명세서 · ERD v1.1.3 | `docs/specs/snaphere-api-spec-v1.1.3.xlsx` |
| 데이터 설계 (DBML) v1.1.3 | `docs/db-schema.dbml` |
| 명세 변경 이력 | `docs/spec-changelog.md` |
| 커밋·브랜치 규칙 | `docs/commit-convention.md` |

## 처음 받은 뒤 한 번

Gradle 래퍼는 저장소에 넣지 않았다. 각자 한 번 만든다.

```bash
cd backend
gradle wrapper --gradle-version 8.14
```

이후로는 래퍼로 실행한다.

```bash
./gradlew build      # 컴파일 + 테스트
./gradlew bootRun    # 로컬 실행 (기본 8080)
```

## 폴더 구조

```text
backend/
└── src/main/java/com/snaphere/api/
    ├── SnapHereApplication.java
    └── common/
        ├── error/     # 에러 코드 체계와 전역 예외 처리 (SYS-002)
        └── web/       # 공통 응답 봉투·커서 페이징·요청 추적 (SYS-001, SYS-003, SYS-016)
```

도메인 패키지는 기능을 붙일 때 `com.snaphere.api.post` 처럼 추가한다.

## 지금 구현된 것

골격만 있다. 엔드포인트는 아직 없다.

| 클래스 | 역할 | 요구사항 |
| --- | --- | --- |
| `common.web.ApiResponse` | 성공·실패 공통 응답 봉투 | `SYS-001` |
| `common.web.CursorPage` | 커서 페이징 응답 | `SYS-003`, `SYS-004`, `CMU-010` |
| `common.web.TraceIdFilter` | `X-Trace-Id` 수용·생성, MDC 주입 | `SYS-016` |
| `common.error.ErrorCode` | 코드 기반 에러 분기 | `SYS-002` |
| `common.error.ErrorBody` | 실패 봉투 본문 (`violations`, `retryAfterSec`) | `SYS-002` |
| `common.error.GlobalExceptionHandler` | 모든 예외를 실패 봉투로 변환 | `SYS-001`, `SYS-002` |
| `auth` | Google OIDC 로그인, 온보딩, JWT·리프레시 토큰 회전, 로그아웃, 경로 인가 | `AUTH-001`~`AUTH-011`, `AUTH-014` |

인증 실행 전에는 PostgreSQL 16 데이터베이스와 아래 환경 변수가 필요합니다. `SNAPHERE_JWT_SECRET`은
32바이트 이상인 무작위 값으로 설정하고, 모바일 앱의 Google OAuth 클라이언트 ID를 사용합니다.

```text
DB_URL=jdbc:postgresql://localhost:5432/snaphere
DB_USERNAME=snaphere
DB_PASSWORD=...
GOOGLE_OAUTH_CLIENT_ID=...
SNAPHERE_JWT_SECRET=...
SNAPHERE_TERMS_VERSION=2026-08-01
```

## 아직 없는 것

- 게시글 도메인 (`PST-001`~`PST-049`) — `docs/commit-convention.md`의 분할 계획 참고
