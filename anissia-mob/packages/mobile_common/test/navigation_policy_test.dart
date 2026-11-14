import 'package:flutter_test/flutter_test.dart';
import 'package:mobile_common/src/navigation_policy.dart';

void main() {
  final schedule = NavigationPolicy(MobileAppKind.schedule);
  final anissia = NavigationPolicy(MobileAppKind.anissia);

  test('schedule allows only its two display versions', () {
    for (final path in [
      '/schedule/2026',
      '/schedule/2015',
      '/schedule/2026/',
    ]) {
      expect(
        schedule.classify(Uri.parse('https://anissia.net$path?view=x#abcdef')),
        Destination.internal,
      );
    }
    expect(
      schedule.classify(Uri.parse('https://anissia.net/schedule/')),
      Destination.scheduleHome,
    );
    for (final path in [
      '/',
      '/anime/12',
      '/admin/schedule',
      '/schedule/2009',
      '/schedule/2024',
      '/schedule/2026/extra',
      '/schedule-extra',
      '/scheduler',
      '/schedule%2F2026',
      '/schedule/2026//',
    ]) {
      expect(
        schedule.classify(Uri.parse('https://anissia.net$path')),
        Destination.browser,
        reason: path,
      );
    }
  });

  test('anissia hands supported schedules to the other app', () {
    for (final path in [
      '/schedule',
      '/schedule/',
      '/schedule/2026',
      '/schedule/2015/',
    ]) {
      expect(
        anissia.classify(Uri.parse('https://anissia.net$path')),
        Destination.schedule,
      );
    }
    expect(
      anissia.classify(Uri.parse('https://anissia.net/schedule/2009')),
      Destination.browser,
    );
    for (final path in [
      '/',
      '/anime/1',
      '/wiki/images',
      '/admin/schedule',
      '/scheduler',
    ]) {
      expect(
        anissia.classify(Uri.parse('https://anissia.net$path')),
        Destination.internal,
      );
    }
  });

  test('origin comparisons reject lookalike hosts and other ports', () {
    for (final value in [
      'https://anissia.net.evil.test/schedule/2026',
      'https://www.anissia.net/schedule/2026',
      'https://anissia.net:444/schedule/2026',
      'http://anissia.net/schedule/2026',
      'https://external.example/schedule/2026',
    ]) {
      expect(
        schedule.classify(Uri.parse(value)),
        Destination.browser,
        reason: value,
      );
      expect(anissia.isSupportedSchedule(Uri.parse(value)), isFalse);
    }
    expect(
      schedule.classify(Uri.parse('https://ANISSIA.NET:443/schedule/2026')),
      Destination.internal,
    );
  });

  test('non-web and credential-bearing links are not launched', () {
    for (final value in [
      'javascript:alert(1)',
      'file:///secret',
      'data:text/html,test',
      'intent://launch',
      'https://user:password@anissia.net/schedule',
      '/schedule',
    ]) {
      expect(
        schedule.classify(Uri.parse(value)),
        Destination.blocked,
        reason: value,
      );
    }
  });

  test('development origin is exact including its port', () {
    final local = NavigationPolicy(
      MobileAppKind.schedule,
      origin: Uri.parse('http://localhost:5173'),
    );
    expect(
      local.classify(Uri.parse('http://localhost:5173/schedule/2015')),
      Destination.internal,
    );
    expect(
      local.classify(Uri.parse('http://localhost:5174/schedule/2015')),
      Destination.browser,
    );
    expect(
      local.classify(Uri.parse('https://anissia.net/schedule/2015')),
      Destination.browser,
    );
  });

  test('browser fallback marker cannot reopen a schedule app', () {
    for (final query in [
      '_anissia_browser=1',
      '_anissia_browser=0&_anissia_browser=1',
      '_anissia_browser=1&_anissia_browser=0',
    ]) {
      final uri = Uri.parse('https://anissia.net/schedule/2026?$query#123456');
      expect(schedule.classify(uri), Destination.browser);
      expect(anissia.classify(uri), Destination.browser);
      expect(schedule.isSupportedSchedule(uri), isFalse);
    }
    expect(
      schedule.classify(
        Uri.parse('https://anissia.net/schedule/2026?_anissia_browser=0'),
      ),
      Destination.internal,
    );
  });

  test('mail and telephone links use a narrow external application path', () {
    expect(
      anissia.classify(Uri.parse('mailto:contact@anissia.net')),
      Destination.externalApp,
    );
    expect(
      schedule.classify(Uri.parse('tel:+821012345678')),
      Destination.externalApp,
    );
    expect(anissia.classify(Uri.parse('mailto:')), Destination.blocked);
  });
}
