package com.snaphere.api.place;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleGeocodingClientTests {

    @Test
    void normalizesGoogleAdministrativeAreaNamesToTourApiRegionNames() {
        assertThat(GoogleGeocodingClient.normalizeRegion("서울특별시")).isEqualTo("서울");
        assertThat(GoogleGeocodingClient.normalizeRegion("세종특별자치시")).isEqualTo("세종");
        assertThat(GoogleGeocodingClient.normalizeRegion("강원특별자치도")).isEqualTo("강원");
        assertThat(GoogleGeocodingClient.normalizeRegion("충청북도")).isEqualTo("충북");
        assertThat(GoogleGeocodingClient.normalizeRegion("전북특별자치도")).isEqualTo("전북");
        assertThat(GoogleGeocodingClient.normalizeRegion("경상남도")).isEqualTo("경남");
    }
}
