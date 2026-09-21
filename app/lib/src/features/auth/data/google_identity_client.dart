import 'package:flutter/foundation.dart';
import 'package:google_sign_in/google_sign_in.dart';
import 'package:snap_here/src/features/auth/data/google_identity_configuration.dart';
import 'package:snap_here/src/features/auth/domain/auth_models.dart';
import 'package:snap_here/src/features/auth/domain/auth_repository.dart';

class AndroidGoogleIdentityClient implements GoogleIdentityClient {
  AndroidGoogleIdentityClient({GoogleSignIn? signIn, String? serverClientId})
    : _signIn = signIn ?? GoogleSignIn.instance,
      _serverClientId = serverClientId ?? resolveGoogleServerClientId();

  final GoogleSignIn _signIn;
  final String? _serverClientId;
  Future<void>? _initialization;

  Future<void> _initialize() {
    return _initialization ??= _signIn.initialize(
      serverClientId: _serverClientId,
    );
  }

  @override
  Future<GoogleIdentityCredential> signIn() async {
    try {
      await _initialize();
      final account = await _signIn.authenticate();
      final idToken = account.authentication.idToken;
      if (idToken == null || idToken.isEmpty) {
        throw const AuthFailure(
          'Google ID 토큰을 받지 못했습니다. Android OAuth 서버 클라이언트 ID를 확인해 주세요.',
        );
      }
      return GoogleIdentityCredential(
        idToken: idToken,
        subject: account.id,
        email: account.email,
        displayName: account.displayName,
        photoUrl: account.photoUrl,
      );
    } on GoogleSignInException catch (error) {
      if (kDebugMode) debugPrint('Google sign-in result: ${error.code.name}');
      if (error.code == GoogleSignInExceptionCode.canceled) {
        throw const AuthFailure('Google 로그인이 취소되었습니다.', isCancellation: true);
      }
      if (error.code == GoogleSignInExceptionCode.clientConfigurationError) {
        throw const AuthFailure(
          'Google 로그인 설정을 확인해 주세요. OAuth 클라이언트 ID와 Android 앱 등록 정보가 필요합니다.',
        );
      }
      throw AuthFailure(error.description ?? 'Google 로그인에 실패했습니다.');
    }
  }

  @override
  Future<void> signOut() async {
    await _initialize();
    await _signIn.signOut();
  }

  @override
  Future<void> disconnect() async {
    await _initialize();
    await _signIn.disconnect();
  }
}

class FakeGoogleIdentityClient implements GoogleIdentityClient {
  const FakeGoogleIdentityClient();

  @override
  Future<GoogleIdentityCredential> signIn() async {
    await Future<void>.delayed(const Duration(milliseconds: 350));
    return const GoogleIdentityCredential(
      idToken: 'fake-google-id-token',
      subject: 'google-demo-user',
      email: 'traveler@example.com',
      displayName: '여행자',
    );
  }

  @override
  Future<void> signOut() async {}

  @override
  Future<void> disconnect() async {}
}
