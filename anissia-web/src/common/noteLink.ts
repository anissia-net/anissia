import type {PasteUrlHandler} from 'nabi-note';

type LinkTarget = {kind: 'anime' | 'notice' | 'inquiry'; id: string};

/** Only public detail routes; old query-string links are still shared in posts. */
export function noteLinkTarget(url: URL): LinkTarget | undefined {
  if (url.searchParams.has('wikiRevision') || url.searchParams.has('type')) return;
  const match = /^\/(anime|notice|inquiry)(?:\/([1-9]\d*))?$/.exec(url.pathname);
  if (!match) return;
  const kind = match[1] as LinkTarget['kind'];
  const key = kind === 'anime' ? 'animeNo' : 'topicNo';
  const values = url.searchParams.getAll(key);
  if (!match[2] && values.length !== 1) return;
  const id = match[2] ?? values[0];
  if (!id || !/^[1-9]\d*$/.test(id) || !Number.isSafeInteger(Number(id))) return;
  return {kind, id};
}

export function createNoteLinkResolver(options: {
  origin: string; api: string; fetcher?: typeof fetch; now?: () => number; timeoutMs?: number;
}): PasteUrlHandler {
  const fetcher = options.fetcher ?? fetch, now = options.now ?? Date.now;
  const cache = new Map<string, {expires: number; promise: Promise<string | null>}>();
  return (href, sameOrigin) => {
    if (!sameOrigin) return null;
    let url: URL;
    try { url = new URL(href, options.origin); } catch { return null; }
    if (url.origin !== options.origin || !/^https?:$/.test(url.protocol) || url.username || url.password) return null;
    const target = noteLinkTarget(url);
    if (!target) return null;
    const key = `${target.kind}/${target.id}`, cached = cache.get(key);
    if (cached && cached.expires > now()) return cached.promise;
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), options.timeoutMs ?? 1500);
    const entry = {expires: Infinity, promise: Promise.resolve<string | null>(null)};
    entry.promise = (async () => {
      try {
        // No page HTML, authentication preflight or global loading/error UI for optional labels.
        const response = await fetcher(`${options.api}/link/title?kind=${target.kind}&id=${target.id}`, {
          signal: controller.signal, credentials: 'omit', cache: 'no-store',
        });
        if (!response.ok) throw new Error('Title lookup failed');
        const result = await response.json();
        if (result?.code !== 'ok') throw new Error('Title lookup failed');
        const title = typeof result.data?.title === 'string' ? result.data.title.trim() : '';
        entry.expires = now() + (title ? 60_000 : 10_000);
        return title || null;
      } catch {
        if (cache.get(key) === entry) cache.delete(key);
        return null;
      } finally { clearTimeout(timeout); }
    })();
    cache.set(key, entry);
    if (cache.size > 64) cache.delete(cache.keys().next().value!);
    return entry.promise;
  };
}

let resolver: PasteUrlHandler | undefined;
export const resolveNoteLink: PasteUrlHandler = (url, sameOrigin, path) => {
  resolver ??= createNoteLinkResolver({origin: location.origin, api: import.meta.env.VITE_API_PATH});
  return resolver(url, sameOrigin, path);
};
