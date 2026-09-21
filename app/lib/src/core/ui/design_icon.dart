import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

/// Figma 원본의 바깥 프레임까지 PNG로 내보내 내부 glyph 비율을 보존한다.
class DesignIcon extends StatelessWidget {
  const DesignIcon(this.name, {this.size = 24, this.color, super.key});
  final String name;
  final double size;
  final Color? color;
  @override
  Widget build(BuildContext context) => Image.asset(
    'assets/images/navigation/$name.png',
    width: size,
    height: size,
    color: color,
    excludeFromSemantics: true,
  );
}

class DesignBackButton extends StatelessWidget {
  const DesignBackButton({super.key});
  @override
  Widget build(BuildContext context) => IconButton(
    tooltip: '뒤로',
    onPressed: () => context.canPop() ? context.pop() : context.go('/home'),
    icon: const DesignIcon('back'),
  );
}
