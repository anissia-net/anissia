import assert from 'node:assert/strict';
import {test} from 'node:test';
import {classifyMobileNavigation, delegateMobileNavigation, installMobileBridge, mobilePageReady, notifyMobileColorMode, registerMobileBackHandler} from '../src/common/mobileBridge.ts';

const origin = 'https://anissia.net';
const config = app => ({version: 1, app, origin, colorMode: 'light'});

test('schedule keeps only the two supported views inside, preserving query and theme', () => {
  for (const path of ['/schedule/2026', '/schedule/2015/', '/schedule/2026/?q=1#abcdef']) {
    assert.deepEqual(classifyMobileNavigation(path, config('schedule')), {action: 'internal', url: origin + path});
  }
  for (const path of ['/', '/schedule', '/schedule/', '/schedule/2009', '/schedule/2024', '/schedule/2026/extra', '/schedule/2026//', '/schedule/2015///', '/anime/1', '/admin/schedule', '/schedule%2f2026']) {
    assert.equal(classifyMobileNavigation(path, config('schedule')).action, 'delegate', path);
  }
});

test('browser fallback flags delegate schedule navigation without changing unrelated site routes', () => {
  for (const app of ['schedule', 'anissia']) {
    for (const path of ['/schedule?_anissia_browser=1', '/schedule/2026/?_anissia_browser=1#abcdef', '/schedule/2015?_anissia_browser=0&_anissia_browser=1', '/schedule/2015?_anissia_browser=1&_anissia_browser=0', '/schedule/2009?_anissia_browser=1']) {
      assert.deepEqual(classifyMobileNavigation(path, config(app)), {action: 'delegate', url: origin + path});
    }
  }
  for (const flag of ['', '0', '10']) {
    assert.equal(classifyMobileNavigation('/schedule/2026?_anissia_browser=' + flag, config('schedule')).action, 'internal');
  }
  for (const path of ['/anime/1?_anissia_browser=1', '/schedule-extra?_anissia_browser=1', '/admin/schedule?_anissia_browser=1']) {
    assert.equal(classifyMobileNavigation(path, config('anissia')).action, 'internal');
  }
});

test('anissia delegates the schedule subtree without swallowing similarly named site paths', () => {
  for (const path of ['/schedule', '/schedule/', '/schedule/2026#theme', '/schedule/2015/', '/schedule/2009', '/schedule/unknown']) {
    assert.equal(classifyMobileNavigation(path, config('anissia')).action, 'delegate', path);
  }
  for (const path of ['/', '/anime/1?q=2#section', '/wiki/images', '/admin/schedule', '/scheduler', '/schedule-extra']) {
    assert.equal(classifyMobileNavigation(path, config('anissia')).action, 'internal', path);
  }
});

test('URL origin boundaries and special schemes are enforced before path matching', () => {
  for (const app of ['anissia', 'schedule']) {
    for (const url of ['https://anissia.net.evil.test/schedule/2026', 'http://anissia.net/schedule/2026', 'https://anissia.net:444/schedule/2026', '//external.test/schedule/2026', 'mailto:auth@anissia.net', 'tel:123']) {
      assert.equal(classifyMobileNavigation(url, config(app)).action, 'delegate', url);
    }
    for (const url of ['https://user:password@anissia.net/schedule/2026', 'javascript:alert(1)', 'data:text/html,hello', 'file:///schedule/2026', 'intent://anissia', 'https://[invalid']) {
      assert.equal(classifyMobileNavigation(url, config(app)).action, 'block', url);
    }
  }
  assert.equal(classifyMobileNavigation('https://ANISSIA.NET:443/schedule/2026', config('schedule')).action, 'internal');
  assert.equal(classifyMobileNavigation('../anime/1', config('schedule'), origin + '/schedule/2026').url, origin + '/anime/1');
});

function environment(initialConfig) {
  const previousWindow = globalThis.window, previousDocument = globalThis.document;
  const messages = [], navigation = [], opened = [], handlers = new Map();
  const view = new EventTarget();
  Object.assign(view, {
    location: {origin, href: origin + '/schedule/2026'},
    __anissiaMobile: initialConfig,
    AnissiaMobile: {postMessage(message) { messages.push(JSON.parse(message)); }},
    open(...args) { opened.push(args); return view; },
  });
  view.top = view;
  globalThis.window = view;
  globalThis.document = {
    addEventListener(type, handler) { handlers.set(type, handler); },
    removeEventListener(type, handler) { if (handlers.get(type) === handler) handlers.delete(type); },
  };
  const uninstall = installMobileBridge(path => navigation.push(path));
  const click = (href, target = '', attributes = {}) => {
    const link = {href, target, hasAttribute(name) { return Boolean(attributes[name]); }};
    const event = {
      type: 'click', button: 0, defaultPrevented: false, stopped: false,
      target: {closest() { return link; }},
      preventDefault() { this.defaultPrevented = true; },
      stopImmediatePropagation() { this.stopped = true; },
    };
    handlers.get('click')(event);
    return event;
  };
  return {view, messages, navigation, opened, click, restore() {
    uninstall();
    globalThis.window = previousWindow;
    globalThis.document = previousDocument;
  }};
}

