import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {test} from 'node:test';
import vm from 'node:vm';
import {installStaticCache} from '../src/common/staticCache.ts';

const origin = 'https://anissia.net';
const source = await readFile(new URL('../public/static-cache.js', import.meta.url), 'utf8');
const scope = {location: {origin}};
vm.runInNewContext(source, {self: scope, URL, Headers, Request, Response, Uint8Array, Date, Promise});
const {classify, createRuntime} = scope.AnissiaStaticCache;
const asset = '/assets/index-Abcd1234.js';
const request = (path = asset, options = {}) => new Request(origin + path, options);
const script = (value = 'code', headers = {}) => new Response(value, {headers: {
  'Content-Type': 'text/javascript', 'Cache-Control': 'public, max-age=31536000, immutable', ...headers,
}});

function storage() {
  const entries = new Map();
  const operations = {keys: 0, matches: 0};
  const cache = {
    async match(key) { operations.matches++; return entries.get(key.url)?.clone(); },
    async put(key, response) { entries.set(key.url, response.clone()); },
    async delete(key) { return entries.delete(key.url); },
    async keys() { operations.keys++; return [...entries.keys()].map(url => new Request(url)); },
  };
  return {entries, operations, async open() { return cache; }, async keys() { return ['anissia-static-v1']; }, async delete() {}};
}

function environment(response = () => script(), immutableAssets = []) {
  const disk = storage(), lifetime = [], fetched = [];
  let clock = 1000000;
  const runtime = createRuntime({storage: disk, now: () => clock, immutableAssets, fetcher: async value => {
    fetched.push(value.url);
    return response(value);
  }});
  return {disk, fetched, runtime, setTime(value) { clock = value; }, async handle(value = request()) {
    const result = await runtime.handle(value, work => lifetime.push(work));
    await Promise.all(lifetime.splice(0));
    return result;
  }};
}

test('cache only fingerprints and an explicit public icon list, never pages or credentials', () => {
  for (const path of [asset, '/assets/logo-Abcd1234.svg', '/assets/font-Zz998877.woff2', '/favicon.png']) {
    assert.ok(classify(request(path), origin), path);
  }
  for (const path of ['/', '/notifications', '/api/notifications', '/api/images/test.png', '/assets/index.js', '/assets/index-Abcd1234.js?token=secret', '/sw.js', '/static-cache.js']) {
    assert.equal(classify(request(path), origin), null, path);
  }
  for (const options of [{method: 'POST'}, {cache: 'no-store'}, {headers: {dat: 'private'}}, {headers: {Authorization: 'Bearer private'}}, {headers: {Range: 'bytes=0-10'}}]) {
    assert.equal(classify(request(asset, options), origin), null);
  }
  assert.equal(classify(new Request('https://file.anissia.net/file/wiki/example.avif'), origin), null);
  const navigation = request();
  Object.defineProperty(navigation, 'destination', {value: 'document'});
  assert.equal(classify(navigation, origin), null);
});

test('hashed resources survive worker restarts and a new deployment fetches only new URLs', async () => {
  const env = environment();
  assert.equal(await (await env.handle()).text(), 'code');
  const restarted = createRuntime({storage: env.disk, now: () => 1000001, fetcher: async () => { throw Error('network unavailable'); }});
  assert.equal(await (await restarted.handle(request())).text(), 'code');
  await env.handle(request('/assets/index-Zyxw9876.js'));
  await env.handle();
  assert.deepEqual(env.fetched, [origin + asset, origin + '/assets/index-Zyxw9876.js']);
});

test('mutable public images expire, and browser reload explicitly refreshes cached assets', async () => {
  let sequence = 0;
  const env = environment(() => new Response(`image-${++sequence}`, {headers: {'Content-Type': 'image/png', 'Cache-Control': 'public, max-age=7200'}}));
  const icon = request('/favicon.png');
  assert.equal(await (await env.handle(icon)).text(), 'image-1');
  env.setTime(1000000 + 3599 * 1000);
  assert.equal(await (await env.handle(icon)).text(), 'image-1');
  env.setTime(1000000 + 3601 * 1000);
  assert.equal(await (await env.handle(icon)).text(), 'image-2');
  assert.equal(await (await env.handle(request('/favicon.png', {cache: 'reload'}))).text(), 'image-3');
});

