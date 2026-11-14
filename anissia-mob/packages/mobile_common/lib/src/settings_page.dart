import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:webview_flutter/webview_flutter.dart';

import 'navigation_policy.dart';
import 'schedule_settings.dart';

class ScheduleSettingsPage extends StatefulWidget {
  const ScheduleSettingsPage({
    super.key,
    required this.initial,
    required this.origin,
    required this.onApply,
    this.firstRun = false,
  });

  final ScheduleSettings initial;
  final Uri origin;
  final bool firstRun;
  final Future<void> Function(ScheduleSettings settings) onApply;

  @override
  State<ScheduleSettingsPage> createState() => _ScheduleSettingsPageState();
}

class _ScheduleSettingsPageState extends State<ScheduleSettingsPage> {
  late ScheduleSettings _draft = widget.initial;
  bool _saving = false;
  bool _editDark = false;
  late int _step = widget.firstRun ? 0 : 1;

  Future<void> _apply() async {
    setState(() => _saving = true);
    try {
      await widget.onApply(_draft.copyWith(completed: true));
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('설정을 저장하지 못했어요. 다시 시도해 주세요.')),
        );
      }
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  void _setTheme(String value) =>
      setState(() => _draft = _draft.withTheme(value));

  Future<void> _editColor(int index) async {
    final old = _draft.theme.substring(index * 6, index * 6 + 6);
    final value = await showDialog<String>(
      context: context,
      builder: (context) => _ColorDialog(value: old),
    );
    if (value == null || !mounted) return;
    final code = _draft.theme;
    _setTheme(code.replaceRange(index * 6, index * 6 + 6, value));
  }

  void _preset(ThemePreset preset) {
    if (_draft.view == ScheduleView.timeline) {
      _setTheme(preset.timeline);
      return;
    }
    final seed = preset.timeline;
    final light = seed.substring(0, 6);
    final lightBase = seed.substring(6, 12);
    final dark = seed.substring(18, 24);
    final darkBase = seed.substring(24, 30);
    _setTheme(
      [
        lightBase,
        light,
        'ffffff',
        lightBase,
        light,
        light,
        'ffffff',
        lightBase,
        '303039',
        'e9e9ee',
        light,
        light,
        darkBase,
        darkBase,
        dark,
        darkBase,
        dark,
        dark,
        darkBase,
        darkBase,
        'e4e4e7',
        '27272a',
        dark,
        dark,
      ].join(),
    );
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return PopScope(
      canPop: !widget.firstRun,
      onPopInvokedWithResult: (didPop, _) {
        if (didPop || !widget.firstRun) return;
        if (_step > 0) {
          setState(() => _step = 0);
        } else if (Theme.of(context).platform == TargetPlatform.android) {
          SystemNavigator.pop();
        }
      },
      child: Scaffold(
        appBar: AppBar(
          automaticallyImplyLeading: !widget.firstRun,
          title: Text(widget.firstRun ? '나의 애니편성표' : '편성표 설정'),
          actions: [
            if (!widget.firstRun)
              TextButton(
                onPressed: _saving
                    ? null
                    : () => setState(
                        () => _draft = const ScheduleSettings(completed: true),
                      ),
                child: const Text('초기화'),
              ),
          ],
        ),
        body: SafeArea(
          child: Align(
            alignment: Alignment.topCenter,
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 640),
              child: ListView(
                padding: const EdgeInsets.fromLTRB(24, 16, 24, 32),
                children: [
                  if (widget.firstRun) ...[
                    Text(
                      _step == 0 ? '어떤 편성표가 편한가요?' : '좋아하는 색으로 꾸며보세요.',
                      style: theme.textTheme.headlineSmall?.copyWith(
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                    const SizedBox(height: 10),
                    Text(
                      _step == 0
                          ? '표시 방식을 고른 뒤 색상을 정해 주세요.'
                          : '나중에도 화면의 설정 버튼으로 바꿀 수 있어요.',
                      style: theme.textTheme.bodyMedium?.copyWith(
                        color: theme.colorScheme.onSurfaceVariant,
                      ),
                    ),
                    const SizedBox(height: 24),
                  ],
                  _TypeCard(
                    title: '타임라인',
                    subtitle: '시간의 흐름을 한눈에',
                    icon: Icons.view_timeline_outlined,
                    selected: _draft.view == ScheduleView.timeline,
                    onTap: () => setState(
                      () =>
                          _draft = _draft.copyWith(view: ScheduleView.timeline),
                    ),
                  ),
                  const SizedBox(height: 10),
                  _TypeCard(
                    title: '리스트',
                    subtitle: '간결하게 모아보는 오늘의 작품',
                    icon: Icons.view_list_outlined,
                    selected: _draft.view == ScheduleView.list,
                    onTap: () => setState(
                      () => _draft = _draft.copyWith(view: ScheduleView.list),
                    ),
                  ),
                  if (_step == 1) ...[
                    const SizedBox(height: 30),
                    Text(
                      '색상',
                      style: theme.textTheme.titleMedium?.copyWith(
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                    const SizedBox(height: 12),
                    Wrap(
                      spacing: 8,
                      runSpacing: 8,
                      children: [
                        for (final preset in timelinePresets)
                          ActionChip(
                            avatar: CircleAvatar(
                              backgroundColor: _color(
                                preset.timeline.substring(0, 6),
                              ),
                              radius: 9,
                            ),
                            label: Text(preset.name),
                            onPressed: () => _preset(preset),
                          ),
                        if (_draft.view == ScheduleView.list)
                          ActionChip(
                            label: const Text('기존 기본색'),
                            onPressed: () => _setTheme(defaultListTheme),
                          ),
                      ],
                    ),
                    const SizedBox(height: 18),
                    ExpansionTile(
                      tilePadding: EdgeInsets.zero,
                      title: const Text('직접 색상 조정'),
                      childrenPadding: const EdgeInsets.only(bottom: 16),
                      children: [
                        SegmentedButton<bool>(
                          segments: const [
                            ButtonSegment(
                              value: false,
                              label: Text('밝은 색'),
                              icon: Icon(Icons.light_mode_outlined),
                            ),
                            ButtonSegment(
                              value: true,
                              label: Text('어두운 색'),
                              icon: Icon(Icons.dark_mode_outlined),
                            ),
                          ],
                          selected: {_editDark},
                          onSelectionChanged: (value) =>
                              setState(() => _editDark = value.first),
                        ),
                        const SizedBox(height: 16),
                        ..._colorRows(),
                      ],
                    ),
                    const SizedBox(height: 18),
                    Text(
                      '화면 밝기',
                      style: theme.textTheme.titleMedium?.copyWith(
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                    const SizedBox(height: 12),
                    SegmentedButton<ScheduleColorMode>(
                      segments: const [
                        ButtonSegment(
                          value: ScheduleColorMode.system,
                          label: Text('시스템'),
                        ),
                        ButtonSegment(
                          value: ScheduleColorMode.light,
                          label: Text('라이트'),
                        ),
                        ButtonSegment(
                          value: ScheduleColorMode.dark,
                          label: Text('다크'),
                        ),
                      ],
                      selected: {_draft.colorMode},
                      onSelectionChanged: (value) => setState(
                        () => _draft = _draft.copyWith(colorMode: value.first),
                      ),
                    ),
                    const SizedBox(height: 24),
                    OutlinedButton.icon(
                      onPressed: _saving
                          ? null
                          : () => Navigator.of(context).push<void>(
                              MaterialPageRoute(
                                builder: (_) => SchedulePreview(
                                  settings: _draft,
                                  origin: widget.origin,
                                ),
                              ),
                            ),
                      icon: const Icon(Icons.visibility_outlined),
                      label: const Text('편성표 미리보기'),
                    ),
                  ],
                  const SizedBox(height: 24),
                  FilledButton(
                    onPressed: _saving
                        ? null
                        : _step == 0
                        ? () => setState(() => _step = 1)
                        : _apply,
                    style: FilledButton.styleFrom(
                      minimumSize: const Size.fromHeight(52),
                    ),
                    child: _saving
                        ? const SizedBox(
                            width: 22,
                            height: 22,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          )
                        : Text(_step == 0 ? '색상 선택하기' : '적용하기'),
                  ),
                  if (widget.firstRun && _step == 1)
                    TextButton(
                      onPressed: _saving
                          ? null
                          : () => setState(() => _step = 0),
                      child: const Text('이전으로'),
                    ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }

  List<Widget> _colorRows() {
    final timeline = _draft.view == ScheduleView.timeline;
    final labels = timeline
        ? ['포인트', '바탕', '분위기']
        : [
            '배경',
            '제목 배경',
            '제목 글자',
            '요일 배경',
            '요일 글자',
            '선택 요일 배경',
            '선택 요일 글자',
            '리스트 배경',
            '리스트 글자',
            '선택 리스트 배경',
            '선택 리스트 글자',
            '접두어 글자',
          ];
    final offset = _editDark ? labels.length : 0;
    return [
      for (var i = 0; i < labels.length; i++)
        ListTile(
          contentPadding: EdgeInsets.zero,
          title: Text(labels[i]),
          trailing: Container(
            width: 54,
            height: 34,
            decoration: BoxDecoration(
              color: _color(
                _draft.theme.substring((offset + i) * 6, (offset + i + 1) * 6),
              ),
              borderRadius: BorderRadius.circular(10),
              border: Border.all(
                color: Theme.of(context).colorScheme.outlineVariant,
              ),
            ),
          ),
          onTap: () => _editColor(offset + i),
        ),
      if (timeline) ...[
        const SizedBox(height: 8),
        const Align(alignment: Alignment.centerLeft, child: Text('배경 번짐')),
        Slider(
          value: int.parse(
            _draft.timelineTheme.substring(
              _editDark ? 38 : 36,
              _editDark ? 40 : 38,
            ),
            radix: 16,
          ).toDouble(),
          min: 0,
          max: 100,
          divisions: 20,
          onChanged: (value) {
            final start = _editDark ? 38 : 36;
            _setTheme(
              _draft.timelineTheme.replaceRange(
                start,
                start + 2,
                value.round().toRadixString(16).padLeft(2, '0'),
              ),
            );
          },
        ),
      ],
    ];
  }
}

Color _color(String hex) => Color(int.parse('ff$hex', radix: 16));

class _TypeCard extends StatelessWidget {
  const _TypeCard({
    required this.title,
    required this.subtitle,
    required this.icon,
    required this.selected,
    required this.onTap,
  });
  final String title;
  final String subtitle;
  final IconData icon;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    return Material(
      color: selected ? colors.primaryContainer : colors.surfaceContainerLow,
      borderRadius: BorderRadius.circular(20),
      child: InkWell(
        borderRadius: BorderRadius.circular(20),
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 20),
          child: Row(
            children: [
              Icon(
                icon,
                size: 30,
                color: selected
                    ? colors.onPrimaryContainer
                    : colors.onSurfaceVariant,
              ),
              const SizedBox(width: 16),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      title,
                      style: Theme.of(context).textTheme.titleMedium
                          ?.copyWith(fontWeight: FontWeight.w700),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      subtitle,
                      style: Theme.of(context).textTheme.bodySmall,
                    ),
                  ],
                ),
              ),
              Icon(
                selected ? Icons.check_circle : Icons.circle_outlined,
                color: selected ? colors.primary : colors.outline,
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _ColorDialog extends StatefulWidget {
  const _ColorDialog({required this.value});
  final String value;
  @override
  State<_ColorDialog> createState() => _ColorDialogState();
}

class _ColorDialogState extends State<_ColorDialog> {
  late HSVColor _hsv = HSVColor.fromColor(_color(widget.value));
  late final TextEditingController _text = TextEditingController(
    text: widget.value.toUpperCase(),
  );
  bool _valid = true;

  String get _hex =>
      (_hsv.toColor().toARGB32() & 0xffffff).toRadixString(16).padLeft(6, '0');

  void _update(HSVColor value) => setState(() {
    _hsv = value;
    _text.text = _hex.toUpperCase();
    _valid = true;
  });

  @override
  void dispose() {
    _text.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => AlertDialog(
    title: const Text('색상 선택'),
    content: SingleChildScrollView(
      child: SizedBox(
        width: 300,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              height: 64,
              decoration: BoxDecoration(
                color: _hsv.toColor(),
                borderRadius: BorderRadius.circular(14),
                border: Border.all(
                  color: Theme.of(context).colorScheme.outlineVariant,
                ),
              ),
            ),
            const SizedBox(height: 20),
            const Align(alignment: Alignment.centerLeft, child: Text('색상')),
            Slider(
              value: _hsv.hue,
              max: 360,
              onChanged: (value) => _update(_hsv.withHue(value)),
            ),
            const Align(alignment: Alignment.centerLeft, child: Text('채도')),
            Slider(
              value: _hsv.saturation,
              onChanged: (value) => _update(_hsv.withSaturation(value)),
            ),
            const Align(alignment: Alignment.centerLeft, child: Text('명도')),
            Slider(
              value: _hsv.value,
              onChanged: (value) => _update(_hsv.withValue(value)),
            ),
            TextField(
              controller: _text,
              maxLength: 6,
              textCapitalization: TextCapitalization.characters,
              inputFormatters: [
                FilteringTextInputFormatter.allow(RegExp('[0-9a-fA-F]')),
              ],
              decoration: InputDecoration(
                prefixText: '#',
                labelText: '색상 코드',
                errorText: _valid ? null : '6자리 색상을 입력해 주세요.',
              ),
              onChanged: (value) => setState(() {
                _valid = RegExp(r'^[0-9a-fA-F]{6}$').hasMatch(value);
                if (_valid) _hsv = HSVColor.fromColor(_color(value));
              }),
            ),
          ],
        ),
      ),
    ),
    actions: [
      TextButton(
        onPressed: () => Navigator.pop(context),
        child: const Text('취소'),
      ),
      FilledButton(
        onPressed: _valid ? () => Navigator.pop(context, _hex) : null,
        child: const Text('선택'),
      ),
    ],
  );
}

class SchedulePreview extends StatefulWidget {
  const SchedulePreview({
    super.key,
    required this.settings,
    required this.origin,
  });
  final ScheduleSettings settings;
  final Uri origin;
  @override
  State<SchedulePreview> createState() => _SchedulePreviewState();
}

class _SchedulePreviewState extends State<SchedulePreview> {
  late final WebViewController _controller;
  bool _loading = true;
  bool _failed = false;

  @override
  void initState() {
    super.initState();
    final policy = NavigationPolicy(
      MobileAppKind.schedule,
      origin: widget.origin,
    );
    _controller = WebViewController()
      ..setJavaScriptMode(JavaScriptMode.unrestricted)
      ..setNavigationDelegate(
        NavigationDelegate(
          onNavigationRequest: (request) =>
              !request.isMainFrame ||
                  policy.classify(Uri.parse(request.url)) ==
                      Destination.internal
              ? NavigationDecision.navigate
              : NavigationDecision.prevent,
          onPageStarted: (_) {
            if (mounted) {
              setState(() {
                _loading = true;
                _failed = false;
              });
            }
          },
          onPageFinished: (_) async {
            if (!mounted) return;
            final dark =
                widget.settings.colorMode == ScheduleColorMode.dark ||
                (widget.settings.colorMode == ScheduleColorMode.system &&
                    MediaQuery.platformBrightnessOf(context) ==
                        Brightness.dark);
            await _controller.runJavaScript(
              'window.colorMode?.(${jsonEncode(dark ? 'dark' : 'light')});',
            );
            if (mounted) setState(() => _loading = false);
          },
          onWebResourceError: (error) {
            if (mounted && error.isForMainFrame == true) {
              setState(() {
                _failed = true;
                _loading = false;
              });
            }
          },
        ),
      )
      ..loadRequest(widget.settings.uri(widget.origin));
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('편성표 미리보기')),
    body: SafeArea(
      child: Stack(
        children: [
          WebViewWidget(controller: _controller),
          if (_loading)
            const Align(
              alignment: Alignment.topCenter,
              child: LinearProgressIndicator(),
            ),
          if (_failed)
            ColoredBox(
              color: Theme.of(context).colorScheme.surface,
              child: Center(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    const Text('미리보기를 불러오지 못했어요.'),
                    const SizedBox(height: 12),
                    FilledButton(
                      onPressed: () => _controller.reload(),
                      child: const Text('다시 시도'),
                    ),
                  ],
                ),
              ),
            ),
        ],
      ),
    ),
  );
}
