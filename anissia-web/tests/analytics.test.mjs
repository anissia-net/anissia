import assert from 'node:assert/strict';
import {test} from 'node:test';
import {analyticsLocation, createAnalytics} from '../src/common/analytics.ts';

const origin = 'https://anissia.net';
const trackingId = 'G-TEST';

function environment(t, path = '/', options = {}) {
  const old = {window: globalThis.window, document: globalThis.document};
  const listeners = new Map(), deferred = [], scripts = [];
  const window = {location: {origin, href: origin + path},
    navigator: {userAgent: options.userAgent || ''},
    addEventListener(name, fn) { listeners.set(name, fn); },
    ...(options.native ? {AnissiaMobile: {postMessage() {}}} : {}),
  };
  const document = {referrer: options.referrer || '', readyState: 'complete',
    createElement() { return {}; }, head: {append(script) { scripts.push(script); }},
  };
  globalThis.window = window;
  globalThis.document = document;
  t.after(() => Object.assign(globalThis, old));
  const analytics = createAnalytics(trackingId, task => deferred.push(task));
  return {analytics, window, scripts, listeners,
    flush() { deferred.splice(0).forEach(task => task()); },
    commands() { return (window.dataLayer || []).map(value => [...value]); },
  };
}

test('native app channels disable analytics before mobile configuration is injected', t => {
  const env = environment(t, '/schedule/2026', {native: true});
  env.analytics.page('/schedule/2026', '편성표');
  env.analytics.install(); env.flush();
  assert.equal(env.window['ga-disable-G-TEST'], true);
  assert.deepEqual(env.scripts, []);
  assert.deepEqual(env.commands(), []);
});

test('late native readiness prevents a deferred tag load', t => {
  const env = environment(t);
  env.analytics.install();
  env.window.AnissiaMobile = {postMessage() {}};
  env.listeners.get('anissia-mobile-ready')();
  env.flush();
  env.analytics.page('/', '애니시아');
  assert.equal(env.window['ga-disable-G-TEST'], true);
  assert.deepEqual(env.scripts, []);
  assert.deepEqual(env.commands(), []);
});

test('Android WebView previews without the main bridge do not load analytics', t => {
  const env = environment(t, '/schedule/2026', {
    userAgent: 'Mozilla/5.0 (Linux; Android 15; Pixel 9 Build/AP3A; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/140.0.0.0 Mobile Safari/537.36',
  });
  env.analytics.install(); env.analytics.page('/schedule/2026', '편성표'); env.flush();
  assert.equal(env.window['ga-disable-G-TEST'], true);
  assert.deepEqual(env.scripts, []);
  assert.deepEqual(env.commands(), []);
});

for (const path of ['/register/private-register-token', '/recover/private-recovery-token',
  '/REGISTER/private-register-token', '/Recover/private-recovery-token', '/anime?token=private-token']) {
  test(`sensitive initial URL blocks analytics: ${path.split('private')[0]}`, t => {
    const env = environment(t, path);
    env.analytics.install(); env.analytics.page(path, '화면'); env.flush();
    assert.equal(env.window['ga-disable-G-TEST'], true);
    assert.deepEqual(env.scripts, []);
    assert.deepEqual(env.commands(), []);
  });
}

test('sensitive navigation disables an existing tag before the URL changes and stays disabled', t => {
  const env = environment(t);
  env.analytics.page('/', '애니시아');
  env.analytics.install(); env.flush();
  const before = env.commands().length;
  env.analytics.beforeNavigation('/Recover/private-token');
  assert.equal(env.window.location.href, origin + '/');
  assert.equal(env.window['ga-disable-G-TEST'], true);
  env.window.location.href = origin + '/Recover/private-token';
  env.analytics.page('/Recover/private-token', '복구');
  env.window.location.href = origin + '/';
  env.analytics.page('/', '애니시아');
  assert.equal(env.commands().length, before);
  assert.ok(!JSON.stringify(env.commands()).includes('private-token'));
});

test('public page views explicitly override location and referrer without query or fragment', t => {
  const env = environment(t, '/', {referrer: 'https://example.com/source#fragment',
    userAgent: 'Mozilla/5.0 (Linux; Android 15; Pixel 9) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Mobile Safari/537.36'});
  env.analytics.page('/', '애니시아');
  env.analytics.install(); env.flush();
  env.window.location.href = origin + '/anime/123#captions';
  env.analytics.beforeNavigation('/anime/123#captions');
  env.analytics.page('/anime/123#captions', '작품');
  assert.equal(env.scripts.length, 1);
  const commands = env.commands();
  assert.equal(commands.filter(x => x[0] === 'config').length, 1);
  assert.equal(commands.find(x => x[0] === 'config')[2].send_page_view, false);
  const pages = commands.filter(x => x[0] === 'event');
  assert.equal(pages.length, 2);
  assert.deepEqual(pages[1][2], {send_to: trackingId, page_path: '/anime/123',
    page_location: origin + '/anime/123', page_referrer: origin + '/', page_title: '작품'});
  assert.equal(pages[0][2].page_referrer, 'https://example.com/source');
});

test('analytics URL redaction strips auth tokens, query strings and fragments', () => {
  assert.equal(analyticsLocation('/register/private-token?email=private#secret', origin), origin + '/register');
  assert.equal(analyticsLocation('/recover/private-token?code=secret', origin), origin + '/recover');
  assert.equal(analyticsLocation('/Recover/private-token?code=secret', origin), origin + '/Recover');
  assert.equal(analyticsLocation('/anime?q=private#results', origin), origin + '/anime');
  assert.equal(analyticsLocation('https://user:password@example.com/', origin), '');
});

test('sensitive referrers are suppressed before a public document loads analytics', t => {
  const env = environment(t, '/', {referrer: origin + '/RECOVER/private-token'});
  env.analytics.install(); env.analytics.page('/', '애니시아'); env.flush();
  assert.deepEqual(env.scripts, []);
  assert.deepEqual(env.commands(), []);
});
