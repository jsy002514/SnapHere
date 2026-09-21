import 'package:flutter/material.dart';
import 'package:snap_here/src/core/network/cursor_page.dart';

/// 커서는 해석하지 않고 서버에 전달한다. 실패한 더보기는 기존 목록을 보존한다.
class PagedSliver<T> extends StatefulWidget {
  const PagedSliver({
    required this.load,
    required this.itemId,
    required this.sliverBuilder,
    required this.empty,
    super.key,
  });
  final Future<CursorPage<T>> Function(String? cursor) load;
  final String Function(T item) itemId;
  final Widget Function(List<T> items) sliverBuilder;
  final Widget empty;

  @override
  State<PagedSliver<T>> createState() => _PagedSliverState<T>();
}

class _PagedSliverState<T> extends State<PagedSliver<T>> {
  final _items = <T>[];
  String? _cursor;
  Object? _error;
  bool _loading = true;
  bool _hasNext = true;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final page = await widget.load(_cursor);
      if (!mounted) return;
      setState(() {
        final ids = _items.map(widget.itemId).toSet();
        _items.addAll(page.items.where((item) => ids.add(widget.itemId(item))));
        _hasNext = page.hasNext && page.nextCursor != _cursor;
        _cursor = page.nextCursor;
        _loading = false;
        _error = null;
      });
    } on Object catch (error) {
      if (mounted) {
        setState(() {
          _loading = false;
          _error = error;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) => SliverMainAxisGroup(
    slivers: [
      if (_items.isNotEmpty) widget.sliverBuilder(List.unmodifiable(_items)),
      SliverToBoxAdapter(
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: _loading
              ? const Center(child: CircularProgressIndicator())
              : _error != null
              ? RetryMessage(
                  message: '목록을 불러오지 못했어요',
                  onRetry: () {
                    setState(() => _loading = true);
                    _load();
                  },
                )
              : _items.isEmpty
              ? widget.empty
              : _hasNext
              ? Center(
                  child: TextButton(
                    onPressed: () {
                      setState(() => _loading = true);
                      _load();
                    },
                    child: const Text('더 보기'),
                  ),
                )
              : const SizedBox.shrink(),
        ),
      ),
    ],
  );
}

class RetryMessage extends StatelessWidget {
  const RetryMessage({required this.message, required this.onRetry, super.key});
  final String message;
  final VoidCallback onRetry;
  @override
  Widget build(BuildContext context) => Column(
    mainAxisSize: MainAxisSize.min,
    children: [
      Text(message, textAlign: TextAlign.center),
      TextButton.icon(
        onPressed: onRetry,
        icon: const Icon(Icons.refresh),
        label: const Text('다시 시도'),
      ),
    ],
  );
}
