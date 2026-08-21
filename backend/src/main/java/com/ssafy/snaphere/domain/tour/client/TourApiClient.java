package com.ssafy.snaphere.domain.tour.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.snaphere.domain.tour.config.TourApiProperties;
import com.ssafy.snaphere.domain.tour.dto.TourApiDtos.AreaCodeItem;
import com.ssafy.snaphere.domain.tour.dto.TourApiDtos.Page;
import com.ssafy.snaphere.domain.tour.dto.TourApiDtos.TourPlaceItem;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 한국관광공사 TourAPI (KorService2) 호출 전담.
 *
 * 이 클래스가 책임지는 함정 5가지
 *  1) serviceKey 이중 인코딩 — build(true) 로 이미 인코딩된 값으로 취급한다.
 *  2) 인증 실패 시 _type=json 이 무시되고 XML 이 온다 — 본문이 '<' 로 시작하면 XML 오류로 처리.
 *  3) 결과가 0건일 때 items 가 객체가 아니라 빈 문자열("") 로 온다 — 빈 리스트로 처리.
 *  4) mapx=경도, mapy=위도 — 여기서 lng/lat 으로 바꿔 담는다.
 *  5) 일 호출 한도(개발계정 1,000회) — 남은 예산을 세고 초과 시 호출 자체를 막는다.
 *
 * ⚠️ 이 클라이언트를 사용자 요청 경로(컨트롤러)에서 호출하지 마라. 배치·지연적재 전용이다.
 *    이유: 응답이 느리고, 한도가 순식간에 소진되고, 외부 장애가 곧 우리 장애가 된다.
 */
@Slf4j
@Component
public class TourApiClient {

    private static final DateTimeFormatter MODIFIED_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter EVENT_FMT    = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final Pattern AUTH_MSG = Pattern.compile("<returnAuthMsg>(.*?)</returnAuthMsg>");
    private static final Pattern REASON   = Pattern.compile("<returnReasonCode>(.*?)</returnReasonCode>");
    private static final int MAX_ATTEMPTS = 3;

    private final TourApiProperties props;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    /** 일 호출 예산. 자정에 리셋된다(스케줄러가 resetDailyBudget 을 호출). */
    private final AtomicInteger callsToday = new AtomicInteger(0);

    public TourApiClient(TourApiProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(props.connectTimeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(props.readTimeoutMs()));

        this.restClient = RestClient.builder()
                .baseUrl(props.baseUrl())
                .requestFactory(factory)
                .build();
    }

    // ── 공개 API ────────────────────────────────────────────────

    /** 시도 17개. 파라미터 없이 호출한다. */
    public List<AreaCodeItem> fetchAreaCodes() {
        JsonNode body = call("/areaCode2", b -> b.queryParam("numOfRows", 50).queryParam("pageNo", 1));
        return readItems(body).stream().map(this::toAreaCode).toList();
    }

    /** 특정 시도의 시군구 목록. */
    public List<AreaCodeItem> fetchSigunguCodes(int areaCode) {
        JsonNode body = call("/areaCode2", b -> b
                .queryParam("areaCode", areaCode)
                .queryParam("numOfRows", 100)
                .queryParam("pageNo", 1));
        return readItems(body).stream().map(this::toAreaCode).toList();
    }

    /**
     * 지역기반 동기화 목록. 일반 목록과 달리 showflag(노출여부)를 함께 주므로 증분 동기화에 쓴다.
     * arrange=C (수정일순) 로 고정해 페이지가 밀리는 것을 줄인다.
     */
    public Page<TourPlaceItem> fetchPlacePage(int areaCode, int contentTypeId, int pageNo) {
        JsonNode body = call("/areaBasedSyncList2", b -> b
                .queryParam("areaCode", areaCode)
                .queryParam("contentTypeId", contentTypeId)
                .queryParam("arrange", "C")
                .queryParam("numOfRows", props.numOfRows())
                .queryParam("pageNo", pageNo));
        return toPage(body, pageNo);
    }

