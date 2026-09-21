import 'package:snap_here/src/core/network/api_client.dart';
import 'package:snap_here/src/features/community/domain/community_models.dart';
import 'package:snap_here/src/features/explore/domain/explore_models.dart';

class ApiExploreRepository implements ExploreRepository {
  ApiExploreRepository({ApiClient? api, this.accessToken})
    : _api = api ?? ApiClient();

  final ApiClient _api;
  final String? accessToken;

  @override
  Future<List<RegionOverview>> fetchRegions() async =>
      jsonMapList(await _api.get('/regions', accessToken: accessToken))
          .map(_plainRegion)
          .toList(growable: false);

  @override
  Future<List<RegionOverview>> fetchMapRegions() async => jsonMapList(
    await _api.get(
      '/map/regions',
      query: const {'period': 'WEEKLY'},
      accessToken: accessToken,
    ),
  ).map(_mapRegion).toList(growable: false);

  RegionOverview _plainRegion(Map<String, Object?> json) => RegionOverview(
    areaCode: (json['areaCode']! as num).toInt(),
    name: json['nameKo']! as String,
    imageUrl: json['representativeImageUrl'] as String?,
  );

  RegionOverview _mapRegion(Map<String, Object?> json) {
    final region = jsonMap(json['region']);
    final rawPost = json['representativePost'];
    final rawPlace = rawPost is Map ? rawPost['place'] : null;
    return RegionOverview(
      areaCode: (region['areaCode']! as num).toInt(),
      name: region['nameKo']! as String,
      imageUrl: region['representativeImageUrl'] as String?,
      postCount: (json['postCount'] as num? ?? 0).toInt(),
      contributorCount: (json['contributorCount'] as num? ?? 0).toInt(),
      latitude: rawPlace is Map ? (rawPlace['lat'] as num?)?.toDouble() : null,
      longitude: rawPlace is Map ? (rawPlace['lng'] as num?)?.toDouble() : null,
      representativePost: rawPost is Map
          ? _post(Map<String, Object?>.from(rawPost))
          : null,
    );
  }

  CommunityPost _post(Map<String, Object?> json) {
    final author = jsonMap(json['author']);
    final place = json['place'] is Map ? jsonMap(json['place']) : null;
    return CommunityPost(
      postId: json['postId']! as String,
      author: CommunityAuthor(
        userId: author['userId']! as String,
        nickname: author['nickname'] as String? ?? '여행자',
        profileImageUrl: author['profileImageUrl'] as String?,
      ),
      title: place?['title'] as String? ?? '인기 여행 스냅',
      content: '',
      placeName: place?['title'] as String?,
      regionName: place?['addr1'] as String?,
      thumbnailUrl: json['thumbnailUrl'] as String?,
      imageCount: (json['imageCount'] as num? ?? 0).toInt(),
      likeCount: (json['likeCount'] as num? ?? 0).toInt(),
      commentCount: (json['commentCount'] as num? ?? 0).toInt(),
      createdAt: DateTime.parse(json['createdAt']! as String),
    );
  }
}
