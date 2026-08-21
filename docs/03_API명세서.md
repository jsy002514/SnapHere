# API 명세서 v3 — SnapHere

> 관광데이터활용공모전 · 백엔드 API
> **이 문서를 프론트엔드에 전달합니다.** 코드보다 이 문서를 먼저 합의합니다.
> Base URL: `https://{host}/api/v1` · Swagger UI: `https://{host}/swagger-ui/index.html`
> 기준: 요구사항 명세서 v3 (201건) · 스키마 27테이블

---

---

# 0. 구현 현황 (2026-08-22 기준) — **프론트가 먼저 읽을 곳**

실제로 서버에 존재하는 엔드포인트는 **83개**다. 아래 표가 구현된 전체 목록이며,
이 문서의 나머지 장은 요청·응답 상세다.

| 컨트롤러 | base path | 개수 | 상태 |
|---|---|---|---|
| `AuthController` | `/api/v1/auth` | 7 | ✅ (구글 로그인은 **스텁** — `AUTH_004` 반환) |
| `UserController` | `/api/v1/users` | 7 | ✅ |
| `DeviceController` | `/api/v1/users/me/devices` | 2 | ✅ FCM 토큰 등록·삭제 |
| `RegionController` | `/api/v1/regions` | 3 | ✅ 목록 · 커뮤니티 홈 · 지역 태그 |
| `PlaceController` | `/api/v1/places` | 4 | ✅ 목록 · 주변 · 상세 · 사용자 장소 생성 |
| `MapController` | `/api/v1/map` | 1 | ✅ 마커 |
| `HeatmapController` | `/api/v1/map` | 3 | ✅ 히트맵 · 시도별 활동량 · 인기사진 레이어 |
| `PostController` | `/api/v1/posts` | 10 | ✅ 업로드URL · 태그추천 · 등록(Tier) · 목록 · 피드 · 상세 · 수정 · 삭제 · 좋아요 |
| `CommentController` | `/api/v1` | 6 | ✅ 목록 · 작성 · 수정 · 삭제 · 좋아요 |
| `FollowController` | `/api/v1` | 5 | ✅ 팔로우 · 언팔 · 팔로워 · 팔로잉 · 추천 |
| `VisitController` | `/api/v1` | 4 | ✅ 체크인 · 내방문 · 통계 · 장소방문자 |
| `BookmarkController` | `/api/v1/bookmarks` | 4 | ✅ POST·PLACE 저장 |
| `NotificationController` | `/api/v1/notifications` | 3 | ✅ 목록 · 안읽은수 · 읽음 |
| `RankingController` | `/api/v1` | 2 | ✅ 장소 랭킹 · 추천 장소 |
| `EventController` | `/api/v1/events` | 3 | ✅ 목록 · 주변 · 상세 |
| `SearchController` | `/api/v1/search` | 2 | ✅ 통합검색 · 인기검색어 |
| `ReportController` | `/api/v1` | 3 | ✅ 게시물 · 댓글 · 장소 신고 |
| `MediaFileController` | `/media` | 2 | ⚠️ **로컬 개발용.** S3 전환 시 불필요 |
| `AdminTourSyncController` | `/api/v1/admin/tour-sync` | 6 | ✅ ROLE_ADMIN 전용 |
| `AdminBatchController` | `/api/v1/admin/batch` | 6 | ✅ ROLE_ADMIN 전용 |

## 프론트가 반드시 알아야 하는 5가지

1. **`tier` 를 보내지 마세요.** 게시물 등록 시 서버가 좌표·촬영시각·촬영방식으로 판정합니다.
   응답의 `tier`, `tierMessageKey`, `tierReason` 을 그대로 표시하세요.
2. **문구는 앱이 만듭니다.** 서버는 `messageKey` + `messageParams` 만 줍니다
   (`tier.on_site`, `notification.post_like` 등). 외국인 대상이라 완성된 문장을 서버가 만들지 않습니다.
3. **`thumbnailRatio`** 가 목록 응답에 포함됩니다. masonry 배치의 높이를 이미지 수신 전에 계산하세요.
4. **히트맵은 `nextRefreshAt` 이후에만 재조회**하세요. 웹소켓을 쓰지 않는 대신 서버가 폴링 주기를 통제합니다.
   단, **업로드 직후에는 주기와 무관하게 즉시 재조회**해도 됩니다(서버가 업로드 시 즉시 재집계합니다).
5. **미디어 업로드는 3단계**입니다: `POST /posts/upload-urls` → 받은 `uploadUrl` 로 **PUT** → `POST /posts`.
   현재는 로컬 저장소라 `uploadUrl` 이 같은 서버를 가리킵니다. S3 전환 후에도 흐름은 동일합니다.

## 아직 안 되는 것

| 항목 | 상태 |
|---|---|
| 구글 로그인 | `GoogleTokenVerifier` 가 스텁. `aud`·`iss` 검증 구현 필요 |
| S3 업로드 | `MediaStorage` 인터페이스만 준비. AWS 키 확보 후 `S3MediaStorage` 추가 |
| FCM 실제 발송 | `PushSender` 인터페이스만 준비. 인앱 알림은 정상 동작 |
| 영상 썸네일 추출 | 영상은 `processStatus: PROCESSING` 으로 남는다 |
| 다국어 장소명 | `places` 에 번역 컬럼 없음. 다음 단계 |

---

# 0. 공통 규약

## 0.1 요청 헤더

| 헤더 | 필수 | 설명 |
|---|---|---|
| `Authorization` | 로그인 필요 API | `Bearer {accessToken}` |
| `Content-Type` | POST/PATCH | `application/json` |
| `Accept-Language` | 선택 | `ko` `en` `ja` `zh` |

## 0.2 응답 형식

**성공**
```json
{ "success": true, "data": { } }
```

**실패**
```json
{ "success": false, "error": { "code": "POST_003", "message": "...", "field": null } }
```

> 프론트는 `message`가 아니라 **`code`로 분기**합니다. 외국인 대상이라 사용자에게 보여줄 문구는
> 앱의 i18n 리소스에서 `code`로 찾습니다. `message`는 개발자용입니다.

**목록(페이징)**
```json
{
  "success": true,
  "data": {
    "content": [],
    "page": 1, "size": 20,
    "totalElements": 137, "totalPages": 7, "hasNext": true
  }
}
```

## 0.3 공통 파라미터

| 파라미터 | 기본 | 설명 |
|---|---|---|
| `page` | 1 | **1부터 시작** |
| `size` | 20 | 최대 100 |
| `sort` | 도메인별 | enum만 허용 |

날짜는 전부 ISO-8601 with offset: `2026-08-21T14:30:00+09:00` (서버 `Asia/Seoul` 고정)

## 0.4 권한 표기

