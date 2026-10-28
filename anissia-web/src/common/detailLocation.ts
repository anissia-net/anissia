/** Compatibility for shared links and persisted document content using the old URLs. */
export function legacyDetailLocation(path: string, query: Record<string, unknown>) {
  const anime = path === '/anime' || path === '/admin/anime';
  if (!anime && path !== '/notice' && path !== '/inquiry') return;
  const key = anime ? 'animeNo' : 'topicNo';
  const value = query[key];
  if (typeof value !== 'string' || !/^(0|[1-9]\d*)$/.test(value) || !Number.isSafeInteger(Number(value))) return;
  if (value === '0' && path === '/anime') return;
  const {[key]: _, ...remaining} = query;
  return {path: `${path}/${value === '0' ? 'new' : value}`, query: remaining};
}
