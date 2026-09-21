import 'package:flutter/widgets.dart';
import 'package:go_router/go_router.dart';

String loginReturnLocation(String? value) {
  final uri = Uri.tryParse(value ?? '');
  if (uri == null ||
      uri.hasScheme ||
      uri.hasAuthority ||
      !uri.path.startsWith('/') ||
      uri.path.contains('\\') ||
      uri.pathSegments.contains('..') ||
      const {
        '/login',
        '/login-required',
        '/onboarding',
        '/profile-setup',
      }.contains(uri.path)) {
    return '/home';
  }
  return uri.toString();
}

String loginPromptLocation(String returnTo) => Uri(
  path: '/login-required',
  queryParameters: {'from': loginReturnLocation(returnTo)},
).toString();

Future<void> requestLogin(BuildContext context, {String? returnTo}) =>
    context.push(
      loginPromptLocation(returnTo ?? GoRouterState.of(context).uri.toString()),
    );
