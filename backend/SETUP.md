# 백엔드 실행 순서

> 목표: **서버가 뜨고 Swagger가 열리고, 가입→로그인→내 정보까지 통과**하는 것.

---

## 빠른 길 — 스크립트 한 번

JDK 17만 미리 깔면 나머지는 스크립트가 합니다.
(Wrapper 내려받기 · `application-local.yml` 생성 · 스키마 적용 · 빌드)

**Windows PowerShell**
```powershell
cd D:\snaphere_folder\SnapHere
powershell -ExecutionPolicy Bypass -File scripts\setup.ps1
```

**WSL Ubuntu / Linux / macOS**
```bash
cd ~/SnapHere        # 또는 /mnt/d/snaphere_folder/SnapHere
bash scripts/setup.sh
```

> JDK가 없으면 스크립트가 설치 명령을 알려주고 멈춥니다. 설치 후 다시 실행하세요.

아래는 스크립트가 무엇을 하는지, 그리고 수동으로 할 때의 순서입니다.

---

## 1. JDK 17

| 환경 | 설치 |
|---|---|
| Windows | `winget install EclipseAdoptium.Temurin.17.JDK` 또는 https://adoptium.net (Temurin 17 LTS MSI) |
| WSL Ubuntu | `sudo apt update && sudo apt install -y openjdk-17-jdk` |

**확인**
```bash
java -version      # openjdk version "17.0.x"
```
> 21이 나와도 동작하지만 **팀 전체를 17로** 맞추세요. 버전이 갈리면 빌드 결과가 달라집니다.

---

## 2. MySQL 8 + 스키마

### 방법 A — Docker (가장 간단, 설치 없음)

Docker Desktop이 있으면 이게 낫습니다. 스키마까지 자동 적용됩니다.

```bash
docker compose up -d
docker compose logs -f mysql     # "ready for connections" 나오면 완료
```
- root 비밀번호: `snaphere1234`
- DB: `tourlab`, 포트 `3306`
- 스키마는 **볼륨이 비어 있을 때만** 자동 적용됩니다. `schema.sql`을 고친 뒤 다시 넣으려면 `docker compose down -v`

### 방법 B — 직접 설치

**WSL Ubuntu**
```bash
sudo apt install -y mysql-server
sudo service mysql start
sudo mysql -e "ALTER USER 'root'@'localhost' IDENTIFIED WITH mysql_native_password BY '본인비밀번호'; FLUSH PRIVILEGES;"
mysql -u root -p < docs/03_schema.sql
```
> ⚠️ WSL은 재부팅하면 MySQL이 자동으로 안 켜집니다. 매번 `sudo service mysql start`
> 자동화: `echo 'sudo service mysql start >/dev/null 2>&1' >> ~/.bashrc`

**Windows** — MySQL Installer로 8.0 설치 → Workbench에서 `docs/03_schema.sql` 실행

**확인**
```bash
mysql -u root -p -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='tourlab';"   # 27
mysql -u root -p -e "SELECT COUNT(*) FROM tourlab.regions;"                                          # 17
```

---

## 3. Gradle Wrapper

`gradlew`·`gradlew.bat`·`gradle-wrapper.properties`는 저장소에 이미 있습니다.
**`gradle-wrapper.jar`(43KB 바이너리)만 없습니다.** 인터넷에서 받아야 합니다.

스크립트를 쓰면 자동입니다. 수동으로 하려면:

```bash
cd backend
curl -sL "https://start.spring.io/starter.zip?type=gradle-project&bootVersion=3.3.4&javaVersion=17" -o _tmp.zip
unzip -oq _tmp.zip "gradlew" "gradlew.bat" "gradle/*" -d .
rm _tmp.zip && chmod +x gradlew
```

또는 Gradle이 이미 있으면:
```bash
cd backend && gradle wrapper --gradle-version 8.10.2
```

**확인**
```bash
./gradlew --version     # 첫 실행은 배포판 내려받아서 1~2분
```

> `gradle-wrapper.jar`는 **커밋해야 합니다.** 루트 `.gitignore`에 예외 처리해뒀습니다.
> 저장소의 `gradlew`는 간소화 버전입니다(핵심 동작은 같음). 스크립트가 공식 버전으로 교체합니다.