| 표기 | 의미 |
|---|---|
| 🌐 | 비로그인 가능 |
| 🔒 | 로그인 필요 |
| ✋ | 작성자 본인만 |
| ⚙️ | 관리자 |

## 0.5 에러 코드

| Code | HTTP | 의미 | 앱 대응 |
|---|---|---|---|
| `COMMON_400` | 400 | 잘못된 요청 | 개발 오류 |
| `COMMON_401` | 401 | 토큰 없음·만료 | 재발급 → 실패 시 로그아웃 |
| `COMMON_403` | 403 | 권한 없음 | 안내 |
| `COMMON_404` | 404 | 리소스 없음 | 이전 화면 |
| `COMMON_429` | 429 | 요청 과다 | 잠시 후 재시도 |
| `COMMON_500` | 500 | 서버 오류 | "잠시 후 다시" |
| `AUTH_001` | 409 | 아이디 중복 | 다른 아이디 안내 |
| `AUTH_002` | 400 | 비밀번호 정책 위반 | 규칙 안내 |
| `AUTH_003` | 401 | 아이디 또는 비밀번호 불일치 | **어느 쪽이 틀렸는지 구분하지 않음** |
| `AUTH_004` | 401 | 구글 토큰 검증 실패 | 로그인 재시도 |
| `AUTH_005` | 401 | 리프레시 토큰 무효 | 로그아웃 처리 |
| `AUTH_006` | 401 | **로그인이 필요한 동작** | **로그인 시트 표시** |
| `AUTH_007` | 429 | 로그인 시도 초과 | "5분 후 다시" |
| `USER_001` | 400 | 닉네임 규칙 위반 | 입력 오류 |
| `USER_002` | 403 | 약관 미동의 | 약관 시트 |
| `USER_003` | 403 | 정지된 계정 | 안내 화면 |
| `USER_004` | 409 | 이미 탈퇴한 계정 | 로그아웃 초기화 |
| `USER_005` | 400 | 복구 기간 경과 | "복구할 수 없습니다" |
| `USER_006` | 400 | 허용되지 않은 SNS 도메인 | 입력 오류 |
| `FOLLOW_001` | 400 | 자기 자신 팔로우 | 개발 오류 |
| `FOLLOW_002` | 429 | 일일 팔로우 한도 초과 | 안내 |
| `REGION_001` | 404 | 없는 지역 코드 | 개발 오류 |
| `PLACE_001` | 404 | 없는 장소 | 이전 화면 |
| `PLACE_002` | 429 | 일일 장소 생성 한도 초과 | 안내 |
| `PLACE_003` | 400 | 서비스 범위 밖 좌표 | "국내만 등록 가능" |
| `PLACE_004` | 403 | 인증 반경 밖 체크인 | "더 가까이 가야 해요" |
| `MEDIA_001` | 400 | 지원하지 않는 형식 | 형식 안내 |
| `MEDIA_002` | 413 | 용량 초과 | 용량 안내 |
| `MEDIA_003` | 400 | 영상 길이 초과 | "60초 이하" |
| `POST_001` | 400 | 제목·본문 모두 없음 | 입력 오류 |
| `POST_002` | 404 | 없거나 삭제된 게시물 | 목록 복귀 |
| `POST_003` | 429 | 일일 업로드 한도 초과 | "오늘 30개를 다 썼어요" |
| `POST_004` | 429 | 동일 장소 하루 3개 초과 | "이 장소는 오늘 3개까지" |
| `POST_005` | 409 | 중복 이미지 | "이미 올린 사진이에요" |
| `POST_006` | 403 | 업로드 정지 상태 | 안내 |
| `COMMENT_001` | 400 | 길이 위반 | 입력 오류 |
| `COMMENT_002` | 400 | 대댓글 깊이 초과 | 개발 오류 |
| `REPORT_001` | 409 | 이미 신고함 | "이미 신고했어요" |

## 0.6 인증 플로우

```
앱 실행 → 지도(비로그인 조회 가능)
   │
   └─ 쓰기 동작 시도 (업로드·좋아요·댓글·팔로우)
        └─ 401 AUTH_006 → 로그인 시트
             ├─ 아이디/비밀번호  → POST /auth/login
             └─ 구글            → POST /auth/google

토큰 만료(401 COMMON_401) → POST /auth/refresh
   └─ 실패(AUTH_005) → 저장 토큰 폐기 후 비로그인 상태로
```

---

# 1. 엔드포인트 전체 목록

