import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:snap_here/src/core/network/api_client.dart';
import 'package:snap_here/src/features/auth/application/auth_controller.dart';
import 'package:snap_here/src/features/badges/data/api_badge_repository.dart';
import 'package:snap_here/src/features/badges/domain/badge_models.dart';

final badgeRepositoryProvider = Provider<ApiBadgeRepository>((ref) {
  final token = ref.watch(authControllerProvider).value?.accessToken;
  if (token == null) throw const ApiException('로그인이 필요합니다.', statusCode: 401);
  return ApiBadgeRepository(accessToken: token);
});
final badgeCollectionProvider = FutureProvider.autoDispose<BadgeCollection>(
  (ref) => ref.watch(badgeRepositoryProvider).fetchCollection(),
);
final visitMapProvider = FutureProvider.autoDispose<VisitMapSnapshot>(
  (ref) => ref.watch(badgeRepositoryProvider).fetchVisitMap(),
);
final badgeDetailProvider = FutureProvider.autoDispose
    .family<BadgeDetail, String>(
      (ref, id) => ref.watch(badgeRepositoryProvider).fetchDetail(id),
    );
