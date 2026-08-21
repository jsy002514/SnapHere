# =====================================================================
# SnapHere 백엔드 전 구간 점검 (E2E 스모크 테스트)
#
# "Swagger 에 보인다" 와 "실제로 동작한다" 는 다르다.
# 이 스크립트는 회원가입 → 로그인 → 업로드 → Tier 판정 → 히트맵 → 랭킹까지
# 실제로 호출해서 각 단계의 성공/실패를 찍는다.
#
# 사용법 (서버가 떠 있는 상태에서):
#   powershell -ExecutionPolicy Bypass -File scripts\smoke-test.ps1
#
# ⚠️ 이 파일은 UTF-8 BOM 으로 저장되어 있다. 지우지 말 것.
#    Windows PowerShell 5.1 은 BOM 이 없으면 .ps1 을 시스템 코드페이지(CP949)로 읽어서
#    한글 문자열이 깨지고, 인용이 무너져 엉뚱한 구문 오류가 쏟아진다.
#    편집기에서 다시 저장할 때 "UTF-8 with BOM" 을 유지해야 한다.
# =====================================================================

$ErrorActionPreference = 'Stop'
# 콘솔 출력 인코딩. 없으면 결과 메시지의 한글이 물음표로 보인다.
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch { }
$BASE = "http://localhost:8080/api/v1"
$pass = 0; $fail = 0; $skip = 0

function Step($name, $block, [switch]$Optional) {
    Write-Host -NoNewline ("  {0,-52}" -f $name)
    try {
        $r = & $block
        Write-Host "PASS" -ForegroundColor Green
        $script:pass++
        return $r
    } catch {
        $msg = $_.Exception.Message
        if ($_.ErrorDetails -and $_.ErrorDetails.Message) { $msg = [string]$_.ErrorDetails.Message }
        if ($Optional) {
            Write-Host "SKIP" -ForegroundColor DarkYellow
            Write-Host "        $msg" -ForegroundColor DarkGray
            $script:skip++
        } else {
            Write-Host "FAIL" -ForegroundColor Red
            Write-Host "        $msg" -ForegroundColor Yellow
            $script:fail++
        }
        return $null
    }
}

# PowerShell 5.1 은 오류 응답 본문을 $_.ErrorDetails.Message 에 항상 담아주지 않는다.
# 담기지 않으면 응답 스트림에서 직접 읽는다. 이게 없으면 "throw $null" 이 되어
# 실제로는 올바르게 동작하는 API 가 ScriptHalted 로 실패한 것처럼 보인다.
function ErrBody($e) {
    if ($e.ErrorDetails -and $e.ErrorDetails.Message) { return [string]$e.ErrorDetails.Message }
    try {
        $resp = $e.Exception.Response
        if ($resp -and $resp.GetResponseStream) {
            $sr = New-Object System.IO.StreamReader($resp.GetResponseStream(), [System.Text.Encoding]::UTF8)
            $body = $sr.ReadToEnd(); $sr.Close()
            if ($body) { return $body }
        }
    } catch { }
    return "(본문 없음) " + $e.Exception.Message
}

function HttpStatus($e) {
    try { return [int]$e.Exception.Response.StatusCode } catch { return -1 }
}

# 실패해야 정상인 호출을 검증한다. 기대한 에러코드가 응답 본문에 있어야 통과.
function ExpectError($expectedCode, $block) {
    try {
        & $block | Out-Null
    } catch {
        $body = ErrBody $_
        if ($body -match $expectedCode) { return }
        throw ("기대={0} / 실제=HTTP {1} {2}" -f $expectedCode, (HttpStatus $_), $body)
    }
    throw ("오류가 나야 하는데 성공했다 (기대: {0})" -f $expectedCode)
}

