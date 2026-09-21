import 'package:flutter/material.dart';

/// Figma `Wireframe_v3 / 10 Design System — Critical Fixes / DS/Foundations`의
/// 변수를 그대로 옮긴 값이다. 디자인에서 토큰이 바뀌면 이 파일만 고친다.
abstract final class AppColors {
  /// `color/bg/brand`
  static const brand = Color(0xFF7ADDF2);

  /// `color/bg/brand-subtle`
  static const brandSubtle = Color(0xFFE8FAFD);

  /// `color/text/primary`
  static const textPrimary = Color(0xFF1E2530);

  /// `color/text/secondary`
  static const textSecondary = Color(0xFF78838F);

  /// `color/border/default`
  static const border = Color(0xFFE8ECEF);

  /// `color/feedback/error`
  static const error = Color(0xFFEF4444);

  /// 화면 배경. 프레임 `03_커뮤니티_전체`의 배경색이다.
  static const surface = Color(0xFFF7F8FA);

  /// 카드 배경.
  static const card = Color(0xFFFFFFFF);
}

/// `DS/Foundations / Spacing` — 4 · 8 · 12 · 16 · 24 · 32
abstract final class AppSpacing {
  static const xs = 4.0;
  static const sm = 8.0;
  static const md = 12.0;
  static const lg = 16.0;
  static const xl = 24.0;
  static const xxl = 32.0;
}

/// `DS/Foundations / Radius` — 0 · 8 · 12 · 16 · 24 · full
abstract final class AppRadius {
  static const none = 0.0;
  static const sm = 8.0;
  static const md = 12.0;
  static const lg = 16.0;
  static const xl = 24.0;

  /// pill 형태. 실제 높이보다 큰 값이면 되므로 999를 쓴다.
  static const full = 999.0;
}
