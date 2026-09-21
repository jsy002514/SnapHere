package com.snaphere.api.search;

import com.snaphere.api.common.error.ApiException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchCursorTest {
    @Test
    void roundTripsTypeFingerprintAndPosition() {
        SearchCursor cursor = new SearchCursor(SearchType.PLACE, "abc123", 2, "10", "5", "99");

        assertThat(SearchCursor.decode(cursor.encode())).isEqualTo(cursor);
    }

    @Test
    void rejectsMalformedCursor() {
        assertThatThrownBy(() -> SearchCursor.decode("not-base64"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void rejectsCursorFromAnotherQuery() {
        SearchCursor cursor = new SearchCursor(SearchType.TAG, "old", 1, "2", "3", "");

        assertThatThrownBy(() -> cursor.require(SearchType.TAG, "new"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void fingerprintChangesWithAreaAndRegionMode() {
        assertThat(SearchCursor.fingerprint("서울", 1, true))
                .isNotEqualTo(SearchCursor.fingerprint("서울", 1, false))
                .isNotEqualTo(SearchCursor.fingerprint("서울", 6, true));
    }
}
