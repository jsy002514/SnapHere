import 'dart:async';

import 'package:snap_here/src/core/network/api_client.dart';
import 'package:snap_here/src/core/network/cursor_page.dart';
import 'package:snap_here/src/features/post/domain/post_models.dart';
import 'package:snap_here/src/features/post/domain/post_id.dart';
import 'package:snap_here/src/features/post/domain/post_repository.dart';

class ApiPostRepository implements PostRepository {
  ApiPostRepository({this.accessToken, ApiClient? client})
    : _client = client ?? ApiClient();

  final String? accessToken;
  final ApiClient _client;

  @override
  Future<PostDetail> fetchPost(String postId) => _guard(() async {
    final response = await _get('/posts/${postApiId(postId)}')
        .timeout(const Duration(seconds: 15));
    return PostDetail.fromJson(jsonMap(response));
  });

  @override
  Future<({int likeCount, bool isLiked})> setLiked(
    String postId,
    bool liked,
  ) async {
    final data = jsonMap(
      await _send(liked ? 'PUT' : 'DELETE', '/posts/$postId/like'),
    );
    return (
      likeCount: (data['likeCount'] as num?)?.toInt() ?? 0,
      isLiked: data['isLiked'] as bool? ?? liked,
    );
  }

  @override
  Future<bool> setBookmarked(String postId, bool bookmarked) async {
    final data = jsonMap(
      await _send(bookmarked ? 'PUT' : 'DELETE', '/posts/$postId/bookmark'),
    );
    return data['isBookmarked'] as bool? ?? bookmarked;
  }

  @override
  Future<void> report(
    String postId, {
    required String reason,
    String? detail,
  }) => _guard(
    () => _client.post(
      '/posts/$postId/reports',
      body: {'reason': reason, 'detail': ?detail},
      accessToken: _requireToken(),
    ),
  );

  @override
  Future<void> deletePost(String postId) => _guard(
    () => _client.delete(
      '/posts/${postApiId(postId)}',
      accessToken: _requireToken(),
    ),
  );

  @override
  Future<CursorPage<CommentThread>> fetchComments(
    String postId, {
    String? cursor,
  }) async {
    final data = jsonMap(
      await _get('/posts/$postId/comments', query: {'cursor': ?cursor}),
    );
    return CursorPage(
      items: jsonMapList(data['items'])
          .map(CommentThread.fromJson)
          .toList(growable: false),
      nextCursor: data['nextCursor'] as String?,
    );
  }

  @override
  Future<Comment> addComment(String postId, String content) async =>
      Comment.fromJson(
        jsonMap(
          await _guard(
            () => _client.post(
              '/posts/$postId/comments',
              body: {'content': content},
              accessToken: _requireToken(),
            ),
          ),
        ),
      );

  @override
  Future<Comment> addReply(String commentId, String content) async =>
      Comment.fromJson(
        jsonMap(
          await _guard(
            () => _client.post(
              '/comments/$commentId/replies',
              body: {'content': content},
              accessToken: _requireToken(),
            ),
          ),
        ),
      );

  @override
  Future<Comment> editComment(String commentId, String content) async =>
      Comment.fromJson(
        jsonMap(
          await _guard(
            () => _client.patch(
              '/comments/$commentId',
              body: {'content': content},
              accessToken: _requireToken(),
            ),
          ),
        ),
      );

  @override
  Future<void> deleteComment(String commentId) => _guard(
    () => _client.delete('/comments/$commentId', accessToken: _requireToken()),
  );

  @override
  Future<ShareMetadata> fetchShareMetadata(String postId) async =>
      ShareMetadata.fromJson(
        jsonMap(await _get('/public/posts/$postId/share-metadata')),
      );

  Future<Object?> _get(String path, {Map<String, String> query = const {}}) =>
      _guard(() => _client.get(path, query: query, accessToken: accessToken));

  Future<Object?> _send(String method, String path) =>
      _guard(() => _client.request(method, path, accessToken: _requireToken()));

  String _requireToken() {
    final token = accessToken;
    if (token == null) throw const PostFailure('로그인이 필요한 기능이에요.');
    return token;
  }

  /// 화면은 `ApiException`을 모른다. 도메인 예외로 바꿔 넘긴다.
  Future<T> _guard<T>(Future<T> Function() run) async {
    try {
      return await run();
    } on ApiException catch (error) {
      throw PostFailure(
        switch (error.code) {
          'POST_NOT_FOUND' => '게시글을 찾을 수 없어요.',
          'POST_NOT_VISIBLE' => '현재 볼 수 없는 게시글이에요.',
          'POST_MEDIA_PROCESSING' => '사진을 처리하고 있어요. 잠시 후 다시 확인해 주세요.',
          'POST_MEDIA_FAILED' => '사진 처리에 실패했어요. 사진을 다시 등록해 주세요.',
          'AUTH_REQUIRED' => '로그인이 필요해요. 다시 로그인해 주세요.',
          _ => switch (error.statusCode) {
            404 => '게시글을 찾을 수 없어요.',
            401 => '로그인이 필요해요. 다시 로그인해 주세요.',
            403 => '이 게시글에 접근할 권한이 없어요.',
            429 => '요청이 너무 많아요. 잠시 후 다시 시도해 주세요.',
            _ => '서버가 요청을 처리하지 못했어요. 잠시 후 다시 시도해 주세요.',
          },
        },
        code: error.code,
        statusCode: error.statusCode,
      );
    } on PostFailure {
      rethrow;
    } on TimeoutException {
      throw const PostFailure('응답이 늦어지고 있어요. 잠시 후 다시 시도해 주세요.');
    } on FormatException {
      throw const PostFailure('게시글 정보를 읽지 못했어요. 잠시 후 다시 시도해 주세요.');
    } on TypeError {
      throw const PostFailure('게시글 정보를 읽지 못했어요. 잠시 후 다시 시도해 주세요.');
    } on Object {
      throw const PostFailure('네트워크에 연결할 수 없어요.');
    }
  }
}
