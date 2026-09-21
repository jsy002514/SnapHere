import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:snap_here/src/core/network/api_client.dart';
import 'package:snap_here/src/features/explore/data/api_explore_repository.dart';
import 'package:snap_here/src/features/home/data/home_map_repository.dart';

void main() {
  test('map region uses representative place coordinates rather than guessed region centers', () async {
    final repository = ApiExploreRepository(
      api: ApiClient(
        baseUrl: 'http://test',
        client: MockClient((request) async {
          expect(request.url.path, '/api/v1/map/regions');
          return response([
            {
              'region': {'areaCode': 37, 'nameKo': '전북'},
              'postCount': 128,
              'representativePost': post,
            },
          ]);
        }),
      ),
    );
    final region = (await repository.fetchMapRegions()).single;
    expect(region.latitude, 35.8);
    expect(region.longitude, 127.1);
    expect(region.postCount, 128);
  });

  test('region sheet forwards region, period, cursor and auth then hydrates content', () async {
    final repository = HomeMapRepository(
      accessToken: 'token',
      api: ApiClient(
        baseUrl: 'http://test',
        client: MockClient((request) async {
          expect(request.headers['authorization'], 'Bearer token');
          if (request.url.path.endsWith('/posts')) {
            expect(request.url.queryParameters, {
              'areaCode': '37',
              'period': 'WEEKLY',
              'size': '12',
              'cursor': 'opaque+/=',
            });
            return response({
              'items': [post],
              'hasNext': false,
            });
          }
          return response({'content': '한옥마을 야경 최고!\n전주의 밤'});
        }),
      ),
    );
    final posts = await repository.fetchRegionPosts(37, cursor: 'opaque+/=');
    expect(posts.items.single.title, '한옥마을 야경 최고!');
    expect(posts.hasNext, false);
  });
}

const post = {
  'postId': 'pst_1',
  'author': {'userId': 'u1', 'nickname': '너구리즈'},
  'place': {'title': '전주 한옥마을', 'lat': 35.8, 'lng': 127.1},
  'createdAt': '2026-09-07T10:00:00+09:00',
};
http.Response response(Object data) =>
    http.Response.bytes(utf8.encode(jsonEncode({'data': data})), 200);
