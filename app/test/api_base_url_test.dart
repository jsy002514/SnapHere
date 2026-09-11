import 'package:flutter_test/flutter_test.dart';
import 'package:snap_here/src/core/network/api_client.dart';

void main() {
  test('production server is the default API base URL', () {
    expect(defaultApiBaseUrl, 'http://3.37.39.98');
  });
}