test('private, no-store, uncacheable, HTML fallback and failed responses never enter the disk cache', async () => {
  const responses = [
    script('private', {'Cache-Control': 'private, max-age=999'}),
    script('no-store', {'Cache-Control': 'public, no-store, max-age=999'}),
    script('revalidate', {'Cache-Control': 'public, no-cache="set-cookie", max-age=999'}),
    script('unknown', {'Cache-Control': ''}),
    script('account', {'Vary': 'Cookie'}),
    script('<html>not found</html>', {'Content-Type': 'text/html'}),
    new Response('missing', {status: 404, headers: {'Content-Type': 'text/javascript', 'Cache-Control': 'public, max-age=999'}}),
    script('large', {'Content-Length': String(9 * 1024 * 1024)}),
  ];
  for (const response of responses) {
    const env = environment(() => response.clone());
    await env.handle();
    await env.handle();
    assert.equal(env.disk.entries.size, 0);
    assert.equal(env.fetched.length, 2);
  }
});

test('HTTP Age and Date prevent extending an already stale server response', async () => {
  const env = environment(() => script('old', {'Cache-Control': 'public, max-age=100', Age: '99', Date: new Date(901000).toUTCString()}));
  await env.handle();
  env.setTime(1002000);
  await env.handle();
  assert.equal(env.fetched.length, 2);
});

test('trusted build assets persist without cache headers or expiry, including reloads', async () => {
  const env = environment(() => script('immutable', {'Cache-Control': ''}), [asset]);
  await env.handle();
  env.setTime(1000000 + 20 * 365 * 86400 * 1000);
  assert.equal(await (await env.handle(request(asset, {cache: 'reload'}))).text(), 'immutable');
  assert.equal(await (await env.handle(request(asset, {cache: 'no-cache'}))).text(), 'immutable');
  const restarted = createRuntime({storage: env.disk, immutableAssets: [asset], now: () => 9000000000000,
    fetcher: async () => { throw Error('offline'); }});
  assert.equal(await (await restarted.handle(request())).text(), 'immutable');
  assert.equal(env.fetched.length, 1);
  await env.handle(request('/assets/index-Zyxw9876.js'));
  assert.equal(env.fetched.length, 2, 'a new build hash still fetches');
  await env.handle(request(asset, {cache: 'no-store'}));
  assert.equal(env.fetched.length, 3, 'no-store requests still bypass storage');
});

test('manifest trust never overrides private/no-store/no-cache, credentials or HTML fallback', async () => {
  for (const response of [
    script('private', {'Cache-Control': 'private'}), script('private', {'Cache-Control': 'no-store'}),
    script('revalidate', {'Cache-Control': 'no-cache'}), script('account', {'Vary': 'Cookie'}),
    script('<html>fallback</html>', {'Content-Type': 'text/html', 'Cache-Control': ''}),
  ]) {
    const env = environment(() => response.clone(), [asset]);
    await env.handle(); await env.handle();
    assert.equal(env.disk.entries.size, 0);
    assert.equal(env.fetched.length, 2);
  }
  const env = environment(() => script('private', {'Cache-Control': ''}), [asset, '/api/account']);
  await env.handle(request(asset, {headers: {Authorization: 'Bearer private'}}));
  await env.handle(request('/api/account'));
  assert.equal(env.disk.entries.size, 0);
});

test('every listed image and font can persist without a scarce hosting header rule', async () => {
  for (const [path, type] of [['/assets/logo-Abcd1234.svg', 'image/svg+xml'], ['/assets/font-Abcd1234.woff2', 'font/woff2']]) {
    const env = environment(() => new Response('bytes', {headers: {'Content-Type': type, 'Cache-Control': 'public, max-age=0, must-revalidate'}}), [path]);
    await env.handle(request(path)); await env.handle(request(path));
    assert.equal(env.fetched.length, 1);
    assert.equal(env.disk.entries.size, 1);
  }
});