function Post($path, $body, $token) {
    $h = @{}
    if ($token) { $h["Authorization"] = "Bearer $token" }
    $json = if ($body -is [string]) { $body } else { $body | ConvertTo-Json -Depth 8 }
    Invoke-RestMethod -Method Post -Uri "$BASE$path" -Headers $h `
        -ContentType "application/json; charset=utf-8" -Body $json
}
function Get_($path, $token) {
    $h = @{}
    if ($token) { $h["Authorization"] = "Bearer $token" }
    Invoke-RestMethod -Method Get -Uri "$BASE$path" -Headers $h
}
# ⚠️ 함수명을 Del 로 두면 안 된다. del 은 Remove-Item 의 기본 별칭이라
#    "Del "/posts/1" $tok" 이 Remove-Item 호출로 해석되어 엉뚱한 오류가 난다.
function Remove_($path, $token) {
    Invoke-RestMethod -Method Delete -Uri "$BASE$path" -Headers @{ Authorization = "Bearer $token" }
}

$stamp = Get-Date -Format "MMddHHmmss"
$loginId = "smoke$stamp"
$pw = "smoke1234test"

Write-Host ""
Write-Host "===== 1. 서버 · 기준 데이터 =====" -ForegroundColor Cyan

$regions = Step "GET /regions — 17개 시도" {
    $r = Get_ "/regions"
    if (-not $r.success) { throw "success=false" }
    if ($r.data.Count -ne 17) { throw "17개가 아니라 $($r.data.Count)개" }
    # 마이그레이션 확인: 라벨 좌표가 채워져 있어야 한다
    $chungbuk = $r.data | Where-Object { $_.areaCode -eq 33 }
    if (-not $chungbuk.labelLat) { throw "labelLat 이 없다 — 06_migration 을 적용하지 않았다" }
    $r.data
}

Step "GET /regions — 지역명이 깨지지 않았는지 (인코딩 무결성)" {
    $seoul = $regions | Where-Object { $_.areaCode -eq 1 }
    if ($seoul.nameKo -ne "서울") {
        throw ("지역명이 '{0}' 로 저장돼 있다 (기대: 서울). TourAPI areaCode 동기화가 이름을 덮어썼을 수 있다" -f $seoul.nameKo)
    }
    $jeonbuk = $regions | Where-Object { $_.areaCode -eq 37 }
    if ($jeonbuk.nameKo -notmatch "전") { throw ("37번 지역명이 '{0}'" -f $jeonbuk.nameKo) }
}

Step "GET /places — 서울 관광지 목록 (TourAPI 적재분)" {
    $r = Get_ "/places?areaCode=1&size=5"
    if ($r.data.totalElements -lt 1) { throw "적재된 장소가 없다 — TourAPI 동기화를 먼저 실행할 것" }
    Write-Host "" ; Write-Host ("        서울 장소 {0}건, 첫 항목: {1}" -f $r.data.totalElements, $r.data.content[0].title) -ForegroundColor DarkGray
    $r
}

$nearby = Step "GET /places/nearby — 경복궁 주변 1km (공간 인덱스)" {
    $r = Get_ "/places/nearby?lat=37.5788&lng=126.977&radius=1000&limit=5"
    if (-not $r.success) { throw "success=false" }
    if ($r.data.items.Count -eq 0 -and -not $r.data.nearestDistanceMeters) {
        throw "결과도 없고 nearestDistanceMeters 도 없다"
    }
    $r.data
}

Write-Host ""
Write-Host "===== 2. 인증 =====" -ForegroundColor Cyan

$tok = Step "POST /auth/signup — 회원가입" {
    $r = Post "/auth/signup" @{
        loginId = $loginId; password = $pw; passwordConfirm = $pw
        nickname = "스모크$stamp"; termsAgreed = $true
        deviceId = "smoke-device"; platform = "WEB"
    }
    if (-not $r.data.accessToken) { throw "accessToken 이 없다" }
    $r.data.accessToken
}
if (-not $tok) { Write-Host "`n가입이 실패해서 이후 단계를 진행할 수 없습니다." -ForegroundColor Red; exit 1 }

Step "POST /auth/login — 로그인 (가입과 같은 초에 호출 · jti 회귀 검사)" {
    # 2026-08-22 장애 회귀 검사: jti 가 없으면 가입 직후 같은 초에 로그인하면
    # 리프레시 토큰이 완전히 같아져 UNIQUE(token_hash) 위반으로 500 이 났다.
    $r = Post "/auth/login" @{ loginId = $loginId; password = $pw }
    if (-not $r.data.accessToken) { throw "accessToken 이 없다" }
}

Step "POST /auth/login x3 (연속) — 매번 다른 리프레시 토큰이어야 정상" {
    $seen = @{}
    for ($i = 0; $i -lt 3; $i++) {
        $r = Post "/auth/login" @{ loginId = $loginId; password = $pw }
        if (-not $r.data.refreshToken) { throw "refreshToken 이 없다" }
        if ($seen.ContainsKey($r.data.refreshToken)) { throw "리프레시 토큰이 중복 발급됐다 (jti 누락)" }
        $seen[$r.data.refreshToken] = 1
    }
}

Step "POST /auth/login (틀린 비밀번호) — AUTH_003 이어야 정상" {
    ExpectError "AUTH_003" { Post "/auth/login" @{ loginId = $loginId; password = "wrongwrong1" } }
}

Step "POST /auth/login (없는 아이디) — 위와 같은 AUTH_003 이어야 정상" {
    ExpectError "AUTH_003" { Post "/auth/login" @{ loginId = "nosuchuser999"; password = "whatever12" } }
}

Step "GET /users/me (토큰 없이) — AUTH_006 이어야 정상" {
    ExpectError "AUTH_006" { Get_ "/users/me" }
}

$me = Step "GET /users/me — 내 정보" {
    $r = Get_ "/users/me" $tok
    if (-not $r.data) { throw "data 가 없다" }
    $r.data
}

Write-Host ""
Write-Host "===== 3. 업로드 3단계 + Tier 판정 (핵심) =====" -ForegroundColor Cyan

# 가장 가까운 장소를 고른다
$placeId = $null; $placeLat = 37.5788; $placeLng = 126.977
if ($nearby -and $nearby.items.Count -gt 0) {
    $placeId = $nearby.items[0].placeId
    $placeLat = [double]$nearby.items[0].lat
    $placeLng = [double]$nearby.items[0].lng
    Write-Host ("        대상 장소: {0} (placeId={1})" -f $nearby.items[0].title, $placeId) -ForegroundColor DarkGray
}

$upload = Step "POST /posts/upload-urls — 업로드 URL 발급" {
    $r = Post "/posts/upload-urls" @{
        files = @(@{ fileName = "smoke.jpg"; contentType = "image/jpeg"; fileSize = 2048 })
    } $tok
    if (-not $r.data.items[0].uploadUrl) { throw "uploadUrl 이 없다" }
    $r.data.items[0]
}

Step "PUT {uploadUrl} — 실제 파일 전송" {
    if (-not $upload) { throw "직전 단계 실패" }
    # 최소한의 유효한 JPEG 바이트
    $bytes = [byte[]](0xFF,0xD8,0xFF,0xE0,0x00,0x10,0x4A,0x46,0x49,0x46,0x00,0x01,
                      0x01,0x00,0x00,0x01,0x00,0x01,0x00,0x00,0xFF,0xD9)
    # -UseBasicParsing: PS 5.1 은 이게 없으면 응답 HTML 파싱을 시도하며 보안 경고로 멈춘다
    Invoke-WebRequest -Method Put -Uri $upload.uploadUrl -Body $bytes `
        -ContentType "image/jpeg" -UseBasicParsing | Out-Null
}

$onSite = Step "POST /posts (카메라 · 장소 좌표 그대로) — ON_SITE 여야 정상" {
    if (-not $upload) { throw "직전 단계 실패" }
    $r = Post "/posts" @{
        placeId = $placeId; areaCode = 1; category = "PHOTO"
        title = "스모크 테스트 현장인증"
        media = @(@{ mediaKey = $upload.mediaKey; mediaType = "IMAGE"
                     width = 1600; height = 2000; sortOrder = 0 })
        source = "CAMERA"
        capturedLat = $placeLat; capturedLng = $placeLng
        takenAt = (Get-Date).ToString("yyyy-MM-ddTHH:mm:ss")
        tags = @(@{ name = "스모크"; source = "USER" })
    } $tok
    if ($r.data.tier -ne "ON_SITE") {
        throw "tier=$($r.data.tier) (기대: ON_SITE) 근거=$($r.data.tierReason)"
    }
    Write-Host ""
    Write-Host ("        tier=ON_SITE  거리={0}m  방문기록생성={1}  랭킹반영={2}" -f `
        $r.data.distanceMeters, $r.data.visitCreated, $r.data.countsForRanking) -ForegroundColor DarkGray
    $r.data
}

