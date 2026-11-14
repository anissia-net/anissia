import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:mobile_common/src/bridge_script.dart';
import 'package:mobile_common/src/navigation_policy.dart';

void main() {
  test(
    'injected bridge handles old production pages and yields to the web bridge',
    () async {
      final script = mobileBridgeScript(
        app: MobileAppKind.schedule,
        origin: Uri.parse('https://anissia.net'),
        colorMode: 'dark',
      );
      final result = await Process.run('node', [
        '-e',
        '''
const assert = require('node:assert/strict');
const messages = [], listeners = {}, opened = [], saved = {}, guards = [];
const elements = new Map();
globalThis.window = globalThis;
window.top = window;
window.location = {origin:'https://anissia.net', href:'https://anissia.net/schedule/2026', assign: url => opened.push(url)};
window.document = {
  addEventListener: (name, fn) => listeners[name] = fn, querySelector: () => null,
  createElement: tagName => ({tagName}),
  head: {appendChild: element => elements.set(element.id, element)},
  getElementById: id => id !== 'app' ? elements.get(id) : ({__vue_app__: {config: {globalProperties: {\$router: {
    beforeEach: guard => { guards.push(guard); return () => {}; }
  }}}}})
};
window.history = {pushState: () => {}, replaceState: () => {}};
window.AnissiaMobile = {postMessage: value => messages.push(JSON.parse(value))};
window.Storage = class { setItem(key, value) { saved[key] = value; } };
window.localStorage = new Storage();
window.colorMode = mode => localStorage.setItem('schedule2026ColorMode', mode);
window.dispatchEvent = () => {};
window.open = () => 'original';
window.Element = class {};
eval(${jsonEncode(script)});
assert.equal(saved.schedule2026ColorMode, 'dark');
assert.deepEqual(messages.pop(), {version:1, type:'ready'});
assert.equal(messages.length, 0, 'host-applied mode must not echo back');
assert.equal(guards.length, 1);
assert.equal(guards[0]({fullPath:'/schedule/2015'}), true);
assert.equal(guards[0]({fullPath:'/anime/42'}), false, 'router navigation must be canceled before currentRoute changes');
assert.equal(messages.pop().url, 'https://anissia.net/anime/42');
localStorage.setItem('schedule2026ColorMode', 'light');
assert.deepEqual(messages.pop(), {version:1, type:'colorMode', mode:'light'});
window.open('https://anissia.net/anime/1');
assert.equal(messages.pop().url, 'https://anissia.net/anime/1');
window.open('/schedule/2015/');
assert.deepEqual(opened, ['https://anissia.net/schedule/2015/']);
for (const query of ['_anissia_browser=0&_anissia_browser=1', '_anissia_browser=1&_anissia_browser=0']) {
  window.open('/schedule/2015?' + query);
  assert.equal(messages.pop().url, 'https://anissia.net/schedule/2015?' + query);
}
assert.equal(opened.length, 1, 'browser-only links must not load inside the WebView');
history.pushState({}, '', '/schedule');
assert.equal(messages.pop().url, 'https://anissia.net/schedule');
window.__anissiaMobileWebBridgeVersion = 1;
assert.equal(window.open('https://external.example'), 'original');
assert.equal(guards[0]({fullPath:'/anime/42'}), true);
assert.equal(messages.length, 0);
''',
      ]);
      expect(result.exitCode, 0, reason: result.stderr.toString());
    },
  );
}
