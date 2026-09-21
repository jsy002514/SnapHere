import 'package:flutter_test/flutter_test.dart';
import 'package:snap_here/src/features/auth/domain/auth_models.dart';

void main() {
  test('authenticated session survives secure-store serialization', () {
    const original = AuthSession.authenticated(
      accessToken: 'access',
      refreshToken: 'refresh',
      user: AuthUser(
        id: 'user-1',
        email: 'user@example.com',
        nickname: '여행토끼',
        needsProfileSetup: false,
      ),
    );

    final restored = AuthSession.fromJson(original.toJson());

    expect(restored.accessToken, 'access');
    expect(restored.refreshToken, 'refresh');
    expect(restored.user?.nickname, '여행토끼');
    expect(restored.user?.needsProfileSetup, isFalse);
  });

  test('consent payload preserves versions and UTC timestamp', () {
    final acceptedAt = DateTime.utc(2026, 9, 1, 12, 30);
    final payload = ConsentRecord(
      termsVersion: 'terms-2',
      privacyVersion: 'privacy-3',
      marketingAccepted: false,
      acceptedAt: acceptedAt,
    ).toJson();

    expect(payload['termsVersion'], 'terms-2');
    expect(payload['privacyVersion'], 'privacy-3');
    expect(payload['marketingVersion'], isNull);
    expect(payload['acceptedAt'], '2026-09-01T12:30:00.000Z');
  });
}
