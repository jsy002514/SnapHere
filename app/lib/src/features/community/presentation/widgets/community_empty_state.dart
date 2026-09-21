import 'package:flutter/material.dart';
import 'package:snap_here/src/app/theme/app_tokens.dart';

/// Figma `03_커뮤니티_팔로잉_빈상태`, `03_커뮤니티_검색_없음`이 공유하는 빈 상태.
///
/// 두 화면의 차이는 아이콘·문구와 버튼 스타일(채움 / 테두리)뿐이다.
class CommunityEmptyState extends StatelessWidget {
  const CommunityEmptyState({
    required this.icon,
    required this.title,
    required this.description,
    this.actionLabel,
    this.onAction,
    this.actionStyle = CommunityEmptyActionStyle.filled,
    super.key,
  });

  final IconData icon;
  final String title;
  final String description;
  final String? actionLabel;
  final VoidCallback? onAction;
  final CommunityEmptyActionStyle actionStyle;

  @override
  Widget build(BuildContext context) {
    final textTheme = Theme.of(context).textTheme;
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.xxl),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              width: 96,
              height: 96,
              decoration: const BoxDecoration(
                color: AppColors.brandSubtle,
                shape: BoxShape.circle,
              ),
              child: Icon(icon, size: 40, color: AppColors.brand),
            ),
            const SizedBox(height: AppSpacing.xl),
            Text(
              title,
              style: textTheme.titleMedium,
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: AppSpacing.sm),
            Text(
              description,
              textAlign: TextAlign.center,
              style: textTheme.bodyMedium?.copyWith(
                color: AppColors.textSecondary,
              ),
            ),
            if (actionLabel case final label?) ...[
              const SizedBox(height: AppSpacing.xl),
              _buildAction(label),
            ],
          ],
        ),
      ),
    );
  }

  Widget _buildAction(String label) {
    final shape = RoundedRectangleBorder(
      borderRadius: BorderRadius.circular(AppRadius.full),
    );
    return switch (actionStyle) {
      CommunityEmptyActionStyle.filled => FilledButton(
        onPressed: onAction,
        style: FilledButton.styleFrom(
          backgroundColor: AppColors.brand,
          foregroundColor: AppColors.textPrimary,
          shape: shape,
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [Text(label), const Icon(Icons.chevron_right, size: 18)],
        ),
      ),
      CommunityEmptyActionStyle.outlined => OutlinedButton(
        onPressed: onAction,
        style: OutlinedButton.styleFrom(
          foregroundColor: AppColors.textPrimary,
          side: const BorderSide(color: AppColors.brand),
          shape: shape,
        ),
        child: Text(label),
      ),
    };
  }
}

enum CommunityEmptyActionStyle { filled, outlined }