test('normal browser and invalid bridge configurations preserve browser navigation', () => {
  for (const value of [undefined, {...config('schedule'), version: 2}, {...config('schedule'), origin: 'https://other.test'}]) {
    const env = environment(value);
    try {
      assert.equal(delegateMobileNavigation(origin + '/anime/1'), false);
      assert.equal(env.click(origin + '/anime/1', '_blank').defaultPrevented, false);
      assert.equal(env.view.open(origin + '/anime/1', '_blank'), env.view);
      assert.equal(env.opened.length, 1);
      assert.deepEqual(env.messages, []);
      assert.equal(env.view.__anissiaMobileBack(), false);
    } finally { env.restore(); }
  }
});

test('late native configuration activates navigation and sends ready', () => {
  const env = environment();
  try {
    env.view.__anissiaMobile = config('schedule');
    env.view.dispatchEvent(new Event('anissia-mobile-ready'));
    assert.deepEqual(env.messages, [{version: 1, type: 'ready'}]);
    assert.equal(delegateMobileNavigation('/anime/123#details'), true);
    assert.deepEqual(env.messages.at(-1), {version: 1, type: 'navigate', url: origin + '/anime/123#details'});
    assert.equal(delegateMobileNavigation('/schedule/2015'), false);
    const count = env.messages.length;
    assert.equal(delegateMobileNavigation('javascript:alert(1)'), true);
    assert.equal(env.messages.length, count);
  } finally { env.restore(); }
});

test('blank targets and window.open use the same policy without creating extra windows', () => {
  const env = environment(config('anissia'));
  try {
    env.messages.length = 0;
    assert.equal(env.click(origin + '/schedule/2026#color', '_blank').stopped, true);
    assert.deepEqual(env.messages, [{version: 1, type: 'navigate', url: origin + '/schedule/2026#color'}]);
    assert.equal(env.click(origin + '/anime/123#details', '_blank').stopped, true);
    assert.deepEqual(env.navigation, ['/anime/123#details']);
    assert.equal(env.view.open('https://external.test/x', '_blank'), null);
    assert.equal(env.messages.at(-1).url, 'https://external.test/x');
    assert.equal(env.view.open('/wiki/images', '_blank'), null);
    assert.equal(env.navigation.at(-1), '/wiki/images');
    const count = env.messages.length;
    assert.equal(env.view.open(), null);
    assert.equal(env.messages.length, count);
    assert.deepEqual(env.opened, []);
    assert.equal(env.click(origin + '/anime/1').defaultPrevented, false);
    assert.equal(env.click(origin + '/download/file', '_blank', {download: true}).defaultPrevented, false);
  } finally { env.restore(); }
});

test('browser fallback links are intercepted before WebView or SPA navigation', () => {
  const env = environment(config('schedule'));
  try {
    env.messages.length = 0;
    const url = origin + '/schedule/2015?_anissia_browser=1#abcdef';
    assert.equal(env.click(url).stopped, true);
    assert.deepEqual(env.messages, [{version: 1, type: 'navigate', url}]);
    assert.equal(env.view.open(url, '_blank'), null);
    assert.equal(env.messages.length, 2);
    assert.deepEqual(env.navigation, []);
    assert.deepEqual(env.opened, []);
  } finally { env.restore(); }
});

test('native color changes and page remounts do not echo messages back to native', () => {
  const env = environment(config('schedule'));
  try {
    const applied = [];
    env.view.colorMode = mode => { applied.push(mode); notifyMobileColorMode(mode); };
    env.messages.length = 0;
    env.view.__anissiaMobileApplyColorMode('dark');
    assert.deepEqual(applied, ['dark']);
    assert.deepEqual(env.messages, []);
    assert.equal(env.view.__anissiaMobile.colorMode, 'dark');
    mobilePageReady();
    assert.deepEqual(applied, ['dark', 'dark']);
    assert.deepEqual(env.messages, [{version: 1, type: 'ready'}]);
    notifyMobileColorMode('light');
    assert.deepEqual(env.messages.at(-1), {version: 1, type: 'colorMode', mode: 'light'});
    assert.equal(env.view.__anissiaMobile.colorMode, 'light');
    env.view.__anissiaMobileApplyColorMode('invalid');
    assert.equal(applied.length, 2);
  } finally { env.restore(); }
});

test('native back closes the latest active popup before falling through to browser history', () => {
  const env = environment(config('schedule'));
  let open = true;
  const unregister = registerMobileBackHandler(() => {
    if (!open) return false;
    open = false;
    return true;
  });
  try {
    assert.equal(env.view.__anissiaMobileBack(), true);
    assert.equal(env.view.__anissiaMobileBack(), false);
    unregister();
    open = true;
    assert.equal(env.view.__anissiaMobileBack(), false);
  } finally { unregister(); env.restore(); }
});
