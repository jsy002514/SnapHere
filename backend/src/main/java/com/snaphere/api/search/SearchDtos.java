package com.snaphere.api.search;

import com.snaphere.api.place.PlaceDtos;
import com.snaphere.api.post.dto.PostSummaryResponse;
import com.snaphere.api.post.dto.TagSummaryResponse;
import com.snaphere.api.social.SocialDtos;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class SearchDtos {
    private SearchDtos() { }

    public record SearchSection<T>(List<T> items, String nextCursor, boolean hasNext,
                                   Integer totalApproximate) {
        public static <T> SearchSection<T> skipped() {
            return new SearchSection<>(List.of(), null, false, null);
        }
    }

    public record SearchResult(
            String query,
            SearchSection<PlaceDtos.PlaceSummary> places,
            SearchSection<PostSummaryResponse> posts,
            SearchSection<SocialDtos.UserSummary> users,
            SearchSection<TagSummaryResponse> tags,
            PlaceDtos.Region matchedRegion
    ) { }

    public record PopularKeyword(int rank, String keyword, long searchCount, Integer areaCode) { }

    public record RecentSearch(UUID searchLogId, String keyword, OffsetDateTime searchedAt) { }
}
