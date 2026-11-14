import 'package:flutter/services.dart';

class NativePlatform {
  static const channel = MethodChannel('net.anissia.mobile/platform');

  Future<String?> listen(Future<void> Function(Uri uri) onLink) async {
    channel.setMethodCallHandler((call) async {
      if (call.method != 'link' || call.arguments is! Map) return;
      final value = (call.arguments as Map)['url'];
      if (value is String) {
        final uri = Uri.tryParse(value);
        if (uri != null) await onLink(uri);
      }
    });
    return channel.invokeMethod<String>('initialLink');
  }

  Future<bool> openBrowser(Uri uri) async =>
      await channel.invokeMethod<bool>('openBrowser', {
        'url': uri.toString(),
      }) ??
      false;

  Future<bool> openSchedule(Uri uri) async =>
      await channel.invokeMethod<bool>('openSchedule', {
        'url': uri.toString(),
      }) ??
      false;

  Future<bool> openExternal(Uri uri) async =>
      await channel.invokeMethod<bool>('openExternal', {
        'url': uri.toString(),
      }) ??
      false;

  Future<void> exitApp() => channel.invokeMethod<void>('exitApp');

  void dispose() => channel.setMethodCallHandler(null);
}