    /**
     * 축제·행사 검색. 행사 기간(eventstartdate/eventenddate)은 이 오퍼레이션에서만 제대로 온다.
     * 이벤트 탭이 이 데이터에 의존한다.
     *
     * @param eventStartDate yyyyMMdd — 이 날짜 이후에 열리는 행사
     */
    public Page<TourPlaceItem> fetchFestivalPage(String eventStartDate, Integer areaCode, int pageNo) {
        JsonNode body = call("/searchFestival2", b -> {
            b.queryParam("eventStartDate", eventStartDate)
             .queryParam("arrange", "C")
             .queryParam("numOfRows", props.numOfRows())
             .queryParam("pageNo", pageNo);
            if (areaCode != null) b.queryParam("areaCode", areaCode);
            return b;
        });
        return toPage(body, pageNo);
    }

    public int callsUsedToday() { return callsToday.get(); }

    public int callBudgetRemaining() { return Math.max(0, props.dailyCallBudget() - callsToday.get()); }

    public void resetDailyBudget() {
        int used = callsToday.getAndSet(0);
        log.info("TourAPI 일 호출 예산 리셋. 어제 사용량={}", used);
    }

    // ── 내부 ────────────────────────────────────────────────────

    private interface QueryCustomizer {
        UriComponentsBuilder apply(UriComponentsBuilder builder);
    }

