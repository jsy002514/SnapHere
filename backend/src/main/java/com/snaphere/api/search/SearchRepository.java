package com.snaphere.api.search;

import com.snaphere.api.common.error.ApiException;
import com.snaphere.api.common.error.ErrorCode;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class SearchRepository {
    private final JdbcClient jdbc;

    public SearchRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<PlaceHit> places(String query, boolean regionMode, Integer areaCode,
                                 SearchCursor cursor, int limit) {
        String rank = regionMode ? "0" : "case when p.normalized_title=:q then 0 "
                + "when p.normalized_title like :prefix then 1 "
                + "when p.normalized_title like :contains then 2 else 3 end";
        StringBuilder sql = new StringBuilder("select * from (select p.place_id,p.post_count,p.view_count,")
                .append(rank).append(" match_rank from places p where p.status='ACTIVE'");
        Map<String, Object> params = new HashMap<>();
        appendArea(sql, params, "p", areaCode);
        if (!regionMode) {
            sql.append(" and (p.normalized_title like :contains or lower(coalesce(p.addr1,'')) like :contains)");
            addTextParams(params, query);
        }
        sql.append(") h where 1=1");
        if (cursor != null) {
            int posts = integer(cursor.first());
            int views = integer(cursor.second());
            long id = longValue(cursor.third());
            sql.append(" and (h.match_rank>:cr or (h.match_rank=:cr and h.post_count<:cp) ")
                    .append("or (h.match_rank=:cr and h.post_count=:cp and h.view_count<:cv) ")
                    .append("or (h.match_rank=:cr and h.post_count=:cp and h.view_count=:cv and h.place_id>:cid))");
            params.put("cr", cursor.matchRank());
            params.put("cp", posts);
            params.put("cv", views);
            params.put("cid", id);
        }
        sql.append(" order by h.match_rank,h.post_count desc,h.view_count desc,h.place_id limit :limit");
        params.put("limit", limit);
        return jdbc.sql(sql.toString()).params(params).query((rs, row) -> new PlaceHit(
                rs.getLong("place_id"), rs.getInt("match_rank"), rs.getInt("post_count"),
                rs.getInt("view_count"))).list();
    }

    public long countPlaces(String query, boolean regionMode, Integer areaCode) {
        StringBuilder sql = new StringBuilder("select count(*) from places p where p.status='ACTIVE'");
        Map<String, Object> params = new HashMap<>();
        appendArea(sql, params, "p", areaCode);
        if (!regionMode) {
            sql.append(" and (p.normalized_title like :contains or lower(coalesce(p.addr1,'')) like :contains)");
            params.put("contains", '%' + query + '%');
        }
        return jdbc.sql(sql.toString()).params(params).query(Long.class).single();
    }

    public List<PostHit> posts(String query, boolean regionMode, Integer areaCode,
                               SearchCursor cursor, int limit) {
        String rank = regionMode ? "0" : "case when lower(coalesce(p.content,''))=:q then 0 "
                + "when lower(coalesce(p.content,'')) like :prefix then 1 else 2 end";
        StringBuilder sql = new StringBuilder("select * from (select p.post_id,p.created_at,")
                .append(rank).append(" match_rank from posts p where p.status='ACTIVE'");
        Map<String, Object> params = new HashMap<>();
        appendArea(sql, params, "p", areaCode);
        if (!regionMode) {
            sql.append(" and lower(coalesce(p.content,'')) like :contains");
            addTextParams(params, query);
        }
        sql.append(") h where 1=1");
        if (cursor != null) {
            OffsetDateTime createdAt = offsetDateTime(cursor.first());
            long id = longValue(cursor.second());
            sql.append(" and (h.match_rank>:cr or (h.match_rank=:cr and h.created_at<:cat) ")
                    .append("or (h.match_rank=:cr and h.created_at=:cat and h.post_id<:cid))");
            params.put("cr", cursor.matchRank());
            params.put("cat", createdAt);
            params.put("cid", id);
        }
        sql.append(" order by h.match_rank,h.created_at desc,h.post_id desc limit :limit");
        params.put("limit", limit);
        return jdbc.sql(sql.toString()).params(params).query((rs, row) -> new PostHit(
                rs.getLong("post_id"), rs.getInt("match_rank"),
                rs.getObject("created_at", OffsetDateTime.class))).list();
    }

    public long countPosts(String query, boolean regionMode, Integer areaCode) {
        StringBuilder sql = new StringBuilder("select count(*) from posts p where p.status='ACTIVE'");
        Map<String, Object> params = new HashMap<>();
        appendArea(sql, params, "p", areaCode);
        if (!regionMode) {
            sql.append(" and lower(coalesce(p.content,'')) like :contains");
            params.put("contains", '%' + query + '%');
        }
        return jdbc.sql(sql.toString()).params(params).query(Long.class).single();
    }

    public List<UserHit> users(String query, boolean regionMode, Integer areaCode,
                               SearchCursor cursor, int limit) {
        String rank = regionMode ? "0" : "case when lower(u.nickname)=:q then 0 else 1 end";
        StringBuilder sql = new StringBuilder("select * from (select u.id,u.follower_count,u.post_count,")
                .append(rank).append(" match_rank from users u where u.status='ACTIVE' and u.nickname is not null");
        Map<String, Object> params = new HashMap<>();
        appendUserArea(sql, params, areaCode);
        if (!regionMode) {
            sql.append(" and lower(u.nickname) like :prefix");
            addTextParams(params, query);
        }
        sql.append(") h where 1=1");
        if (cursor != null) {
            int followers = integer(cursor.first());
            int posts = integer(cursor.second());
            UUID id = uuid(cursor.third());
            sql.append(" and (h.match_rank>:cr or (h.match_rank=:cr and h.follower_count<:cf) ")
                    .append("or (h.match_rank=:cr and h.follower_count=:cf and h.post_count<:cp) ")
                    .append("or (h.match_rank=:cr and h.follower_count=:cf and h.post_count=:cp and h.id>:cid))");
            params.put("cr", cursor.matchRank());
            params.put("cf", followers);
            params.put("cp", posts);
            params.put("cid", id);
        }
        sql.append(" order by h.match_rank,h.follower_count desc,h.post_count desc,h.id limit :limit");
        params.put("limit", limit);
        return jdbc.sql(sql.toString()).params(params).query((rs, row) -> new UserHit(
                rs.getObject("id", UUID.class), rs.getInt("match_rank"),
                rs.getInt("follower_count"), rs.getInt("post_count"))).list();
    }

    public long countUsers(String query, boolean regionMode, Integer areaCode) {
        StringBuilder sql = new StringBuilder(
                "select count(*) from users u where u.status='ACTIVE' and u.nickname is not null");
        Map<String, Object> params = new HashMap<>();
        appendUserArea(sql, params, areaCode);
        if (!regionMode) {
            sql.append(" and lower(u.nickname) like :prefix");
            params.put("prefix", query + '%');
        }
        return jdbc.sql(sql.toString()).params(params).query(Long.class).single();
    }

    public List<TagHit> tags(String query, boolean regionMode, Integer areaCode,
                             SearchCursor cursor, int limit) {
        String rank = regionMode ? "0" : "case when t.normalized_name=:q then 0 else 1 end";
        StringBuilder sql = new StringBuilder("select * from (select t.tag_id,t.usage_count,")
                .append(rank).append(" match_rank from tags t where 1=1");
        Map<String, Object> params = new HashMap<>();
        appendTagArea(sql, params, areaCode);
        if (!regionMode) {
            sql.append(" and t.normalized_name like :prefix");
            addTextParams(params, query);
        }
        sql.append(") h where 1=1");
        if (cursor != null) {
            long usage = longValue(cursor.first());
            long id = longValue(cursor.second());
            sql.append(" and (h.match_rank>:cr or (h.match_rank=:cr and h.usage_count<:cu) ")
                    .append("or (h.match_rank=:cr and h.usage_count=:cu and h.tag_id>:cid))");
            params.put("cr", cursor.matchRank());
            params.put("cu", usage);
            params.put("cid", id);
        }
        sql.append(" order by h.match_rank,h.usage_count desc,h.tag_id limit :limit");
        params.put("limit", limit);
        return jdbc.sql(sql.toString()).params(params).query((rs, row) -> new TagHit(
                rs.getLong("tag_id"), rs.getInt("match_rank"), rs.getLong("usage_count"))).list();
    }

    public long countTags(String query, boolean regionMode, Integer areaCode) {
        StringBuilder sql = new StringBuilder("select count(*) from tags t where 1=1");
        Map<String, Object> params = new HashMap<>();
        appendTagArea(sql, params, areaCode);
        if (!regionMode) {
            sql.append(" and t.normalized_name like :prefix");
            params.put("prefix", query + '%');
        }
        return jdbc.sql(sql.toString()).params(params).query(Long.class).single();
    }

    public void record(String normalizedKeyword, Integer areaCode) {
        JdbcClient.StatementSpec spec = jdbc.sql(
                        "insert into search_logs(keyword,area_code,searched_at) values(:keyword,:area,now())")
                .param("keyword", normalizedKeyword);
        spec = areaCode == null ? spec.param("area", null, java.sql.Types.INTEGER)
                : spec.param("area", areaCode);
        spec.update();
    }

    public List<PopularRow> popular(Integer areaCode, OffsetDateTime cutoff, int limit) {
        StringBuilder sql = new StringBuilder("select keyword,count(*) search_count,max(searched_at) latest ")
                .append("from search_logs where searched_at>=:cutoff");
        Map<String, Object> params = new HashMap<>();
        params.put("cutoff", cutoff);
        if (areaCode != null) {
            sql.append(" and area_code=:area");
            params.put("area", areaCode);
        }
        sql.append(" group by keyword order by search_count desc,latest desc,keyword limit :limit");
        params.put("limit", limit);
        return jdbc.sql(sql.toString()).params(params).query((rs, row) ->
                new PopularRow(rs.getString("keyword"), rs.getLong("search_count"))).list();
    }

    public int purgeBefore(OffsetDateTime cutoff) {
        return jdbc.sql("delete from search_logs where searched_at<:cutoff")
                .param("cutoff", cutoff).update();
    }

    private static void appendArea(StringBuilder sql, Map<String, Object> params,
                                   String alias, Integer areaCode) {
        if (areaCode != null) {
            sql.append(" and ").append(alias).append(".area_code=:area");
            params.put("area", areaCode);
        }
    }

    private static void appendUserArea(StringBuilder sql, Map<String, Object> params, Integer areaCode) {
        if (areaCode != null) {
            sql.append(" and exists(select 1 from posts ap where ap.user_id=u.id ")
                    .append("and ap.status='ACTIVE' and ap.area_code=:area)");
            params.put("area", areaCode);
        }
    }

    private static void appendTagArea(StringBuilder sql, Map<String, Object> params, Integer areaCode) {
        if (areaCode != null) {
            sql.append(" and exists(select 1 from post_tags apt join posts ap on ap.post_id=apt.post_id ")
                    .append("where apt.tag_id=t.tag_id and ap.status='ACTIVE' and ap.area_code=:area)");
            params.put("area", areaCode);
        }
    }

    private static void addTextParams(Map<String, Object> params, String query) {
        params.put("q", query);
        params.put("prefix", query + '%');
        params.put("contains", '%' + query + '%');
    }

    private static int integer(String value) {
        try { return Integer.parseInt(value); }
        catch (RuntimeException invalid) { throw invalidCursor(); }
    }

    private static long longValue(String value) {
        try { return Long.parseLong(value); }
        catch (RuntimeException invalid) { throw invalidCursor(); }
    }

    private static UUID uuid(String value) {
        try { return UUID.fromString(value); }
        catch (RuntimeException invalid) { throw invalidCursor(); }
    }

    private static OffsetDateTime offsetDateTime(String value) {
        try { return OffsetDateTime.parse(value); }
        catch (RuntimeException invalid) { throw invalidCursor(); }
    }

    private static ApiException invalidCursor() {
        return new ApiException(ErrorCode.COMMON_400, Map.of("field", "cursor"));
    }

    public record PlaceHit(long id, int matchRank, int postCount, int viewCount) { }
    public record PostHit(long id, int matchRank, OffsetDateTime createdAt) { }
    public record UserHit(UUID id, int matchRank, int followerCount, int postCount) { }
    public record TagHit(long id, int matchRank, long usageCount) { }
    public record PopularRow(String keyword, long count) { }
}
