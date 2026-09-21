import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:snap_here/src/app/theme/app_tokens.dart';
import 'package:snap_here/src/features/event/application/event_providers.dart';
import 'package:snap_here/src/features/event/domain/event_models.dart';
import 'package:snap_here/src/features/event/presentation/widgets/event_image.dart';

class EventScreen extends ConsumerWidget {
  const EventScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final selectedRegion = ref.watch(selectedEventRegionProvider);
    final regions = ref.watch(eventRegionSummaryProvider);
    final events = ref.watch(eventListProvider);

    return Scaffold(
      appBar: AppBar(
        title: Text('이벤트', style: Theme.of(context).textTheme.headlineSmall),
        actions: [
          IconButton(
            tooltip: '알림',
            onPressed: () => context.push('/notifications'),
            icon: const Icon(Icons.notifications_none),
          ),
          const SizedBox(width: AppSpacing.sm),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: () async {
          ref.invalidate(eventRegionSummaryProvider);
          ref.invalidate(eventListProvider);
        },
        child: CustomScrollView(
          physics: const AlwaysScrollableScrollPhysics(),
          slivers: [
            SliverToBoxAdapter(
              child: Padding(
                padding: const EdgeInsets.fromLTRB(
                  AppSpacing.lg,
                  AppSpacing.sm,
                  AppSpacing.lg,
                  0,
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      '지금, 여기서만 만나는 순간',
                      style: Theme.of(context).textTheme.headlineMedium,
                    ),
                    const SizedBox(height: AppSpacing.xs),
                    Text(
                      '가까운 행사에 참여하고 한정 뱃지를 모아보세요.',
                      style: Theme.of(context).textTheme.bodyMedium
                          ?.copyWith(color: AppColors.textSecondary),
                    ),
                    const SizedBox(height: AppSpacing.xl),
                    _RegionSelector(
                      regions: regions,
                      selectedRegion: selectedRegion,
                    ),
                    const SizedBox(height: AppSpacing.xl),
                    Row(
                      children: [
                        Text(
                          selectedRegion == null ? '다가오는 이벤트' : '지역 이벤트',
                          style: Theme.of(context).textTheme.titleMedium,
                        ),
                        const Spacer(),
                        Text(
                          '진행 중 · 예정',
                          style: Theme.of(context).textTheme.bodySmall,
                        ),
                      ],
                    ),
                    const SizedBox(height: AppSpacing.md),
                  ],
                ),
              ),
            ),
            events.when(
              loading: () => const SliverFillRemaining(
                hasScrollBody: false,
                child: Center(child: CircularProgressIndicator()),
              ),
              error: (error, _) => SliverFillRemaining(
                hasScrollBody: false,
                child: _EventError(
                  message: '$error',
                  onRetry: () => ref.invalidate(eventListProvider),
                ),
              ),
              data: (items) => items.isEmpty
                  ? const SliverFillRemaining(
                      hasScrollBody: false,
                      child: _EventEmpty(),
                    )
                  : SliverPadding(
                      padding: const EdgeInsets.fromLTRB(
                        AppSpacing.lg,
                        0,
                        AppSpacing.lg,
                        120,
                      ),
                      sliver: SliverToBoxAdapter(child: _EventMasonry(items)),
                    ),
            ),
          ],
        ),
      ),
    );
  }
}

class _RegionSelector extends ConsumerWidget {
  const _RegionSelector({required this.regions, required this.selectedRegion});

  final AsyncValue<List<EventRegionSummary>> regions;
  final int? selectedRegion;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final viewed = ref.watch(viewedEventRegionsProvider);
    return SizedBox(
      height: 40,
      child: regions.when(
        loading: () => const Align(
          alignment: Alignment.centerLeft,
          child: SizedBox(
            width: 20,
            height: 20,
            child: CircularProgressIndicator(strokeWidth: 2),
          ),
        ),
        error: (_, _) => TextButton(
          onPressed: () => ref.invalidate(eventRegionSummaryProvider),
          child: const Text('지역을 다시 불러오기'),
        ),
        data: (items) => ListView(
          scrollDirection: Axis.horizontal,
          children: [
            _RegionChip(
              label: '전체',
              selected: selectedRegion == null,
              onTap: () =>
                  ref.read(selectedEventRegionProvider.notifier).select(null),
            ),
            for (final region in items) ...[
              const SizedBox(width: AppSpacing.sm),
              _RegionChip(
                label: region.areaName,
                selected: selectedRegion == region.areaCode,
                isNew: region.newCount > 0 && !viewed.contains(region.areaCode),
                onTap: () {
                  ref
                      .read(selectedEventRegionProvider.notifier)
                      .select(region.areaCode);
                  ref
                      .read(viewedEventRegionsProvider.notifier)
                      .markViewed(region.areaCode);
                },
              ),
            ],
          ],
        ),
      ),
    );
  }
}

class _RegionChip extends StatelessWidget {
  const _RegionChip({
    required this.label,
    required this.selected,
    required this.onTap,
    this.isNew = false,
  });