test('activation warms only current public code and fonts without account credentials', async () => {
  const paths = [asset, '/assets/style-Abcd1234.css', '/assets/font-Abcd1234.woff2', '/assets/image-Abcd1234.svg'];
  const fetched = [];
  const disk = storage();
  const runtime = createRuntime({storage: disk, immutableAssets: paths, fetcher: async value => {
    fetched.push({url: value.url, credentials: value.credentials});
    return new Response('bytes', {headers: {'Content-Type': value.url.endsWith('.js') ? 'text/javascript'
      : value.url.endsWith('.css') ? 'text/css' : 'font/woff2'}});
  }});
  await runtime.warm();
  assert.deepEqual(fetched.map(value => value.url), paths.slice(0, 3).map(path => origin + path));
  assert.ok(fetched.every(value => value.credentials === 'omit'));
  assert.equal(disk.entries.size, 3);
});

test('cache writes scan disk metadata once and retain the current deployment when bounded', async () => {
  const env = environment(() => script(), [asset]);
  await env.handle();
  for (let index = 0; index < 520; index++) await env.handle(request(`/assets/old-${String(index).padStart(8, '0')}.js`));
  assert.equal(env.disk.operations.keys, 1);
  assert.ok(env.disk.operations.matches <= 522, 'cache saves do not repeatedly re-read every existing response');
  assert.ok(env.disk.entries.size <= 512);
  assert.ok(env.disk.entries.has(origin + asset), 'current manifest assets survive older deployment eviction');
});

test('cache storage failures never prevent downloading an asset', async () => {
  const runtime = createRuntime({storage: {async open() { throw Error('quota'); }}, fetcher: async () => script()});
  const pending = [];
  assert.equal(await (await runtime.handle(request(), work => pending.push(work))).text(), 'code');
  await Promise.all(pending);
});

test('retention is bounded and activation cleans only old static caches', async () => {
  const env = environment();
  for (let index = 0; index < 192; index++) {
    env.disk.entries.set(origin + `/assets/old-${String(index).padStart(8, '0')}.js`, new Response('old', {headers: {
      'x-anissia-cache-until': '9999999999999', 'x-anissia-cache-bytes': String(1024 * 1024),
    }}));
  }
  await env.handle();
  const bytes = [...env.disk.entries.values()].reduce((sum, entry) => sum + Number(entry.headers.get('x-anissia-cache-bytes')), 0);
  assert.ok(bytes <= 64 * 1024 * 1024);
  assert.ok(env.disk.entries.size <= 192);
  assert.ok(env.disk.entries.has(origin + asset));
  const removed = [];
  const runtime = createRuntime({storage: {async keys() { return ['anissia-static-v0', 'anissia-static-v1', 'other-cache']; }, async delete(name) { removed.push(name); }}, fetcher: async () => script()});
  await runtime.activate();
  assert.deepEqual(removed, ['anissia-static-v0']);
});

test('service-worker cache registration works without PushManager or Notification APIs', t => {
  const old = Object.fromEntries(['window', 'document', 'navigator'].map(key => [key, Object.getOwnPropertyDescriptor(globalThis, key)]));
  const calls = [];
  Object.defineProperty(globalThis, 'window', {configurable: true, value: {isSecureContext: true}});
  Object.defineProperty(globalThis, 'document', {configurable: true, value: {readyState: 'complete'}});
  Object.defineProperty(globalThis, 'navigator', {configurable: true, value: {serviceWorker: {register(...args) { calls.push(args); return Promise.resolve(); }}}});
  t.after(() => { for (const [key, value] of Object.entries(old)) { if (value) Object.defineProperty(globalThis, key, value); else delete globalThis[key]; } });
  installStaticCache();
  assert.deepEqual(calls, [['/sw.js', {scope: '/', updateViaCache: 'none'}]]);
});