| Method | Path | 기능 | 권한 |
|---|---|---|---|
| **인증** | | | |
| POST | `/auth/signup` | 회원가입 | 🌐 |
| GET | `/auth/check-login-id` | 아이디 중복 확인 | 🌐 |
| POST | `/auth/login` | 아이디·비밀번호 로그인 | 🌐 |
| POST | `/auth/google` | 구글 로그인 | 🌐 |
| POST | `/auth/refresh` | 토큰 재발급 | 🌐 |
| POST | `/auth/logout` | 로그아웃 | 🔒 |
| POST | `/auth/logout-all` | 전체 기기 로그아웃 | 🔒 |
| POST | `/auth/restore` | 탈퇴 계정 복구 | 🌐 |
| **사용자** | | | |
| GET | `/users/me` | 내 정보 | 🔒 |
| PATCH | `/users/me` | 프로필 수정 | 🔒 |
| POST | `/users/me/password` | 비밀번호 변경 | 🔒 |
| POST | `/users/me/terms` | 약관 동의 | 🔒 |
| PATCH | `/users/me/notifications` | 알림 설정 | 🔒 |
| POST | `/users/me/devices` | FCM 토큰 등록 | 🔒 |
| GET | `/users/me/deletion-preview` | 삭제 전 미리보기 | 🔒 |
| DELETE | `/users/me` | 계정 삭제 | 🔒 |
| GET | `/users/{userId}` | 프로필 조회 | 🌐 |
| GET | `/users/{userId}/posts` | 사용자 게시물 | 🌐 |
| **팔로우** | | | |
| POST | `/users/{userId}/follow` | 팔로우 | 🔒 |
| DELETE | `/users/{userId}/follow` | 언팔로우 | 🔒 |
| GET | `/users/{userId}/followers` | 팔로워 목록 | 🌐 |
| GET | `/users/{userId}/followings` | 팔로잉 목록 | 🌐 |
| GET | `/feed` | 팔로잉 피드 | 🔒 |
| GET | `/follow-suggestions` | 추천 사용자 | 🔒 |
| **지도** | | | |
| GET | `/map/heatmap` | 히트맵 격자 | 🌐 |
| GET | `/map/regions` | 시도별 활동량 | 🌐 |
| GET | `/map/markers` | 장소 마커 | 🌐 |
| GET | `/map/photo-markers` | 인기 사진 마커 | 🌐 |
| **지역** | | | |
| GET | `/regions` | 17개 시도 | 🌐 |
| GET | `/regions/{areaCode}/community` | 커뮤니티 홈 | 🌐 |
| GET | `/regions/{areaCode}/tags` | 지역 인기 태그 | 🌐 |
| **장소** | | | |
| GET | `/places` | 장소 목록 | 🌐 |
| GET | `/places/nearby` | 주변 장소 | 🌐 |
| GET | `/places/{placeId}` | 장소 상세 | 🌐 |
| GET | `/places/{placeId}/posts` | 장소 게시물 | 🌐 |
| POST | `/places` | 사용자 장소 생성 | 🔒 |
| POST | `/places/{placeId}/checkin` | 체크인 | 🔒 |
| GET | `/places/{placeId}/visitors` | 방문자 목록 | 🌐 |
| **게시물** | | | |
| POST | `/posts/upload-urls` | 미디어 업로드 URL | 🔒 |
| GET | `/posts/tag-suggestions` | **자동 태그 추천** | 🔒 |
| POST | `/posts` | **게시물 등록 + Tier 판정** | 🔒 |
| GET | `/posts` | 게시물 목록 | 🌐 |
| GET | `/posts/{postId}` | 게시물 상세 | 🌐 |
| PATCH | `/posts/{postId}` | 수정 | ✋ |
| DELETE | `/posts/{postId}` | 삭제 | ✋ |
| POST/DELETE | `/posts/{postId}/like` | 좋아요 | 🔒 |
| POST/DELETE | `/posts/{postId}/bookmark` | 저장 | 🔒 |
| POST | `/posts/{postId}/reports` | 신고 | 🔒 |
| **댓글** | | | |
| GET | `/posts/{postId}/comments` | 댓글 목록 | 🌐 |
| POST | `/posts/{postId}/comments` | 댓글 작성 | 🔒 |
| PATCH | `/comments/{commentId}` | 수정 | ✋ |
| DELETE | `/comments/{commentId}` | 삭제 | ✋ |
| POST/DELETE | `/comments/{commentId}/like` | 댓글 좋아요 | 🔒 |
| **방문·저장** | | | |
| GET | `/visits` | 내 방문 기록 | 🔒 |
| GET | `/visits/stats` | 방문 통계 | 🔒 |
| GET | `/bookmarks` | 저장 목록 | 🔒 |
| **알림** | | | |
| GET | `/notifications` | 알림 목록 | 🔒 |
| GET | `/notifications/unread-count` | 안읽은 수 | 🔒 |
| PATCH | `/notifications/read` | 읽음 처리 | 🔒 |
| **랭킹·추천** | | | |
| GET | `/rankings/places` | 장소 랭킹 | 🌐 |
| GET | `/rankings/posts` | 인기 게시물 | 🌐 |
| GET | `/recommendations/places` | 추천 장소 | 🌐 |
| **이벤트** | | | |
| GET | `/events` | 행사 목록 | 🌐 |
| GET | `/events/nearby` | 주변 행사 | 🌐 |
| GET | `/events/{placeId}` | 행사 상세 | 🌐 |
| **검색** | | | |
| GET | `/search` | 통합 검색 | 🌐 |
| GET | `/search/popular` | 인기 검색어 | 🌐 |
| **관리자** | | | |
| POST | `/admin/sync/{type}` | 배치 수동 실행 | ⚙️ |
| DELETE | `/admin/users/{userId}` | 계정 강제 삭제 | ⚙️ |

---

# 2. 인증

## POST `/auth/signup` — 회원가입 🌐

```json
{
  "loginId": "lotus_2026",
  "password": "abcd1234",
  "passwordConfirm": "abcd1234",
  "nickname": "여행하는너구리",
  "email": "you@example.com",
  "termsAgreed": true
}
```

| 필드 | 규칙 |
|---|---|
| `loginId` | 4~20자, 영문 소문자·숫자·`_` |
| `password` | 8~64자, 영문+숫자 필수 |
| `nickname` | 2~20자, 중복 허용 |
| `email` | **선택. 없으면 비밀번호 찾기가 불가능** |

**200** → `TokenResponse` (아래 로그인과 동일)
**Errors** `AUTH_001` 아이디 중복 · `AUTH_002` 비밀번호 정책 · `USER_001` 닉네임

---

## GET `/auth/check-login-id?loginId=lotus_2026` 🌐
```json
{ "available": true }
```
> 중복 확인 후 가입까지 사이에 선점될 수 있어 **가입 시점에 서버가 다시 검증**합니다.

---

## POST `/auth/login` 🌐

```json
{ "loginId": "lotus_2026", "password": "abcd1234", "deviceId": "550e8400-...", "platform": "ANDROID" }
```

**200 `TokenResponse`**
```json
{
  "accessToken": "eyJhbGciOi...",
  "refreshToken": "eyJhbGciOi...",
  "accessTokenExpiresIn": 7200,
  "user": {
    "userId": 1024,
    "authType": "LOCAL",
    "nickname": "여행하는너구리",
    "profileImageUrl": null,
    "grade": "SEED",
    "popularityScore": 0,
    "termsAgreed": true,
    "locale": "ko"
  },
  "isNewUser": false,
  "withdrawn": false,
  "restorableUntil": null
}
```

| 필드 | 설명 |
|---|---|
| `withdrawn: true` | 탈퇴 유예 중인 계정 → 앱은 복구 안내 시트 표시 |
| `restorableUntil` | 이 시각까지 `POST /auth/restore` 로 복구 가능 |

**Errors** `AUTH_003` 아이디·비밀번호 불일치(**어느 쪽이 틀렸는지 구분하지 않음** — 계정 존재 여부 노출 방지) · `AUTH_007` 시도 초과 · `USER_003` 정지

---

## POST `/auth/google` 🌐
```json
{ "idToken": "eyJhbGciOiJSUzI1NiIs...", "deviceId": "550e8400-...", "platform": "IOS" }
```
**200** `TokenResponse` (`authType: "GOOGLE"`)
**Errors** `AUTH_004`

> 서버는 구글 공개키로 서명을 검증하고 `iss`·`aud`·`exp`를 함께 확인합니다.
> `aud` 검증을 빼면 다른 앱의 토큰으로도 로그인이 됩니다.

---

## POST `/auth/refresh` 🌐
`{ "refreshToken": "..." }` → 새 `accessToken` + **새 refreshToken**
리프레시 토큰은 **1회용**입니다. 재사용이 감지되면 해당 사용자의 전체 토큰을 무효화합니다.
**Errors** `AUTH_005`

## POST `/auth/logout` 🔒
`{ "refreshToken": "...", "deviceId": "..." }` → `204`. FCM 토큰도 함께 제거합니다.

## POST `/auth/logout-all` 🔒 → `204`. 전체 기기 세션 종료.

