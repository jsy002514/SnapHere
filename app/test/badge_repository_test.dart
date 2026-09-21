import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:snap_here/src/core/network/api_client.dart';
import 'package:snap_here/src/features/badges/data/api_badge_repository.dart';

void main() {
  test(
    'badge collection and detail keep earned status, dates and source post',
    () async {
      final repository = ApiBadgeRepository(
        accessToken: 'token',
        api: ApiClient(
          baseUrl: 'http://test',
          client: MockClient((request) async {
            expect(request.headers['authorization'], 'Bearer token');
            if (request.url.path.endsWith('/users/me/badges')) {
              return response({
                'items': [badge],
                'earnedCount': 1,
                'obtainableCount': 0,
                'progress': 1.0,
              });
            }
            expect(request.url.path, '/api/v1/badges/bdg_1');
            return response({
              'badge': badge,
              'currentValue': 5,
              'targetValue': 5,
              'earnedCount': 20,
              'sourcePostId': 'pst_1',
            });
          }),
        ),
      );
      final collection = await repository.fetchCollection();
      expect(collection.items.single.earned, true);
      expect(collection.items.single.earnedAt, isNotNull);
      expect(collection.progress, 1);
      expect((await repository.fetchDetail('bdg_1')).sourcePostId, 'pst_1');
    },
  );

  test(
    'empty visit map accepts null bounds and uses server region total',
    () async {
      final repository = ApiBadgeRepository(
        accessToken: 'token',
        api: ApiClient(
          baseUrl: 'http://test',
          client: MockClient((request) async {
            expect(request.url.path, '/api/v1/me/visit-map');
            return response({
              'points': [],
              'bounds': null,
              'badges': [],
              'stats': {
                'regions': [],
                'visitedRegionCount': 0,
                'totalRegionCount': 18,
                'progress': 0.0,
              },
            });
          }),
        ),
      );
      final map = await repository.fetchVisitMap();
      expect(map.points, isEmpty);
      expect(map.totalRegionCount, 18);
      expect(map.progress, 0);
    },
  );
}

const badge = {
  'badgeId': 'bdg_1',
  'name': '전주 봄축제',
  'earned': true,
  'earnedAt': '2026-03-20T12:00:00+09:00',
};
http.Response response(Object data) =>
    http.Response.bytes(utf8.encode(jsonEncode({'data': data})), 200);
