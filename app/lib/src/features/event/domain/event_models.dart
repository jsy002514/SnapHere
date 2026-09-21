import 'package:flutter/foundation.dart';

enum EventStatus {
  upcoming,
  ongoing,
  ended;

  factory EventStatus.fromJson(String value) => switch (value) {
    'ONGOING' => ongoing,
    'ENDED' => ended,
    _ => upcoming,
  };

  String get label => switch (this) {
    upcoming => '진행 예정',
    ongoing => '진행 중',
    ended => '종료',
  };
}

@immutable
class EventSummary {
  const EventSummary({
    required this.eventId,
    required this.title,
    required this.areaCode,
    required this.areaName,
    required this.startDate,
    required this.endDate,
    required this.status,
    required this.participantCount,
    this.thumbnailUrl,
    this.dday,
    this.isNew = false,
  });

  factory EventSummary.fromJson(Map<String, Object?> json) => EventSummary(
    eventId: json['eventId']! as String,
    title: json['title']! as String,
    thumbnailUrl: json['thumbnailUrl'] as String?,
    areaCode: (json['areaCode']! as num).toInt(),
    areaName:
        json['areaName'] as String? ??
        _areaNames[(json['areaCode']! as num).toInt()] ??
        '지역',
    startDate: DateTime.parse(json['startDate']! as String),
    endDate: DateTime.parse(json['endDate']! as String),
    status: EventStatus.fromJson(json['status']! as String),
    dday: (json['dday'] as num?)?.toInt(),
    isNew: json['isNew'] as bool? ?? false,
    participantCount: (json['participantCount']! as num).toInt(),
  );

  final String eventId;
  final String title;
  final String? thumbnailUrl;
  final int areaCode;
  final String areaName;
  final DateTime startDate;
  final DateTime endDate;
  final EventStatus status;
  final int? dday;
  final bool isNew;
  final int participantCount;

  String get periodLabel => '${_date(startDate)} - ${_date(endDate)}';

  String get scheduleLabel => switch (status) {
    EventStatus.ongoing => '지금 참여 가능',
    EventStatus.upcoming when dday != null => 'D-${dday!}',
    EventStatus.upcoming => '곧 시작',
    EventStatus.ended => '종료',
  };
}

@immutable
class EventRegionSummary {
  const EventRegionSummary({
    required this.areaCode,
    required this.areaName,
    required this.eventCount,
    required this.newCount,
  });

  factory EventRegionSummary.fromJson(Map<String, Object?> json) =>
      EventRegionSummary(
        areaCode: (json['areaCode']! as num).toInt(),
        areaName: json['areaName']! as String,
        eventCount: (json['eventCount']! as num).toInt(),
        newCount: (json['newCount']! as num).toInt(),
      );

  final int areaCode;
  final String areaName;
  final int eventCount;
  final int newCount;
}

@immutable
class EventPlace {
  const EventPlace({
    required this.placeId,
    required this.name,
    required this.address,
    this.latitude,
    this.longitude,
  });

  factory EventPlace.fromJson(Map<String, Object?> json) => EventPlace(
    placeId: json['placeId']! as String,
    name: (json['name'] ?? json['title'])! as String,
    address: (json['address'] ?? json['addr1']) as String? ?? '',
    latitude: ((json['latitude'] ?? json['lat']) as num?)?.toDouble(),
    longitude: ((json['longitude'] ?? json['lng']) as num?)?.toDouble(),
  );

  final String placeId;
  final String name;
  final String address;
  final double? latitude;
  final double? longitude;
}

@immutable
class EventBadge {
  const EventBadge({
    required this.badgeId,
    required this.name,
    required this.description,
    this.iconUrl,
  });

  factory EventBadge.fromJson(Map<String, Object?> json) => EventBadge(
    badgeId: json['badgeId']! as String,
    name: json['name']! as String,
    description: json['description']! as String,
    iconUrl: json['iconUrl'] as String?,
  );

  final String badgeId;
  final String name;
  final String description;
  final String? iconUrl;
}

@immutable
class EventPost {
  const EventPost({
    required this.postId,
    required this.authorName,
    required this.likeCount,
    this.thumbnailUrl,
  });

  factory EventPost.fromJson(Map<String, Object?> json) {
    final author = Map<String, Object?>.from(json['author']! as Map);
    return EventPost(
      postId: json['postId']! as String,
      authorName: author['nickname']! as String,
      thumbnailUrl: (json['thumbnailUrl'] ?? json['imageUrl']) as String?,
      likeCount: (json['likeCount'] as num? ?? 0).toInt(),
    );
  }

  final String postId;
  final String authorName;
  final String? thumbnailUrl;
  final int likeCount;
}

@immutable
class EventDetail {
  const EventDetail({
    required this.event,
    required this.overview,
    required this.place,
    required this.fixedTags,
    required this.verifyRadiusM,
    this.badge,
  });

  factory EventDetail.fromJson(Map<String, Object?> json) => EventDetail(
    event: EventSummary.fromJson(
      Map<String, Object?>.from(json['event']! as Map),
    ),
    overview: json['overview'] as String? ?? '',
    place: EventPlace.fromJson(
      Map<String, Object?>.from(json['place']! as Map),
    ),
    fixedTags: (json['fixedTags']! as List)
        .map((tag) => Map<String, Object?>.from(tag as Map)['name']! as String)
        .toList(growable: false),
    badge: json['badge'] == null
        ? null
        : EventBadge.fromJson(Map<String, Object?>.from(json['badge']! as Map)),
    verifyRadiusM: (json['verifyRadiusM']! as num).toInt(),
  );

  final EventSummary event;
  final String overview;
  final EventPlace place;
  final List<String> fixedTags;
  final EventBadge? badge;
  final int verifyRadiusM;
}

@immutable
class EventUploadContext {
  const EventUploadContext({
    required this.event,
    required this.place,
    required this.fixedTags,
    required this.verifyRadiusM,
    this.badge,
  });

  factory EventUploadContext.fromJson(
    Map<String, Object?> json,
  ) => EventUploadContext(
    event: EventSummary.fromJson(
      Map<String, Object?>.from(json['event']! as Map),
    ),
    place: EventPlace.fromJson(
      Map<String, Object?>.from(json['place']! as Map),
    ),
    fixedTags: (json['fixedTags']! as List)
        .map((tag) => Map<String, Object?>.from(tag as Map)['name']! as String)
        .toList(growable: false),
    verifyRadiusM: (json['verifyRadiusM']! as num).toInt(),
    badge: json['badge'] == null
        ? null
        : EventBadge.fromJson(Map<String, Object?>.from(json['badge']! as Map)),
  );

  final EventSummary event;
  final EventPlace place;
  final List<String> fixedTags;
  final EventBadge? badge;
  final int verifyRadiusM;
}

String _date(DateTime date) =>
    '${date.year}.${date.month.toString().padLeft(2, '0')}.${date.day.toString().padLeft(2, '0')}';

const _areaNames = <int, String>{
  1: '서울',
  2: '인천',
  3: '대전',
  4: '대구',
  5: '광주',
  6: '부산',
  7: '울산',
  8: '세종',
  31: '경기',
  32: '강원',
  33: '충북',
  34: '충남',
  35: '경북',
  36: '경남',
  37: '전북',
  38: '전남',
  39: '제주',
};
