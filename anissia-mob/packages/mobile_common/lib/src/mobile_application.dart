import 'dart:async';
import 'dart:convert';

import 'package:file_selector/file_selector.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:webview_flutter/webview_flutter.dart';
import 'package:webview_flutter_android/webview_flutter_android.dart';
import 'package:webview_flutter_wkwebview/webview_flutter_wkwebview.dart';

import 'bridge_script.dart';
import 'native_platform.dart';
import 'navigation_policy.dart';
import 'schedule_settings.dart';
import 'settings_page.dart';

class MobileApplication extends StatelessWidget {
  const MobileApplication({super.key, required this.app});
  final MobileAppKind app;

  @override
  Widget build(BuildContext context) => MaterialApp(
    title: app == MobileAppKind.schedule ? '애니편성표' : '애니시아',
    debugShowCheckedModeBanner: false,
    theme: ThemeData(
      colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xff905574)),
      useMaterial3: true,
    ),
    darkTheme: ThemeData(
      colorScheme: ColorScheme.fromSeed(
        seedColor: const Color(0xffe3aa9a),
        brightness: Brightness.dark,
      ),
      useMaterial3: true,
    ),
    home: _MobileHome(app: app),
  );
}

class _MobileHome extends StatefulWidget {
  const _MobileHome({required this.app});
  final MobileAppKind app;
  @override
  State<_MobileHome> createState() => _MobileHomeState();
}

class _MobileHomeState extends State<_MobileHome> with WidgetsBindingObserver {
  final _platform = NativePlatform();
  late final Uri _origin = _configuredOrigin();
  late final NavigationPolicy _policy = NavigationPolicy(
    widget.app,
    origin: _origin,
  );
  SettingsRepository? _repository;
  ScheduleSettings? _settings;
  ScheduleSettings? _displaySettings;
  WebViewController? _controller;
  Uri? _pendingLink;
  Uri? _lastGoodUri;
  String? _error;
  bool _startupFailed = false;
  bool _loading = true;
  bool _settingsOpen = false;
  bool _handlingBack = false;
  bool _openingExternal = false;
  int _progress = 0;

  bool get _schedule => widget.app == MobileAppKind.schedule;
  ScheduleSettings get _currentSettings =>
      _displaySettings ?? _settings ?? const ScheduleSettings();

  static Uri _configuredOrigin() {
    const production = 'https://anissia.net';
    const configured = String.fromEnvironment(
      'ANISSIA_ORIGIN',
      defaultValue: production,
    );
    if (kReleaseMode) return Uri.parse(production);
    final uri = Uri.tryParse(configured);
    if (uri == null ||
        !['https', 'http'].contains(uri.scheme) ||
        uri.host.isEmpty ||
        uri.userInfo.isNotEmpty) {
      return Uri.parse(production);
    }
    return uri.replace(path: '', query: '', fragment: '');
  }

