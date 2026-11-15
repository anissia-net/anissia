import assert from 'node:assert/strict';
import {spawn} from 'node:child_process';
import {createServer} from 'node:http';
import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const root = fileURLToPath(new URL('..', import.meta.url));
const runtime = path.resolve(process.env.TEST_RUNTIME_ROOT || path.join(root, '../ailog/runtime/mobile-cache-verification'));
await fs.mkdir(runtime, {recursive: true});
const profile = await fs.mkdtemp(path.join(runtime, 'chrome-'));
const hits = new Map();
const worker = await fs.readFile(path.join(root, 'public/sw.js'));
const helper = await fs.readFile(path.join(root, 'public/static-cache.js'));
let unavailable = false;
const server = createServer((request, response) => {
  const url = new URL(request.url, 'http://localhost');
  hits.set(url.pathname, (hits.get(url.pathname) || 0) + 1);
  if (url.pathname === '/sw.js' || url.pathname === '/static-cache.js') {
    response.writeHead(200, {'Content-Type': 'text/javascript', 'Cache-Control': 'no-cache'});
    response.end(url.pathname === '/sw.js' ? worker : helper);
  } else if (url.pathname.startsWith('/assets/')) {
    if (unavailable && url.pathname === '/assets/index-Abcd1234.js') {
      response.writeHead(503, {'Cache-Control': 'no-store'}); response.end('offline'); return;
    }
    const privateAsset = url.pathname.includes('private');
    const fallback = url.pathname.includes('missing');
    response.writeHead(200, {'Content-Type': fallback ? 'text/html' : 'text/javascript',
      'Cache-Control': privateAsset ? 'private, no-store' : 'public, max-age=31536000, immutable'});
    response.end(fallback ? '<html>SPA fallback</html>' : `/* ${url.pathname} */`);
  } else if (url.pathname === '/api/account') {
    response.writeHead(200, {'Content-Type': 'application/json', 'Cache-Control': 'no-store'});
    response.end(JSON.stringify({sequence: hits.get(url.pathname)}));
  } else {
    response.writeHead(200, {'Content-Type': 'text/html', 'Cache-Control': 'no-store'});
    response.end('<!doctype html><title>Static cache verification</title>Local verification only');
  }
});
await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
const origin = `http://127.0.0.1:${server.address().port}`;
const chrome = spawn(process.env.CHROME_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', [
  '--headless=new', '--no-first-run', '--no-default-browser-check', '--disable-default-apps',
  '--disable-background-networking', '--disable-component-update', '--disable-sync',
  '--remote-debugging-port=0', `--user-data-dir=${profile}`, 'about:blank',
], {stdio: 'ignore'});
const pause = ms => new Promise(resolve => setTimeout(resolve, ms));
let socket;
try {
  let port;
  for (let attempt = 0; attempt < 80; attempt++) {
    try { port = Number((await fs.readFile(path.join(profile, 'DevToolsActivePort'), 'utf8')).split('\n')[0]); } catch {}
    if (port) break;
    if (chrome.exitCode !== null) throw Error(`Chrome exited: ${chrome.exitCode}`);
    await pause(100);
  }
  assert.ok(port, 'Chrome DevTools started');
  const [target] = await fetch(`http://127.0.0.1:${port}/json/list`).then(response => response.json());
  socket = new WebSocket(target.webSocketDebuggerUrl);
  await new Promise((resolve, reject) => { socket.addEventListener('open', resolve, {once: true}); socket.addEventListener('error', reject, {once: true}); });
  let sequence = 0;
  const pending = new Map();
  socket.addEventListener('message', event => {
    const result = JSON.parse(event.data);
    if (result.id) { pending.get(result.id)?.(result); pending.delete(result.id); }
  });
  const call = (method, params = {}) => new Promise((resolve, reject) => {
    const id = ++sequence;
    const timeout = setTimeout(() => { pending.delete(id); reject(Error(`CDP timeout: ${method}`)); }, 15000);
    pending.set(id, message => { clearTimeout(timeout); message.error ? reject(Error(JSON.stringify(message.error))) : resolve(message.result); });
    socket.send(JSON.stringify({id, method, params}));
  });
  const evaluate = async expression => {
    const result = await call('Runtime.evaluate', {expression, returnByValue: true, awaitPromise: true});
    if (result.exceptionDetails) throw Error(JSON.stringify(result.exceptionDetails));
    return result.result.value;
  };
  await call('Page.navigate', {url: origin});
  for (let attempt = 0; attempt < 100; attempt++) {
    if (await evaluate(`location.origin === ${JSON.stringify(origin)} && document.readyState === 'complete'`)) break;
    await pause(50);
  }
  await evaluate(`(async () => {
    await navigator.serviceWorker.register('/sw.js', {scope:'/', updateViaCache:'none'});
    await navigator.serviceWorker.ready;
    if (!navigator.serviceWorker.controller) await new Promise(resolve => navigator.serviceWorker.addEventListener('controllerchange', resolve, {once:true}));
  })()`);
  const load = url => evaluate(`(async () => { const response = await fetch(${JSON.stringify(url)}); return {status: response.status, body: await response.text(), cached: !!response.headers.get('x-anissia-cache-until')}; })()`);
  const asset = '/assets/index-Abcd1234.js';
  assert.equal((await load(asset)).status, 200);
  for (let attempt = 0; attempt < 100; attempt++) {
    if (await evaluate(`caches.open('anissia-static-v1').then(cache => cache.match('${asset}')).then(Boolean)`)) break;
    await pause(20);
  }
  unavailable = true;
  const cached = await load(asset);
  assert.equal(cached.cached, true);
  assert.equal(cached.status, 200);
  assert.equal(hits.get(asset), 1);
  await call('ServiceWorker.enable');
  await call('ServiceWorker.stopAllWorkers');
  assert.equal((await load(asset)).cached, true, 'cached body survives worker restart');
  assert.equal(hits.get(asset), 1);
  const changed = '/assets/index-Zyxw9876.js';
  assert.equal((await load(changed)).cached, false, 'new hash downloads once');
  assert.equal(hits.get(changed), 1);
  for (const excluded of ['/api/account', '/assets/private-Abcd1234.js', '/assets/missing-Abcd1234.js']) {
    assert.equal((await load(excluded)).cached, false);
    assert.equal((await load(excluded)).cached, false);
    assert.equal(await evaluate(`caches.open('anissia-static-v1').then(cache => cache.match('${excluded}')).then(Boolean)`), false);
  }
  assert.equal(hits.get('/api/account'), 2);
  const summary = {passed: true, origin, cacheHitWithoutNetwork: true, survivesWorkerRestart: true,
    newDeploymentHashFetched: true, apiPrivateAndHtmlExcluded: true, networkHits: Object.fromEntries(hits)};
  await fs.writeFile(path.join(runtime, 'browser-result.json'), JSON.stringify(summary, null, 2) + '\n');
  console.log(JSON.stringify(summary, null, 2));
} finally {
  socket?.close();
  chrome.kill('SIGTERM');
  await new Promise(resolve => chrome.exitCode !== null ? resolve() : chrome.once('exit', resolve));
  await new Promise(resolve => server.close(resolve));
  await fs.rm(profile, {recursive: true, force: true});
}
