import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:snap_here/src/core/network/cursor_page.dart';
import 'package:snap_here/src/core/ui/paged_sliver.dart';

void main() {
  testWidgets(
    'pagination keeps previous items on error, retries same cursor, and deduplicates',
    (tester) async {
      var attempts = 0;
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: CustomScrollView(
              slivers: [
                PagedSliver<String>(
                  load: (cursor) async {
                    if (cursor == null) {
                      return const CursorPage(
                        items: ['first'],
                        nextCursor: 'next',
                      );
                    }
                    expect(cursor, 'next');
                    if (attempts++ == 0) throw Exception('offline');
                    return const CursorPage(items: ['first', 'second']);
                  },
                  itemId: (item) => item,
                  empty: const Text('empty'),
                  sliverBuilder: (items) =>
                      SliverList.list(children: items.map(Text.new).toList()),
                ),
              ],
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('first'), findsOneWidget);
      await tester.tap(find.text('더 보기'));
      await tester.pumpAndSettle();
      expect(find.text('first'), findsOneWidget);
      expect(find.text('다시 시도'), findsOneWidget);
      await tester.tap(find.text('다시 시도'));
      await tester.pumpAndSettle();
      expect(find.text('first'), findsOneWidget);
      expect(find.text('second'), findsOneWidget);
      expect(find.text('더 보기'), findsNothing);
    },
  );
}
