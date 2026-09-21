import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:snap_here/src/core/network/api_client.dart';
import 'package:snap_here/src/features/community/data/api_community_repository.dart';
import 'package:snap_here/src/features/community/data/post_page_reader.dart';
import 'package:snap_here/src/features/community/domain/community_models.dart';
import 'package:snap_here/src/features/post/data/api_post_repository.dart';
import 'package:snap_here/src/features/post/domain/post_models.dart';

http.Response _data(Object? data) => http.Response(
  jsonEncode({'data': data}),
  200,
  headers: {'content-type': 'application/json; charset=utf-8'},
);

Map<String, Object?> _summary(String id) => {
  'postId': id,
  'author': {'userId': 'user-1', 'nickname': '여행자'},
  'place': {'placeId': 'plc_1', 'title': '사천 에어쇼'},
  'createdAt': '2026-09-16T12:00:00Z',
};

void main() {
  test('기존 숫자 ID와 외부 ID가 같은 상세를 조회하며 36진수를 정확히 처리한다', () async {
    for (final entry in {
      '2': 'pst_2',
      '10': 'pst_a',
      '36': 'pst_10',
      '42': 'pst_16',
      'pst_10': 'pst_10',
    }.entries) {
      final client = MockClient((request) async {
        expect(request.url.path, '/api/v1/posts/${entry.value}');
        return _data({'summary': _summary(entry.key), 'content': '에어쇼\n설명'});
      });
      addTearDown(client.close);
      final repository = ApiPostRepository(client: ApiClient(client: client));
      final post = await repository.fetchPost(entry.key);
      expect(post.title, '에어쇼');
      expect(post.body, '설명');
    }
  });

  test('커뮤니티와 프로필의 기존 숫자 목록에서 본문 보충 조회도 성공한다', () async {
    final paths = <String>[];
    final client = MockClient((request) async {
      paths.add(request.url.path);
      if (request.url.path == '/api/v1/posts/pst_16') {
        return _data({'content': '비행 장면\n후기'});
      }
      expect(
        request.url.path,
        anyOf('/api/v1/feeds/recent', '/api/v1/users/user-1/posts'),
      );
      return _data({
        'items': [_summary('42')],
        'hasNext': false,
      });
    });
    addTearDown(client.close);
    final api = ApiClient(client: client);
    final feed = await ApiCommunityRepository(api: api)
        .fetchFeed(tab: CommunityFeedTab.all, sort: CommunitySort.latest);
    final profile = await PostPageReader(api).fetch('/users/user-1/posts');
    expect(feed.posts.single.title, '비행 장면');
    expect(profile.items.single.content, '비행 장면\n후기');
    expect(paths.where((path) => path.endsWith('/pst_16')), hasLength(2));
  });

  test('상세의 404·사진 처리 상태 오류를 구분하고 서버 원문을 표시하지 않는다', () async {
    for (final entry in {
      'POST_NOT_FOUND': (status: 404, message: '게시글을 찾을 수 없어요.'),
      'POST_NOT_VISIBLE': (status: 404, message: '현재 볼 수 없는 게시글이에요.'),
      'POST_MEDIA_PROCESSING': (
        status: 409,
        message: '사진을 처리하고 있어요. 잠시 후 다시 확인해 주세요.',
      ),
      'POST_MEDIA_FAILED': (
        status: 409,
        message: '사진 처리에 실패했어요. 사진을 다시 등록해 주세요.',
      ),
    }.entries) {
      final client = MockClient(
        (_) async => http.Response(
          jsonEncode({
            'error': {'code': entry.key, 'messageKey': 'error.post.internal'},
          }),
          entry.value.status,
        ),
      );
      addTearDown(client.close);
      await expectLater(
        ApiPostRepository(client: ApiClient(client: client)).fetchPost('2'),
        throwsA(
          isA<PostFailure>()
              .having((e) => e.code, 'code', entry.key)
              .having((e) => e.message, 'message', entry.value.message),
        ),
      );
    }
  });

  test('게시글 응답이 손상돼도 화면에서 처리 가능한 오류로 바꾼다', () async {
    final client = MockClient((_) async => _data({'summary': null}));
    addTearDown(client.close);
    await expectLater(
      ApiPostRepository(client: ApiClient(client: client)).fetchPost('pst_2'),
      throwsA(
        isA<PostFailure>().having(
          (e) => e.message,
          'message',
          contains('정보를 읽지 못했어요'),
        ),
      ),
    );
  });

  test('기존 상세 응답의 숫자 ID로 삭제해도 외부 ID 경로를 사용한다', () async {
    final client = MockClient((request) async {
      expect(request.method, 'DELETE');
      expect(request.url.path, '/api/v1/posts/pst_16');
      return http.Response('', 204);
    });
    addTearDown(client.close);
    await ApiPostRepository(
      accessToken: 'test',
      client: ApiClient(client: client),
    ).deletePost('42');
  });
}
