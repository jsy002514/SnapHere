import 'package:snap_here/src/features/auth/domain/auth_models.dart';

abstract interface class AuthRepository {
  Future<AuthSession> exchangeGoogleCredential(
    GoogleIdentityCredential credential,
  );

  Future<AuthSession> completeProfile({
    required String accessToken,
    required String refreshToken,
    required ProfileSubmission submission,
  });

  Future<AuthSession> refreshSession(String refreshToken);

  Future<void> signOut(String accessToken);

  Future<void> deleteAccount(
    String accessToken, {
    required String contentAction,
  });
}

abstract interface class LegalDocumentRepository {
  Future<LegalDocument> fetch(LegalDocumentType type);
}

abstract interface class GoogleIdentityClient {
  Future<GoogleIdentityCredential> signIn();
  Future<void> signOut();
  Future<void> disconnect();
}

abstract interface class SessionStore {
  Future<AuthSession?> read();
  Future<void> write(AuthSession session);
  Future<void> clear();
}

class AuthFailure implements Exception {
  const AuthFailure(this.message, {this.isCancellation = false});
  final String message;
  final bool isCancellation;

  @override
  String toString() => message;
}
