enum MobileAppKind { anissia, schedule }

enum Destination {
  internal,
  schedule,
  scheduleHome,
  browser,
  externalApp,
  blocked,
}

class NavigationPolicy {
  NavigationPolicy(this.app, {Uri? origin})
    : origin = origin ?? Uri.parse('https://anissia.net');

  final MobileAppKind app;
  final Uri origin;

  static const schedulePaths = {
    '/schedule',
    '/schedule/2026',
    '/schedule/2015',
  };

  static String normalizedPath(Uri uri) {
    final path = uri.path;
    return path.length > 1 && path.endsWith('/')
        ? path.substring(0, path.length - 1)
        : path;
  }

  bool isTrusted(Uri uri) =>
      uri.scheme == origin.scheme &&
      uri.host == origin.host &&
      uri.port == origin.port &&
      uri.userInfo.isEmpty;

  bool isSupportedSchedule(Uri uri) =>
      isTrusted(uri) &&
      !_requestsBrowser(uri) &&
      schedulePaths.contains(normalizedPath(uri));

  bool _requestsBrowser(Uri uri) =>
      uri.queryParametersAll['_anissia_browser']?.contains('1') ?? false;

  Destination classify(Uri uri) {
    if (['mailto', 'tel'].contains(uri.scheme) && uri.path.isNotEmpty) {
      return Destination.externalApp;
    }
    if ((uri.scheme != 'https' && uri.scheme != 'http') ||
        uri.host.isEmpty ||
        uri.userInfo.isNotEmpty) {
      return Destination.blocked;
    }
    if (!isTrusted(uri)) return Destination.browser;
    final path = normalizedPath(uri);
    if (_requestsBrowser(uri) &&
        (path == '/schedule' || path.startsWith('/schedule/'))) {
      return Destination.browser;
    }
    if (app == MobileAppKind.schedule) {
      if (path == '/schedule') return Destination.scheduleHome;
      return schedulePaths.contains(path)
          ? Destination.internal
          : Destination.browser;
    }
    if (schedulePaths.contains(path)) return Destination.schedule;
    if (path.startsWith('/schedule/')) return Destination.browser;
    return Destination.internal;
  }
}
