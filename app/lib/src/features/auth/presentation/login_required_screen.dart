import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:snap_here/src/app/router/login_navigation.dart';
import 'package:snap_here/src/features/auth/presentation/auth_ui.dart';

class LoginRequiredScreen extends StatelessWidget {
  const LoginRequiredScreen({this.returnTo, super.key});
  final String? returnTo;

  void _close(BuildContext context) {
    if (context.canPop()) {
      context.pop();
    } else {
      context.go('/home');
    }
  }

  @override
  Widget build(BuildContext context) => Stack(
    children: [
      if (!context.canPop())
        const Positioned.fill(child: ColoredBox(color: AuthColors.canvas)),
      Center(
        child: AlertDialog(
          scrollable: true,
          backgroundColor: Colors.white,
          surfaceTintColor: Colors.transparent,
          constraints: const BoxConstraints(maxWidth: 360),
          titlePadding: const EdgeInsets.fromLTRB(24, 12, 12, 0),
          title: Row(
            children: [
              const Expanded(
                child: Text(
                  '로그인이 필요해요',
                  style: TextStyle(fontSize: 18, fontWeight: FontWeight.w700),
                ),
              ),
              IconButton(
                tooltip: '닫기',
                onPressed: () => _close(context),
                icon: const Icon(Icons.close),
              ),
            ],
          ),
          content: const Text('좋아요, 팔로우, 댓글, 업로드 기능을 이용하려면 로그인해 주세요.'),
          actionsPadding: const EdgeInsets.fromLTRB(24, 0, 24, 16),
          actions: [
            Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                FilledButton.icon(
                  style: FilledButton.styleFrom(
                    foregroundColor: AuthColors.textPrimary,
                  ),
                  onPressed: () => context.pushReplacement(
                    Uri(
                      path: '/login',
                      queryParameters: {'from': loginReturnLocation(returnTo)},
                    ).toString(),
                  ),
                  icon: const Icon(Icons.login, size: 18),
                  label: const Text('로그인하러 가기'),
                ),
                TextButton(
                  style: TextButton.styleFrom(
                    foregroundColor: AuthColors.textPrimary,
                  ),
                  onPressed: () => _close(context),
                  child: const Text('취소'),
                ),
              ],
            ),
          ],
        ),
      ),
    ],
  );
}
