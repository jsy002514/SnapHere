package com.snaphere.api.search;

import com.snaphere.api.auth.ExternalIds;
import com.snaphere.api.auth.User;
import com.snaphere.api.auth.UserRepository;
import com.snaphere.api.auth.UserStatus;
import com.snaphere.api.common.error.ApiException;
import com.snaphere.api.common.error.ErrorCode;
import com.snaphere.api.place.PlaceDtos;
import com.snaphere.api.post.PostResponseAssembler;
import com.snaphere.api.post.PostStatus;
import com.snaphere.api.post.dto.PostSummaryResponse;
import com.snaphere.api.post.dto.TagSummaryResponse;
import com.snaphere.api.post.entity.PostEntity;
import com.snaphere.api.post.entity.TagEntity;
import com.snaphere.api.post.repository.PostRepository;
import com.snaphere.api.post.repository.TagRepository;
import com.snaphere.api.social.FollowRepository;
import com.snaphere.api.social.SocialDtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class SearchService {
    private static final Logger log = LoggerFactory.getLogger(SearchService.class);
    private static final int DEFAULT_SIZE = 5;
    private static final int MAX_SIZE = 50;
    private static final int DEFAULT_POPULAR_LIMIT = 10;
    private static final int MAX_POPULAR_LIMIT = 50;

    private final SearchRepository searches;
    private final com.snaphere.api.place.PlaceRepository places;
    private final PostRepository posts;
    private final PostResponseAssembler postResponses;
    private final UserRepository users;
    private final FollowRepository follows;
    private final TagRepository tags;
    private final RecentSearchStore recentSearches;
    private final PopularSearchCache popularCache;

    public SearchService(SearchRepository searches,
                         com.snaphere.api.place.PlaceRepository places,
                         PostRepository posts,
                         PostResponseAssembler postResponses,
                         UserRepository users,
                         FollowRepository follows,
                         TagRepository tags,
                         RecentSearchStore recentSearches,
                         PopularSearchCache popularCache) {
        this.searches = searches;
        this.places = places;
        this.posts = posts;
        this.postResponses = postResponses;
        this.users = users;
        this.follows = follows;
        this.tags = tags;
        this.recentSearches = recentSearches;
        this.popularCache = popularCache;
    }

    public SearchDtos.SearchResult search(String rawQuery, List<SearchType> requestedTypes,
                                          Integer requestedAreaCode, String encodedCursor,
                                          Integer requestedSize, Optional<UUID> viewerId) {
        String displayQuery = requireQuery(rawQuery);
        String normalizedQuery = SearchText.normalize(displayQuery);
        List<PlaceDtos.Region> regions = places.regions();
        PlaceDtos.Region matchedRegion = SearchText.matchedRegion(regions, normalizedQuery);
        Integer areaCode = matchedRegion == null
                ? requestedAreaCode : Integer.valueOf(matchedRegion.areaCode());
        requireArea(regions, areaCode);
        boolean regionMode = matchedRegion != null;
        int size = resolveSize(requestedSize);

        Set<SearchType> selected = requestedTypes == null || requestedTypes.isEmpty()
                ? EnumSet.allOf(SearchType.class)
                : new LinkedHashSet<>(requestedTypes);
        SearchCursor cursor = SearchCursor.decode(encodedCursor);
        if (cursor != null && selected.size() != 1) {
            throw new ApiException(ErrorCode.COMMON_400, Map.of("field", "types"));
        }
        String fingerprint = SearchCursor.fingerprint(normalizedQuery, areaCode, regionMode);
        if (cursor != null) cursor.require(selected.iterator().next(), fingerprint);

        SearchDtos.SearchSection<PlaceDtos.PlaceSummary> placeSection = selected.contains(SearchType.PLACE)
                ? placeSection(normalizedQuery, regionMode, areaCode,
                    cursorFor(cursor, SearchType.PLACE), size, fingerprint, viewerId.orElse(null))
                : SearchDtos.SearchSection.skipped();
        SearchDtos.SearchSection<PostSummaryResponse> postSection = selected.contains(SearchType.POST)
                ? postSection(normalizedQuery, regionMode, areaCode,
                    cursorFor(cursor, SearchType.POST), size, fingerprint, viewerId)
                : SearchDtos.SearchSection.skipped();
        SearchDtos.SearchSection<SocialDtos.UserSummary> userSection = selected.contains(SearchType.USER)
                ? userSection(normalizedQuery, regionMode, areaCode,
                    cursorFor(cursor, SearchType.USER), size, fingerprint, viewerId)
                : SearchDtos.SearchSection.skipped();
        String tagQuery = SearchText.normalizeTag(displayQuery);
        SearchDtos.SearchSection<TagSummaryResponse> tagSection = selected.contains(SearchType.TAG)
                ? tagSection(tagQuery, regionMode, areaCode,
                    cursorFor(cursor, SearchType.TAG), size, fingerprint)
                : SearchDtos.SearchSection.skipped();

        if (cursor == null) {
            recordFirstSearch(viewerId, normalizedQuery, displayQuery, areaCode);
        }
        return new SearchDtos.SearchResult(displayQuery, placeSection, postSection,
                userSection, tagSection, matchedRegion);
    }

    public List<SearchDtos.PopularKeyword> popular(Integer areaCode, Integer requestedLimit) {
        List<PlaceDtos.Region> regions = places.regions();
        requireArea(regions, areaCode);
        int limit = requestedLimit == null || requestedLimit <= 0
                ? DEFAULT_POPULAR_LIMIT : Math.min(requestedLimit, MAX_POPULAR_LIMIT);
        return popularCache.get(areaCode, limit).orElseGet(() -> {
            List<SearchRepository.PopularRow> rows = searches.popular(areaCode,
                    OffsetDateTime.now(ZoneOffset.UTC).minusDays(7), limit);
            List<SearchDtos.PopularKeyword> result = new ArrayList<>(rows.size());
            for (int i = 0; i < rows.size(); i++) {
                SearchRepository.PopularRow row = rows.get(i);
                result.add(new SearchDtos.PopularKeyword(i + 1, row.keyword(), row.count(), areaCode));
            }
            List<SearchDtos.PopularKeyword> immutable = List.copyOf(result);
            popularCache.put(areaCode, limit, immutable);
            return immutable;
        });
    }

    public List<SearchDtos.RecentSearch> recent(UUID userId) {
        return recentSearches.recent(userId);
    }

    public void deleteRecent(UUID userId, String keyword) {
        if (keyword == null) {
            recentSearches.clear(userId);
            return;
        }
        String display = requireQuery(keyword);
        recentSearches.remove(userId, SearchText.normalize(display));
    }

    private SearchDtos.SearchSection<PlaceDtos.PlaceSummary> placeSection(
            String query, boolean regionMode, Integer areaCode, SearchCursor cursor,
            int size, String fingerprint, UUID viewerId) {
        List<SearchRepository.PlaceHit> rows = searches.places(query, regionMode, areaCode, cursor, size + 1);
        boolean hasNext = rows.size() > size;
        List<SearchRepository.PlaceHit> page = hasNext ? rows.subList(0, size) : rows;
        int total = count(searches.countPlaces(query, regionMode, areaCode));
        if (page.isEmpty()) return new SearchDtos.SearchSection<>(List.of(), null, false, total);
        List<Long> ids = page.stream().map(SearchRepository.PlaceHit::id).toList();
        Map<String, PlaceDtos.PlaceSummary> found = new HashMap<>();
        for (PlaceDtos.PlaceSummary place : places.summaries(ids, viewerId)) found.put(place.placeId(), place);
        List<PlaceDtos.PlaceSummary> items = ids.stream()
                .map(id -> found.get(ExternalIds.place(id))).filter(java.util.Objects::nonNull).toList();
        String next = hasNext ? placeCursor(page.getLast(), fingerprint).encode() : null;
        return new SearchDtos.SearchSection<>(items, next, hasNext, total);
    }

    private SearchDtos.SearchSection<PostSummaryResponse> postSection(
            String query, boolean regionMode, Integer areaCode, SearchCursor cursor,
            int size, String fingerprint, Optional<UUID> viewerId) {
        List<SearchRepository.PostHit> rows = searches.posts(query, regionMode, areaCode, cursor, size + 1);
        boolean hasNext = rows.size() > size;
        List<SearchRepository.PostHit> page = hasNext ? rows.subList(0, size) : rows;
        int total = count(searches.countPosts(query, regionMode, areaCode));
        if (page.isEmpty()) return new SearchDtos.SearchSection<>(List.of(), null, false, total);
        Map<Long, PostEntity> found = new LinkedHashMap<>();
        posts.findAllById(page.stream().map(SearchRepository.PostHit::id).toList()).stream()
                .filter(post -> post.getStatus() == PostStatus.ACTIVE)
                .forEach(post -> found.put(post.getPostId(), post));
        List<PostEntity> ordered = page.stream().map(hit -> found.get(hit.id()))
                .filter(java.util.Objects::nonNull).toList();
        String next = hasNext ? postCursor(page.getLast(), fingerprint).encode() : null;
        return new SearchDtos.SearchSection<>(postResponses.summaries(ordered, viewerId), next, hasNext, total);
    }

    private SearchDtos.SearchSection<SocialDtos.UserSummary> userSection(
            String query, boolean regionMode, Integer areaCode, SearchCursor cursor,
            int size, String fingerprint, Optional<UUID> viewerId) {
        List<SearchRepository.UserHit> rows = searches.users(query, regionMode, areaCode, cursor, size + 1);
        boolean hasNext = rows.size() > size;
        List<SearchRepository.UserHit> page = hasNext ? rows.subList(0, size) : rows;
        int total = count(searches.countUsers(query, regionMode, areaCode));
        if (page.isEmpty()) return new SearchDtos.SearchSection<>(List.of(), null, false, total);
        Set<UUID> ids = new LinkedHashSet<>();
        page.forEach(hit -> ids.add(hit.id()));
        Map<UUID, User> found = new HashMap<>();
        users.findAllById(ids).stream().filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .forEach(user -> found.put(user.getId(), user));
        Set<UUID> following = viewerId.map(id -> follows.followed(id, ids)).orElse(Set.of());
        Set<UUID> followedBy = viewerId.map(id -> follows.followedBy(id, ids)).orElse(Set.of());
        List<SocialDtos.UserSummary> items = page.stream().map(hit -> found.get(hit.id()))
                .filter(java.util.Objects::nonNull)
                .map(user -> new SocialDtos.UserSummary(user.getId(), user.getNickname(),
                        user.getProfileImageUrl(), user.getBio(),
                        viewerId.isPresent() ? following.contains(user.getId()) : null,
                        viewerId.isPresent() ? followedBy.contains(user.getId()) : null))
                .toList();
        String next = hasNext ? userCursor(page.getLast(), fingerprint).encode() : null;
        return new SearchDtos.SearchSection<>(items, next, hasNext, total);
    }

    private SearchDtos.SearchSection<TagSummaryResponse> tagSection(
            String query, boolean regionMode, Integer areaCode, SearchCursor cursor,
            int size, String fingerprint) {
        if (!regionMode && query.isBlank()) {
            return new SearchDtos.SearchSection<>(List.of(), null, false, 0);
        }
        List<SearchRepository.TagHit> rows = searches.tags(query, regionMode, areaCode, cursor, size + 1);
        boolean hasNext = rows.size() > size;
        List<SearchRepository.TagHit> page = hasNext ? rows.subList(0, size) : rows;
        int total = count(searches.countTags(query, regionMode, areaCode));
        if (page.isEmpty()) return new SearchDtos.SearchSection<>(List.of(), null, false, total);
        Map<Long, TagEntity> found = new HashMap<>();
        tags.findAllById(page.stream().map(SearchRepository.TagHit::id).toList())
                .forEach(tag -> found.put(tag.getTagId(), tag));
        List<TagSummaryResponse> items = page.stream().map(hit -> found.get(hit.id()))
                .filter(java.util.Objects::nonNull)
                .map(tag -> TagSummaryResponse.popular(tag, tag.getUsageCount())).toList();
        String next = hasNext ? tagCursor(page.getLast(), fingerprint).encode() : null;
        return new SearchDtos.SearchSection<>(items, next, hasNext, total);
    }

    private void recordFirstSearch(Optional<UUID> viewerId, String normalizedQuery,
                                   String displayQuery, Integer areaCode) {
        try {
            searches.record(normalizedQuery, areaCode);
        } catch (RuntimeException failure) {
            log.warn("검색 로그 기록 실패 keyword={}", normalizedQuery, failure);
        }
        viewerId.ifPresent(id -> recentSearches.record(id, normalizedQuery, displayQuery));
    }

    private static SearchCursor cursorFor(SearchCursor cursor, SearchType type) {
        return cursor != null && cursor.type() == type ? cursor : null;
    }

    private static SearchCursor placeCursor(SearchRepository.PlaceHit hit, String fingerprint) {
        return new SearchCursor(SearchType.PLACE, fingerprint, hit.matchRank(),
                Integer.toString(hit.postCount()), Integer.toString(hit.viewCount()), Long.toString(hit.id()));
    }

    private static SearchCursor postCursor(SearchRepository.PostHit hit, String fingerprint) {
        return new SearchCursor(SearchType.POST, fingerprint, hit.matchRank(),
                hit.createdAt().toString(), Long.toString(hit.id()), "");
    }

    private static SearchCursor userCursor(SearchRepository.UserHit hit, String fingerprint) {
        return new SearchCursor(SearchType.USER, fingerprint, hit.matchRank(),
                Integer.toString(hit.followerCount()), Integer.toString(hit.postCount()), hit.id().toString());
    }

    private static SearchCursor tagCursor(SearchRepository.TagHit hit, String fingerprint) {
        return new SearchCursor(SearchType.TAG, fingerprint, hit.matchRank(),
                Long.toString(hit.usageCount()), Long.toString(hit.id()), "");
    }

    private static int count(long value) {
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

    private static int resolveSize(Integer requested) {
        if (requested == null || requested <= 0) return DEFAULT_SIZE;
        return Math.min(requested, MAX_SIZE);
    }

    private static String requireQuery(String raw) {
        String query = SearchText.display(raw);
        if (query == null || query.isEmpty() || query.length() > 100) {
            throw new ApiException(ErrorCode.COMMON_400, Map.of("field", "q"));
        }
        return query;
    }

    private static void requireArea(List<PlaceDtos.Region> regions, Integer areaCode) {
        if (areaCode != null && regions.stream().noneMatch(region -> region.areaCode() == areaCode)) {
            throw new ApiException(ErrorCode.COMMON_400, Map.of("field", "areaCode"));
        }
    }
}
