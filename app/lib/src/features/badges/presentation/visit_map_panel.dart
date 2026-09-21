import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:google_maps_flutter/google_maps_flutter.dart';
import 'package:snap_here/src/app/theme/app_tokens.dart';
import 'package:snap_here/src/core/ui/paged_sliver.dart';
import 'package:snap_here/src/features/badges/application/badge_providers.dart';
import 'package:snap_here/src/features/badges/domain/badge_models.dart';
import 'package:snap_here/src/features/map/presentation/snap_map.dart';

class VisitMapPanel extends ConsumerWidget {
  const VisitMapPanel({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) => SizedBox(
    height: 280,
    child: ref
        .watch(visitMapProvider)
        .when(
          loading: () => const Center(child: CircularProgressIndicator()),
          error: (_, _) => Center(
            child: RetryMessage(
              message: '방문 지도를 불러오지 못했어요',
              onRetry: () => ref.invalidate(visitMapProvider),
            ),
          ),
          data: (data) => _VisitMap(key: ValueKey(data), snapshot: data),
        ),
  );
}

class _VisitMap extends StatefulWidget {
  const _VisitMap({required this.snapshot, super.key});
  final VisitMapSnapshot snapshot;
  @override
  State<_VisitMap> createState() => _VisitMapState();
}

class _VisitMapState extends State<_VisitMap> {
  GoogleMapController? _controller;
  @override
  void dispose() {
    _controller?.dispose();
    super.dispose();
  }

  Future<void> _fit(GoogleMapController controller) async {
    _controller = controller;
    final points = widget.snapshot.points;
    if (points.isEmpty) return;
    final latitudes = points.map((point) => point.latitude);
    final longitudes = points.map((point) => point.longitude);
    final south = latitudes.reduce(math.min),
        north = latitudes.reduce(math.max);
    final west = longitudes.reduce(math.min),
        east = longitudes.reduce(math.max);
    final update = north - south < .001 && east - west < .001
        ? CameraUpdate.newLatLngZoom(LatLng(south, west), 12)
        : CameraUpdate.newLatLngBounds(
            LatLngBounds(
              southwest: LatLng(south, west),
              northeast: LatLng(north, east),
            ),
            44,
          );
    await controller.moveCamera(update);
  }

  @override
  Widget build(BuildContext context) => Stack(
    children: [
      Positioned.fill(
        child: SnapMap(
          onCreated: _fit,
          markers: {
            for (final point in widget.snapshot.points)
              Marker(
                markerId: MarkerId(point.placeId),
                position: LatLng(point.latitude, point.longitude),
                infoWindow: InfoWindow(
                  title: '방문 ${point.visitCount}회',
                  snippet: '방문 장소 보기',
                  onTap: () => context.push('/places/${point.placeId}'),
                ),
              ),
          },
        ),
      ),
      Positioned(
        top: 12,
        right: 12,
        child: DecoratedBox(
          decoration: BoxDecoration(
            color: Colors.white,
            borderRadius: BorderRadius.circular(20),
            border: Border.all(color: AppColors.brand),
          ),
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
            child: Text(
              '${widget.snapshot.visitedRegionCount} / ${widget.snapshot.totalRegionCount} 지역 방문',
              style: const TextStyle(fontSize: 11, fontWeight: FontWeight.bold),
            ),
          ),
        ),
      ),
      if (widget.snapshot.points.isEmpty)
        const Positioned(
          top: 52,
          left: 16,
          right: 16,
          child: Card(
            child: Padding(
              padding: EdgeInsets.all(8),
              child: Text(
                '여행 사진을 올리면 방문 장소가 표시돼요.',
                textAlign: TextAlign.center,
                style: TextStyle(fontSize: 12),
              ),
            ),
          ),
        ),
    ],
  );
}