  final String label;
  final bool selected;
  final bool isNew;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => Material(
    color: selected ? AppColors.textPrimary : AppColors.card,
    shape: StadiumBorder(
      side: BorderSide(
        color: isNew ? AppColors.brand : AppColors.border,
        width: isNew ? 2 : 1,
      ),
    ),
    child: InkWell(
      onTap: onTap,
      customBorder: const StadiumBorder(),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            if (isNew) ...[
              const Icon(Icons.auto_awesome, size: 14, color: AppColors.brand),
              const SizedBox(width: AppSpacing.xs),
            ],
            Text(
              label,
              style: Theme.of(context).textTheme.labelLarge?.copyWith(
                color: selected ? Colors.white : AppColors.textPrimary,
              ),
            ),
          ],
        ),
      ),
    ),
  );
}

class _EventMasonry extends StatelessWidget {
  const _EventMasonry(this.events);

  final List<EventSummary> events;

  @override
  Widget build(BuildContext context) {
    final left = <Widget>[];
    final right = <Widget>[];
    for (var index = 0; index < events.length; index++) {
      final card = Padding(
        padding: const EdgeInsets.only(bottom: AppSpacing.md),
        child: _EventCard(
          event: events[index],
          imageHeight: index.isEven ? 190 : 150,
        ),
      );
      (index.isEven ? left : right).add(card);
    }
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Expanded(child: Column(children: left)),
        const SizedBox(width: AppSpacing.md),
        Expanded(child: Column(children: right)),
      ],
    );
  }
}

class _EventCard extends StatelessWidget {
  const _EventCard({required this.event, required this.imageHeight});

  final EventSummary event;
  final double imageHeight;

  @override
  Widget build(BuildContext context) => Card(
    shape: RoundedRectangleBorder(
      borderRadius: BorderRadius.circular(AppRadius.lg),
      side: const BorderSide(color: AppColors.border),
    ),
    child: InkWell(
      onTap: () => context.push('/events/${event.eventId}'),
      borderRadius: BorderRadius.circular(AppRadius.lg),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            height: imageHeight,
            child: Stack(
              fit: StackFit.expand,
              children: [
                EventImage(source: event.thumbnailUrl),
                Positioned(
                  top: AppSpacing.sm,
                  left: AppSpacing.sm,
                  child: _ScheduleBadge(event.scheduleLabel),
                ),
                if (event.isNew)
                  const Positioned(
                    top: AppSpacing.sm,
                    right: AppSpacing.sm,
                    child: _NewBadge(),
                  ),
              ],
            ),
          ),
          Padding(
            padding: const EdgeInsets.all(AppSpacing.md),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  event.areaName,
                  style: Theme.of(context).textTheme.bodySmall?.copyWith(
                    color: AppColors.brand,
                    fontWeight: FontWeight.w700,
                  ),
                ),
                const SizedBox(height: AppSpacing.xs),
                Text(
                  event.title,
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                  style: Theme.of(context).textTheme.labelLarge,
                ),
                const SizedBox(height: AppSpacing.sm),
                Text(
                  event.periodLabel,
                  style: Theme.of(context).textTheme.bodySmall,
                ),
                const SizedBox(height: AppSpacing.sm),
                Row(
                  children: [
                    const Icon(
                      Icons.photo_camera_outlined,
                      size: 14,
                      color: AppColors.textSecondary,
                    ),
                    const SizedBox(width: AppSpacing.xs),
                    Text(
                      '${event.participantCount}명이 기록했어요',
                      style: Theme.of(context).textTheme.bodySmall,
                    ),
                  ],
                ),
              ],
            ),
          ),
        ],
      ),
    ),
  );
}

class _ScheduleBadge extends StatelessWidget {
  const _ScheduleBadge(this.label);
  final String label;

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 5),
    decoration: BoxDecoration(
      color: AppColors.textPrimary.withValues(alpha: 0.84),
      borderRadius: BorderRadius.circular(AppRadius.full),
    ),
    child: Text(
      label,
      style: const TextStyle(
        color: Colors.white,
        fontSize: 11,
        fontWeight: FontWeight.w700,
      ),
    ),
  );
}

class _NewBadge extends StatelessWidget {
  const _NewBadge();

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 5),
    decoration: BoxDecoration(
      color: AppColors.brand,
      borderRadius: BorderRadius.circular(AppRadius.full),
    ),
    child: const Text(
      'NEW',
      style: TextStyle(
        color: AppColors.textPrimary,
        fontSize: 10,
        fontWeight: FontWeight.w900,
      ),
    ),
  );
}

class _EventEmpty extends StatelessWidget {
  const _EventEmpty();

  @override
  Widget build(BuildContext context) => const Center(
    child: Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(
          Icons.event_busy_outlined,
          size: 52,
          color: AppColors.textSecondary,
        ),
        SizedBox(height: AppSpacing.md),
        Text('예정된 이벤트가 없어요'),
      ],
    ),
  );
}

class _EventError extends StatelessWidget {
  const _EventError({required this.message, required this.onRetry});

  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) => Center(
    child: Padding(
      padding: const EdgeInsets.all(AppSpacing.xl),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Icon(Icons.cloud_off_outlined, size: 48),
          const SizedBox(height: AppSpacing.md),
          const Text('이벤트를 불러오지 못했어요'),
          const SizedBox(height: AppSpacing.xs),
          Text(message, textAlign: TextAlign.center),
          const SizedBox(height: AppSpacing.lg),
          FilledButton(onPressed: onRetry, child: const Text('다시 시도')),
        ],
      ),
    ),
  );
}
