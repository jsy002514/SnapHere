# ============================================================
#  SnapHere 백엔드 개발환경 세팅 (Windows PowerShell)
#
#  실행:  powershell -ExecutionPolicy Bypass -File scripts\setup.ps1
# ============================================================
# 콘솔 한글 출력. .ps1 은 UTF-8 BOM 으로 저장해야 PowerShell 5.1 이 제대로 읽는다.
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch { }
$ErrorActionPreference = "Stop"
$ProgressPreference    = "SilentlyContinue"

$Root    = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$Backend = Join-Path $Root "backend"

function Step($n, $msg) { Write-Host "`n[$n] $msg" -ForegroundColor Cyan }
function Ok($msg)       { Write-Host "  OK   $msg" -ForegroundColor Green }
function Warn($msg)     { Write-Host "  !!   $msg" -ForegroundColor Yellow }
function Die($msg)      { Write-Host "  X    $msg" -ForegroundColor Red; exit 1 }

Write-Host "SnapHere 백엔드 세팅" -ForegroundColor White
Write-Host "루트: $Root"

# ── 1. JDK 17 확인 ──────────────────────────────────────────
Step 1 "JDK 확인"
$javaOk = $false
try {
    $v = (& java -version 2>&1) -join " "
    if ($v -match 'version "(\d+)') {
        $major = [int]$Matches[1]
        if ($major -eq 17) { Ok "Java $major"; $javaOk = $true }
        elseif ($major -gt 17) { Warn "Java $major 입니다. 팀 표준은 17 — 팀원과 버전이 갈리면 빌드가 달라집니다"; $javaOk = $true }
        else { Warn "Java $major 은 Spring Boot 3.x 에 부족합니다" }
    }
} catch { }

if (-not $javaOk) {
    Warn "JDK 17 이 없습니다. 아래 중 하나로 설치하세요."
    Write-Host "    winget install EclipseAdoptium.Temurin.17.JDK"
    Write-Host "    또는 https://adoptium.net 에서 Temurin 17 (LTS) MSI"
    Write-Host "  설치 후 새 터미널에서 이 스크립트를 다시 실행하세요."
    exit 1
}

# ── 2. Gradle Wrapper 내려받기 ──────────────────────────────
Step 2 "Gradle Wrapper"
$wrapperJar = Join-Path $Backend "gradle\wrapper\gradle-wrapper.jar"

if (Test-Path $wrapperJar) {
    Ok "이미 있습니다 ($([math]::Round((Get-Item $wrapperJar).Length/1KB))KB)"
} else {
    $tmp = Join-Path $env:TEMP "snaphere-wrapper.zip"
    $got = $false

    # 방법 A: Spring Initializr — 공식 gradlew / gradlew.bat / jar 을 한 번에
    try {
        Write-Host "  Spring Initializr 에서 받는 중..."
        Invoke-WebRequest -Uri "https://start.spring.io/starter.zip?type=gradle-project&bootVersion=3.3.4&javaVersion=17" `
                          -OutFile $tmp -TimeoutSec 60
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        $zip = [System.IO.Compression.ZipFile]::OpenRead($tmp)
        foreach ($e in $zip.Entries) {
            if ($e.FullName -in @("gradlew","gradlew.bat","gradle/wrapper/gradle-wrapper.jar","gradle/wrapper/gradle-wrapper.properties")) {
                $dest = Join-Path $Backend ($e.FullName -replace "/","\")
                New-Item -ItemType Directory -Force -Path (Split-Path $dest) | Out-Null
                [System.IO.Compression.ZipFileExtensions]::ExtractToFile($e, $dest, $true)
            }
        }
        $zip.Dispose(); Remove-Item $tmp -Force
        $got = $true
        Ok "공식 Wrapper 설치 완료 (gradlew / gradlew.bat / jar)"
    } catch { Warn "Initializr 실패: $($_.Exception.Message)" }

    # 방법 B: Gradle 저장소의 wrapper jar 만
    if (-not $got) {
        try {
            Write-Host "  GitHub 에서 wrapper jar 만 받는 중..."
            New-Item -ItemType Directory -Force -Path (Split-Path $wrapperJar) | Out-Null
            Invoke-WebRequest -Uri "https://raw.githubusercontent.com/gradle/gradle/v8.10.2/gradle/wrapper/gradle-wrapper.jar" `
                              -OutFile $wrapperJar -TimeoutSec 60
            $got = $true
            Ok "wrapper jar 설치 완료 (gradlew 는 저장소의 간소화 버전을 사용)"
        } catch { Warn "GitHub 실패: $($_.Exception.Message)" }
    }

    if (-not $got) { Die "Wrapper 를 받지 못했습니다. 네트워크·사내 프록시를 확인하세요." }
}

# ── 3. 개인 설정 파일 ───────────────────────────────────────
Step 3 "application-local.yml"
$resDir  = Join-Path $Backend "src\main\resources"
$local   = Join-Path $resDir "application-local.yml"
$example = Join-Path $resDir "application-local.yml.example"

if (Test-Path $local) {
    Ok "이미 있습니다 (건드리지 않습니다)"
} else {
    $dbPw = Read-Host "  MySQL root 비밀번호를 입력하세요 (그냥 Enter 치면 나중에 직접 채움)"
    $secret = -join ((1..72) | ForEach-Object { [char[]]"abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789" | Get-Random })
    @"
# 이 파일은 .gitignore 대상입니다. 절대 커밋하지 마세요.
spring:
  datasource:
    username: root
    password: $dbPw

app:
  jwt:
    secret: $secret
"@ | Set-Content -Path $local -Encoding UTF8
    Ok "생성 완료 (JWT secret 은 랜덤 72자로 자동 생성)"
}

# ── 4. MySQL 스키마 ─────────────────────────────────────────
Step 4 "MySQL 스키마"
$mysql = Get-Command mysql -ErrorAction SilentlyContinue
if (-not $mysql) {
    Warn "mysql 클라이언트가 없습니다. 아래 중 하나로 진행하세요."
    Write-Host "    (A) Docker:  docker compose up -d   그다음 이 스크립트 재실행"
    Write-Host "    (B) MySQL Installer 로 8.0 설치 후 Workbench 에서 docs\03_schema.sql 실행"
} else {
    $schema = Join-Path $Root "docs\03_schema.sql"
    Write-Host "  스키마를 넣습니다 (비밀번호를 물어봅니다)"
    & mysql -u root -p -e "source $schema" 2>&1 | Out-Null
    $cnt = (& mysql -u root -p -N -B -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='tourlab';" 2>$null)
    if ($cnt -eq 27) { Ok "테이블 27개 확인" } else { Warn "테이블 수가 $cnt 입니다 (기대값 27). docs\03_schema.sql 을 직접 실행해보세요" }
}

# ── 5. 빌드 ─────────────────────────────────────────────────
Step 5 "빌드"
Push-Location $Backend
try {
    & .\gradlew.bat build -x test --console=plain
    if ($LASTEXITCODE -eq 0) { Ok "BUILD SUCCESSFUL" } else { Warn "빌드 실패 — 위 로그를 확인하세요" }
} finally { Pop-Location }

Write-Host "`n다음 단계" -ForegroundColor White
Write-Host "  cd backend"
Write-Host "  .\gradlew.bat bootRun"
Write-Host "  http://localhost:8080/swagger-ui/index.html"
