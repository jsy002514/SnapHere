# =====================================================================
# SQL 파일을 MySQL 컨테이너에 안전하게 적용한다.
#
# ⚠️ 왜 파이프를 쓰지 않는가 (2026-08-22 실제 사고)
#
#   Get-Content file.sql -Encoding UTF8 | docker exec -i mysql ...   # ❌ 한글이 ? 로 파괴된다
#
#   -Encoding UTF8 은 파일을 "읽을" 때만 적용된다.
#   PowerShell 이 네이티브 프로그램으로 파이프할 때 쓰는 인코딩은 $OutputEncoding 이고,
#   Windows PowerShell 5.1 의 기본값은 ASCII 다. 한글은 전부 '?' 가 되고 오류도 나지 않는다.
#   실제로 regions.name_ko 가 '??' (HEX 3F3F) 로 저장돼 태그 추천과 지도 라벨이 조용히 깨졌다.
#
#   docker cp 는 바이트를 그대로 복사하므로 인코딩이 개입하지 않는다.
#
# ⚠️ 인수 구성 주의
#   mysql -u$User  형태로 쓰면 PowerShell 이 변수를 확장하지 않아
#   literal '$User' 가 그대로 전달된다 (Access denied for user '$User'@'localhost').
#   반드시 "-u", $User 처럼 토큰을 분리하거나 별도 변수에 담아 넘긴다.
#
# 사용법:
#   powershell -ExecutionPolicy Bypass -File scripts\apply-sql.ps1 docs\07_fix_region_names.sql
# =====================================================================

param(
    [Parameter(Mandatory = $true, Position = 0)][string]$SqlFile,
    [string]$Container = "snaphere-mysql",
    [string]$Database  = "tourlab",
    [string]$DbUser    = "root",
    [string]$DbPassword = "snaphere1234"
)

$ErrorActionPreference = 'Stop'
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch { }

if (-not (Test-Path $SqlFile)) {
    Write-Host "파일을 찾을 수 없습니다: $SqlFile" -ForegroundColor Red
    exit 1
}

$full   = (Resolve-Path $SqlFile).Path
$name   = [System.IO.Path]::GetFileName($full)
$inside = "/tmp/$name"

# BOM 검사 — .sql 에 BOM 이 있으면 MySQL 이 첫 구문에서 문법 오류를 낸다
$bytes = [System.IO.File]::ReadAllBytes($full)
if ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF) {
    Write-Host "이 .sql 파일에 UTF-8 BOM 이 있습니다. MySQL 이 첫 문장에서 오류를 냅니다." -ForegroundColor Red
    Write-Host ".sql 은 BOM 없이 저장하세요 (.ps1 은 반대로 BOM 이 필요합니다)." -ForegroundColor Yellow
    exit 1
}

Write-Host ""
Write-Host ("  파일      : {0} ({1:N0} bytes)" -f $name, $bytes.Length) -ForegroundColor DarkGray
Write-Host ("  컨테이너  : {0}" -f $Container) -ForegroundColor DarkGray
Write-Host ("  데이터베이스 / 사용자 : {0} / {1}" -f $Database, $DbUser) -ForegroundColor DarkGray
Write-Host ""

Write-Host "1) 컨테이너로 복사 (바이트 그대로)" -ForegroundColor Cyan
& docker cp $full "${Container}:${inside}"
if ($LASTEXITCODE -ne 0) { Write-Host "docker cp 실패" -ForegroundColor Red; exit 1 }

Write-Host "2) 컨테이너 안에서 실행" -ForegroundColor Cyan
# MYSQL_PWD 로 비밀번호를 넘긴다 — 명령줄에 노출되지 않고 경고도 사라진다.
# 인수는 토큰을 분리해 넘긴다 (위 '인수 구성 주의' 참고).
$sql = "SET NAMES utf8mb4; SOURCE $inside;"
& docker exec -i -e "MYSQL_PWD=$DbPassword" $Container `
    mysql "-u" $DbUser "--default-character-set=utf8mb4" $Database "-e" $sql
$code = $LASTEXITCODE

Write-Host "3) 임시 파일 정리" -ForegroundColor Cyan
& docker exec -i $Container rm -f $inside 2>$null | Out-Null

if ($code -ne 0) {
    Write-Host ""
    Write-Host "SQL 실행 실패 (exit $code)" -ForegroundColor Red
    exit $code
}

Write-Host ""
Write-Host "완료: $name" -ForegroundColor Green
Write-Host ""
Write-Host "한글이 제대로 들어갔는지는 눈이 아니라 HEX 로 확인하세요." -ForegroundColor Yellow
Write-Host "  콘솔 출력은 콘솔 인코딩 때문에 따로 깨질 수 있어 신뢰할 수 없습니다." -ForegroundColor DarkGray
Write-Host ""
& docker exec -i -e "MYSQL_PWD=$DbPassword" $Container `
    mysql "-u" $DbUser $Database "-e" `
    "SELECT area_code, HEX(name_ko) AS hex_name, name_en FROM regions WHERE area_code IN (1,33,37) ORDER BY area_code;"
Write-Host ""
Write-Host "  기대값:  1 -> EC849CEC9AB8 (서울)" -ForegroundColor DarkGray
Write-Host "          33 -> ECB6A9ECB2ADEBB681EB8F84 (충청북도)" -ForegroundColor DarkGray
Write-Host "  3F3F 처럼 3F 가 보이면 아직 '?' 로 깨져 있습니다." -ForegroundColor DarkGray
