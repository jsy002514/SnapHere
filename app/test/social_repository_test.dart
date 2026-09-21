import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:snap_here/src/core/network/api_client.dart';
import 'package:snap_here/src/features/social/data/api_social_repository.dart';

void main() {
  test(
    'follow and unfollow use idempotent PUT/DELETE and server returned state',
    () async {
      final methods = <String>[];
      final repository = ApiSocialRepository(
        accessToken: 'token',
        api: ApiClient(
          baseUrl: 'http://test',
          client: MockClient((request) async {
            expect(request.url.path, '/api/v1/users/u2/follow');
            expect(request.headers['authorization'], 'Bearer token');
            methods.add(request.method);
            return response({'isFollowing': request.method == 'PUT'});
          }),
        ),
      );
      expect(await repository.setFollowing('u2', following: true), true);
      expect(await repository.setFollowing('u2', following: false), false);
      expect(methods, ['PUT', 'DELETE']);
    },
  );

  test('guest cannot send follow mutation', () async {
    final repository = ApiSocialRepository(
      api: ApiClient(
        client: MockClient((_) async {
          fail('guest must not make a mutation request');
        }),
      ),
    );
    await expectLater(
      repository.setFollowing('u2', following: true),
      throwsA(isA<ApiException>()),
    );
  });

  test(
    'following page hydrates stats without turning a failed stat into zero',
    () async {
      final repository = ApiSocialRepository(
        api: ApiClient(
          baseUrl: 'http://test',
          client: MockClient((request) async {
            if (request.url.path.endsWith('/following')) {
              expect(request.url.queryParameters['cursor'], 'opaque+/=');
              return response({
                'items': [
                  {'userId': 'u2', 'nickname': '서울여행러', 'isFollowing': true},
                  {
                    'userId': 'u3',
                    'nickname': '부산여행러',
                    'bio': '부산',
                    'isFollowing': false,
                  },
                ],
                'nextCursor': null,
                'hasNext': false,
              });
            }
            if (request.url.path.endsWith('/u2')) {
              return response({
                'stats': {'postCount': 45},
              });
            }
            return http.Response('', 503);
          }),
        ),
      );
      final page = await repository.fetchConnections(
        'u1',
        ConnectionKind.following,
        cursor: 'opaque+/=',
      );
      expect(page.items.first.postCount, 45);
      expect(page.items.last.postCount, isNull);
      expect(page.items.last.bio, '부산');
      expect(page.hasNext, false);
    },
  );
}

http.Response response(Object data) =>
    http.Response.bytes(utf8.encode(jsonEncode({'data': data})), 200);
