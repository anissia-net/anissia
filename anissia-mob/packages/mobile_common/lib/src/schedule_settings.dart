import 'dart:convert';

import 'package:shared_preferences/shared_preferences.dart';

enum ScheduleView { timeline, list }

enum ScheduleColorMode { system, light, dark }

class ThemePreset {
  const ThemePreset(this.name, this.timeline);
  final String name;
  final String timeline;
}

const timelinePresets = [
  ThemePreset('애니시아', '905574f5efe6ae97bee3aa9a0b0b109673783232'),
  ThemePreset('벚꽃', 'c64f78fbf4f6f0a6c0f2a0bc120d108a5cc83737'),
  ThemePreset('민트', '0f7468f1f7f57ccab872d8c0090f0e3b80c43232'),
  ThemePreset('라벤더', '6a4fbaf5f3fab49cdcbba5f60e0c14d17fae3737'),
  ThemePreset('노을', 'b9512bfaf5f1f0b27af3a266120d0bcf5f7a3737'),
  ThemePreset('바다', '1d68b0f0f5fb6cc3e07fc5f3080d145a6ed63232'),
  ThemePreset('숲', '3d6b2ef4f6f0b7cf8aa9d58a0b0e09c9b45e2d2d'),
  ThemePreset('모노', '27272af4f4f5a1a1aae4e4e70a0a0b71717a1e1e'),
];

const defaultListTheme =
    'ffffff5987b6fffffff2f2f2497ba79cb3c7ffffffffffff555555f8f8f82474cecb3434'
    '000000000000777777111111777777111111c3b443070707999999000000cccccc3a7da3';

class ScheduleSettings {
  const ScheduleSettings({
    this.completed = false,
    this.view = ScheduleView.timeline,
    this.timelineTheme = '905574f5efe6ae97bee3aa9a0b0b109673783232',
    this.listTheme = defaultListTheme,
    this.colorMode = ScheduleColorMode.system,
  });

  final bool completed;
  final ScheduleView view;
  final String timelineTheme;
  final String listTheme;
  final ScheduleColorMode colorMode;

  String get theme => view == ScheduleView.timeline ? timelineTheme : listTheme;
  String get path =>
      view == ScheduleView.timeline ? '/schedule/2026' : '/schedule/2015';

  static String? validateTheme(ScheduleView view, String code) {
    final value = code.replaceFirst(RegExp(r'^#'), '').toLowerCase();
    final length = view == ScheduleView.timeline ? 40 : 144;
    if (!RegExp('^[0-9a-f]{$length}\$').hasMatch(value)) return null;
    if (view == ScheduleView.timeline) {
      final light = int.parse(value.substring(36, 38), radix: 16);
      final dark = int.parse(value.substring(38, 40), radix: 16);
      if (light > 100 || dark > 100) return null;
    }
    return value;
  }

  factory ScheduleSettings.fromJson(Object? value) {
    if (value is! Map<String, dynamic> || value['schemaVersion'] != 1) {
      return const ScheduleSettings();
    }
    final defaults = const ScheduleSettings();
    final timeline = value['timelineTheme'];
    final list = value['listTheme'];
    return ScheduleSettings(
      completed: value['completed'] == true,
      view: value['view'] == 'list' ? ScheduleView.list : ScheduleView.timeline,
      timelineTheme: timeline is String
          ? validateTheme(ScheduleView.timeline, timeline) ??
                defaults.timelineTheme
          : defaults.timelineTheme,
      listTheme: list is String
          ? validateTheme(ScheduleView.list, list) ?? defaults.listTheme
          : defaults.listTheme,
      colorMode: switch (value['colorMode']) {
        'light' => ScheduleColorMode.light,
        'dark' => ScheduleColorMode.dark,
        _ => ScheduleColorMode.system,
      },
    );
  }

  Map<String, Object> toJson() => {
    'schemaVersion': 1,
    'completed': completed,
    'view': view.name,
    'timelineTheme': timelineTheme,
    'listTheme': listTheme,
    'colorMode': colorMode.name,
  };

  ScheduleSettings copyWith({
    bool? completed,
    ScheduleView? view,
    String? timelineTheme,
    String? listTheme,
    ScheduleColorMode? colorMode,
  }) => ScheduleSettings(
    completed: completed ?? this.completed,
    view: view ?? this.view,
    timelineTheme: timelineTheme ?? this.timelineTheme,
    listTheme: listTheme ?? this.listTheme,
    colorMode: colorMode ?? this.colorMode,
  );

  ScheduleSettings withTheme(String code) => view == ScheduleView.timeline
      ? copyWith(timelineTheme: validateTheme(view, code) ?? timelineTheme)
      : copyWith(listTheme: validateTheme(view, code) ?? listTheme);

  ScheduleSettings fromLink(Uri uri) {
    final path = uri.path.endsWith('/')
        ? uri.path.substring(0, uri.path.length - 1)
        : uri.path;
    final nextView = switch (path) {
      '/schedule/2026' => ScheduleView.timeline,
      '/schedule/2015' => ScheduleView.list,
      _ => view,
    };
    final next = copyWith(view: nextView);
    return uri.fragment.isEmpty ? next : next.withTheme(uri.fragment);
  }

  Uri uri(Uri origin) => origin.replace(path: path, fragment: theme);
}

class SettingsRepository {
  SettingsRepository(this.preferences);
  final SharedPreferences preferences;
  static const key = 'anissia.schedule.settings.v1';

  ScheduleSettings read() {
    try {
      final raw = preferences.getString(key);
      return raw == null
          ? const ScheduleSettings()
          : ScheduleSettings.fromJson(jsonDecode(raw));
    } on FormatException {
      return const ScheduleSettings();
    } on TypeError {
      return const ScheduleSettings();
    }
  }

  Future<void> save(ScheduleSettings value) async {
    if (!await preferences.setString(key, jsonEncode(value.toJson()))) {
      throw StateError('설정을 저장하지 못했습니다. 다시 시도해 주세요.');
    }
  }
}
