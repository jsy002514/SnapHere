package com.snaphere.api;

import org.junit.jupiter.api.Test;
import com.snaphere.api.auth.ExternalIds;
import com.snaphere.api.common.error.ErrorCode;
import com.snaphere.api.common.web.CursorCodec;
import com.snaphere.api.place.PlaceRepository;
import org.junit.jupiter.api.Assertions;

class SnapHereApplicationTests {

    @Test
    void 외부_ID와_커서를_왕복한다() {
        String placeId = ExternalIds.place(12345);
        Assertions.assertEquals(12345, ExternalIds.parse(placeId, "plc", ErrorCode.PLACE_NOT_FOUND));
        Assertions.assertEquals(12345, CursorCodec.decode(CursorCodec.encode(12345)));
    }

    @Test
    void 장소명과_장소_태그를_일관되게_정규화한다() {
        Assertions.assertEquals("경복궁 근정전", PlaceRepository.normalizeTitle("  경복궁   근정전 "));
        Assertions.assertEquals("경복궁근정전", PlaceRepository.normalizeTag(" #경복궁 근정전 "));
    }
}
