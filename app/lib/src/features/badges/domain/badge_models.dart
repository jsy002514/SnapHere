class CollectedBadge {
  const CollectedBadge({
    required this.id,
    required this.name,
    required this.earned,
    this.description,
    this.iconUrl,
    this.earnedAt,
  });
  final String id;
  final String name;
  final String? description;
  final String? iconUrl;
  final bool earned;
  final DateTime? earnedAt;
}

class BadgeCollection {
  const BadgeCollection({
    required this.items,
    required this.earnedCount,
    required this.obtainableCount,
    required this.progress,
  });
  final List<CollectedBadge> items;
  final int earnedCount;
  final int obtainableCount;
  final double progress;
}

class BadgeDetail {
  const BadgeDetail({
    required this.badge,
    required this.currentValue,
    required this.targetValue,
    required this.earnedCount,
    this.sourcePostId,
  });
  final CollectedBadge badge;
  final int currentValue;
  final int targetValue;
  final int earnedCount;
  final String? sourcePostId;
}

class VisitPoint {
  const VisitPoint({
    required this.placeId,
    required this.latitude,
    required this.longitude,
    required this.visitCount,
  });
  final String placeId;
  final double latitude;
  final double longitude;
  final int visitCount;
}

class VisitedRegion {
  const VisitedRegion({
    required this.areaCode,
    required this.name,
    required this.visitCount,
    required this.placeCount,
  });
  final int areaCode;
  final String name;
  final int visitCount;
  final int placeCount;
}

class VisitMapSnapshot {
  const VisitMapSnapshot({
    required this.points,
    required this.regions,
    required this.visitedRegionCount,
    required this.totalRegionCount,
    required this.progress,
  });
  final List<VisitPoint> points;
  final List<VisitedRegion> regions;
  final int visitedRegionCount;
  final int totalRegionCount;
  final double progress;
}
