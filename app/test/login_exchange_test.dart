import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:snap_here/src/features/auth/data/api_auth_repository.dart';
import 'package:snap_here/src/features/auth/domain/auth_models.dart';
import 'package:snap_here/src/features/auth/domain/auth_repository.dart';

const _credential = GoogleIdentityCredential(
  idToken: 'test-google-token',
  subject: 'test-subject',
  email: 'test@example.test',
);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUp(() => FlutterSecureStorage.setMockInitialValues({}));

  for (final scenario in [
    (
      code: 'AUTH_AUDIENCE_MISMATCH',
      status: 401,
      message: '앱과 서버의 Google 로그인 설정이 일치하지 않아요',
    ),
    (
      code: 'AUTH_INVALID_GOOGLE_TOKEN',
      status: 401,
      message: 'Google 인증을 확인하지 못했어요',
    ),
    (code: 'USER_RECOVERY_EXPIRED', status: 410, message: '계정 복구 기간이 지났어요'),
    (code: 'COMMON_500', status: 500, message: '로그인 서버에 오류가 발생했어요'),
    (code: 'COMMON_400', status: 400, message: '서버에서 로그인을 처리하지 못했어요'),
  ]) {
    test(
      'login exchange translates ${scenario.code} without exposing server details',
      () async {
        final repository = ApiAuthRepository(
          baseUrl: 'http://test',
          client: MockClient(
            (request) async => http.Response(
              jsonEncode({
                'error': {
                  'code': scenario.code,
                  'message': 'private-diagnostics',
                },
              }),
              scenario.status,
            ),
          ),
        );
        await expectLater(
          repository.exchangeGoogleCredential(_credential),
          throwsA(
            isA<AuthFailure>()
                .having(
                  (error) => error.message,
                  'message',
                  contains(scenario.message),
                )
                .having(
                  (error) => error.message,
                  'private server detail',
                  isNot(contains('private-diagnostics')),
                ),
          ),
        );
      },
    );
  }

  for (final scenario in [
    (
      error: const SocketException('private endpoint'),
      message: '서버에 연결할 수 없어요',
    ),
    (error: http.ClientException('private endpoint'), message: '서버에 연결할 수 없어요'),
    (error: TimeoutException('private endpoint'), message: '서버의 응답이 늦어지고 있어요'),
  ]) {
    test(
      'login exchange translates ${scenario.error.runtimeType} into recovery guidance',
      () async {
        final repository = ApiAuthRepository(
          baseUrl: 'http://test',
          client: MockClient((_) async => throw scenario.error),
        );
        await expectLater(
          repository.exchangeGoogleCredential(_credential),
          throwsA(
            isA<AuthFailure>()
                .having(
                  (error) => error.message,
                  'message',
                  contains(scenario.message),
                )
                .having(
                  (error) => error.message,
                  'private endpoint',
                  isNot(contains('private endpoint')),
                ),
          ),
        );
      },
    );
  }

  test('successful login keeps the existing token exchange contract', () async {
    final repository = ApiAuthRepository(
      baseUrl: 'http://test',
      client: MockClient((request) async {
        expect(request.method, 'POST');
        expect(request.url.path, '/api/v1/auth/google');
        final body = jsonDecode(request.body) as Map;
        expect(body['idToken'], _credential.idToken);
        expect(body['deviceId'], startsWith('flutter-'));
        expect(body['platform'], 'ANDROID');
        return http.Response(
          jsonEncode({
            'data': {
              'tokens': {'accessToken': 'access', 'refreshToken': 'refresh'},
              'user': {'userId': 'u1', 'email': 'test@example.test'},
              'onboardingRequired': true,
            },
          }),
          200,
        );
      }),
    );
    final session = await repository.exchangeGoogleCredential(_credential);
    expect(session.accessToken, 'access');
    expect(session.user?.needsProfileSetup, isTrue);
  });
}