## POST `/auth/restore` 🌐
탈퇴 후 30일 이내 복구. 요청은 로그인과 동일(`loginId`+`password` 또는 `idToken`).
> ⚠️ 개인정보는 탈퇴 즉시 파기되므로 **이메일·닉네임·프로필 이미지는 복구되지 않습니다.**
> 돌아오는 것은 계정과 게시물입니다. 앱 안내 문구에 반드시 명시해야 합니다.

**Errors** `USER_005` 기간 경과

---

# 3. 사용자

## GET `/users/me` 🔒
```json
{
  "userId": 1024, "authType": "LOCAL", "loginId": "lotus_2026",
  "nickname": "여행하는너구리", "profileImageUrl": null, "bio": "사진 찍는 여행자",
  "email": "you@example.com", "locale": "ko",
  "grade": "TREE", "popularityScore": 1842, "nextGradeScore": 3000,
  "stats": { "postCount": 128, "followerCount": 842, "followingCount": 316, "visitCount": 47 },
  "snsLinks": { "instagram": "https://instagram.com/...", "youtube": null },
  "termsAgreed": true,
  "quota": { "dailyLimit": 30, "used": 3, "remaining": 27, "resetAt": "2026-08-22T00:00:00+09:00" },
  "uploadBlockedUntil": null,
  "notificationSettings": { "like": true, "comment": true, "follow": true, "followeePost": false }
}
```

## PATCH `/users/me` 🔒
```json
{ "nickname": "너구리즈", "bio": "...", "profileImageKey": "profiles/...", "locale": "en",
  "snsLinks": { "instagram": "https://instagram.com/xxx" } }
```
> SNS 링크는 **허용 도메인 화이트리스트**로 검증합니다(instagram·youtube·tiktok·twitter·blog.naver 등).
> 검증 없이 받으면 프로필이 피싱 링크 유포 통로가 됩니다. **Errors** `USER_006`

## POST `/users/me/password` 🔒
`{ "currentPassword": "...", "newPassword": "..." }` → `204`
변경 시 **다른 기기의 리프레시 토큰을 전부 폐기**합니다.

## POST `/users/me/devices` 🔒 — FCM 토큰 등록
```json
{ "deviceId": "550e8400-...", "fcmToken": "cZ1...", "platform": "ANDROID",
  "appVersion": "1.0.0", "pushEnabled": true }
```
→ `204`. **앱 시작마다 호출**해야 합니다. 토큰은 재설치·OS 업데이트 시 바뀌고, 미갱신이 푸시 미수신 1위 원인입니다.

## GET `/users/me/deletion-preview` 🔒
```json
{ "postCount": 128, "mediaCount": 214, "commentCount": 56, "followerCount": 842,
  "visitCount": 47, "rankedPlaceCount": 5, "graceDays": 30 }
```

## DELETE `/users/me` 🔒
```json
{ "contentAction": "KEEP_ANONYMIZED", "reason": "NOT_USING", "password": "abcd1234" }
```
| `contentAction` | 동작 |
|---|---|
| `KEEP_ANONYMIZED` | 게시물 유지, 작성자만 "탈퇴한 사용자"로 표시 (랭킹 점수 유지) |
| `DELETE_ALL` | 게시물·댓글 전부 삭제 |

**200**
```json
{ "deletedAt": "...", "purgeScheduledAt": "...", "contentAction": "KEEP_ANONYMIZED",
  "deletedPosts": 0, "deletedComments": 0, "restorableUntil": "..." }
```
처리: 개인정보(이메일·구글sub·닉네임·프로필) **즉시 제거** → 전체 토큰·FCM 폐기 → 팔로우·좋아요·저장 즉시 삭제 → 30일 후 파기 배치.

## GET `/users/{userId}` 🌐
```json
{ "userId": 88, "nickname": "mina_travel", "profileImageUrl": "...", "bio": "...",
  "grade": "TREE", "popularityScore": 1842,
  "stats": { "postCount": 128, "followerCount": 842, "followingCount": 316, "visitCount": 47 },
  "snsLinks": { "instagram": "..." },
  "isFollowing": false, "isFollowedBy": true, "isMe": false }
```
> `isFollowing` + `isFollowedBy` 를 **함께** 줍니다. 앱이 "맞팔로우" 배지를 그리려면 둘 다 필요합니다.

---

# 4. 팔로우

## POST / DELETE `/users/{userId}/follow` 🔒
```json
{ "following": true, "followerCount": 843 }
```
- 이미 같은 상태여도 **오류 없이 성공** 처리(멱등) — 네트워크 재시도 대응
- 자기 자신 → `FOLLOW_001` / 하루 200회 초과 → `FOLLOW_002`
- 성공 시 상대에게 푸시 알림

## GET `/users/{userId}/followers` · `/followings` 🌐
페이징. 각 항목에 `isFollowing` 포함(내가 그 사람을 팔로우 중인지).

## GET `/feed` 🔒 — 팔로잉 피드
팔로우한 사용자의 게시물만. 응답은 `PostListItem` 페이징.
> 정렬은 `created_at DESC, post_id DESC`. **tie-breaker(post_id)가 없으면 페이지 경계에서 중복·누락이 생깁니다.**

## GET `/follow-suggestions?limit=10` 🔒
같은 지역에 자주 올리는 사용자·인기 작성자 기준.
> 팔로잉이 0명이면 게시물 목록의 팔로잉 가중치가 아무 효과가 없어서 필요한 API입니다.

---

# 5. 지도 (홈 랜딩)

## GET `/map/heatmap` 🌐 ★ 메인 화면

| 파라미터 | 필수 | 기본 | 설명 |
|---|---|---|---|
| `swLat` `swLng` `neLat` `neLng` | ✅ | | 화면에 보이는 사각 범위 |
| `zoom` | ✅ | | 지도 줌 레벨 → 서버가 격자 크기 결정 |
| `period` | ❌ | `REALTIME` | `REALTIME`(최근 1시간) `DAY` `WEEK` `MONTH` `ALL` |

**200**
```json
{
  "period": "REALTIME",
  "gridLevel": 2,
  "fallbackApplied": true,
  "fallbackPeriod": "DAY",
  "calculatedAt": "2026-08-21T14:03:00+09:00",
  "nextRefreshAt": "2026-08-21T14:04:00+09:00",
  "truncated": false,
  "cells": [
    {
      "cellId": 88213,
      "lat": 35.82, "lng": 127.15,
      "postCount": 24, "userCount": 18, "placeCount": 6,
      "intensity": 0.87,
      "lastPostAt": "2026-08-21T14:02:41+09:00",
      "topPlace": { "placeId": 501, "title": "전주 한옥마을", "thumbnailUrl": "..." }
    }
  ]
}
```

