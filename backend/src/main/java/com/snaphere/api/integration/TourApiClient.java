package com.snaphere.api.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.snaphere.api.common.error.ApiException;
import com.snaphere.api.common.error.ErrorCode;
import com.snaphere.api.config.PlatformProperties;
import com.snaphere.api.place.TourPlaceDetailClient;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Component
public class TourApiClient implements TourPlaceDetailClient {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private final RestClient client;
    private final PlatformProperties.TourApi properties;

    public TourApiClient(RestClient.Builder builder, PlatformProperties.TourApi properties) {
        this.client = builder.baseUrl(properties.baseUrl()).build();
        this.properties = properties;
    }

    public List<CodeItem> sigungu(int areaCode, String language) {
        JsonNode root = get(service(language), "areaCode2", uri -> uri
                .queryParam("areaCode", areaCode).queryParam("numOfRows", 200).queryParam("pageNo", 1));
        return items(root).stream().map(n -> new CodeItem(intValue(n, "code"), text(n, "name"))).toList();
    }

    public PlacePage places(int areaCode, int contentTypeId, int page, int size) {
        JsonNode root = get("KorService2", "areaBasedSyncList2", uri -> uri
                .queryParam("areaCode", areaCode).queryParam("contentTypeId", contentTypeId)
                .queryParam("showflag", 1).queryParam("numOfRows", size).queryParam("pageNo", page));
        List<OfficialPlace> values = items(root).stream().map(this::officialPlace).toList();
        int total = root.path("response").path("body").path("totalCount").asInt(values.size());
        return new PlacePage(values, total);
    }

    public FestivalPage festivals(int areaCode, LocalDate startDate, LocalDate endDate, int page, int size) {
        JsonNode root = get("KorService2", "searchFestival2", uri -> uri
                .queryParam("areaCode", areaCode)
                .queryParam("eventStartDate", startDate.format(DateTimeFormatter.BASIC_ISO_DATE))
                .queryParam("eventEndDate", endDate.format(DateTimeFormatter.BASIC_ISO_DATE))
                .queryParam("arrange", "A").queryParam("numOfRows", size).queryParam("pageNo", page));
        List<Festival> values = items(root).stream().map(n -> new Festival(
                text(n,"contentid"), text(n,"title"), blankToNull(text(n,"addr1")),
                blankToNull(text(n,"firstimage")), doubleOrNull(n,"mapy"), doubleOrNull(n,"mapx"),
                intValue(n,"areacode"), integerOrNull(n,"sigungucode"),
                parseDate(text(n,"eventstartdate")), parseDate(text(n,"eventenddate")))).toList();
        int total = root.path("response").path("body").path("totalCount").asInt(values.size());
        return new FestivalPage(values,total);
    }

    @Override
    public Detail load(String contentId, String languageCode) {
        JsonNode root = get(service(languageCode), "detailCommon2", uri -> uri
                .queryParam("contentId", contentId).queryParam("defaultYN", "Y")
                .queryParam("firstImageYN", "Y").queryParam("addrinfoYN", "Y")
                .queryParam("overviewYN", "Y").queryParam("numOfRows", 10).queryParam("pageNo", 1));
        List<JsonNode> values = items(root);
        if (values.isEmpty()) return null;
        JsonNode item = values.get(0);
        return new Detail(blankToNull(text(item, "overview")), blankToNull(text(item, "tel")),
                blankToNull(text(item, "homepage")), null, null);
    }

    private JsonNode get(String service, String operation,
                         java.util.function.Function<org.springframework.web.util.UriBuilder,
                                 org.springframework.web.util.UriBuilder> customizer) {
        requireKey();
        try {
            JsonNode root = client.get().uri(uri -> {
                var builder = uri.pathSegment(service, operation)
                        .queryParam("serviceKey", properties.serviceKey())
                        .queryParam("MobileOS", properties.mobileOs())
                        .queryParam("MobileApp", properties.mobileApp())
                        .queryParam("_type", "json");
                return customizer.apply(builder).build();
            }).retrieve().body(JsonNode.class);
            String resultCode = root == null ? "" : root.path("response").path("header").path("resultCode").asText();
            if (!("0000".equals(resultCode) || "0".equals(resultCode))) {
                throw new IllegalStateException("TourAPI resultCode=" + resultCode);
            }
            return root;
        } catch (RuntimeException e) {
            throw new TourApiException(operation, e);
        }
    }

    private OfficialPlace officialPlace(JsonNode n) {
        Double lng = doubleOrNull(n, "mapx");
        Double lat = doubleOrNull(n, "mapy");
        boolean valid = lng != null && lat != null && Double.isFinite(lng) && Double.isFinite(lat)
                && lng >= -180 && lng <= 180 && lat >= -90 && lat <= 90;
        return new OfficialPlace(text(n, "contentid"), intValue(n, "contenttypeid"), text(n, "title"),
                blankToNull(text(n, "addr1")), blankToNull(text(n, "firstimage")),
                valid ? lat : null, valid ? lng : null, intValue(n, "areacode"),
                integerOrNull(n, "sigungucode"), parseModified(text(n, "modifiedtime")),
                "0".equals(text(n, "showflag")) || "D".equalsIgnoreCase(text(n, "showflag")));
    }

    private static List<JsonNode> items(JsonNode root) {
        JsonNode node = root.path("response").path("body").path("items").path("item");
        List<JsonNode> result = new ArrayList<>();
        if (node.isArray()) node.forEach(result::add);
        else if (node.isObject()) result.add(node);
        return result;
    }

    private String service(String language) {
        if (language == null) return "KorService2";
        if (language.startsWith("en")) return "EngService2";
        if (language.startsWith("ja")) return "JpnService2";
        if (language.startsWith("zh-CN")) return "ChsService2";
        if (language.startsWith("zh")) return "ChtService2";
        return "KorService2";
    }

    private void requireKey() {
        if (properties.serviceKey() == null || properties.serviceKey().isBlank())
            throw new ApiException(ErrorCode.COMMON_503);
    }

    private static String text(JsonNode node, String field) { return node.path(field).asText(""); }
    private static int intValue(JsonNode node, String field) { return node.path(field).asInt(); }
    private static Integer integerOrNull(JsonNode node, String field) {
        String value = text(node, field); return value.isBlank() ? null : Integer.valueOf(value);
    }
    private static Double doubleOrNull(JsonNode node, String field) {
        String value = text(node, field); try { return value.isBlank() ? null : Double.valueOf(value); } catch (NumberFormatException e) { return null; }
    }
    private static Instant parseModified(String value) {
        try { return LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyyMMddHHmmss")).atZone(KST).toInstant(); }
        catch (RuntimeException e) { return null; }
    }
    private static LocalDate parseDate(String value) {
        try { return LocalDate.parse(value,DateTimeFormatter.BASIC_ISO_DATE); }
        catch (RuntimeException failure) { return null; }
    }
    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value; }

    public record CodeItem(int code, String name) { }
    public record OfficialPlace(String contentId, int contentTypeId, String title, String addr1,
                                String imageUrl, Double lat, Double lng, int areaCode,
                                Integer sigunguCode, Instant modifiedAt, boolean deleted) { }
    public record PlacePage(List<OfficialPlace> items, int totalCount) { }
    public record Festival(String contentId, String title, String addr1, String imageUrl,
                           Double lat, Double lng, int areaCode, Integer sigunguCode,
                           LocalDate startDate, LocalDate endDate) { }
    public record FestivalPage(List<Festival> items, int totalCount) { }
    public static class TourApiException extends RuntimeException {
        public TourApiException(String operation, Throwable cause) { super(operation, cause); }
    }
}