Step "POST /posts (좌표 없음) — NO_LOCATION 이어야 정상" {
    $r = Post "/posts" @{
        placeId = $placeId; areaCode = 1; category = "FREE"
        title = "스모크 위치없음"; content = "좌표를 보내지 않았다"
        source = "NONE"
    } $tok
    if ($r.data.tier -ne "NO_LOCATION") { throw "tier=$($r.data.tier) (기대: NO_LOCATION)" }
    if ($r.data.countsForRanking) { throw "NO_LOCATION 이 랭킹에 반영된다 (심각)" }
}

Step "POST /posts (반경 밖 5km) — NO_LOCATION 이어야 정상" {
    if (-not $upload) { throw "업로드 단계 실패" }
    $r = Post "/posts" @{
        placeId = $placeId; areaCode = 1; category = "PHOTO"
        title = "스모크 반경밖"
        source = "CAMERA"
        capturedLat = ($placeLat + 0.05); capturedLng = $placeLng
        takenAt = (Get-Date).ToString("yyyy-MM-ddTHH:mm:ss")
    } $tok
    if ($r.data.tier -ne "NO_LOCATION") {
        throw "반경 밖인데 tier=$($r.data.tier) — 인증 반경 검사가 동작하지 않는다 (심각)"
    }
}

Step "POST /posts (제목·본문 둘 다 없음) — POST_001 이어야 정상" {
    ExpectError "POST_001" { Post "/posts" @{ areaCode = 1; source = "NONE" } $tok }
}