| 필드 | 앱이 하는 일 |
|---|---|
| `intensity` | 0.0~1.0 정규화값. **색 농도로 그대로 사용.** 서버가 색을 정하지 않습니다 |
| `lastPostAt` | 최근일수록 강조(반짝임) 애니메이션 |
| `nextRefreshAt` | **이 시각 이후에만 재조회.** 앱이 임의 주기로 폴링하면 서버 부하를 통제할 수 없습니다 |
| `fallbackApplied` | `true`면 데이터가 적어 자동으로 넓은 기간으로 대체됨 → "최근 24시간 기준" 라벨 표시 |
| `truncated` | 격자가 500개를 넘어 잘림 → "더 확대해보세요" 힌트 |

> **실시간의 정의(확정)**: 웹소켓·SSE를 쓰지 않습니다. 서버 1분 캐시 + 앱 60초 폴링.
> 시간당 유입이 수십 건 수준이라 초 단위 갱신은 화면이 바뀌지 않습니다.
> 단, **업로드 직후에는 주기와 무관하게 즉시 재조회**하세요(시연 대응).

**격자 크기**
| `gridLevel` | 반올림 | 실거리 | zoom |
|---|---|---|---|
| 0 | 1.0° | ~110km | 1~6 |
| 1 | 0.1° | ~11km | 7~9 |
| 2 | 0.01° | ~1.1km | 10~12 |
| 3 | 0.001° | ~110m | 13+ |

---

## GET `/map/regions?period=REALTIME` 🌐 — 시도별 활동량
```json
{
  "period": "REALTIME",
  "regions": [
    { "areaCode": 37, "nameKo": "전북특별자치도", "nameEn": "Jeonbuk",
      "centerLat": 35.7175, "centerLng": 127.153,
      "labelLat": 35.72, "labelLng": 127.15,
      "postCount": 84, "userCount": 61, "intensity": 1.0,
      "lastPostAt": "2026-08-21T14:02:41+09:00" }
  ]
}
```
> `labelLat/Lng` 는 **터치 타겟용 좌표**입니다. 충청북도처럼 얇고 긴 지역은 영역 탭이 어려워서,
> 앱이 이 지점에 별도 핀을 놓을 수 있게 서버가 제공합니다.

## GET `/map/photo-markers` 🌐 — 인기 사진 레이어
bbox + zoom. 격자별 대표 게시물의 썸네일을 반환합니다.
```json
{ "markers": [ { "postId": 9001, "lat": 35.82, "lng": 127.15,
                 "thumbnailUrl": "...", "thumbnailRatio": 0.8,
                 "tier": "ON_SITE", "likeCount": 312 } ] }
```
> 썸네일 URL은 집계 단계에서 미리 저장합니다. 조회 시 조인하면 지도 드래그마다 무거워집니다.

## GET `/map/markers` 🌐 — 개별 장소 마커 (상위 200개, `truncated` 포함)

---

# 6. 지역

## GET `/regions` 🌐
```json
[ { "areaCode": 1, "nameKo": "서울", "nameEn": "Seoul", "nameJa": "ソウル", "nameZh": "首尔",
    "centerLat": 37.5665, "centerLng": 126.978, "defaultZoom": 11, "thumbnailUrl": "...",
    "stats": { "placeCount": 4821, "userPlaceCount": 312, "postCount": 1204, "contributorCount": 486 } } ]
```

## GET `/regions/{areaCode}/community` 🌐 — 커뮤니티 홈
**한 화면 = 한 번의 호출.** 나눠 부르면 진입이 느려집니다. (서버 5분 캐시 권장)
```json
{
  "region": { "areaCode": 37, "nameKo": "전북특별자치도", "stats": {} },
  "popularPosts": [ /* PostListItem, period=WEEK */ ],
  "recommendedPlaces": [
    { "placeId": 501, "title": "전주 한옥마을", "thumbnailUrl": "...",
      "recentPostCount": 18, "reason": "HOT_THIS_WEEK" } ],
  "ranking": { "period": "WEEKLY", "updatedAt": "...", "items": [ /* 상위 5 */ ] },
  "popularTags": [ { "tagId": 12, "name": "한옥마을", "postCount": 1204 } ]
}
```
`reason` : `HOT_THIS_WEEK` `TOP_RATED` `HIDDEN_GEM` `EDITOR_PICK` (앱이 다국어 매핑)

## GET `/regions/{areaCode}/tags?limit=30` 🌐 — 태그별 탭
배치가 미리 만든 집계(`region_tag_stats`)를 읽습니다.

---

# 7. 장소

## GET `/places/nearby` 🌐 ★ 재사용 API

> **업로드 시 장소 후보 · 장소 상세의 주변 · 지도 주변 탐색이 모두 이 API를 씁니다.**

| 파라미터 | 필수 | 기본 | 설명 |
|---|---|---|---|
| `lat` `lng` | ✅ | | 기준 좌표 |
| `radius` | ❌ | 500 | m, 최대 20000 |
| `placeType` | ❌ | 전체 | `OFFICIAL` `USER` |
| `contentTypeId` | ❌ | | 12관광지 14문화시설 15축제 28레포츠 38쇼핑 39음식점 |
| `excludePlaceId` | ❌ | | 상세에서 자기 자신 제외 |
| `limit` | ❌ | 5 | 최대 50 |

**200**
```json
{
  "items": [
    { "placeId": 501, "placeType": "OFFICIAL", "title": "전주 한옥마을",
      "thumbnailUrl": "...", "contentTypeId": 12,
      "lat": 35.8150, "lng": 127.1530,
      "distanceMeters": 34,
      "withinVerifyRadius": true, "verifyRadiusMeters": 500,
      "postCount": 1204 }
  ],
  "nearestDistanceMeters": 34
}
```
| 필드 | 설명 |
|---|---|
| `withinVerifyRadius` | `true`면 이 좌표로 올릴 때 **현장 인증 가능** |
| `nearestDistanceMeters` | 결과가 비어도 반환 → "가장 가까운 장소가 3.2km" 표시 + 새 장소 만들기 유도 |

## GET `/places/{placeId}` 🌐 — 장소 상세
```json
{
  "place": {
    "placeId": 501, "placeType": "OFFICIAL", "contentId": 264337, "contentTypeId": 12,
    "title": "전주 한옥마을", "addr1": "전북 전주시 완산구 기린대로 99", "tel": "063-...",
    "homepage": "...", "overview": "...",
    "lat": 35.8150, "lng": 127.1530, "verifyRadiusMeters": 500,
    "region": { "areaCode": 37, "nameKo": "전북특별자치도" }, "sigunguName": "완산구",
    "officialImages": [ { "imageUrl": "...", "thumbnailUrl": "..." } ],
    "event": null
  },
  "stats": { "postCount": 1204, "likeCount": 3412, "visitCount": 861, "viewCount": 20114,
             "tierBreakdown": { "onSite": 812, "locationConfirmed": 341, "noLocation": 51 } },
  "ranking": { "regionRank": 1, "nationalRank": 7, "period": "WEEKLY" },
  "isBookmarked": false, "hasVisited": true,
  "recentPosts": [ /* PostListItem 12 */ ],
  "nearbyPlaces": [ /* NearbyItem 5 */ ]
}
```
`overview`가 비어 있으면 최초 조회 시 TourAPI에서 채워 저장합니다(write-through). 두 번째부터는 DB만 읽습니다.

