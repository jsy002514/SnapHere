import 'package:flutter_dotenv/flutter_dotenv.dart';

/// 서버의 토큰 검증 audience와 같은 Web OAuth 클라이언트 ID를 사용한다.
/// 기존 dart-define을 우선하고 main에서 로드한 로컬 설정을 보충한다.
String? resolveGoogleServerClientId({
  String serverClientId = const String.fromEnvironment(
    'GOOGLE_SERVER_CLIENT_ID',
  ),
  String oauthClientId = const String.fromEnvironment('GOOGLE_OAUTH_CLIENT_ID'),
  Map<String, String>? environment,
}) {
  final values = environment ?? (dotenv.isInitialized ? dotenv.env : const {});
  for (final candidate in [
    serverClientId,
    oauthClientId,
    values['GOOGLE_SERVER_CLIENT_ID'],
    values['GOOGLE_OAUTH_CLIENT_ID'],
  ]) {
    final value = candidate?.trim();
    if (value != null && value.isNotEmpty) return value;
  }
  // 네이티브 google-services 설정을 사용하는 기존 SDK 동작도 보존한다.
  return null;
}
