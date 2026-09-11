package com.snaphere.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import com.snaphere.api.map.MapAggregationService;
import com.snaphere.api.map.MapPeriod;
import com.snaphere.api.ranking.RankingAggregationService;
import com.snaphere.api.ranking.RankingPlaceType;
import com.snaphere.api.ranking.RankingPeriod;
import com.snaphere.api.ranking.RankingRepository;
import com.snaphere.api.ranking.RankingScope;
import com.snaphere.api.search.SearchDtos;
import com.snaphere.api.search.RecentSearchStore;
import com.snaphere.api.search.SearchRepository;
import com.snaphere.api.search.SearchService;
import com.snaphere.api.search.SearchType;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.List;
import java.util.Optional;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@SpringBootTest(properties = {
        // stub-data 부재 시 StubPlaceData(matchIfMissing)와 JpaEventSnapshotReader(matchIfMissing)가
        // 동시에 EventSnapshotReader 빈을 등록해 NoUniqueBeanDefinitionException 이 난다.
        // 이 테스트는 실 DB 로 도니 Jpa 리더만 쓰도록 명시한다.
        "snaphere.stub-data=false",
        "snaphere.jobs.enabled=false",
        "snaphere.jobs.place-sync-cron=-",
        "snaphere.jobs.view-flush-cron=-"
})
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class PlaceSchemaIntegrationTests {
    private static final int POSTGRES_PORT = 5432;
    private static final String POSTGRES_DATABASE = "snaphere_test";
    private static final String POSTGRES_USERNAME = "snaphere";
    private static final String POSTGRES_PASSWORD = "snaphere";

    @Container
    static final GenericContainer<?> POSTGRES = new GenericContainer<>(
            new ImageFromDockerfile(
                    "snaphere/percona-postgresql-with-postgis:17.10.2-postgis3.6.2", false)
                    .withDockerfile(customPostgisDockerfile()))
            .withEnv("POSTGRES_DB", POSTGRES_DATABASE)
            .withEnv("POSTGRES_USER", POSTGRES_USERNAME)
            .withEnv("POSTGRES_PASSWORD", POSTGRES_PASSWORD)
            .withExposedPorts(POSTGRES_PORT)
            .waitingFor(Wait.forListeningPort());
    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://" + POSTGRES.getHost()
                + ":" + POSTGRES.getMappedPort(POSTGRES_PORT) + "/" + POSTGRES_DATABASE);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.datasource.username", () -> POSTGRES_USERNAME);
        registry.add("spring.datasource.password", () -> POSTGRES_PASSWORD);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    private static Path customPostgisDockerfile() {
        Path backendWorkingDirectory = Path.of("docker", "postgres", "Dockerfile")
                .toAbsolutePath().normalize();
        if (Files.isRegularFile(backendWorkingDirectory)) return backendWorkingDirectory;

        Path repositoryWorkingDirectory = Path.of("backend", "docker", "postgres", "Dockerfile")
                .toAbsolutePath().normalize();
        if (Files.isRegularFile(repositoryWorkingDirectory)) return repositoryWorkingDirectory;

        throw new IllegalStateException("Custom PostGIS Dockerfile not found");
    }

    @Autowired JdbcClient jdbc;
    @Autowired MapAggregationService mapAggregation;
    @Autowired RankingAggregationService rankingAggregation;
    @Autowired RankingRepository rankingRepository;
    @Autowired com.snaphere.api.place.PlaceRepository placeJdbcRepository;
    @Autowired SearchService searchService;
    @Autowired SearchRepository searchRepository;
    @Autowired RecentSearchStore recentSearchStore;
    @Autowired StringRedisTemplate redis;

    @Test
    void 시도_코드는_비연속_17개이고_고정된_DB_버전과_PostGIS가_활성화된다() {
        assertThat(jdbc.sql("SELECT area_code FROM regions ORDER BY area_code").query(Integer.class).list())
                .containsExactly(1,2,3,4,5,6,7,8,31,32,33,34,35,36,37,38,39);
        assertThat(jdbc.sql("SHOW server_version").query(String.class).single()).startsWith("17.10");
        assertThat(jdbc.sql("SELECT PostGIS_Lib_Version()").query(String.class).single()).isEqualTo("3.6.2");
        assertThat(jdbc.sql("SELECT to_regclass('public.heatmap_cells') IS NOT NULL").query(Boolean.class).single()).isTrue();
        assertThat(jdbc.sql("SELECT to_regclass('public.region_stats') IS NOT NULL").query(Boolean.class).single()).isTrue();
        assertThat(jdbc.sql("SELECT to_regclass('public.account_deletion_logs') IS NOT NULL")
                .query(Boolean.class).single()).isTrue();
        assertThat(jdbc.sql("SELECT column_default FROM information_schema.columns "
                        + "WHERE table_name = 'users' AND column_name = 'push_like_enabled'")
                .query(String.class).single()).isEqualTo("true");
        assertThat(jdbc.sql("SELECT to_regclass('public.search_logs') IS NOT NULL")
                .query(Boolean.class).single()).isTrue();
        assertThat(jdbc.sql("SELECT indexname FROM pg_indexes WHERE schemaname='public'")
                .query(String.class).list()).contains("gin_places_addr1_search",
                        "gin_posts_content_search", "idx_users_nickname_search",
                        "idx_tags_normalized_search");
    }

    @Test
    void 검색_실행계획이_부분어와_접두어_인덱스를_사용한다() {
        UUID userId = UUID.randomUUID();
        jdbc.sql("""
                insert into users(id,google_subject,email,nickname,status,role,created_at,updated_at)
                values (:id,:subject,:email,'계획','ACTIVE','USER',now(),now())
                """).param("id", userId).param("subject", "plan-" + userId)
                .param("email", "plan-" + userId + "@example.com").update();
        long placeId = jdbc.sql("""
                insert into places(place_type,title,normalized_title,addr1,lat,lng,verify_radius_m,area_code)
                values ('OFFICIAL','실행계획장소','실행계획장소','서울 uniquejongno 주소',37.57,126.98,500,1)
                returning place_id
                """).query(Long.class).single();
        jdbc.sql("""
                insert into places(place_type,title,normalized_title,addr1,lat,lng,verify_radius_m,area_code)
                select 'OFFICIAL','일반장소 ' || n,'일반장소 ' || n,'검색과 무관한 주소 ' || n,
                       37.0,127.0,500,1
                from generate_series(1,2000) n
                """).update();
        jdbc.sql("""
                insert into posts(user_id,place_id,area_code,content,tier,status,created_at,updated_at)
                select :user,:place,1,
                       case when n=1 then 'uniquenight 검색' else '검색과 무관한 게시글 ' || n end,
                       'HIGH','ACTIVE',now(),now()
                from generate_series(1,2000) n
                """).param("user", userId).param("place", placeId).update();
        jdbc.sql("analyze places").update();
        jdbc.sql("analyze posts").update();
        jdbc.sql("select gin_clean_pending_list('gin_places_addr1_search')")
                .query(Long.class).single();
        jdbc.sql("select gin_clean_pending_list('gin_posts_content_search')")
                .query(Long.class).single();
        jdbc.sql("set local enable_seqscan=off").update();

        String placePlan = String.join("\n", jdbc.sql("""
                explain (costs off)
                select place_id from places
                where status='ACTIVE' and lower(coalesce(addr1,'')) like '%uniquejongno%'
                """).query(String.class).list());
        String postPlan = String.join("\n", jdbc.sql("""
                explain (costs off)
                select post_id from posts
                where status='ACTIVE' and lower(coalesce(content,'')) like '%uniquenight%'
                """).query(String.class).list());
        String userPlan = String.join("\n", jdbc.sql("""
                explain (costs off)
                select id from users
                where status='ACTIVE' and nickname is not null and lower(nickname) like '여행%'
                """).query(String.class).list());
        String tagPlan = String.join("\n", jdbc.sql("""
                explain (costs off)
                select tag_id from tags where normalized_name like '드라마%'
                """).query(String.class).list());

        String placeIndexDefinition = jdbc.sql(
                "select pg_get_indexdef('gin_places_addr1_search'::regclass)")
                .query(String.class).single();
        assertThat(placePlan).as(placeIndexDefinition).contains("gin_places_addr1_search");
        assertThat(postPlan).contains("gin_posts_content_search");
        assertThat(userPlan).contains("idx_users_nickname_search");
        assertThat(tagPlan).contains("idx_tags_normalized_search");
    }

    @Test
    void 게시글을_네_격자와_지역_통계로_사전_집계한다() {
        UUID userId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO users(id,google_subject,email,status,role,created_at,updated_at)
                VALUES (:id,:subject,'map@example.com','ACTIVE','USER',now(),now())
                """).param("id", userId).param("subject", "map-" + userId).update();
        long placeId = jdbc.sql("""
                INSERT INTO places(place_type,title,normalized_title,lat,lng,verify_radius_m,area_code)
                VALUES ('OFFICIAL','지도 테스트','지도 테스트',37.55,126.99,500,1) RETURNING place_id
                """).query(Long.class).single();
        long postId = jdbc.sql("""
                INSERT INTO posts(user_id,place_id,area_code,tier,lat,lng,status,created_at,updated_at)
                VALUES (:user,:place,1,'HIGH',37.55,126.99,'ACTIVE',now(),now()) RETURNING post_id
                """).param("user", userId).param("place", placeId).query(Long.class).single();
        jdbc.sql("""
                INSERT INTO post_images(post_id,image_key,thumbnail_url,sort_order)
                VALUES (:post,'posts/map-test.jpg','https://cdn.example/map-thumb.jpg',1)
                """).param("post", postId).update();

        assertThat(placeJdbcRepository.posts(placeId, null, 10, null))
                .singleElement()
                .satisfies(post -> assertThat(post.aspectRatio()).isEqualTo(1.0));

        mapAggregation.rebuild(MapPeriod.WEEKLY);

        assertThat(jdbc.sql("SELECT count(*) FROM heatmap_cells WHERE period='WEEKLY'")
                .query(Long.class).single()).isEqualTo(4);
        assertThat(jdbc.sql("SELECT sample_post_ids[1] FROM heatmap_cells WHERE period='WEEKLY' AND grid_level=2")
                .query(Long.class).single()).isEqualTo(postId);
        assertThat(jdbc.sql("SELECT sample_thumbnail_urls[1] FROM heatmap_cells WHERE period='WEEKLY' AND grid_level=2")
                .query(String.class).single()).isEqualTo("https://cdn.example/map-thumb.jpg");
        assertThat(jdbc.sql("SELECT post_count FROM region_stats WHERE area_code=1 AND period='WEEKLY'")
                .query(Integer.class).single()).isEqualTo(1);
    }

    @Test
    void 장소_랭킹을_범위와_장소유형별로_사전_집계하고_직전순위를_보관한다() {
        UUID userId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO users(id,google_subject,email,status,role,created_at,updated_at)
                VALUES (:id,:subject,:email,'ACTIVE','USER',now(),now())
                """).param("id", userId).param("subject", "rank-" + userId)
                .param("email", "rank-" + userId + "@example.com").update();
        long official = jdbc.sql("""
                INSERT INTO places(place_type,title,normalized_title,lat,lng,verify_radius_m,area_code,
                                   visit_count,view_count,is_curated)
                VALUES ('OFFICIAL','랭킹 공식','랭킹 공식',37.5,127.0,500,31,3,20,true)
                RETURNING place_id
                """).query(Long.class).single();
        long userPlace = jdbc.sql("""
                INSERT INTO places(place_type,title,normalized_title,lat,lng,verify_radius_m,area_code)
                VALUES ('USER','랭킹 사용자','랭킹 사용자',37.6,127.1,100,31)
                RETURNING place_id
                """).query(Long.class).single();
        long officialPost = insertRankingPost(userId, official, "HIGH", 2, 1);
        long userPost = insertRankingPost(userId, userPlace, "LOW", 0, 0);
        jdbc.sql("INSERT INTO likes(user_id,target_type,target_id) VALUES (:user,'POST',:post)")
                .param("user", userId).param("post", officialPost).update();
        long tagId = jdbc.sql("""
                INSERT INTO tags(name,normalized_name,theme_code) VALUES ('케이팝',:name,'KPOP')
                RETURNING tag_id
                """).param("name", "kpop-" + userId).query(Long.class).single();
        jdbc.sql("INSERT INTO post_tags(post_id,tag_id) VALUES (:post,:tag)")
                .param("post", officialPost).param("tag", tagId).update();

        rankingAggregation.rebuild(RankingPeriod.WEEKLY);

        assertThat(jdbc.sql("""
                SELECT score FROM place_rankings
                 WHERE place_id=:place AND period='WEEKLY' AND theme='ALL'
                   AND scope='REGION' AND place_type='ALL'
                """).param("place", official).query(String.class).single()).isEqualTo("12.5000");
        assertThat(jdbc.sql("""
                SELECT rank_no FROM place_rankings
                 WHERE place_id=:place AND period='WEEKLY' AND theme='KPOP'
                   AND scope='REGION' AND place_type='OFFICIAL'
                """).param("place", official).query(Integer.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("""
                SELECT rank_no FROM place_rankings
                 WHERE place_id=:place AND period='WEEKLY' AND theme='ALL'
                   AND scope='REGION' AND place_type='USER'
                """).param("place", userPlace).query(Integer.class).single()).isEqualTo(1);

        jdbc.sql("UPDATE posts SET like_count=100 WHERE post_id=:post")
                .param("post", userPost).update();
        rankingAggregation.rebuild(RankingPeriod.WEEKLY);

        assertThat(jdbc.sql("""
                SELECT rank_no,previous_rank FROM place_rankings
                 WHERE place_id=:place AND period='WEEKLY' AND theme='ALL'
                   AND scope='REGION' AND place_type='ALL'
                """).param("place", userPlace).query((rs, row) ->
                java.util.List.of(rs.getInt(1), rs.getInt(2))).single()).containsExactly(1, 2);
        assertThat(rankingRepository.rankings(RankingScope.REGION, 31, RankingPeriod.WEEKLY,
                "ALL", RankingPlaceType.ALL, null, 10, null))
                .extracting(row -> row.place().placeId())
                .startsWith(com.snaphere.api.auth.ExternalIds.place(userPlace));
        assertThat(rankingRepository.recommendations(31, null, null, 10, null)).isNotEmpty();
        assertThat(rankingRepository.recommendations(31, 37.5, 127.0, 10, null))
                .allSatisfy(row -> assertThat(row.place().distanceM()).isNotNull().isLessThanOrEqualTo(20_000));
        assertThat(rankingRepository.curated(31, null, null, 10, null))
                .extracting(row -> row.place().placeId())
                .contains(com.snaphere.api.auth.ExternalIds.place(official));
    }

    @Test
    void 통합검색이_네_타입_부분검색과_지역필터_최근검색을_지원한다() {
        UUID userId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO users(id,google_subject,email,nickname,status,role,created_at,updated_at)
                VALUES (:id,:subject,:email,'여행콩','ACTIVE','USER',now(),now())
                """).param("id", userId).param("subject", "search-" + userId)
                .param("email", "search-" + userId + "@example.com").update();
        long seoulPlace = jdbc.sql("""
                INSERT INTO places(place_type,title,normalized_title,addr1,lat,lng,verify_radius_m,area_code)
                VALUES ('OFFICIAL','경복궁','경복궁','서울 종로구 사직로',37.57,126.98,500,1)
                RETURNING place_id
                """).query(Long.class).single();
        long busanPlace = jdbc.sql("""
                INSERT INTO places(place_type,title,normalized_title,addr1,lat,lng,verify_radius_m,area_code)
                VALUES ('OFFICIAL','해운대','해운대','부산 해운대구',35.16,129.16,500,6)
                RETURNING place_id
                """).query(Long.class).single();
        jdbc.sql("""
                INSERT INTO places(place_type,title,normalized_title,addr1,lat,lng,verify_radius_m,area_code)
                VALUES ('OFFICIAL','경복 별관','경복 별관','서울 종로구',37.56,126.97,500,1)
                """).update();
        long postId = jdbc.sql("""
                INSERT INTO posts(user_id,place_id,area_code,content,tier,status,created_at,updated_at)
                VALUES (:user,:place,1,'서울 궁궐 야경이 멋져요','HIGH','ACTIVE',now(),now())
                RETURNING post_id
                """).param("user", userId).param("place", seoulPlace).query(Long.class).single();
        jdbc.sql("""
                INSERT INTO post_images(post_id,image_key,thumbnail_url,sort_order)
                VALUES (:post,'posts/search.jpg','https://cdn.example/search.jpg',1)
                """).param("post", postId).update();
        long tagId = jdbc.sql("""
                INSERT INTO tags(name,normalized_name,usage_count) VALUES ('드라마촬영지','드라마촬영지',5)
                RETURNING tag_id
                """).query(Long.class).single();
        jdbc.sql("INSERT INTO post_tags(post_id,tag_id) VALUES (:post,:tag)")
                .param("post", postId).param("tag", tagId).update();

        SearchDtos.SearchResult placeResult = searchService.search("복궁", List.of(SearchType.PLACE),
                null, null, 5, Optional.of(userId));
        assertThat(placeResult.places().items()).extracting(item -> item.title())
                .containsExactly("경복궁");

        SearchDtos.SearchResult postResult = searchService.search("야경", List.of(SearchType.POST),
                null, null, 5, Optional.of(userId));
        assertThat(postResult.posts().items()).hasSize(1);

        SearchDtos.SearchResult userResult = searchService.search("여행", List.of(SearchType.USER),
                1, null, 5, Optional.of(userId));
        assertThat(userResult.users().items()).extracting(item -> item.nickname())
                .containsExactly("여행콩");

        SearchDtos.SearchResult tagResult = searchService.search("#드라", List.of(SearchType.TAG),
                1, null, 5, Optional.of(userId));
        assertThat(tagResult.tags().items()).extracting(item -> item.name())
                .containsExactly("드라마촬영지");

        SearchDtos.SearchResult regionResult = searchService.search("서울특별시",
                List.of(SearchType.PLACE), 6, null, 10, Optional.of(userId));
        assertThat(regionResult.matchedRegion().areaCode()).isEqualTo(1);
        assertThat(regionResult.places().items()).extracting(item -> item.title())
                .contains("경복궁").doesNotContain("해운대");

        SearchDtos.SearchResult firstPage = searchService.search("경복", List.of(SearchType.PLACE),
                null, null, 1, Optional.empty());
        SearchDtos.SearchResult secondPage = searchService.search("경복", List.of(SearchType.PLACE),
                null, firstPage.places().nextCursor(), 1, Optional.empty());
        assertThat(firstPage.places().hasNext()).isTrue();
        assertThat(firstPage.places().items()).extracting(item -> item.title())
                .doesNotContainAnyElementsOf(secondPage.places().items().stream()
                        .map(item -> item.title()).toList());

        assertThat(searchService.recent(userId)).extracting(SearchDtos.RecentSearch::keyword)
                .containsExactly("서울특별시", "#드라", "여행", "야경", "복궁");
        assertThat(redis.getExpire("search:recent:order:" + userId)).isBetween(1L, 30L * 24 * 60 * 60);
    }

    @Test
    void 최근검색은_동일검색어를_최신화하고_사용자별_20개만_보관한다() {
        UUID userId = UUID.randomUUID();
        for (int i = 0; i < 21; i++) {
            recentSearchStore.record(userId, "검색" + i, "검색" + i);
        }
        recentSearchStore.record(userId, "검색5", "검색어 5 최신");

        List<SearchDtos.RecentSearch> items = recentSearchStore.recent(userId);
        assertThat(items).hasSize(20);
        assertThat(items.getFirst().keyword()).isEqualTo("검색어 5 최신");
        assertThat(items).extracting(SearchDtos.RecentSearch::keyword)
                .doesNotContain("검색0", "검색5");
    }

    @Test
    void 인기검색은_최근_7일만_집계하고_검색로그는_30일_뒤_정리한다() {
        redis.delete("search:popular:all:10");
        searchService.search("고유검색어", List.of(SearchType.PLACE), null, null, 5, Optional.empty());
        searchService.search("고유검색어", List.of(SearchType.PLACE), null, null, 5, Optional.empty());
        searchService.search("고유검색어", List.of(SearchType.PLACE), null, null, 5, Optional.empty());
        jdbc.sql("insert into search_logs(keyword,searched_at) values('오래된검색어',now()-interval '31 days')")
                .update();

        assertThat(searchService.popular(null, 10)).first()
                .satisfies(item -> {
                    assertThat(item.keyword()).isEqualTo("고유검색어");
                    assertThat(item.searchCount()).isEqualTo(3);
                });
        assertThat(searchRepository.purgeBefore(OffsetDateTime.now(ZoneOffset.UTC).minusDays(30)))
                .isEqualTo(1);
        assertThat(jdbc.sql("select count(*) from search_logs where keyword='오래된검색어'")
                .query(Long.class).single()).isZero();
    }

    private long insertRankingPost(UUID userId, long placeId, String tier, int likes, int comments) {
        return jdbc.sql("""
                INSERT INTO posts(user_id,place_id,area_code,tier,like_count,comment_count,status,
                                  created_at,updated_at)
                VALUES (:user,:place,31,:tier,:likes,:comments,'ACTIVE',now(),now())
                RETURNING post_id
                """).param("user", userId).param("place", placeId).param("tier", tier)
                .param("likes", likes).param("comments", comments).query(Long.class).single();
    }
}
