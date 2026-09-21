import 'package:flutter/material.dart';

abstract final class AuthColors {
  static const primary = Color(0xFF7ADDF2);
  static const primaryDark = Color(0xFF0E7F94);
  static const textPrimary = Color(0xFF1E2530);
  static const textSecondary = Color(0xFF5F6B77);
  static const border = Color(0xFFE8ECEF);
  static const canvas = Color(0xFFF7F8FA);
  static const iconMuted = Color(0xFF8A98A6);
}

class AuthAppLogo extends StatelessWidget {
  const AuthAppLogo({super.key, this.showTagline = true});
  final bool showTagline;

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Container(
          width: 80,
          height: 80,
          decoration: BoxDecoration(
            color: AuthColors.primary,
            borderRadius: BorderRadius.circular(24),
          ),
          alignment: Alignment.center,
          child: const Icon(
            Icons.photo_camera_outlined,
            size: 40,
            color: Colors.white,
          ),
        ),
        const SizedBox(height: 20),
        const Text(
          'SnapHere',
          style: TextStyle(
            color: AuthColors.textPrimary,
            fontSize: 36,
            height: 1.2,
            fontWeight: FontWeight.w900,
            letterSpacing: -1,
          ),
        ),
        if (showTagline) ...[
          const SizedBox(height: 8),
          const Text(
            '한국의 관광지를 사진으로 만나보세요',
            textAlign: TextAlign.center,
            style: TextStyle(
              color: AuthColors.textSecondary,
              fontSize: 16,
              fontWeight: FontWeight.w500,
            ),
          ),
        ],
      ],
    );
  }
}

class AuthPrimaryButton extends StatelessWidget {
  const AuthPrimaryButton({
    required this.label,
    required this.onPressed,
    super.key,
  });
  final String label;
  final VoidCallback? onPressed;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: double.infinity,
      height: 52,
      child: FilledButton(
        style: FilledButton.styleFrom(
          backgroundColor: AuthColors.primary,
          foregroundColor: Colors.white,
          disabledBackgroundColor: AuthColors.border,
          disabledForegroundColor: AuthColors.textSecondary,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(12),
          ),
          textStyle: const TextStyle(fontSize: 16, fontWeight: FontWeight.w700),
        ),
        onPressed: onPressed,
        child: Text(label),
      ),
    );
  }
}

class AuthTopBar extends StatelessWidget implements PreferredSizeWidget {
  const AuthTopBar({required this.title, this.onBack, super.key});
  final String title;
  final VoidCallback? onBack;

  @override
  Size get preferredSize => const Size.fromHeight(52);

  @override
  Widget build(BuildContext context) {
    return AppBar(
      toolbarHeight: 52,
      elevation: 0,
      scrolledUnderElevation: 0,
      backgroundColor: Colors.white,
      surfaceTintColor: Colors.white,
      foregroundColor: AuthColors.textPrimary,
      leadingWidth: 48,
      leading: IconButton(
        icon: const Icon(Icons.chevron_left, size: 28),
        onPressed: onBack ?? () => Navigator.maybePop(context),
      ),
      titleSpacing: 0,
      title: Text(
        title,
        style: const TextStyle(
          color: AuthColors.textPrimary,
          fontSize: 18,
          fontWeight: FontWeight.w700,
        ),
      ),
      bottom: const PreferredSize(
        preferredSize: Size.fromHeight(1),
        child: Divider(height: 1, color: AuthColors.border),
      ),
    );
  }
}

class AuthTextLink extends StatelessWidget {
  const AuthTextLink({required this.label, required this.onTap, super.key});
  final String label;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return TextButton(
      style: TextButton.styleFrom(
        foregroundColor: AuthColors.textSecondary,
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
        minimumSize: Size.zero,
        tapTargetSize: MaterialTapTargetSize.shrinkWrap,
        textStyle: const TextStyle(
          fontSize: 14,
          fontWeight: FontWeight.w600,
          decoration: TextDecoration.underline,
        ),
      ),
      onPressed: onTap,
      child: Text(label),
    );
  }
}
