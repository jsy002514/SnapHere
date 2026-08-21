#!/usr/bin/env bash
# ============================================================
#  SnapHere 백엔드 개발환경 세팅 (WSL Ubuntu / Linux / macOS)
#
#  실행:  bash scripts/setup.sh
# ============================================================
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BACKEND="$ROOT/backend"

c_cyan=$'\033[36m'; c_green=$'\033[32m'; c_yellow=$'\033[33m'; c_red=$'\033[31m'; c_off=$'\033[0m'
step() { printf "\n%s[%s] %s%s\n" "$c_cyan" "$1" "$2" "$c_off"; }
ok()   { printf "  %sOK%s   %s\n" "$c_green" "$c_off" "$1"; }
warn() { printf "  %s!!%s   %s\n" "$c_yellow" "$c_off" "$1"; }
die()  { printf "  %sX%s    %s\n" "$c_red" "$c_off" "$1"; exit 1; }

echo "SnapHere 백엔드 세팅"
echo "루트: $ROOT"

# ── 1. JDK 17 ───────────────────────────────────────────────
step 1 "JDK 확인"
if command -v java >/dev/null 2>&1; then
  ver=$(java -version 2>&1 | head -1 | sed -E 's/.*version "([0-9]+).*/\1/')
  if [ "$ver" = "17" ]; then ok "Java 17"
  elif [ "$ver" -gt 17 ] 2>/dev/null; then warn "Java $ver 입니다. 팀 표준은 17 — 버전이 갈리면 빌드가 달라집니다"
  else warn "Java $ver 은 Spring Boot 3.x 에 부족합니다"; fi
else
  warn "JDK 가 없습니다. 설치 후 다시 실행하세요:"
  echo "    sudo apt update && sudo apt install -y openjdk-17-jdk"
  exit 1
fi

# ── 2. Gradle Wrapper ───────────────────────────────────────
step 2 "Gradle Wrapper"
WJAR="$BACKEND/gradle/wrapper/gradle-wrapper.jar"
if [ -f "$WJAR" ]; then
  ok "이미 있습니다 ($(du -h "$WJAR" | cut -f1))"
else
  got=0
  mkdir -p "$BACKEND/gradle/wrapper"

  # 방법 A: Spring Initializr — 공식 gradlew / gradlew.bat / jar 한 번에
  if command -v curl >/dev/null 2>&1 && command -v unzip >/dev/null 2>&1; then
    echo "  Spring Initializr 에서 받는 중..."
    tmp=$(mktemp -d)
    if curl -fsSL -m 60 \
        "https://start.spring.io/starter.zip?type=gradle-project&bootVersion=3.3.4&javaVersion=17" \
        -o "$tmp/s.zip" 2>/dev/null; then
      if unzip -oq "$tmp/s.zip" "gradlew" "gradlew.bat" "gradle/wrapper/*" -d "$BACKEND" 2>/dev/null; then
        chmod +x "$BACKEND/gradlew"; got=1
        ok "공식 Wrapper 설치 완료 (gradlew / gradlew.bat / jar)"
      fi
    fi
    rm -rf "$tmp"
  fi

  # 방법 B: wrapper jar 만
  if [ "$got" -eq 0 ]; then
    echo "  GitHub 에서 wrapper jar 만 받는 중..."
    if curl -fsSL -m 60 \
        "https://raw.githubusercontent.com/gradle/gradle/v8.10.2/gradle/wrapper/gradle-wrapper.jar" \
        -o "$WJAR" 2>/dev/null; then
      got=1; ok "wrapper jar 설치 완료 (gradlew 는 저장소의 간소화 버전 사용)"
    fi
  fi

  # 방법 C: gradle 이 이미 있으면 그걸로 생성
  if [ "$got" -eq 0 ] && command -v gradle >/dev/null 2>&1; then
    (cd "$BACKEND" && gradle wrapper --gradle-version 8.10.2 >/dev/null 2>&1) && got=1 \
      && ok "로컬 gradle 로 Wrapper 생성"
  fi

  [ "$got" -eq 1 ] || die "Wrapper 를 받지 못했습니다. 네트워크를 확인하거나 'sudo apt install -y gradle' 후 다시 실행하세요."
fi
chmod +x "$BACKEND/gradlew" 2>/dev/null || true

# ── 3. 개인 설정 파일 ───────────────────────────────────────
step 3 "application-local.yml"
RES="$BACKEND/src/main/resources"
LOCAL="$RES/application-local.yml"
if [ -f "$LOCAL" ]; then
  ok "이미 있습니다 (건드리지 않습니다)"
else
  read -r -s -p "  MySQL root 비밀번호 (Enter 치면 비워둠): " DBPW; echo
  SECRET=$(LC_ALL=C tr -dc 'A-Za-z0-9' </dev/urandom | head -c 72)
  cat > "$LOCAL" <<YML
# 이 파일은 .gitignore 대상입니다. 절대 커밋하지 마세요.
spring:
  datasource:
    username: root
    password: ${DBPW}

app:
  jwt:
    secret: ${SECRET}
YML
  ok "생성 완료 (JWT secret 은 랜덤 72자로 자동 생성)"
fi

# ── 4. MySQL 스키마 ─────────────────────────────────────────
step 4 "MySQL 스키마"
if ! command -v mysql >/dev/null 2>&1; then
  warn "mysql 클라이언트가 없습니다. 아래 중 하나로 진행하세요."
  echo "    (A) Docker:  docker compose up -d   그다음 이 스크립트 재실행"
  echo "    (B) sudo apt install -y mysql-server && sudo service mysql start"
else
  sudo service mysql start >/dev/null 2>&1 || true
  echo "  스키마를 넣습니다 (비밀번호를 물어봅니다)"
  if mysql -u root -p < "$ROOT/docs/03_schema.sql" 2>/dev/null; then
    cnt=$(mysql -u root -p -N -B -e \
      "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='tourlab';" 2>/dev/null || echo 0)
    [ "$cnt" = "27" ] && ok "테이블 27개 확인" || warn "테이블 수가 $cnt 입니다 (기대값 27)"
  else
    warn "스키마 실행 실패. MySQL 이 켜져 있는지 확인하세요: sudo service mysql start"
  fi
fi

# ── 5. 빌드 ─────────────────────────────────────────────────
step 5 "빌드"
if (cd "$BACKEND" && ./gradlew build -x test --console=plain); then
  ok "BUILD SUCCESSFUL"
else
  warn "빌드 실패 — 위 로그를 확인하세요"
fi

echo
echo "다음 단계"
echo "  cd backend && ./gradlew bootRun"
echo "  http://localhost:8080/swagger-ui/index.html"