    private JsonNode call(String path, QueryCustomizer customizer) {
        if (!props.hasKey()) {
            throw new TourApiCallException("NO_KEY",
                    "TourAPI 인증키가 설정되지 않았습니다. application-local.yml 의 app.tour-api.service-key 또는 환경변수 TOUR_API_KEY 를 확인하세요.",
                    false);
        }
        if (callBudgetRemaining() <= 0) {
            throw new TourApiCallException("BUDGET_EXHAUSTED",
                    "일 호출 예산(" + props.dailyCallBudget() + "회)을 모두 사용했습니다.", false);
        }

        UriComponentsBuilder builder = UriComponentsBuilder.fromPath(path)
                .queryParam("serviceKey", props.serviceKey())
                .queryParam("MobileOS", props.mobileOs())
                .queryParam("MobileApp", props.mobileApp())
                .queryParam("_type", "json");
        // ★ build(true) — serviceKey 를 다시 인코딩하지 않는다. 이걸 빼면 인증이 깨진다.
        String uri = customizer.apply(builder).build(true).toUriString();

        TourApiCallException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                callsToday.incrementAndGet();
                String raw = restClient.get().uri(uri).retrieve().body(String.class);
                return parse(raw);
            } catch (TourApiCallException e) {
                if (!e.isRetryable()) throw e;
                last = e;
            } catch (Exception e) {
                last = new TourApiCallException("IO_ERROR", e.getMessage(), true);
            }
            sleepBackoff(attempt);
            log.warn("TourAPI 재시도 {}/{} path={} 원인={}", attempt, MAX_ATTEMPTS, path,
                    last == null ? "?" : last.getMessage());
        }
        throw last;
    }

    private void sleepBackoff(int attempt) {
        try {
            Thread.sleep(500L * attempt);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new TourApiCallException("INTERRUPTED", "호출이 중단되었습니다.", false);
        }
    }

    /** 응답 본문을 읽어 response.body 노드를 돌려준다. 인증 실패 XML 도 여기서 걸러낸다. */
    private JsonNode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new TourApiCallException("EMPTY_BODY", "응답 본문이 비어 있습니다.", true);
        }
        String trimmed = raw.stripLeading();

        // 함정 2) 인증 실패면 _type=json 을 무시하고 XML 이 온다.
        if (trimmed.startsWith("<")) {
            String msg = firstGroup(AUTH_MSG, trimmed, "알 수 없는 인증 오류");
            String code = firstGroup(REASON, trimmed, "AUTH_XML");
            // 한도 초과(22)·미등록(30)·접근거부(20) 는 재시도해도 소용없다.
            throw new TourApiCallException(code, msg + " (TourAPI 가 XML 오류를 반환했습니다)", false);
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(trimmed);
        } catch (Exception e) {
            throw new TourApiCallException("PARSE_ERROR",
                    "응답을 JSON 으로 읽지 못했습니다: " + shorten(trimmed), false);
        }

        JsonNode response = root.path("response");
        String resultCode = response.path("header").path("resultCode").asText("");
        String resultMsg  = response.path("header").path("resultMsg").asText("");
        if (!"0000".equals(resultCode)) {
            boolean retryable = "0001".equals(resultCode); // 일시적 처리 오류만 재시도
            throw new TourApiCallException(resultCode.isBlank() ? "NO_RESULT_CODE" : resultCode,
                    resultMsg.isBlank() ? shorten(trimmed) : resultMsg, retryable);
        }
        return response.path("body");
    }

    /** 함정 3) 0건이면 items 가 빈 문자열("")로 온다. item 이 단건일 때 객체로 오는 경우도 있다. */
    private List<JsonNode> readItems(JsonNode body) {
        JsonNode items = body.path("items");
        if (items.isMissingNode() || items.isNull() || items.isTextual()) return List.of();

        JsonNode item = items.path("item");
        if (item.isMissingNode() || item.isNull()) return List.of();
        if (item.isObject()) return List.of(item);

        List<JsonNode> out = new ArrayList<>();
        item.forEach(out::add);
        return out;
    }

    private Page<TourPlaceItem> toPage(JsonNode body, int pageNo) {
        List<TourPlaceItem> items = readItems(body).stream().map(this::toPlace).toList();
        return new Page<>(items, pageNo, props.numOfRows(), body.path("totalCount").asInt(0));
    }

    private AreaCodeItem toAreaCode(JsonNode n) {
        return new AreaCodeItem(text(n, "code"), text(n, "name"));
    }

    private TourPlaceItem toPlace(JsonNode n) {
        return new TourPlaceItem(
                longOrNull(n, "contentid"),
                intOrNull(n, "contenttypeid"),
                text(n, "title"),
                text(n, "addr1"),
                text(n, "addr2"),
                text(n, "zipcode"),
                text(n, "tel"),
                intOrNull(n, "areacode"),
                intOrNull(n, "sigungucode"),
                decimalOrNull(n, "mapy"),   // ⚠️ y = 위도
                decimalOrNull(n, "mapx"),   // ⚠️ x = 경도
                text(n, "cat1"),
                text(n, "cat2"),
                text(n, "cat3"),
                text(n, "firstimage"),
                text(n, "firstimage2"),
                dateTimeOrNull(n, "modifiedtime"),
                "0".equals(text(n, "showflag")),
                dateOrNull(n, "eventstartdate"),
                dateOrNull(n, "eventenddate"),
                text(n, "eventplace"),
                firstNonBlank(text(n, "sponsor1"), text(n, "organizer"))
        );
    }

    // ── 필드 파싱 헬퍼. TourAPI 는 값이 없을 때 null 이 아니라 빈 문자열을 준다. ──

    private static String text(JsonNode n, String field) {
        String v = n.path(field).asText("");
        return v.isBlank() ? null : v.trim();
    }

    private static Long longOrNull(JsonNode n, String field) {
        String v = text(n, field);
        try { return v == null ? null : Long.parseLong(v); } catch (NumberFormatException e) { return null; }
    }

    private static Integer intOrNull(JsonNode n, String field) {
        String v = text(n, field);
        try { return v == null ? null : Integer.parseInt(v); } catch (NumberFormatException e) { return null; }
    }

    private static BigDecimal decimalOrNull(JsonNode n, String field) {
        String v = text(n, field);
        if (v == null) return null;
        try {
            BigDecimal d = new BigDecimal(v);
            return d.signum() == 0 ? null : d;   // 0.0 은 좌표 없음으로 본다
        } catch (NumberFormatException e) { return null; }
    }

    private static LocalDateTime dateTimeOrNull(JsonNode n, String field) {
        String v = text(n, field);
        if (v == null || v.length() < 14) return null;
        try { return LocalDateTime.parse(v.substring(0, 14), MODIFIED_FMT); }
        catch (Exception e) { return null; }
    }

    private static LocalDate dateOrNull(JsonNode n, String field) {
        String v = text(n, field);
        if (v == null || v.length() < 8) return null;
        try { return LocalDate.parse(v.substring(0, 8), EVENT_FMT); }
        catch (Exception e) { return null; }
    }

    private static String firstNonBlank(String a, String b) { return a != null ? a : b; }

    private static String firstGroup(Pattern p, String s, String fallback) {
        Matcher m = p.matcher(s);
        return m.find() ? m.group(1).trim() : fallback;
    }

    private static String shorten(String s) {
        return s.length() <= 200 ? s : s.substring(0, 200) + "...";
    }
}
