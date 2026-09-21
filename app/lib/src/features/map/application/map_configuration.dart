import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:flutter_dotenv/flutter_dotenv.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// Android는 manifest의 키, iOS는 앱 .env의 키로 초기화한다.
/// 키 자체는 플랫폼 채널 응답·로그에 노출하지 않는다.
final mapConfiguredProvider = FutureProvider<bool>((ref) async {
  if (kIsWeb ||
      (defaultTargetPlatform != TargetPlatform.android &&
          defaultTargetPlatform != TargetPlatform.iOS)) {
    return false;
  }
  final key = dotenv.isInitialized
      ? dotenv.maybeGet('GOOGLE_MAPS_API_KEY') ?? ''
      : '';
  try {
    return await const MethodChannel('com.snaphere.snap_here/maps')
            .invokeMethod<bool>('configure', {'apiKey': key}) ??
        false;
  } on PlatformException {
    return false;
  } on MissingPluginException {
    return false;
  }
});
