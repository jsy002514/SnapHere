package com.ssafy.snaphere.domain.post.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.snaphere.domain.post.entity.PostSource;
import com.ssafy.snaphere.domain.post.entity.PostTier;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 위치 신뢰도 판정 테스트.
 *
 * 이 프로젝트에서 가장 중요한 테스트다. Tier 가 틀리면 랭킹·방문기록·히트맵이 전부 함께 틀어지고,
 * "현장 인증" 배지가 의미를 잃는다. 경계값을 하나씩 못박아 둔다.
 */
class TierEvaluatorTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 22, 12, 0, 0);
    private static final int ON_SITE_WINDOW = 10;   // 분
    private static final int CONFIRMED_DAYS = 30;

    private PostTier tierOf(Integer distance, int radius, PostSource source, LocalDateTime takenAt) {
        return TierEvaluator.evaluate(distance, radius, source, takenAt, NOW,
                ON_SITE_WINDOW, CONFIRMED_DAYS).tier();
    }

    @Nested
    @DisplayName("좌표와 인증 반경")
    class Distance {

        @Test
        @DisplayName("좌표가 없으면 NO_LOCATION — 위치를 모르는 사진은 인증할 수 없다")
        void noCoordinate() {
            assertThat(tierOf(null, 500, PostSource.CAMERA, NOW)).isEqualTo(PostTier.NO_LOCATION);
        }

        @Test
        @DisplayName("인증 반경 경계값은 포함이다 (500m / 500m → 통과)")
        void radiusBoundaryInclusive() {
            assertThat(tierOf(500, 500, PostSource.CAMERA, NOW)).isEqualTo(PostTier.ON_SITE);
        }

        @Test
        @DisplayName("반경을 1m라도 넘으면 NO_LOCATION")
        void radiusExceeded() {
            assertThat(tierOf(501, 500, PostSource.CAMERA, NOW)).isEqualTo(PostTier.NO_LOCATION);
        }

        @Test
        @DisplayName("사용자 장소는 반경이 100m로 더 좁다")
        void userPlaceRadius() {
            assertThat(tierOf(100, 100, PostSource.CAMERA, NOW)).isEqualTo(PostTier.ON_SITE);
            assertThat(tierOf(150, 100, PostSource.CAMERA, NOW)).isEqualTo(PostTier.NO_LOCATION);
        }
    }

    @Nested
    @DisplayName("촬영 시각과 촬영 방식")
    class Timing {

        @Test
        @DisplayName("앱 카메라 + 10분 이내면 ON_SITE (경계값 포함)")
        void onSiteWindow() {
            assertThat(tierOf(10, 500, PostSource.CAMERA, NOW)).isEqualTo(PostTier.ON_SITE);
            assertThat(tierOf(10, 500, PostSource.CAMERA, NOW.minusMinutes(10))).isEqualTo(PostTier.ON_SITE);
        }

        @Test
        @DisplayName("카메라라도 11분이 지나면 LOCATION_CONFIRMED로 내려간다")
        void onSiteWindowExpired() {
            assertThat(tierOf(10, 500, PostSource.CAMERA, NOW.minusMinutes(11)))
                    .isEqualTo(PostTier.LOCATION_CONFIRMED);
        }

        @Test
        @DisplayName("갤러리 업로드는 방금 찍었더라도 ON_SITE가 될 수 없다")
        void galleryNeverOnSite() {
            assertThat(tierOf(10, 500, PostSource.GALLERY, NOW)).isEqualTo(PostTier.LOCATION_CONFIRMED);
        }

        @Test
        @DisplayName("30일 경계는 포함, 31일은 NO_LOCATION")
        void confirmedDaysBoundary() {
            assertThat(tierOf(10, 500, PostSource.GALLERY, NOW.minusDays(30)))
                    .isEqualTo(PostTier.LOCATION_CONFIRMED);
            assertThat(tierOf(10, 500, PostSource.GALLERY, NOW.minusDays(31)))
                    .isEqualTo(PostTier.NO_LOCATION);
        }

        @Test
        @DisplayName("오래된 사진을 카메라로 위장해도 통과하지 못한다")
        void oldPhotoCannotFakeCamera() {
            assertThat(tierOf(10, 500, PostSource.CAMERA, NOW.minusDays(31)))
                    .isEqualTo(PostTier.NO_LOCATION);
        }
    }

    @Nested
    @DisplayName("위변조 방어")
    class Tampering {

        @Test
        @DisplayName("EXIF 촬영시각을 지우면 인증되지 않는다")
        void missingTakenAt() {
            assertThat(tierOf(10, 500, PostSource.CAMERA, null)).isEqualTo(PostTier.NO_LOCATION);
        }

        @Test
        @DisplayName("기기 시계 오차 범위(10분)의 미래 값은 허용한다")
        void smallClockSkewAllowed() {
            assertThat(tierOf(10, 500, PostSource.CAMERA, NOW.plusMinutes(10)))
                    .isEqualTo(PostTier.ON_SITE);
        }

        @Test
        @DisplayName("허용 범위를 넘는 미래 시각은 거부한다 — 시계를 조작한 것으로 본다")
        void largeFutureRejected() {
            assertThat(tierOf(10, 500, PostSource.CAMERA, NOW.plusMinutes(11)))
                    .isEqualTo(PostTier.NO_LOCATION);
        }

        @Test
        @DisplayName("미래 25일도 거부한다 — abs 로 계산하면 통과해버리는 구멍이었다")
        void futureDaysMustNotPassAsConfirmed() {
            assertThat(tierOf(10, 500, PostSource.GALLERY, NOW.plusDays(25)))
                    .isEqualTo(PostTier.NO_LOCATION);
        }
    }

    @Nested
    @DisplayName("Tier 별 부수 효과")
    class SideEffects {

        @Test
        @DisplayName("NO_LOCATION 은 랭킹·방문·히트맵 어디에도 기여하지 않는다")
        void noLocationContributesNothing() {
            assertThat(PostTier.NO_LOCATION.countsForRanking()).isFalse();
            assertThat(PostTier.NO_LOCATION.createsVisit()).isFalse();
            assertThat(PostTier.NO_LOCATION.countsForHeatmap()).isFalse();
            assertThat(PostTier.NO_LOCATION.rankingWeight()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("현장 인증이 위치 확인보다 가중치가 높다")
        void onSiteWeighsMore() {
            assertThat(PostTier.ON_SITE.rankingWeight())
                    .isGreaterThan(PostTier.LOCATION_CONFIRMED.rankingWeight());
        }

        @Test
        @DisplayName("문구 키만 주고 완성된 문장은 서버가 만들지 않는다 (다국어)")
        void messageKeyOnly() {
            assertThat(PostTier.ON_SITE.messageKey()).isEqualTo("tier.on_site");
            assertThat(PostTier.LOCATION_CONFIRMED.messageKey()).isEqualTo("tier.location_confirmed");
        }
    }

    @Test
    @DisplayName("판정 근거를 함께 돌려준다 — 사용자에게 왜 미인증인지 설명할 수 있어야 한다")
    void resultCarriesReason() {
        var r = TierEvaluator.evaluate(900, 500, PostSource.CAMERA, NOW, NOW, ON_SITE_WINDOW, CONFIRMED_DAYS);
        assertThat(r.tier()).isEqualTo(PostTier.NO_LOCATION);
        assertThat(r.distanceMeters()).isEqualTo(900);
        assertThat(r.reason()).contains("900").contains("500");
    }
}
