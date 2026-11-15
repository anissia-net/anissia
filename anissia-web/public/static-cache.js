(function (scope) {
  const cacheName = 'anissia-static-v1';
  const maxBytes = 64 * 1024 * 1024;
  const maxEntryBytes = 8 * 1024 * 1024;
  const maxEntries = 192;
  const publicImages = new Set([
    '/favicon.ico', '/favicon.png', '/app-icon.svg', '/app-icon-192.png',
    '/app-icon-512.png', '/apple-touch-icon.png', '/push-icon.svg',
  ]);
  const fingerprint = /^\/assets\/[^/]+-[A-Za-z0-9_-]{8,}\.(js|css|avif|webp|png|jpe?g|gif|svg|ico|woff2?|ttf|otf)$/;

  function classify(request, origin) {
    if (request.method !== 'GET' || request.mode === 'navigate' || request.destination === 'document'
      || request.cache === 'no-store' || request.headers.has('authorization')
      || request.headers.has('dat') || request.headers.has('range')) return null;
    const url = new URL(request.url);
    if (url.origin !== origin || url.username || url.password || url.search) return null;
    const match = fingerprint.exec(url.pathname);
    if (match) return {extension: match[1], maxAge: 31536000};
    if (publicImages.has(url.pathname)) return {extension: 'image', maxAge: 3600};
    return null;
  }

  function lifetime(response, policy, now) {
    if (response.status !== 200 || response.redirected || !['basic', 'default'].includes(response.type)) return 0;
    if (Number(response.headers.get('content-length')) > maxEntryBytes) return 0;
    const control = response.headers.get('cache-control') || '';
    if (!/(?:^|,)\s*public\s*(?:,|$)/i.test(control)
      || /(?:^|,)\s*(?:private|no-store|no-cache)(?:\s*[=,]|\s*$)/i.test(control)) return 0;
    const vary = (response.headers.get('vary') || '').toLowerCase().split(',').map(value => value.trim());
    if (vary.some(value => ['*', 'cookie', 'authorization', 'dat'].includes(value))) return 0;
    const mime = (response.headers.get('content-type') || '').split(';')[0].trim().toLowerCase();
    const validMime = policy.extension === 'js' ? /^(?:text|application)\/(?:javascript|ecmascript)$/.test(mime)
      : policy.extension === 'css' ? mime === 'text/css'
      : /^(?:woff2?|ttf|otf)$/.test(policy.extension) ? /^(?:font\/|application\/(?:font-|x-font-|vnd\.ms-fontobject))/.test(mime)
      : mime.startsWith('image/');
    if (!validMime) return 0;
    const seconds = Number(/(?:^|,)\s*max-age\s*=\s*"?(\d+)"?\s*(?:,|$)/i.exec(control)?.[1] || 0);
    if (!Number.isSafeInteger(seconds) || seconds <= 0) return 0;
    const age = Number(response.headers.get('age') || 0);
    const date = Date.parse(response.headers.get('date') || '');
    const elapsed = Math.max(Number.isFinite(age) ? age : 0, Number.isFinite(date) ? (now - date) / 1000 : 0, 0);
    return Math.max(0, Math.min(policy.maxAge, seconds) - elapsed);
  }

  async function boundedBody(response) {
    const length = Number(response.headers.get('content-length'));
    if (length > maxEntryBytes || !response.body) return null;
    const reader = response.body.getReader();
    const chunks = [];
    let bytes = 0;
    while (true) {
      const chunk = await reader.read();
      if (chunk.done) break;
      bytes += chunk.value.byteLength;
      if (bytes > maxEntryBytes) {
        void reader.cancel().catch(() => {});
        return null;
      }
      chunks.push(chunk.value);
    }
    const body = new Uint8Array(bytes);
    let offset = 0;
    for (const chunk of chunks) { body.set(chunk, offset); offset += chunk.byteLength; }
    return body;
  }

  function createRuntime({storage = scope.caches, fetcher = scope.fetch.bind(scope), now = Date.now} = {}) {
    let writes = Promise.resolve();

    async function save(request, response, seconds) {
      const body = await boundedBody(response);
      if (!body) return;
      const headers = new Headers(response.headers);
      headers.delete('content-encoding');
      headers.delete('content-length');
      headers.set('x-anissia-cache-until', String(now() + seconds * 1000));
      headers.set('x-anissia-cache-bytes', String(body.byteLength));
      const cached = new Response(body, {status: 200, statusText: response.statusText, headers});
      writes = writes.catch(() => {}).then(async () => {
        const cache = await storage.open(cacheName);
        await cache.put(request, cached);
        const keys = await cache.keys();
        const entries = await Promise.all(keys.map(async key => {
          const value = await cache.match(key);
          return {key, bytes: Number(value?.headers.get('x-anissia-cache-bytes')) || 0,
            until: Number(value?.headers.get('x-anissia-cache-until')) || 0};
        }));
        let total = entries.reduce((sum, entry) => sum + entry.bytes, 0), count = entries.length;
        for (const entry of entries) {
          if (entry.until <= now() || entry.bytes <= 0 || total > maxBytes || count > maxEntries) {
            await cache.delete(entry.key);
            total -= entry.bytes;
            count--;
          }
        }
      });
      await writes;
    }

    async function handle(request, waitUntil = () => {}) {
      const policy = classify(request, scope.location.origin);
      if (!policy) return fetcher(request);
      const bypass = request.cache === 'reload' || request.cache === 'no-cache';
      if (!bypass) {
        try {
          const cache = await storage.open(cacheName);
          const cached = await cache.match(request);
          if (cached && Number(cached.headers.get('x-anissia-cache-until')) > now()) return cached;
          if (cached) await cache.delete(request);
        } catch (_) { /* Storage failures must not prevent the network request. */ }
      }
      const response = await fetcher(request);
      const seconds = lifetime(response, policy, now());
      if (seconds > 0) waitUntil(save(request, response.clone(), seconds).catch(() => {}));
      else waitUntil(storage.open(cacheName).then(cache => cache.delete(request)).catch(() => {}));
      return response;
    }

    async function activate() {
      const names = await storage.keys();
      await Promise.all(names.filter(name => name.startsWith('anissia-static-') && name !== cacheName).map(name => storage.delete(name)));
    }

    return {handle, activate};
  }

  scope.AnissiaStaticCache = {classify, createRuntime};
})(self);