## POST `/places` 🔒 — 사용자 장소 생성 (숨은 명소)
```json
{ "title": "고창 청보리밭 뷰포인트", "lat": 35.4350, "lng": 126.7020, "addr1": "전북 고창군 ..." }
```
**200**
```json
{ "placeId": 8821, "placeType": "USER", "title": "...", "merged": false,
  "mergedFromExisting": null, "areaCode": 37, "verifyRadiusMeters": 100 }
```
| 규칙 | 내용 |
|---|---|
| 중복 방지 | **반경 100m 안에 같은 이름이 있으면 새로 만들지 않고 기존 장소를 반환** (`merged: true`) |
| 한도 | 1인당 하루 5개 → `PLACE_002` |
| 범위 | 대한민국 좌표 밖 → `PLACE_003` |
| 인증 반경 | 사용자 장소는 100m (관광지는 500m) |

> 중복 방지가 없으면 같은 카페가 10개 생겨서 랭킹·히트맵이 전부 무의미해집니다.

## POST `/places/{placeId}/checkin` 🔒
`{ "lat": 35.8151, "lng": 127.1531 }` → 방문 기록 생성. 같은 날 같은 장소는 1회.
인증 반경 밖 → `PLACE_004`

---

# 8. 게시물 ★ 핵심

## 업로드 3단계

```
① POST /posts/upload-urls        → uploadUrl[], mediaKey[] 받기
② PUT  {uploadUrl}   (S3 직접)   → 파일 바이너리 전송 (서버를 거치지 않음)
③ POST /posts                    → mediaKey + 좌표 + 촬영시각 → Tier 판정 결과 수신
```

## POST `/posts/upload-urls` 🔒
```json
{ "files": [ { "fileName": "IMG_0421.jpg", "contentType": "image/jpeg", "fileSize": 2483920 },
             { "fileName": "VID_0007.mp4", "contentType": "video/mp4", "fileSize": 48211003 } ] }
```
**200**
```json
{ "items": [ { "uploadUrl": "https://...s3...", "mediaKey": "posts/2026/08/21/uuid.jpg",
               "mediaType": "IMAGE", "expiresIn": 300 } ] }
```
| 제한 | 값 |
|---|---|
| 이미지 | JPG·PNG·HEIC·WEBP, 장당 10MB, 최대 10장 |
| 영상 | MP4·MOV, 100MB, 60초 |

**Errors** `MEDIA_001` `MEDIA_002` `USER_002`(약관) `POST_006`(정지)

---

## GET `/posts/tag-suggestions` 🔒 ★ 자동 태그 추천

| 파라미터 | 필수 | 설명 |
|---|---|---|
| `lat` `lng` | ✅ | 촬영 좌표 |
| `placeId` | ❌ | 선택한 장소 |
| `takenAt` | ❌ | 촬영 시각 (진행중 행사 판정에 사용) |

**200**
```json
{
  "suggestions": [
    { "name": "전주",              "source": "AUTO_REGION"   },
    { "name": "완산구",            "source": "AUTO_REGION"   },
    { "name": "한옥마을",          "source": "AUTO_CATEGORY" },
    { "name": "2026 전주비빔밥축제", "source": "AUTO_EVENT", "placeId": 7712,
      "eventStartDate": "2026-10-02", "eventEndDate": "2026-10-05" }
  ]
}
```
| `source` | 출처 |
|---|---|
| `AUTO_REGION` | 좌표 → 시도·시군구 이름 |
| `AUTO_CATEGORY` | 장소 분류 코드 → 태그명 매핑 |
| `AUTO_EVENT` | **행사 기간과 좌표 반경이 둘 다 겹치는** 진행중 행사 |

> `AUTO_EVENT`가 가장 무겁습니다. TourAPI 축제 데이터 적재가 선행되어야 하고,
> `event_start_date <= takenAt <= event_end_date` **그리고** 반경 조건을 모두 만족해야 추천합니다.

---

## POST `/posts` 🔒 ★ 게시물 등록 + Tier 판정

```json
{
  "placeId": 501,
  "areaCode": 37,
  "category": "PHOTO",
  "title": null,
  "content": "야간개장 다녀왔어요. 조명이 정말 예뻤습니다 🏮",
  "media": [
    { "mediaKey": "posts/2026/08/21/uuid.jpg", "mediaType": "IMAGE",
      "width": 1600, "height": 2000, "mediaHash": "e3b0c442...", "sortOrder": 0 }
  ],
  "source": "CAMERA",
  "capturedLat": 35.8151,
  "capturedLng": 127.1531,
  "takenAt": "2026-08-21T14:28:11+09:00",
  "tags": [ { "name": "전주", "source": "AUTO_REGION" },
            { "name": "야경", "source": "USER" } ]
}
```

| 필드 | 필수 | 설명 |
|---|---|---|
| `placeId` | ❌ | 없으면 장소에 묶이지 않은 지역 글 |
| `areaCode` | ✅ | 좌표가 있으면 앱이 서버 판정값을 그대로, 없으면 사용자 선택 |
| `title`/`content` | — | **둘 중 하나는 필수** → 없으면 `POST_001` |
| `media` | ❌ | 0개면 텍스트 전용 게시물 |
| `source` | ✅ | `CAMERA`(앱에서 즉시 촬영) `GALLERY`(앨범) `NONE`(미디어 없음) |
| `capturedLat/Lng` | ❌ | 없으면 `NO_LOCATION` 판정 |
| `takenAt` | ❌ | EXIF 촬영시각. 없으면 `NO_LOCATION` |

> ⚠️ **프론트는 `tier`를 보내지 않습니다.** 서버가 좌표·시각·촬영방식으로 판정합니다(위변조 방지).

### Tier 판정 규칙 (서버)

```
1. 좌표 없음                                        → NO_LOCATION
2. distance = 장소 중심과 촬영지점 거리
3. distance > verifyRadius (관광지 500m / 사용자장소 100m) → NO_LOCATION
4. source=CAMERA AND |now - takenAt| <= 10분        → ON_SITE
5. takenAt 이 30일 이내                             → LOCATION_CONFIRMED
6. 그 외                                            → NO_LOCATION
```

| Tier | 배지 | 랭킹 가중치 | 방문 기록 | 히트맵 |
|---|---|---|---|---|
| `ON_SITE` | 현장 인증 | ×3.0 | 생성 | 반영 |
| `LOCATION_CONFIRMED` | 위치 확인 | ×1.8 | 생성 | 반영 |
| `NO_LOCATION` | 위치 미확인 | **0** | 없음 | 제외 |

