(function (scope) {
  const cacheName = 'anissia-static-v1';
  const maxBytes = 64 * 1024 * 1024;
  const maxEntryBytes = 8 * 1024 * 1024;
  const maxEntries = 512;
  const publicImages = new Set([
    '/favicon.ico', '/favicon.png', '/app-icon.svg', '/app-icon-192.png',
    '/app-icon-512.png', '/apple-touch-icon.png', '/push-icon.svg',
  ]);
  const fingerprint = /^\/assets\/[^/]+-[A-Za-z0-9_-]{8,}\.(js|css|avif|webp|png|jpe?g|gif|svg|ico|woff2?|ttf|otf)$/;
  const trustedPaths = new Set((scope.AnissiaStaticAssets || []).filter(path => fingerprint.test(path)));

  function classify(request, origin, trusted = trustedPaths) {
    if (request.method !== 'GET' || request.mode === 'navigate' || request.destination === 'document'
      || request.cache === 'no-store' || request.headers.has('authorization')
      || request.headers.has('dat') || request.headers.has('range')) return null;
    const url = new URL(request.url);
    if (url.origin !== origin || url.username || url.password || url.search) return null;
    const match = fingerprint.exec(url.pathname);
    if (match) return {extension: match[1], maxAge: 31536000, immutable: trusted.has(url.pathname)};
    if (publicImages.has(url.pathname)) return {extension: 'image', maxAge: 3600};
    return null;
  }

  function lifetime(response, policy, now) {
    if (response.status !== 200 || response.redirected || !['basic', 'default'].includes(response.type)) return 0;
    if (Number(response.headers.get('content-length')) > maxEntryBytes) return 0;
    const control = response.headers.get('cache-control') || '';
    if (/(?:^|,)\s*(?:private|no-store|no-cache)(?:\s*[=,]|\s*$)/i.test(control)) return 0;
    if (!policy.immutable && !/(?:^|,)\s*public\s*(?:,|$)/i.test(control)) return 0;
    const vary = (response.headers.get('vary') || '').toLowerCase().split(',').map(value => value.trim());
    if (vary.some(value => ['*', 'cookie', 'authorization', 'dat'].includes(value))) return 0;
    const mime = (response.headers.get('content-type') || '').split(';')[0].trim().toLowerCase();
    const validMime = policy.extension === 'js' ? /^(?:text|application)\/(?:javascript|ecmascript)$/.test(mime)
      : policy.extension === 'css' ? mime === 'text/css'
      : /^(?:woff2?|ttf|otf)$/.test(policy.extension) ? /^(?:font\/|application\/(?:font-|x-font-|vnd\.ms-fontobject))/.test(mime)
      : mime.startsWith('image/');
    if (!validMime) return 0;
    if (policy.immutable) return Infinity;
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

  function createRuntime({storage = scope.caches, fetcher = scope.fetch.bind(scope), now = Date.now,
    immutableAssets = scope.AnissiaStaticAssets || []} = {}) {
    let writes = Promise.resolve();
    let index;
    const trusted = new Set(immutableAssets.filter(path => fingerprint.test(path)));
    const inFlight = new Map();

    async function metadata() {
      if (!index) index = (async () => {
        const cache = await storage.open(cacheName);
        const entries = new Map();
        let total = 0;
        const keys = await cache.keys();
        const values = await Promise.all(keys.map(key => cache.match(key)));
        keys.forEach((key, offset) => {
          const value = values[offset];
          const bytes = Number(value?.headers.get('x-anissia-cache-bytes')) || 0;
          const until = Number(value?.headers.get('x-anissia-cache-until')) || 0;
          entries.set(key.url, {key, bytes, until: until > 0 && trusted.has(new URL(key.url).pathname) ? Number.MAX_SAFE_INTEGER : until});
          total += bytes;
        });
        return {cache, entries, total};
      })().catch(error => { index = null; throw error; });
      return index;
    }

    function change(action) {
      writes = writes.catch(() => {}).then(async () => action(await metadata()));
      return writes;
    }

    async function remove(state, key) {
      const entry = state.entries.get(key.url);
      await state.cache.delete(key);
      if (entry) { state.entries.delete(key.url); state.total -= entry.bytes; }
    }

    async function save(request, response, seconds) {
      const body = await boundedBody(response);
      if (!body) return;
      const headers = new Headers(response.headers);
      headers.delete('content-encoding');
      headers.delete('content-length');
      const until = seconds === Infinity ? Number.MAX_SAFE_INTEGER : now() + seconds * 1000;
      headers.set('x-anissia-cache-until', String(until));
      headers.set('x-anissia-cache-bytes', String(body.byteLength));
      const cached = new Response(body, {status: 200, statusText: response.statusText, headers});
      await change(async state => {
        await state.cache.put(request, cached);
        state.total -= state.entries.get(request.url)?.bytes || 0;
        state.entries.delete(request.url);
        state.entries.set(request.url, {key: request, bytes: body.byteLength, until});
        state.total += body.byteLength;
        if (state.total <= maxBytes && state.entries.size <= maxEntries) return;
        const candidates = [...state.entries.values()].sort((left, right) =>
          Number(left.until > now()) - Number(right.until > now())
          || Number(trusted.has(new URL(left.key.url).pathname)) - Number(trusted.has(new URL(right.key.url).pathname)));
        for (const entry of candidates) {
          if (state.total <= maxBytes && state.entries.size <= maxEntries) break;
          await remove(state, entry.key);
        }
      });
    }

    async function load(request, policy, waitUntil) {
      const bypass = !policy.immutable && (request.cache === 'reload' || request.cache === 'no-cache');
      if (!bypass) {
        try {
          const cache = await storage.open(cacheName);
          const cached = await cache.match(request);
          const until = Number(cached?.headers.get('x-anissia-cache-until')) || 0;
          if (cached && until > 0 && (policy.immutable || until > now())) return cached;
          if (cached) await change(state => remove(state, request));
        } catch (_) { /* Storage failures must not prevent the network request. */ }
      }
      const response = await fetcher(request);
      const seconds = lifetime(response, policy, now());
      if (seconds > 0) waitUntil(save(request, response.clone(), seconds).catch(() => {}));
      else waitUntil(change(state => remove(state, request)).catch(() => {}));
      return response;
    }

    async function handle(request, waitUntil = () => {}) {
      const policy = classify(request, scope.location.origin, trusted);
      if (!policy) return fetcher(request);
      if (!policy.immutable) return load(request, policy, waitUntil);
      let shared = inFlight.get(request.url);
      const joining = !!shared;
      if (!shared) {
        const pending = [];
        const response = load(request, policy, work => pending.push(work));
        const completed = response.then(() => Promise.all(pending));
        shared = {response, completed};
        inFlight.set(request.url, shared);
        const clear = () => { if (inFlight.get(request.url) === shared) inFlight.delete(request.url); };
        void completed.then(clear, clear);
      }
      waitUntil(shared.completed.catch(() => {}));
      const response = await shared.response;
      if (joining && lifetime(response, policy, now()) === 0) return load(request, policy, waitUntil);
      return response.clone();
    }

    async function activate() {
      const names = await storage.keys();
      await Promise.all(names.filter(name => name.startsWith('anissia-static-') && name !== cacheName).map(name => storage.delete(name)));
    }

    async function warm() {
      await Promise.allSettled([...trusted].filter(path => /\.(js|css|woff2?|ttf|otf)$/.test(path)).map(async path => {
        const pending = [];
        await handle(new Request(scope.location.origin + path, {credentials: 'omit'}), work => pending.push(work));
        await Promise.all(pending);
      }));
    }

    return {handle, activate, warm};
  }

  scope.AnissiaStaticCache = {classify, createRuntime};
})(self);