  String get _colorMode => switch (_currentSettings.colorMode) {
    ScheduleColorMode.light => 'light',
    ScheduleColorMode.dark => 'dark',
    ScheduleColorMode.system =>
      WidgetsBinding.instance.platformDispatcher.platformBrightness ==
              Brightness.dark
          ? 'dark'
          : 'light',
  };

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    unawaited(_start());
  }

  Future<void> _start() async {
    try {
      final initial = await _platform.listen(_receiveLink);
      if (initial != null) _pendingLink ??= Uri.tryParse(initial);
      final preferences = await SharedPreferences.getInstance();
      if (!mounted) return;
      _repository = SettingsRepository(preferences);
      _settings = _repository!.read();
      await _createController();
      if (!mounted) return;
      setState(() {
        _startupFailed = false;
        _error = null;
      });
      if (!_schedule || _settings!.completed) {
        final pending = _pendingLink;
        _pendingLink = null;
        await _load(
          _schedule ? _settings!.uri(_origin) : _origin.replace(path: '/'),
        );
        if (pending != null) {
          await _receiveLink(pending);
        }
      }
    } catch (_) {
      if (mounted) {
        setState(() {
          _startupFailed = true;
          _loading = false;
          _error = '앱을 준비하지 못했어요. 다시 시도해 주세요.';
        });
      }
    }
  }

  Future<void> _createController() async {
    final controller = WebViewController();
    _controller = controller;
    await controller.setJavaScriptMode(JavaScriptMode.unrestricted);
    await controller.addJavaScriptChannel(
      'AnissiaMobile',
      onMessageReceived: (message) => unawaited(_onMessage(message.message)),
    );
    await controller.setNavigationDelegate(
      NavigationDelegate(
        onNavigationRequest: (request) {
          if (!request.isMainFrame) return NavigationDecision.navigate;
          final uri = Uri.tryParse(request.url);
          if (uri == null) return NavigationDecision.prevent;
          if (_policy.classify(uri) == Destination.internal) {
            _lastGoodUri = uri;
            return NavigationDecision.navigate;
          }
          unawaited(_route(uri));
          return NavigationDecision.prevent;
        },
        onProgress: (progress) {
          if (mounted) setState(() => _progress = progress);
        },
        onPageStarted: (_) {
          if (mounted) {
            setState(() {
              _loading = true;
              _error = null;
              _progress = 0;
            });
          }
        },
        onPageFinished: (url) => unawaited(_pageFinished(url)),
        onUrlChange: (change) {
          final uri = change.url == null ? null : Uri.tryParse(change.url!);
          if (uri == null || uri.scheme == 'about') return;
          if (_policy.classify(uri) == Destination.internal) {
            _lastGoodUri = uri;
          } else {
            unawaited(_recoverUnexpectedNavigation(uri));
          }
        },
        onWebResourceError: (error) {
          if (mounted && error.isForMainFrame == true) {
            setState(() {
              _loading = false;
              _error = '화면을 불러오지 못했어요. 인터넷 연결을 확인해 주세요.';
            });
          }
        },
      ),
    );
    await controller.setOnJavaScriptAlertDialog((request) async {
      if (!mounted) return;
      await showDialog<void>(
        context: context,
        builder: (context) => AlertDialog(
          content: Text(request.message),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context),
              child: const Text('확인'),
            ),
          ],
        ),
      );
    });
    await controller.setOnJavaScriptConfirmDialog((request) async {
      if (!mounted) return false;
      return await showDialog<bool>(
            context: context,
            builder: (context) => AlertDialog(
              content: Text(request.message),
              actions: [
                TextButton(
                  onPressed: () => Navigator.pop(context, false),
                  child: const Text('취소'),
                ),
                FilledButton(
                  onPressed: () => Navigator.pop(context, true),
                  child: const Text('확인'),
                ),
              ],
            ),
          ) ??
          false;
    });
    await controller.setOnJavaScriptTextInputDialog((request) async {
      if (!mounted) return '';
      final text = TextEditingController(text: request.defaultText ?? '');
      final value = await showDialog<String>(
        context: context,
        builder: (context) => AlertDialog(
          title: Text(request.message),
          content: TextField(controller: text, autofocus: true),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context),
              child: const Text('취소'),
            ),
            FilledButton(
              onPressed: () => Navigator.pop(context, text.text),
              child: const Text('확인'),
            ),
          ],
        ),
      );
      text.dispose();
      return value ?? '';
    });
    if (controller.platform is AndroidWebViewController) {
      final android = controller.platform as AndroidWebViewController;
      await android.setOnShowFileSelector((params) async {
        final accepted = params.acceptTypes
            .where((value) => value.contains('/') && value != '*/*')
            .toList();
        final groups = accepted.isEmpty
            ? <XTypeGroup>[]
            : [XTypeGroup(label: '첨부 파일', mimeTypes: accepted)];
        try {
          final files = params.mode == FileSelectorMode.openMultiple
              ? await openFiles(acceptedTypeGroups: groups)
              : [?await openFile(acceptedTypeGroups: groups)];
          return files.map((file) => Uri.file(file.path).toString()).toList();
        } catch (_) {
          _notice('파일 선택기를 열지 못했어요. 다시 시도해 주세요.');
          return <String>[];
        }
      });
      await android.setMediaPlaybackRequiresUserGesture(true);
    }
    if (controller.platform is WebKitWebViewController) {
      await (controller.platform as WebKitWebViewController)
          .setAllowsBackForwardNavigationGestures(true);
    }
  }

  Future<void> _pageFinished(String url) async {
    final uri = Uri.tryParse(url);
    if (uri == null || _policy.classify(uri) != Destination.internal) return;
    try {
      await _controller!.runJavaScript(
        mobileBridgeScript(
          app: widget.app,
          origin: _origin,
          colorMode: _colorMode,
        ),
      );
    } catch (_) {
      if (mounted) {
        setState(() {
          _loading = false;
          _error = '화면 연결을 마치지 못했어요. 다시 시도해 주세요.';
        });
      }
    }
  }

  Future<void> _recoverUnexpectedNavigation(Uri uri) async {
    final previous = _lastGoodUri;
    await _route(uri);
    if (previous != null && _controller != null) {
      await _controller!.loadRequest(previous);
    }
  }

  Future<void> _load(Uri uri) async {
    if (_controller == null || _policy.classify(uri) != Destination.internal) {
      return;
    }
    _lastGoodUri = uri;
    if (mounted) {
      setState(() {
        _loading = true;
        _error = null;
      });
    }
    await _controller!.loadRequest(uri);
  }

  Future<void> _receiveLink(Uri uri) async {
    final path = NavigationPolicy.normalizedPath(uri);
    if (!_policy.isTrusted(uri) ||
        (path != '/schedule' && !path.startsWith('/schedule/'))) {
      return;
    }
    if (_settings == null ||
        _controller == null ||
        (_schedule && !_settings!.completed)) {
      _pendingLink = uri;
      return;
    }
    if (_schedule && _policy.isSupportedSchedule(uri)) {
      _displaySettings = _settings!.fromLink(uri);
      await _load(
        _displaySettings!
            .uri(_origin)
            .replace(query: uri.hasQuery ? uri.query : null),
      );
    } else {
      await _route(uri);
    }
  }

  Future<void> _route(Uri uri) async {
    switch (_policy.classify(uri)) {
      case Destination.internal:
        if (_schedule) {
          _displaySettings = _currentSettings.fromLink(uri);
          await _load(
            _displaySettings!
                .uri(_origin)
                .replace(query: uri.hasQuery ? uri.query : null),
          );
        } else {
          await _load(uri);
        }
      case Destination.scheduleHome:
        await _showSettings();
      case Destination.schedule:
        if (_openingExternal) return;
        _openingExternal = true;
        try {
          final launched = await _platform.openSchedule(uri);
          if (!launched && !await _platform.openBrowser(uri)) {
            _notice('편성표를 열 수 있는 브라우저가 없어요.');
          }
        } on PlatformException {
          try {
            if (!await _platform.openBrowser(uri)) _notice('편성표를 열지 못했어요.');
          } catch (_) {
            _notice('편성표를 열지 못했어요.');
          }
        } finally {
          _openingExternal = false;
        }
      case Destination.browser:
        if (_openingExternal) return;
        _openingExternal = true;
        try {
          if (!await _platform.openBrowser(uri)) _notice('브라우저를 열지 못했어요.');
        } catch (_) {
          _notice('브라우저를 열지 못했어요.');
        } finally {
          _openingExternal = false;
        }
      case Destination.externalApp:
        if (_openingExternal) return;
        _openingExternal = true;
        try {
          if (!await _platform.openExternal(uri)) {
            _notice('이 링크를 열 수 있는 앱이 없어요.');
          }
        } catch (_) {
          _notice('앱을 열지 못했어요.');
        } finally {
          _openingExternal = false;
        }
      case Destination.blocked:
        _notice('지원하지 않는 링크예요.');
    }
  }

  Future<void> _onMessage(String message) async {
    if (_controller == null || message.length > 16384) return;
    final currentUrl = await _controller!.currentUrl();
    final current = currentUrl == null ? null : Uri.tryParse(currentUrl);
    if (current == null || !_policy.isTrusted(current)) return;
    try {
      final value = jsonDecode(message);
      if (value is! Map<String, dynamic> || value['version'] != 1) return;
      if (value['type'] == 'ready') {
        if (mounted) setState(() => _loading = false);
      } else if (value['type'] == 'bridgeError') {
        if (mounted) {
          setState(() {
            _loading = false;
            _error = '화면 연결을 마치지 못했어요. 다시 시도해 주세요.';
          });
        }
      } else if (value['type'] == 'navigate' && value['url'] is String) {
        final uri = Uri.tryParse(value['url'] as String);
        if (uri != null) await _route(uri);
      } else if (_schedule &&
          value['type'] == 'colorMode' &&
          ['light', 'dark'].contains(value['mode'])) {
        final mode = value['mode'] == 'dark'
            ? ScheduleColorMode.dark
            : ScheduleColorMode.light;
        final settings = _settings!.copyWith(colorMode: mode);
        await _repository!.save(settings);
        if (mounted) {
          setState(() {
            _settings = settings;
            _displaySettings = _currentSettings.copyWith(colorMode: mode);
          });
        }
      }
    } on FormatException {
      return;
    } on StateError {
      _notice('밝기 설정을 저장하지 못했어요.');
    }
  }

  Future<void> _applySettings(ScheduleSettings settings) async {
    await _repository!.save(settings);
    if (!mounted) return;
    setState(() {
      _settings = settings;
      _displaySettings = settings;
    });
    final pending = _pendingLink;
    _pendingLink = null;
    await _load(settings.uri(_origin));
    if (pending != null) {
      await _receiveLink(pending);
    }
  }

  Future<void> _showSettings() async {
    if (!_schedule ||
        !mounted ||
        _settings == null ||
        !_settings!.completed ||
        _settingsOpen) {
      return;
    }
    _settingsOpen = true;
    await Navigator.of(context).push<void>(
      MaterialPageRoute(
        fullscreenDialog: true,
        builder: (pageContext) => ScheduleSettingsPage(
          initial: _currentSettings,
          origin: _origin,
          onApply: (settings) async {
            await _applySettings(settings);
            if (pageContext.mounted) Navigator.of(pageContext).pop();
          },
        ),
      ),
    );
    _settingsOpen = false;
    await _applyMode();
  }

  Future<void> _applyMode() async {
    if (!_schedule || _controller == null) return;
    try {
      await _controller!.runJavaScript(
        'window.__anissiaMobileApplyColorMode?.(${jsonEncode(_colorMode)});',
      );
    } catch (_) {
      // The document may have been discarded while the app was in the background.
    }
  }

  Future<void> _back() async {
    if (_handlingBack || _controller == null) return;
    _handlingBack = true;
    try {
      final closed = await _controller!.runJavaScriptReturningResult(
        'Boolean(window.__anissiaMobileBack?.())',
      );
      if (closed == true || closed == 'true') return;
      if (await _controller!.canGoBack()) {
        await _controller!.goBack();
      } else if (defaultTargetPlatform == TargetPlatform.android) {
        await _platform.exitApp();
      }
    } catch (_) {
      if (defaultTargetPlatform == TargetPlatform.android) {
        await _platform.exitApp();
      }
    } finally {
      _handlingBack = false;
    }
  }

  void _notice(String message) {
    if (!mounted) return;
    ScaffoldMessenger.of(context)
        .showSnackBar(SnackBar(content: Text(message)));
  }

  @override
  void didChangePlatformBrightness() => unawaited(_applyMode());

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) unawaited(_applyMode());
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _platform.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (_startupFailed) {
      return Scaffold(
        body: SafeArea(
          child: _ErrorView(
            message: _error!,
            onRetry: () {
              setState(() {
                _startupFailed = false;
                _loading = true;
              });
              unawaited(_start());
            },
          ),
        ),
      );
    }
    if (_settings == null || _controller == null) {
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    }
    if (_schedule && !_settings!.completed) {
      return ScheduleSettingsPage(
        initial: _settings!,
        origin: _origin,
        firstRun: true,
        onApply: _applySettings,
      );
    }
    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop) unawaited(_back());
      },
      child: Scaffold(
        body: SafeArea(
          child: Stack(
            children: [
              AbsorbPointer(
                absorbing: _loading || _error != null,
                child: WebViewWidget(controller: _controller!),
              ),
              if (_loading)
                Align(
                  alignment: Alignment.topCenter,
                  child: LinearProgressIndicator(
                    value: _progress > 0 && _progress < 100
                        ? _progress / 100
                        : null,
                  ),
                ),
              if (_error != null)
                Positioned.fill(
                  child: ColoredBox(
                    color: Theme.of(context).colorScheme.surface,
                    child: _ErrorView(
                      message: _error!,
                      onRetry: () {
                        final uri = _lastGoodUri;
                        if (uri != null) unawaited(_load(uri));
                      },
                    ),
                  ),
                ),
            ],
          ),
        ),
        floatingActionButton: _schedule
            ? FloatingActionButton.small(
                heroTag: 'schedule-settings',
                onPressed: _showSettings,
                tooltip: '편성표 설정',
                child: const Icon(Icons.tune),
              )
            : null,
      ),
    );
  }
}

class _ErrorView extends StatelessWidget {
  const _ErrorView({required this.message, required this.onRetry});
  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) => Center(
    child: Padding(
      padding: const EdgeInsets.all(32),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(
            Icons.cloud_off_outlined,
            size: 44,
            color: Theme.of(context).colorScheme.onSurfaceVariant,
          ),
          const SizedBox(height: 20),
          Text(message, textAlign: TextAlign.center),
          const SizedBox(height: 20),
          FilledButton.icon(
            onPressed: onRetry,
            icon: const Icon(Icons.refresh),
            label: const Text('다시 시도'),
          ),
        ],
      ),
    ),
  );
}
