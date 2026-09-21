import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:snap_here/src/features/auth/application/auth_controller.dart';
import 'package:snap_here/src/features/auth/domain/auth_models.dart';
import 'package:snap_here/src/features/auth/presentation/auth_ui.dart';

class LegalDocumentScreen extends ConsumerWidget {
  const LegalDocumentScreen({required this.type, super.key});

  final LegalDocumentType? type;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final documentType = type;
    if (documentType == null) {
      return const Scaffold(body: Center(child: Text('요청한 문서를 찾을 수 없습니다.')));
    }

    final document = ref.watch(legalDocumentProvider(documentType));
    return Scaffold(
      backgroundColor: Colors.white,
      appBar: AuthTopBar(
        title: document.value?.title ?? '약관 및 정책',
        onBack: () => Navigator.maybePop(context),
      ),
      body: document.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (_, _) => _DocumentError(
          onRetry: () => ref.invalidate(legalDocumentProvider(documentType)),
        ),
        data: (value) => ListView(
          padding: const EdgeInsets.all(24),
          children: [
            Text(
              '버전 ${value.version} · 시행 ${_formatDate(value.effectiveDate)}',
              style: const TextStyle(
                color: AuthColors.textSecondary,
                fontSize: 13,
              ),
            ),
            const SizedBox(height: 24),
            for (final section in value.sections) ...[
              Text(
                section.heading,
                style: const TextStyle(
                  color: AuthColors.textPrimary,
                  fontSize: 16,
                  fontWeight: FontWeight.w700,
                ),
              ),
              const SizedBox(height: 8),
              Text(
                section.body,
                style: const TextStyle(
                  color: AuthColors.textSecondary,
                  fontSize: 14,
                  height: 1.6,
                ),
              ),
              const SizedBox(height: 24),
            ],
          ],
        ),
      ),
    );
  }

  String _formatDate(DateTime date) {
    final month = date.month.toString().padLeft(2, '0');
    final day = date.day.toString().padLeft(2, '0');
    return '${date.year}.$month.$day';
  }
}

class _DocumentError extends StatelessWidget {
  const _DocumentError({required this.onRetry});
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Text('문서를 불러오지 못했습니다.'),
          const SizedBox(height: 12),
          OutlinedButton(onPressed: onRetry, child: const Text('다시 시도')),
        ],
      ),
    );
  }
}
