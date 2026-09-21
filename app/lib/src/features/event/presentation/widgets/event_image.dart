import 'package:flutter/material.dart';
import 'package:snap_here/src/app/theme/app_tokens.dart';

class EventImage extends StatelessWidget {
  const EventImage({
    required this.source,
    this.fit = BoxFit.cover,
    this.width,
    this.height,
    super.key,
  });

  final String? source;
  final BoxFit fit;
  final double? width;
  final double? height;

  @override
  Widget build(BuildContext context) {
    final value = source;
    if (value == null || value.isEmpty) return _fallback();
    if (value.startsWith('assets/')) {
      return Image.asset(
        value,
        fit: fit,
        width: width,
        height: height,
        errorBuilder: (_, _, _) => _fallback(),
      );
    }
    return Image.network(
      value,
      fit: fit,
      width: width,
      height: height,
      errorBuilder: (_, _, _) => _fallback(),
      loadingBuilder: (_, child, progress) => progress == null
          ? child
          : const ColoredBox(
              color: AppColors.brandSubtle,
              child: Center(child: CircularProgressIndicator(strokeWidth: 2)),
            ),
    );
  }

  Widget _fallback() => Container(
    width: width,
    height: height,
    color: AppColors.brandSubtle,
    alignment: Alignment.center,
    child: const Icon(
      Icons.local_florist_outlined,
      color: AppColors.textSecondary,
      size: 40,
    ),
  );
}
