import 'package:flutter_dotenv/flutter_dotenv.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:snap_here/src/features/auth/data/google_identity_configuration.dart';

void main() {
  test('existing server dart define takes priority over all alternatives', () {
    expect(
      resolveGoogleServerClientId(
        serverClientId: ' server-define ',
        oauthClientId: 'oauth-define',
        environment: const {
          'GOOGLE_SERVER_CLIENT_ID': 'server-env',
          'GOOGLE_OAUTH_CLIENT_ID': 'oauth-env',
        },
      ),
      'server-define',
    );
  });

  test('OAuth dart define takes priority over environment file', () {
    expect(
      resolveGoogleServerClientId(
        serverClientId: '',
        oauthClientId: 'oauth-define',
        environment: const {'GOOGLE_SERVER_CLIENT_ID': 'server-env'},
      ),
      'oauth-define',
    );
  });

  test('empty overrides fall back to the existing OAuth environment name', () {
    expect(
      resolveGoogleServerClientId(
        serverClientId: ' ',
        oauthClientId: '',
        environment: const {
          'GOOGLE_SERVER_CLIENT_ID': ' ',
          'GOOGLE_OAUTH_CLIENT_ID':
              ' local-web-client.apps.googleusercontent.com ',
        },
      ),
      'local-web-client.apps.googleusercontent.com',
    );
  });

  test('missing settings preserve native SDK configuration fallback', () {
    expect(
      resolveGoogleServerClientId(
        serverClientId: '',
        oauthClientId: '',
        environment: const {},
      ),
      isNull,
    );
  });

  test('default environment reads dotenv loaded by the app entry point', () {
    dotenv.loadFromString(envString: 'GOOGLE_OAUTH_CLIENT_ID=local-web-client');
    addTearDown(dotenv.clean);
    expect(
      resolveGoogleServerClientId(serverClientId: '', oauthClientId: ''),
      'local-web-client',
    );
  });
}
