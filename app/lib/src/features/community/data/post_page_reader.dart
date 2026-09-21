import 'package:snap_here/src/core/network/api_client.dart';
import 'package:snap_here/src/core/network/cursor_page.dart';
import 'package:snap_here/src/features/community/domain/community_models.dart';
import 'package:snap_here/src/features/post/domain/post_id.dart';

/// 여러 화면에서 사용하는 게시글 목록. 본문 보충 조회는 동시 4개로 제한한다.
class PostPageReader {
  const PostPageReader(this.api, {this.accessToken});
  final ApiClient api;
  final String? accessToken;

  Future<CursorPage<CommunityPost>> fetch(
    String path, {
    Map<String, String> query = const {},
    String? cursor,
  }) async {
    final page = jsonMap(
      await api.get(
        path,
        accessToken: accessToken,
        query: {...query, 'size': '12', 'cursor': ?cursor},
      ),
    );
    final summaries = jsonMapList(page['items']);
    final posts = <CommunityPost>[];
    for (var start = 0; start < summaries.length; start += 4) {
      posts.addAll(
        await Future.wait(summaries.skip(start).take(4).map(hydrate)),
      );
    }
    return CursorPage(
      items: posts,
      nextCursor: page['hasNext'] == true
          ? page['nextCursor'] as String?
          : null,
    );
  }

  Future<CommunityPost> hydrate(Map<String, Object?> summary) async {
    String content = '';
    try {
      final detail = jsonMap(
        await api.get(
          '/posts/${postApiId(summary['postId']! as String)}',
          accessToken: accessToken,
        ),
      );
      content = (detail['content'] as String? ?? '').trim();
    } on ApiException {
      // 비공개 전환·일시 장애 시에도 이미 받은 공개 요약은 표시한다.
    }
    final author = jsonMap(summary['author']);
    final place = summary['place'] is Map ? jsonMap(summary['place']) : null;
    return CommunityPost(
      postId: summary['postId']! as String,
      author: CommunityAuthor(
        userId: author['userId']! as String,
        nickname: author['nickname'] as String? ?? '여행자',
        profileImageUrl: author['profileImageUrl'] as String?,
      ),
      title: content.isEmpty
          ? (place?['title'] as String? ?? '여행 스냅')
          : content.split('\n').first,
      content: content,
      placeName: place?['title'] as String?,
      regionName: place?['addr1'] as String?,
      thumbnailUrl: summary['thumbnailUrl'] as String?,
      imageCount: (summary['imageCount'] as num? ?? 0).toInt(),
      likeCount: (summary['likeCount'] as num? ?? 0).toInt(),
      commentCount: (summary['commentCount'] as num? ?? 0).toInt(),
      createdAt: DateTime.parse(summary['createdAt']! as String),
    );
  }
}
