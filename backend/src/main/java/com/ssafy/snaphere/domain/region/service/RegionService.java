package com.ssafy.snaphere.domain.region.service;

import com.ssafy.snaphere.domain.post.dto.PostDtos.ListItem;
import com.ssafy.snaphere.domain.post.service.PostService;
import com.ssafy.snaphere.domain.ranking.service.RankingService;
import com.ssafy.snaphere.domain.region.entity.Region;
import com.ssafy.snaphere.domain.region.entity.RegionStats;
import com.ssafy.snaphere.domain.region.repository.RegionRepository;
import com.ssafy.snaphere.domain.region.repository.RegionStatsRepository;
import com.ssafy.snaphere.global.common.PageRequestParam;
import com.ssafy.snaphere.global.error.BusinessException;
import com.ssafy.snaphere.global.error.ErrorCode;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 지역 · 커뮤니티 홈.
 *
 * ⚠️ 커뮤니티 홈은 한 화면 = 한 번의 호출이다.
 *    인기글·추천장소·랭킹·태그를 각각 부르게 하면 진입에 4번의 왕복이 생겨 화면이 늦게 뜬다.
 */
@Service
@RequiredArgsConstructor
public class RegionService {

    private static final int POPULAR_POST_COUNT = 12;
    private static final int RECOMMEND_COUNT = 6;
    private static final int RANKING_COUNT = 5;
    private static final int TAG_COUNT = 10;

    private final RegionRepository regionRepository;
    private final RegionStatsRepository regionStatsRepository;
    private final PostService postService;
    private final RankingService rankingService;

    @Transactional(readOnly = true)
    public List<RegionItem> listAll() {
        Map<Integer, RegionStats> statsMap = new HashMap<>();
        regionStatsRepository.findAll().forEach(s -> statsMap.put(s.getAreaCode(), s));
        return regionRepository.findAllByOrderBySortOrderAsc().stream()
                .map(r -> RegionItem.of(r, statsMap.get(r.getAreaCode())))
                .toList();
    }

    @Transactional(readOnly = true)
    public CommunityHome community(int areaCode) {
        Region region = regionRepository.findById(areaCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.REGION_001));

        RegionStats stats = regionStatsRepository.findById(areaCode).orElse(null);

        PageRequestParam pageParam = new PageRequestParam();
        pageParam.setPage(1);
        pageParam.setSize(POPULAR_POST_COUNT);

        // 이번주 인기 사진. 사진 있는 글만 대상이다(hasMedia = true).
        var popularPosts = postService.list(areaCode, null, null, "POPULAR", "WEEK", true, null, pageParam);

        return new CommunityHome(
                RegionItem.of(region, stats),
                popularPosts.content(),
                rankingService.recommendations(areaCode, RECOMMEND_COUNT),
                rankingService.places(areaCode, "WEEKLY", RANKING_COUNT),
                rankingService.popularTags(areaCode, TAG_COUNT));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> tags(int areaCode, int limit) {
        return rankingService.popularTags(areaCode, limit);
    }

    public record RegionStatsItem(int placeCount, int userPlaceCount, int postCount,
                                  int contributorCount, int recentPost1h, int recentPost24h,
                                  BigDecimal trafficIntensity) {}

    public record RegionItem(Integer areaCode, String nameKo, String nameEn, String nameJa, String nameZh,
                             BigDecimal centerLat, BigDecimal centerLng,
                             BigDecimal labelLat, BigDecimal labelLng,
                             Integer defaultZoom, String thumbnailUrl,
                             RegionStatsItem stats) {

        static RegionItem of(Region r, RegionStats s) {
            RegionStatsItem stats = s == null
                    ? new RegionStatsItem(0, 0, 0, 0, 0, 0, BigDecimal.ZERO)
                    : new RegionStatsItem(s.getPlaceCount(), s.getUserPlaceCount(), s.getPostCount(),
                            s.getContributorCount(), s.getRecentPost1h(), s.getRecentPost24h(),
                            s.getTrafficIntensity());
            return new RegionItem(r.getAreaCode(), r.getNameKo(), r.getNameEn(), r.getNameJa(), r.getNameZh(),
                    r.getCenterLat(), r.getCenterLng(), r.labelLatOrCenter(), r.labelLngOrCenter(),
                    r.getDefaultZoom(), r.getThumbnailUrl(), stats);
        }
    }

    public record CommunityHome(RegionItem region,
                                List<ListItem> popularPosts,
                                List<Map<String, Object>> recommendedPlaces,
                                RankingService.RankingResponse ranking,
                                List<Map<String, Object>> popularTags) {}
}
