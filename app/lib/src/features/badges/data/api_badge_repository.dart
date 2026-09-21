import 'package:snap_here/src/core/network/api_client.dart';
import 'package:snap_here/src/features/badges/domain/badge_models.dart';

class ApiBadgeRepository {
  ApiBadgeRepository({required this.accessToken, ApiClient? api})
    : _api = api ?? ApiClient();
  final String accessToken;
  final ApiClient _api;

  Future<BadgeCollection> fetchCollection() async {
    final data = jsonMap(
      await _api.get('/users/me/badges', accessToken: accessToken),
    );
    return BadgeCollection(
      items: jsonMapList(data['items']).map(_badge).toList(),
      earnedCount: (data['earnedCount'] as num).toInt(),
      obtainableCount: (data['obtainableCount'] as num).toInt(),
      progress: (data['progress'] as num).toDouble().clamp(0, 1),
    );
  }

  Future<BadgeDetail> fetchDetail(String badgeId) async {
    final data = jsonMap(
      await _api.get('/badges/$badgeId', accessToken: accessToken),
    );
    return BadgeDetail(
      badge: _badge(jsonMap(data['badge'])),
      currentValue: (data['currentValue'] as num).toInt(),
      targetValue: (data['targetValue'] as num).toInt(),
      earnedCount: (data['earnedCount'] as num).toInt(),
      sourcePostId: data['sourcePostId'] as String?,
    );
  }

  Future<VisitMapSnapshot> fetchVisitMap() async {
    final data = jsonMap(
      await _api.get('/me/visit-map', accessToken: accessToken),
    );
    final stats = jsonMap(data['stats']);
    return VisitMapSnapshot(
      points: jsonMapList(data['points'])
          .map(
            (point) => VisitPoint(
              placeId: point['placeId'] as String,
              latitude: (point['lat'] as num).toDouble(),
              longitude: (point['lng'] as num).toDouble(),
              visitCount: (point['visitCount'] as num).toInt(),
            ),
          )
          .toList(),
      regions: jsonMapList(stats['regions']).map((row) {
        final region = jsonMap(row['region']);
        return VisitedRegion(
          areaCode: (region['areaCode'] as num).toInt(),
          name: region['nameKo'] as String,
          visitCount: (row['visitCount'] as num).toInt(),
          placeCount: (row['placeCount'] as num).toInt(),
        );
      }).toList(),
      visitedRegionCount: (stats['visitedRegionCount'] as num).toInt(),
      totalRegionCount: (stats['totalRegionCount'] as num).toInt(),
      progress: (stats['progress'] as num).toDouble().clamp(0, 1),
    );
  }

  CollectedBadge _badge(Map<String, Object?> data) => CollectedBadge(
    id: data['badgeId'] as String,
    name: data['name'] as String,
    description: data['description'] as String?,
    iconUrl: data['iconUrl'] as String?,
    earned: data['earned'] == true,
    earnedAt: DateTime.tryParse(data['earnedAt'] as String? ?? ''),
  );
}
