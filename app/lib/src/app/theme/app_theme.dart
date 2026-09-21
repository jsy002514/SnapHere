import 'package:flutter/material.dart';
import 'package:snap_here/src/app/theme/app_tokens.dart';

abstract final class AppTheme {
  static ThemeData get light {
    const colorScheme = ColorScheme.light(
      primary: AppColors.brand,
      onPrimary: AppColors.textPrimary,
      primaryContainer: AppColors.brandSubtle,
      onPrimaryContainer: AppColors.textPrimary,
      secondary: AppColors.brand,
      onSecondary: AppColors.textPrimary,
      surface: AppColors.card,
      onSurface: AppColors.textPrimary,
      onSurfaceVariant: AppColors.textSecondary,
      outline: AppColors.border,
      outlineVariant: AppColors.border,
      error: AppColors.error,
      onError: Colors.white,
    );

    return ThemeData(
      colorScheme: colorScheme,
      scaffoldBackgroundColor: AppColors.surface,
      useMaterial3: true,
      textTheme: _textTheme,
      appBarTheme: const AppBarTheme(
        centerTitle: false,
        backgroundColor: AppColors.card,
        foregroundColor: AppColors.textPrimary,
        elevation: 0,
        scrolledUnderElevation: 0,
      ),
      cardTheme: const CardThemeData(
        clipBehavior: Clip.antiAlias,
        margin: EdgeInsets.zero,
        color: AppColors.card,
        elevation: 0,
      ),
      dividerTheme: const DividerThemeData(
        color: AppColors.border,
        thickness: 1,
        space: 1,
      ),
      // Figma `SearchBar`: 높이 38 · radius 10 · padding 좌 10 / 우 14
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: AppColors.card,
        isDense: true,
        constraints: const BoxConstraints(minHeight: 38, maxHeight: 38),
        contentPadding: const EdgeInsets.symmetric(
          horizontal: 10,
          vertical: AppSpacing.sm,
        ),
        hintStyle: _textTheme.bodyMedium?.copyWith(
          color: AppColors.textSecondary,
        ),
        border: _inputBorder(AppColors.border),
        enabledBorder: _inputBorder(AppColors.border),
        focusedBorder: _inputBorder(AppColors.brand),
      ),
    );
  }

  static OutlineInputBorder _inputBorder(Color color) => OutlineInputBorder(
    borderRadius: BorderRadius.circular(10),
    borderSide: BorderSide(color: color),
  );

  /// `DS/Foundations / Typography`.
  ///
  /// 디자인은 Inter 단일 사용을 전제하지만 앱에 폰트를 아직 번들하지 않았다.
  /// 검수 노트의 "한국어 가독성 위해 Pretendard 등 한글 폰트 도입 검토 필요"가
  /// 결정되면 여기에 `fontFamily`를 지정한다.
  static const _textTheme = TextTheme(
    // Display · 20 / 28 / 900
    headlineMedium: TextStyle(
      fontSize: 20,
      height: 28 / 20,
      fontWeight: FontWeight.w900,
      color: AppColors.textPrimary,
    ),
    // Heading · 18 / 24 / 700
    headlineSmall: TextStyle(
      fontSize: 18,
      height: 24 / 18,
      fontWeight: FontWeight.w700,
      color: AppColors.textPrimary,
    ),
    // Title · 16 / 22 / 700
    titleMedium: TextStyle(
      fontSize: 16,
      height: 22 / 16,
      fontWeight: FontWeight.w700,
      color: AppColors.textPrimary,
    ),
    // Label · 14 / 20 / 600
    labelLarge: TextStyle(
      fontSize: 14,
      height: 20 / 14,
      fontWeight: FontWeight.w600,
      color: AppColors.textPrimary,
    ),
    // Body · 14 / 20 / 400
    bodyMedium: TextStyle(
      fontSize: 14,
      height: 20 / 14,
      fontWeight: FontWeight.w400,
      color: AppColors.textPrimary,
    ),
    // Caption · 11 / 16 / 500
    bodySmall: TextStyle(
      fontSize: 11,
      height: 16 / 11,
      fontWeight: FontWeight.w500,
      color: AppColors.textSecondary,
    ),
  );
}