---

## 4. 개인 설정 파일

```bash
cd backend/src/main/resources
cp application-local.yml.example application-local.yml
```
DB 비밀번호와 JWT secret(32바이트 이상)을 채웁니다.
**`.gitignore` 대상입니다. 커밋하지 마세요.**

Docker를 쓰면 비밀번호는 `snaphere1234`입니다.

---

## 5. 실행

```bash
cd backend
./gradlew bootRun
```

### 확인 1 — 기동 로그
```
Started SnaphereApplication in 4.xxx seconds
```

### 확인 2 — Swagger
http://localhost:8080/swagger-ui/index.html
→ `01. 인증`, `02. 사용자`, `03. 지역` 세 그룹이 보이면 성공

### 확인 3 — DB 연결
```bash
curl -s http://localhost:8080/api/v1/regions
```
```json
{"success":true,"data":[{"areaCode":1,"nameKo":"서울",...
```

### 확인 4 — 가입 → 로그인 → 내 정보
```bash
curl -s -X POST http://localhost:8080/api/v1/auth/signup \
  -H 'Content-Type: application/json' \
  -d '{"loginId":"tester01","password":"abcd1234","passwordConfirm":"abcd1234","nickname":"테스터","email":"t@example.com","termsAgreed":true}'

# 응답의 accessToken 을 넣어서
curl -s http://localhost:8080/api/v1/users/me -H "Authorization: Bearer <accessToken>"
```
`"nickname":"테스터"`가 나오면 **S1~S3 완료**입니다.

> VS Code에 REST Client 확장이 있으면 `docs/http/auth.http`를 열어 클릭만으로 12가지를
> 순서대로 확인할 수 있습니다. 실패해야 정상인 케이스(권한 없음·잘못된 비밀번호)도 들어 있습니다.

### 확인 5 — 단위 테스트
```bash
./gradlew test     # 5개 통과
```

---

## 막히는 곳

| 증상 | 원인 · 해결 |
|---|---|
| `gradle-wrapper.jar 가 없습니다` | 3단계를 안 했습니다. `bash scripts/setup.sh` |
| `Schema-validation: missing table` | 스키마를 넣지 않았습니다 (2단계) |
| `Schema-validation: wrong column type` | 엔티티-테이블 타입 불일치. **`application.yml`의 `ddl-auto`를 `none`으로 바꾸고 어떤 컬럼인지 알려주세요** |
| `Access denied for user 'root'` | `application-local.yml`의 비밀번호 확인 |
| `Communications link failure` | MySQL이 꺼져 있습니다. `sudo service mysql start` 또는 `docker compose up -d` |
| `app.jwt.secret 이 너무 짧습니다` | 32바이트 이상으로 |
| `Permission denied: ./gradlew` | `chmod +x gradlew` |
| Swagger 404 | 경로에 `index.html`이 필요합니다 |
| 포트 8080 사용 중 | `application.yml`의 `server.port` 변경 |
| 빌드가 유난히 느림 (WSL) | 프로젝트가 `/mnt/d/`에 있으면 파일 IO가 느립니다. Linux 홈으로 옮기면 훨씬 빨라집니다 |

---

## 지금 구현된 것 / 안 된 것

**동작합니다 (S1~S3)**
- 공통 응답 형식 · 예외 처리 · 에러 코드 42종
- JWT 발급·검증·로테이션, `@AuthUser` 주입, CORS, Swagger
- 회원가입 / 아이디·비밀번호 로그인 / 토큰 재발급 / 로그아웃 / 전체 기기 로그아웃
- 내 정보 조회·수정, 비밀번호 변경(다른 기기 세션 종료), 약관 동의, 알림 설정
- 계정 삭제 (개인정보 즉시 파기 + 30일 유예 + 복구 키)
- SNS 링크 허용 도메인 검증
- 지역 목록 조회

**스텁 / 미구현**
- 구글 로그인 — `GoogleTokenVerifier`. 구글 클라이언트 ID를 받아야 `aud` 검증을 넣을 수 있습니다
- 로그인 실패 횟수 차단 (AUTH_007)
- S4~S14: 장소 · 게시물 · 위치 신뢰도 판정 · 히트맵 · 랭킹 · 알림 · 이벤트