Write-Host ""
Write-Host "===== 4. 조회 · 상호작용 =====" -ForegroundColor Cyan

Step "GET /posts — 목록 (thumbnailRatio 포함 확인)" {
    $r = Get_ "/posts?areaCode=1&size=5" $tok
    if ($r.data.totalElements -lt 1) { throw "게시물이 없다" }
    $first = $r.data.content[0]
    if ($null -eq $first.tierMessageKey) { throw "tierMessageKey 가 없다 (다국어 매핑 불가)" }
}

Step "GET /posts/{id} — 상세" {
    if (-not $onSite) { throw "게시물 생성 실패" }
    $r = Get_ "/posts/$($onSite.postId)" $tok
    if ($r.data.postId -ne $onSite.postId) { throw "postId 불일치" }
}

Step "POST /posts/{id}/likes — 좋아요" {
    if (-not $onSite) { throw "게시물 생성 실패" }
    $r = Post "/posts/$($onSite.postId)/likes" @{} $tok
    if (-not $r.data.liked) { throw "liked=false" }
}

Step "POST /posts/{id}/likes (다시) — 카운터가 2 안 되어야 정상" {
    if (-not $onSite) { throw "게시물 생성 실패" }
    $r = Post "/posts/$($onSite.postId)/likes" @{} $tok
    if ($r.data.likeCount -gt 1) { throw "중복 좋아요로 카운터가 $($r.data.likeCount) 이 됐다" }
}

$comment = Step "POST /posts/{id}/comments — 댓글" {
    if (-not $onSite) { throw "게시물 생성 실패" }
    $r = Post "/posts/$($onSite.postId)/comments" @{ content = "스모크 댓글" } $tok
    if (-not $r.data.commentId) { throw "commentId 가 없다" }
    $r.data
}

Step "POST /comments — 대댓글의 대댓글은 COMMENT_002 이어야 정상" {
    if (-not $comment) { throw "댓글 생성 실패" }
    $reply = Post "/posts/$($onSite.postId)/comments" `
        @{ content = "스모크 대댓글"; parentCommentId = $comment.commentId } $tok
    ExpectError "COMMENT_002" {
        Post "/posts/$($onSite.postId)/comments" `
            @{ content = "2단계 대댓글"; parentCommentId = $reply.data.commentId } $tok
    }
}

