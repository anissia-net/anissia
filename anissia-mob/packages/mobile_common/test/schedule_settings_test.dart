import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:mobile_common/src/schedule_settings.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('all website presets and list default conform to their own formats', () {
    for (final preset in timelinePresets) {
      expect(
        ScheduleSettings.validateTheme(ScheduleView.timeline, preset.timeline),
        preset.timeline,
        reason: preset.name,
      );
    }
    expect(defaultListTheme.length, 144);
    expect(
      ScheduleSettings.validateTheme(ScheduleView.list, defaultListTheme),
      defaultListTheme,
    );
    expect(
      ScheduleSettings.validateTheme(ScheduleView.timeline, defaultListTheme),
      isNull,
    );
    expect(
      ScheduleSettings.validateTheme(
        ScheduleView.list,
        timelinePresets.first.timeline,
      ),
      isNull,
    );
  });

  test('themes reject malformed values and out of range glow strength', () {
    final good = timelinePresets.first.timeline;
    expect(
      ScheduleSettings.validateTheme(
        ScheduleView.timeline,
        '#${good.toUpperCase()}',
      ),
      good,
    );
    for (final code in [
      '$good<script>',
      '${good.substring(0, 36)}ffff',
      good.substring(0, 38),
      'z' * 40,
    ]) {
      expect(
        ScheduleSettings.validateTheme(ScheduleView.timeline, code),
        isNull,
      );
    }
  });

  test('type changes retain independent themes and user selection', () {
    final original = const ScheduleSettings().copyWith(
      completed: true,
      timelineTheme: timelinePresets[2].timeline,
    );
    final list = original.copyWith(view: ScheduleView.list);
    final restored = list.copyWith(view: ScheduleView.timeline);
    expect(list.theme, defaultListTheme);
    expect(restored.theme, timelinePresets[2].timeline);
    expect(restored.completed, isTrue);
  });

  test('serialized settings restore all fields', () {
    final original = ScheduleSettings(
      completed: true,
      view: ScheduleView.list,
      timelineTheme: timelinePresets[3].timeline,
      colorMode: ScheduleColorMode.dark,
    );
    final restored = ScheduleSettings.fromJson(
      jsonDecode(jsonEncode(original.toJson())),
    );
    expect(restored.toJson(), original.toJson());
  });

  test('unrecognized schema returns onboarding defaults', () {
    expect(
      ScheduleSettings.fromJson({
        'schemaVersion': 2,
        'completed': true,
      }).completed,
      isFalse,
    );
    expect(ScheduleSettings.fromJson(['wrong']).completed, isFalse);
  });

  test(
    'invalid fields recover independently without losing completed setup',
    () {
      final settings = ScheduleSettings.fromJson({
        'schemaVersion': 1,
        'completed': true,
        'view': 'list',
        'timelineTheme': null,
        'listTheme': 'broken',
        'colorMode': 'invalid',
      });
      expect(settings.completed, isTrue);
      expect(settings.view, ScheduleView.list);
      expect(settings.listTheme, defaultListTheme);
      expect(settings.colorMode, ScheduleColorMode.system);
    },
  );

  test(
    'deep link overrides are transient and malformed themes keep saved color',
    () {
      final saved = ScheduleSettings(
        completed: true,
        timelineTheme: timelinePresets[2].timeline,
      );
      final linked = saved.fromLink(
        Uri.parse('https://anissia.net/schedule/2015/#$defaultListTheme'),
      );
      expect(linked.view, ScheduleView.list);
      expect(saved.view, ScheduleView.timeline);
      final malformed = saved.fromLink(
        Uri.parse('https://anissia.net/schedule/2026#bad'),
      );
      expect(malformed.timelineTheme, saved.timelineTheme);
      expect(
        saved.uri(Uri.parse('https://anissia.net')).fragment,
        timelinePresets[2].timeline,
      );
    },
  );

  test('repository round trips and repairs malformed storage', () async {
    SharedPreferences.setMockInitialValues({
      SettingsRepository.key: '{invalid',
    });
    final preferences = await SharedPreferences.getInstance();
    final repository = SettingsRepository(preferences);
    expect(repository.read().completed, isFalse);
    final configured = const ScheduleSettings(
      completed: true,
      view: ScheduleView.list,
      colorMode: ScheduleColorMode.dark,
    );
    await repository.save(configured);
    final afterRestart = SettingsRepository(
      await SharedPreferences.getInstance(),
    ).read();
    expect(afterRestart.toJson(), configured.toJson());
  });
}