### 업로드 제한
| 규칙 | 값 | 위반 |
|---|---|---|
| 일일 게시물 | 30개 | `POST_003` |
| 동일 장소 하루 | 3개 | `POST_004` |
| 동일 미디어 해시 중복 | 차단 | `POST_005` |
| 약관 미동의 | 차단 | `USER_002` |
| 신고 누적 정지 | 차단 | `POST_006` |

**201**
```json
{
  "postId": 9001,
  "tier": "ON_SITE",
  "tierLabel": { "ko": "현장 인증", "en": "On-site verified" },
  "countsForRanking": true,
  "distanceMeters": 34,
  "place": { "placeId": 501, "title": "전주 한옥마을", "areaCode": 37 },
  "visitCreated": true,
  "media": [ { "mediaId": 30011, "mediaUrl": "...", "thumbnailUrl": "...",
               "mediaType": "IMAGE", "processStatus": "READY", "width": 1600, "height": 2000 } ],
  "tags": [ { "tagId": 12, "name": "전주", "source": "AUTO_REGION" } ],
  "quota": { "dailyLimit": 30, "used": 4, "remaining": 26 },
  "createdAt": "2026-08-21T14:31:02+09:00"
}
```
> 영상은 썸네일 추출이 필요해 `processStatus: "PROCESSING"` 으로 먼저 응답될 수 있습니다.
> 앱은 `READY`가 아니면 "처리중"으로 표시합니다.

---

## GET `/posts` 🌐 — 게시물 목록

| 파라미터 | 기본 | 설명 |
|---|---|---|
| `areaCode` | ❌ | 없으면 전국 |
| `placeId` `tag` `category` `keyword` | ❌ | 필터 |
| `hasMedia` | ❌ | `true`면 사진·영상 있는 것만 |
| `period` | `ALL` | `DAY`(24시간) `WEEK` `MONTH` `ALL` |
| `sort` | `RECOMMENDED` | `RECOMMENDED` `LATEST` `POPULAR` `MOST_COMMENTED` |

**`PostListItem`**
```json
{
  "postId": 9001,
  "category": "PHOTO",
  "title": null,
  "contentPreview": "야간개장 다녀왔어요. 조명이 정말...",
  "thumbnailUrl": "...",
  "thumbnailRatio": 0.8,
  "mediaCount": 3, "videoCount": 0,
  "tier": "ON_SITE",
  "author": { "userId": 88, "nickname": "mina_travel", "profileImageUrl": "...",
              "grade": "TREE", "isFollowing": true },
  "place": { "placeId": 501, "title": "전주 한옥마을" },
  "tags": [ "전주", "한옥마을" ],
  "likeCount": 312, "commentCount": 28, "viewCount": 3021,
  "isLiked": false, "isBookmarked": false,
  "createdAt": "2026-08-21T14:31:02+09:00"
}
```
| 필드 | 왜 필요한가 |
|---|---|
| `thumbnailRatio` | **가변 높이 격자(핀터레스트식) 필수.** 없으면 앱이 이미지를 다 받은 뒤에야 높이를 알아 레이아웃이 튑니다 |
| `author.isFollowing` | "팔로잉" 배지 표시 — 가중치가 적용된 이유를 사용자가 알 수 있게 |

### `sort=RECOMMENDED` 의 팔로잉 가중치
- 팔로우한 사용자의 게시물이 위쪽에 노출됩니다.
- **개인화는 첫 페이지에만 적용합니다.** 전체에 적용하면 사용자마다 순서가 달라 목록 캐시를 전혀 못 씁니다.
- 비로그인이면 가중치 없이 공통 정렬입니다.

## GET `/posts/{postId}` 🌐
`PostListItem` + `content`(전문), `media[]`(전체), `capturedLat/Lng`, `distanceMeters`, `takenAt`, `isMine`, `updatedAt`

## PATCH `/posts/{postId}` ✋
`title` `content` `category` `tags` `media` 수정 가능.
> **위치와 Tier는 수정할 수 없습니다.** 인증 체계를 보호해야 랭킹이 신뢰를 얻습니다.

## DELETE `/posts/{postId}` ✋ → `204` (논리 삭제. S3 객체는 30일 후 배치)

## POST / DELETE `/posts/{postId}/like` 🔒
`{ "liked": true, "likeCount": 313 }` — 작성자에게 푸시(자기 글 제외)

## POST `/posts/{postId}/reports` 🔒
`{ "reason": "NOT_THE_PLACE", "detail": "..." }` → `201`
사유: `INAPPROPRIATE` `COPYRIGHT` `NOT_THE_PLACE` `SPAM` `OTHER`
**신고 3회 누적 → 자동 블라인드** + 작성자 24시간 업로드 정지. 중복 신고 → `REPORT_001`

---

# 9. 댓글

## GET `/posts/{postId}/comments` 🌐
```json
{ "content": [
  { "commentId": 771, "content": "야간개장 몇 시까지 하나요?",
    "author": { "userId": 91, "nickname": "jun_photo", "profileImageUrl": "...", "grade": "SPROUT" },
    "likeCount": 3, "isLiked": false, "isMine": false,
    "replyCount": 2,
    "replies": [ { "commentId": 772, "content": "...", "author": {} } ],
    "createdAt": "..." } ] }
```
> 대댓글은 **1단계까지**. 자식 댓글은 부모 목록의 id를 모아 `IN` 절로 **한 번에** 조회합니다(N+1 방지).

## POST `/posts/{postId}/comments` 🔒
`{ "content": "...", "parentCommentId": null }` — 1~1000자
2단계 초과 → `COMMENT_002`

## DELETE `/comments/{commentId}` ✋
자식 댓글이 있으면 행은 남기고 **"삭제된 댓글입니다"로 표시**합니다(자식 유지).

---

# 10. 방문 · 저장 · 알림

## GET `/visits` 🔒 · GET `/visits/stats` 🔒
```json
{ "visitedRegionCount": 8, "totalRegionCount": 17, "visitedPlaceCount": 47,
  "byRegion": [ { "areaCode": 37, "nameKo": "전북", "placeCount": 12 } ] }
```

## GET `/bookmarks?targetType=POST|PLACE` 🔒

## GET `/notifications` 🔒
```json
{ "content": [
  { "notificationId": 5521, "type": "POST_LIKE",
    "actor": { "userId": 91, "nickname": "jun_photo", "profileImageUrl": "..." },
    "targetType": "POST", "targetId": 9001,
    "messageKey": "notification.post_like",
    "messageParams": { "nickname": "jun_photo" },
    "thumbnailUrl": "...", "isRead": false, "createdAt": "..." } ] }
```
`type`: `POST_LIKE` `COMMENT` `COMMENT_REPLY` `FOLLOW` `FOLLOWEE_POST` `EVENT_NEARBY` `GRADE_UP` `SYSTEM`

