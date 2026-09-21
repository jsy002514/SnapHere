import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:snap_here/src/features/social/application/social_providers.dart';
import 'package:snap_here/src/features/social/data/api_social_repository.dart';

class DelayedSocialRepository extends ApiSocialRepository {
  Completer<bool> completer = Completer<bool>();
  int calls = 0;
  @override
  Future<bool> setFollowing(String userId, {required bool following}) {
    calls++;
    return completer.future;
  }
}

void main() {
  test('follow controller blocks duplicates and adopts authoritative server result', () async {
    final repository = DelayedSocialRepository();
    final container = ProviderContainer(
      overrides: [socialRepositoryProvider.overrideWithValue(repository)],
    );
    addTearDown(container.dispose);
    final notifier = container.read(followStateProvider.notifier);
    final first = notifier.toggle('u2', initialFollowing: false);
    await notifier.toggle('u2', initialFollowing: false);
    expect(repository.calls, 1);
    expect(container.read(followStateProvider)['u2']!.busy, true);
    repository.completer.complete(true);
    await first;
    expect(container.read(followStateProvider)['u2'], (
      following: true,
      busy: false,
    ));
  });

  test('follow failure leaves original state and allows retry', () async {
    final repository = DelayedSocialRepository();
    final container = ProviderContainer(
      overrides: [socialRepositoryProvider.overrideWithValue(repository)],
    );
    addTearDown(container.dispose);
    final notifier = container.read(followStateProvider.notifier);
    final request = notifier.toggle('u2', initialFollowing: true);
    final failure = expectLater(request, throwsException);
    repository.completer.completeError(Exception('offline'));
    await failure;
    expect(container.read(followStateProvider)['u2'], (
      following: true,
      busy: false,
    ));
    repository.completer = Completer<bool>();
    final retry = notifier.toggle('u2', initialFollowing: true);
    repository.completer.complete(false);
    await retry;
    expect(repository.calls, 2);
    expect(container.read(followStateProvider)['u2']!.following, false);
  });
}
