import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:snap_here/src/features/auth/application/auth_controller.dart';
import 'package:snap_here/src/features/community/application/community_providers.dart';
import 'package:snap_here/src/features/community/domain/community_models.dart';
import 'package:snap_here/src/features/explore/data/api_explore_repository.dart';
import 'package:snap_here/src/features/explore/domain/explore_models.dart';

final exploreRepositoryProvider = Provider<ExploreRepository>((ref) {
  return ApiExploreRepository(
    accessToken: ref.watch(authControllerProvider).value?.accessToken,
  );
});

final regionsProvider = FutureProvider<List<RegionOverview>>(
  (ref) => ref.watch(exploreRepositoryProvider).fetchRegions(),
);

final mapRegionsProvider = FutureProvider<List<RegionOverview>>(
  (ref) => ref.watch(exploreRepositoryProvider).fetchMapRegions(),
);

final homePopularPostsProvider = FutureProvider<List<CommunityPost>>((
  ref,
) async {
  final feed = await ref
      .watch(communityRepositoryProvider)
      .fetchFeed(tab: CommunityFeedTab.all, sort: CommunitySort.popular);
  return feed.posts;
});
