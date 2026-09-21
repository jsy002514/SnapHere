import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:snap_here/src/core/network/api_client.dart';
import 'package:snap_here/src/features/profile/data/api_profile_repository.dart';

void main() {
  test(
    'me unwraps private profile and public profile supports anonymous access',
    () async {
      final requests = <http.Request>[];
      final client = MockClient((request) async {
        requests.add(request);
        return response(
          request.url.path.endsWith('/me') ? {'profile': profile} : profile,
        );
      });
      final api = ApiClient(baseUrl: 'http://test', client: client);
      final own = await ApiProfileRepository(
        api: api,
        accessToken: 'test-token',
      ).fetchMe();
      final other = await ApiProfileRepository(api: api).fetchUser('u1');
      expect(own.userId, 'u1');
      expect(own.nickname, '여행자');
      expect(own.bio, '소개');
      expect(own.stats.badgeCount, 3);
      expect(other.isFollowing, true);
      expect(requests[0].headers['authorization'], 'Bearer test-token');
      expect(requests[1].url.path, '/api/v1/users/u1');
      expect(requests[1].headers['authorization'], isNull);
    },
  );

  test('user posts preserves opaque cursor and falls back to public summary on detail failure', () async {
    final repository = ApiProfileRepository(
      api: ApiClient(
        baseUrl: 'http://test',
        client: MockClient((request) async {
          if (request.url.path == '/api/v1/users/u1/posts') {
            expect(request.url.queryParameters['cursor'], 'a+/=한글');
            return response({
              'items': [post],
              'hasNext': true,
              'nextCursor': 'next+/=',
            });
          }
          expect(request.url.path, '/api/v1/posts/pst_1');
          return http.Response('', 404);
        }),
      ),
    );
    final page = await repository.fetchPosts('u1', cursor: 'a+/=한글');
    expect(page.items.single.title, '경복궁');
    expect(page.items.single.imageCount, 2);
    expect(page.nextCursor, 'next+/=');
  });
}

http.Response response(Object data) =>
    http.Response.bytes(utf8.encode(jsonEncode({'data': data})), 200);
const profile = {
  'user': {'userId': 'u1', 'nickname': '여행자', 'bio': '소개', 'isFollowing': true},
  'stats': {
    'postCount': 2,
    'followerCount': 12,
    'followingCount': 4,
    'badgeCount': 3,
  },
};
const post = {
  'postId': 'pst_1',
  'author': {'userId': 'u1', 'nickname': '여행자'},
  'place': {'placeId': 'plc_1', 'title': '경복궁', 'addr1': '서울 종로구'},
  'imageCount': 2,
  'likeCount': 3,
  'commentCount': 1,
  'createdAt': '2026-09-07T10:00:00+09:00',
};