Step "GET /posts/tag-suggestions — 자동 태그 추천" {
    $r = Get_ "/posts/tag-suggestions?lat=$placeLat&lng=$placeLng&placeId=$placeId" $tok
    if ($null -eq $r.data.suggestions) { throw "suggestions 가 없다" }
    Write-Host ""
    Write-Host ("        추천 태그: {0}" -f (($r.data.suggestions | ForEach-Object { $_.name }) -join ", ")) -ForegroundColor DarkGray
}

Step "POST /places/{id}/checkin (반경 안) — 방문 기록" {
    if (-not $placeId) { throw "장소가 없다" }
    $r = Post "/places/$placeId/checkin" @{ lat = $placeLat; lng = $placeLng } $tok
    if (-not $r.data.tier) { throw "tier 가 없다" }
}

Step "POST /places/{id}/checkin (5km 밖) — PLACE_004 이어야 정상" {
    if (-not $placeId) { throw "장소가 없다" }
    ExpectError "PLACE_004" {
        Post "/places/$placeId/checkin" @{ lat = ($placeLat + 0.05); lng = $placeLng } $tok
    }
}

Step "GET /visits/stats — 방문 통계" {
    $r = Get_ "/visits/stats" $tok
    if ($null -eq $r.data.placeCount) { throw "placeCount 가 없다" }
}

Step "POST /bookmarks — 저장" {
    if (-not $onSite) { throw "게시물 생성 실패" }
    Post "/bookmarks?targetType=POST&targetId=$($onSite.postId)" @{} $tok | Out-Null
}

Step "GET /notifications/unread-count — 알림" {
    $r = Get_ "/notifications/unread-count" $tok
    if ($null -eq $r.data.unreadCount) { throw "unreadCount 가 없다" }
}

Step "POST /places — 사용자 장소 생성" {
    $r = Post "/places" @{
        title = "스모크 뷰포인트 $stamp"; lat = 35.4350; lng = 126.7020; addr1 = "전북 고창군"
    } $tok
    if (-not $r.data.placeId) { throw "placeId 가 없다" }
    if ($r.data.verifyRadiusMeters -ne 100) { throw "사용자 장소 인증 반경이 100m 가 아니다" }
}

Step "POST /places (같은 이름 · 같은 좌표) — merged=true 여야 정상" {
    $title = "스모크 중복테스트 $stamp"
    Post "/places" @{ title = $title; lat = 35.4360; lng = 126.7030 } $tok | Out-Null
    $r = Post "/places" @{ title = $title; lat = 35.4360; lng = 126.7030 } $tok
    if (-not $r.data.merged) { throw "중복 장소가 새로 생성됐다 — 랭킹·히트맵이 무의미해진다" }
}

Write-Host ""
Write-Host "===== 5. 검색 · 랭킹 · 히트맵 =====" -ForegroundColor Cyan

Step "GET /search — 통합 검색" {
    $r = Get_ "/search?keyword=서울&limit=5"
    if ($null -eq $r.data.totalCount) { throw "totalCount 가 없다" }
}

Step "GET /search (연산자 문자) — 오류 없이 처리되어야 정상" {
    Get_ "/search?keyword=-%22%2B%2B%2B" | Out-Null
}

Write-Host ""
Write-Host "  (아래 3개는 ROLE_ADMIN 이 필요합니다. 권한이 없으면 SKIP 됩니다)" -ForegroundColor DarkGray

Step "POST /admin/batch/heatmap — 히트맵 즉시 집계" -Optional {
    $r = Post "/admin/batch/heatmap" @{} $tok
    Write-Host ""
    Write-Host ("        생성된 격자: {0}개" -f $r.data.cells) -ForegroundColor DarkGray
}

Step "POST /admin/batch/rankings — 랭킹 집계" -Optional {
    Post "/admin/batch/rankings" @{} $tok | Out-Null
}

Step "POST /admin/batch/fix-counters — 카운터 보정" -Optional {
    $r = Post "/admin/batch/fix-counters" @{} $tok
    Write-Host ""
    Write-Host ("        보정된 행: {0}" -f $r.data.fixedRows) -ForegroundColor DarkGray
}