> **서버는 완성된 문장을 만들지 않습니다.** `messageKey` + `messageParams` 를 주고
> 앱이 언어에 맞게 조립합니다. 다국어 앱에서 서버가 문장을 만들면 언어를 바꿀 수 없습니다.

## GET `/notifications/unread-count` 🔒 → `{ "count": 3 }`
호출 빈도가 매우 높습니다. 서버 캐시 필요.

## PATCH `/notifications/read` 🔒
`{ "notificationIds": [5521], "all": false }` → `204`

---

# 11. 랭킹 · 추천

## GET `/rankings/places` 🌐

| 파라미터 | 기본 | 설명 |
|---|---|---|
| `areaCode` | 전국 | 생략 시 전국 |
| `period` | `WEEKLY` | `DAILY` `WEEKLY` `MONTHLY` `ALL_TIME` |
| `theme` | `ALL` | `ALL` `HIDDEN_GEM` `NATURE` `CULTURE` `FOOD` `FESTIVAL` |
| `limit` | 20 | 최대 100 |

```json
{ "areaCode": 37, "period": "WEEKLY", "theme": "ALL", "calculatedAt": "...",
  "items": [
    { "rank": 1, "previousRank": 3, "rankChange": "UP",
      "placeId": 501, "title": "전주 한옥마을", "thumbnailUrl": "...",
      "placeType": "OFFICIAL", "contentTypeId": 12,
      "score": 1846.60, "postCount": 1204, "likeCount": 3412, "visitCount": 861,
      "region": { "areaCode": 37, "nameEn": "Jeonbuk" } } ] }
```
`rankChange`: `UP` `DOWN` `SAME` `NEW`(`previousRank: null`)

**점수 공식** (심사 설명용 — 프론트에도 공유)
```
score = T1게시물 × 3.0 + T2게시물 × 1.8 + 좋아요 × 1.0
      + 댓글 × 1.5 + 방문자 × 2.0 + 조회수 × 0.05
※ T3(위치 미확인)는 0점
※ 자기 게시물에 누른 좋아요는 제외
```
- 동점 시 `postCount` → `placeId` 오름차순 (**결정적 정렬 필수.** 없으면 새로고침마다 순위가 흔들립니다)
- T1·T2 게시물이 1개 이상인 장소만 진입
- **조회 시 계산하지 않습니다.** 배치가 만든 스냅샷만 읽습니다.

## GET `/rankings/posts` 🌐 — 인기 게시물 (커뮤니티 "이번주 인기 사진")
`areaCode` `period`(`DAY` `WEEK` `MONTH` `ALL`) `hasMedia` `limit`

## GET `/recommendations/places?areaCode=37&limit=5` 🌐
데이터가 부족하면 운영자 지정(`is_featured`) 장소로 채웁니다. **시연 때 빈 화면이 최악입니다.**

---

# 12. 이벤트 (지자체 행사)

## GET `/events` 🌐
`status`(`ONGOING` `UPCOMING` `ENDED`) `areaCode` `page` `size`
```json
{ "content": [
  { "placeId": 7712, "title": "진안 홍삼축제",
    "eventStartDate": "2026-09-28", "eventEndDate": "2026-10-06",
    "status": "ONGOING", "dDay": -3,
    "eventPlace": "진안군 진안읍 홍삼로", "organizer": "진안군청",
    "thumbnailUrl": "...", "areaCode": 37, "regionName": "전북특별자치도",
    "postCount": 617 } ] }
```
`dDay`: 음수는 종료까지 남은 일수, 양수는 시작까지 남은 일수, 0은 오늘

## GET `/events/nearby?lat&lng&radius` 🌐 — 주변 장소 쿼리를 재사용
## GET `/events/{placeId}` 🌐 — 행사 상세 + 참여 게시물 + `autoTagName`(업로드 시 자동 선택될 태그명)

---

# 13. 검색

## GET `/search?q=한옥&type=ALL&page=1` 🌐
`type`: `ALL` `PLACE` `POST` `USER` `TAG`
```json
{ "query": "한옥",
  "places": { "totalElements": 48, "content": [] },
  "posts":  { "totalElements": 312, "content": [] },
  "users":  { "totalElements": 4, "content": [] },
  "tags":   { "totalElements": 6, "content": [] } }
```
> 한글 부분어 검색은 `ngram` FULLTEXT 인덱스를 씁니다. `LIKE '%한옥%'` 는 인덱스를 못 탑니다.

## GET `/search/popular?limit=10` 🌐

---

# 14. 관리자

## POST `/admin/sync/{type}` ⚙️
`type`: `places` `festivals` `place-details` `rankings` `heatmap` `stats` `counters` `popularity` `tag-stats`
```json
{ "areaCodes": [37], "contentTypeIds": [12, 15], "full": false }
```
→ `202` `{ "syncId": 77 }` (비동기. `sync_logs` 에서 진행상황 확인)
> **시연 직전에 랭킹·히트맵을 강제로 최신화할 때 유용합니다.**

## DELETE `/admin/users/{userId}` ⚙️ — 유예 없이 즉시 파기, 콘텐츠는 항상 `DELETE_ALL`

---

# 15. 프론트엔드 체크리스트

- [ ] 앱 실행 시 **지도가 첫 화면**. 로그인 화면으로 보내지 않는다
- [ ] `AUTH_006` 수신 시에만 로그인 시트를 띄운다
- [ ] 에러 문구는 `error.code` 로 i18n 매핑 (`message` 직접 표시 금지)
- [ ] 앱 시작마다 `POST /users/me/devices` 로 FCM 토큰 갱신
- [ ] 업로드 시 **EXIF의 GPS와 촬영시각을 반드시 전송** — 없으면 T3로 떨어져 랭킹·방문기록에 반영되지 않음
- [ ] 앱 카메라 즉시 촬영은 `source: "CAMERA"`, 앨범은 `"GALLERY"` — **이 구분이 현장 인증의 핵심 입력값**
- [ ] `tier` 를 앱이 계산해서 보내지 않는다 (서버가 판정)
- [ ] 히트맵은 `nextRefreshAt` 이후에만 재조회. 단 **업로드 직후에는 즉시 재조회**
- [ ] 지도 드래그는 300ms 디바운스
- [ ] 목록은 `thumbnailRatio` 로 가변 높이 격자를 구성
- [ ] 커뮤니티 홈은 `/regions/{areaCode}/community` 한 번만 호출
- [ ] 페이지네이션 무한스크롤 시 응답의 `hasNext` 를 신뢰 (직접 계산 금지)
- [ ] 계정 삭제 진입점을 마이페이지에 둔다. 삭제 전 `deletion-preview` 표시
