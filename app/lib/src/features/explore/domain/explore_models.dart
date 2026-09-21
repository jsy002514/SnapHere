import 'package:flutter/foundation.dart';
import 'package:snap_here/src/features/community/domain/community_models.dart';

@immutable
class RegionOverview {
  const RegionOverview({
    required this.areaCode,
    required this.name,
    this.imageUrl,
    this.postCount = 0,
    this.contributorCount = 0,
    this.representativePost,
    this.latitude,
    this.longitude,
  });

  final int areaCode;
  final String name;
  final String? imageUrl;
  final int postCount;
  final int contributorCount;
  final CommunityPost? representativePost;

  /// 대표 게시글 장소 좌표. 서버에 없는 지역 중심을 별도로 추정하지 않는다.
  final double? latitude;
  final double? longitude;
}

abstract interface class ExploreRepository {
  Future<List<RegionOverview>> fetchRegions();
  Future<List<RegionOverview>> fetchMapRegions();
}
