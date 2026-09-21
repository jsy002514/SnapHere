import 'package:snap_here/src/core/network/api_client.dart';
import 'package:snap_here/src/core/network/cursor_page.dart';
import 'package:snap_here/src/features/community/data/post_page_reader.dart';
import 'package:snap_here/src/features/community/domain/community_models.dart';

class HomeMapRepository {
  HomeMapRepository({ApiClient? api, this.accessToken})
    : _api = api ?? ApiClient();
  final ApiClient _api;
  final String? accessToken;

  Future<CursorPage<CommunityPost>> fetchRegionPosts(
    int areaCode, {
    String? cursor,
  }) => PostPageReader(_api, accessToken: accessToken).fetch(
    '/posts',
    cursor: cursor,
    query: {'areaCode': '$areaCode', 'period': 'WEEKLY'},
  );
}
