import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mobile_common/src/schedule_settings.dart';
import 'package:mobile_common/src/settings_page.dart';

void main() {
  testWidgets('first run saves the chosen type and color only after apply', (
    tester,
  ) async {
    ScheduleSettings? applied;
    await tester.pumpWidget(
      MaterialApp(
        home: ScheduleSettingsPage(
          firstRun: true,
          initial: const ScheduleSettings(),
          origin: Uri.parse('https://anissia.net'),
          onApply: (value) async {
            applied = value;
          },
        ),
      ),
    );
    expect(find.text('어떤 편성표가 편한가요?'), findsOneWidget);
    await tester.tap(find.text('리스트'));
    await tester.tap(find.text('색상 선택하기'));
    await tester.pumpAndSettle();
    expect(applied, isNull);
    await tester.scrollUntilVisible(find.widgetWithText(ActionChip, '민트'), 160);
    await tester.tap(find.widgetWithText(ActionChip, '민트'));
    await tester.pumpAndSettle();
    await tester.scrollUntilVisible(find.text('적용하기'), 160);
    await tester.tap(find.text('적용하기'));
    await tester.pumpAndSettle();
    expect(applied?.completed, isTrue);
    expect(applied?.view, ScheduleView.list);
    expect(applied?.listTheme, isNot(defaultListTheme));
    expect(
      ScheduleSettings.validateTheme(ScheduleView.list, applied!.listTheme),
      isNotNull,
    );
  });

  testWidgets('settings remain usable at narrow width and larger text', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(320, 568);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    await tester.pumpWidget(
      MaterialApp(
        builder: (context, child) => MediaQuery(
          data: MediaQuery.of(
            context,
          ).copyWith(textScaler: const TextScaler.linear(1.4)),
          child: child!,
        ),
        home: ScheduleSettingsPage(
          initial: const ScheduleSettings(completed: true),
          origin: Uri.parse('https://anissia.net'),
          onApply: (_) async {},
        ),
      ),
    );
    await tester.pumpAndSettle();
    await tester.scrollUntilVisible(find.text('직접 색상 조정'), 160);
    await tester.tap(find.text('직접 색상 조정'));
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
  });
}