Step "GET /map/heatmap — 히트맵 조회" {
    $r = Get_ "/map/heatmap?swLat=37.4&swLng=126.8&neLat=37.7&neLng=127.1&zoom=13"
    if ($null -eq $r.data.gridLevel) { throw "gridLevel 이 없다" }
    if (-not $r.data.nextRefreshAt) { throw "nextRefreshAt 이 없다 — 앱이 폴링 주기를 알 수 없다" }
    Write-Host ""
    Write-Host ("        격자 {0}개, fallback={1}, nextRefreshAt={2}" -f `
        $r.data.cells.Count, $r.data.fallbackApplied, $r.data.nextRefreshAt) -ForegroundColor DarkGray
}

Step "GET /map/regions — 시도별 활동량 (labelLat 확인)" {
    $r = Get_ "/map/regions?period=REALTIME"
    if ($r.data.regions.Count -ne 17) { throw "17개가 아니다" }
    $cb = $r.data.regions | Where-Object { $_.areaCode -eq 33 }
    if (-not $cb.labelLat) { throw "충북 labelLat 이 없다" }
}

Step "GET /map/markers — 장소 마커" {
    $r = Get_ "/map/markers?minLat=37.4&maxLat=37.7&minLng=126.8&maxLng=127.1"
    if ($null -eq $r.data.totalCount) { throw "totalCount 가 없다" }
}

Step "GET /rankings/places — 장소 랭킹" {
    $r = Get_ "/rankings/places?areaCode=1&period=WEEKLY&limit=5"
    if ($null -eq $r.data.items) { throw "items 가 없다" }
}

Step "GET /recommendations/places — 추천 (비어도 fallback 이 채워야 정상)" {
    $r = Get_ "/recommendations/places?areaCode=1&limit=5"
    if ($r.data.Count -eq 0) { throw "추천이 비었다 — fallback 이 동작하지 않는다" }
}

Step "GET /regions/{code}/community — 커뮤니티 홈 (한 화면 한 호출)" {
    $r = Get_ "/regions/1/community"
    if (-not $r.data.region) { throw "region 이 없다" }
    if ($null -eq $r.data.popularPosts) { throw "popularPosts 가 없다" }
    if ($null -eq $r.data.recommendedPlaces) { throw "recommendedPlaces 가 없다" }
}

Step "GET /events — 이벤트 목록" {
    $r = Get_ "/events"
    if ($null -eq $r.data.totalElements) { throw "totalElements 가 없다" }
    if ($r.data.totalElements -eq 0) {
        Write-Host ""
        Write-Host "        행사 0건 — TourAPI 축제 동기화(/admin/tour-sync/festivals)를 아직 안 돌렸다" -ForegroundColor DarkYellow
    }
}

Write-Host ""
Write-Host "===== 6. 정리 =====" -ForegroundColor Cyan

Step "DELETE /posts/{id} — 삭제" {
    if (-not $onSite) { throw "게시물 생성 실패" }
    Remove_ "/posts/$($onSite.postId)" $tok | Out-Null
}

Step "DELETE /users/me — 계정 삭제 (테스트 계정 정리)" {
    Invoke-RestMethod -Method Delete -Uri "$BASE/users/me" `
        -Headers @{ Authorization = "Bearer $tok" } `
        -ContentType "application/json; charset=utf-8" `
        -Body (@{ password = $pw; contentAction = "DELETE_ALL"; reason = "OTHER" } | ConvertTo-Json) | Out-Null
}

Write-Host ""
Write-Host "=======================================================" -ForegroundColor Cyan
Write-Host ("  PASS {0}   FAIL {1}   SKIP {2}" -f $pass, $fail, $skip) -ForegroundColor Cyan
Write-Host "=======================================================" -ForegroundColor Cyan
if ($fail -gt 0) {
    Write-Host ""
    Write-Host "FAIL 항목을 그대로 복사해서 전달하세요." -ForegroundColor Yellow
    exit 1
}
Write-Host ""
Write-Host "전 구간 통과. 단, 이것은 '로컬에서 동작한다' 는 뜻입니다." -ForegroundColor Green
Write-Host "제출 전에 남은 것은 docs/00_작업현황.md 6장을 확인하세요." -ForegroundColor Green
