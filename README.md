# SnapHere

관광데이터활용공모전 프로젝트 레포입니다.

**사진으로 채우는 대한민국 실시간 여행 지도.**
GPS가 담긴 사진을 올리면 서버가 위치 신뢰도를 판정하고, 그 데이터로 지도 히트맵·장소 랭킹·지역 커뮤니티가 굴러갑니다.

---

## 폴더 구조

```
SnapHere/
├── CLAUDE.md                  개발 지침 (Claude Code가 자동으로 읽습니다)
├── docs/
│   ├── 01_요구사항명세서.md      요구사항 201건
│   ├── 02_기능명세서.md          화면별 동작·전환
│   ├── 03_API명세서.md      ★  프론트엔드에 전달하는 문서
│   ├── 04_ERD.md                테이블 관계도
│   ├── 03_schema.sql        ★  DB 원본 (MySQL 8에서 실행 검증됨)
│   ├── 05_모바일목업.html        화면 19개 목업
│   └── 요구사항_기능_명세서.xlsx
├── scripts/
│   ├── setup.ps1              Windows 세팅 자동화
│   └── setup.sh               WSL/Linux 세팅 자동화
├── docker-compose.yml         MySQL 을 설치 없이 띄우기
├── .vscode/                   권장 확장 · 실행 구성
└── backend/                   Spring Boot
    ├── SETUP.md           ★  실행 순서 — 여기부터 보세요
    ├── build.gradle
    ├── gradlew / gradlew.bat
    └── src/main/...
```

## 시작하기

JDK 17만 깔고 스크립트를 돌리면 나머지는 자동입니다.

```powershell
# Windows
powershell -ExecutionPolicy Bypass -File scripts\setup.ps1
```
```bash
# WSL / Linux / macOS
bash scripts/setup.sh
```

그 다음
```bash
cd backend && ./gradlew bootRun
```
→ Swagger: http://localhost:8080/swagger-ui/index.html

MySQL을 직접 설치하고 싶지 않으면 `docker compose up -d` 로 띄우면 됩니다 (스키마 자동 적용).

자세한 순서와 문제 해결은 **`backend/SETUP.md`**, 프론트엔드는 **`docs/03_API명세서.md`** 를 봅니다.

## 기술 스택

| 영역 | 선택 |
|---|---|
| 백엔드 | Java 17 · Spring Boot 3.3 · Gradle |
| DB | MySQL 8 (공간 인덱스) |
| 인증 | 자체 JWT · 아이디/비밀번호(BCrypt) + 구글 |
| 저장소 | AWS S3 (Presigned URL) |
| 푸시 | FCM |
| 외부 데이터 | 한국관광공사 TourAPI (배치 적재) |

## 핵심 개념 — 위치 신뢰도 3단계

사진의 위치가 얼마나 믿을 수 있는지 **서버가** 판정합니다. 앱이 보낸 값은 신뢰하지 않습니다.

| 등급 | 조건 | 랭킹 반영 |
|---|---|---|
| **현장 인증** | 앱 카메라 촬영 + 10분 이내 + 장소 반경 안 | ×3.0 |
| **위치 확인** | EXIF 위치 + 30일 이내 + 장소 반경 안 | ×1.8 |
| **위치 미확인** | 위치 없음 또는 반경 밖 | **0 (제외)** |

이 판정이 랭킹·히트맵·방문기록의 신뢰도를 전부 지탱합니다.

## ⚠️ 커밋하지 말 것

DB 비밀번호 · JWT secret · TourAPI 키 · AWS 키 · Firebase 인증 파일.
`application-local.yml` 과 `.env` 는 `.gitignore` 대상입니다.
