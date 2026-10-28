export interface WikiTarget { kind: 'anime' | 'template'; targetNo: number }

export function wikiPositiveInteger(value: unknown): number | undefined {
  if (typeof value !== 'string' || !/^[1-9]\d*$/.test(value)) return;
  const number = Number(value);
  return Number.isSafeInteger(number) ? number : undefined;
}

export function wikiHistoryTarget(query: Record<string, unknown>): WikiTarget | undefined {
  if (query.animeNo !== undefined && query.templateNo !== undefined) return;
  const animeNo = wikiPositiveInteger(query.animeNo);
  if (animeNo) return {kind: 'anime', targetNo: animeNo};
  const templateNo = wikiPositiveInteger(query.templateNo);
  if (templateNo) return {kind: 'template', targetNo: templateNo};
}

export function wikiHistoryLocation(item: WikiTarget & {revision?: number | null}): string {
  const key = item.kind === 'anime' ? 'animeNo' : 'templateNo';
  const revision = item.revision && item.revision > 0 ? `&revision=${item.revision}` : '';
  return `/wiki/history?${key}=${item.targetNo}${revision}`;
}

export function wikiLocation(item: WikiTarget & {scope?: 'current' | 'history'; revision?: number | null}): string {
  if (item.scope === 'history') return wikiHistoryLocation(item);
  return item.kind === 'anime' ? `/anime/${item.targetNo}` : `/wiki/templates?templateNo=${item.targetNo}`;
}

export function legacyWikiHistoryLocation(path: string, query: Record<string, unknown>): string | undefined {
  const revision = wikiPositiveInteger(query.wikiRevision);
  if (!revision) return;
  const kind = path === '/anime' ? 'anime' : path === '/wiki/templates' ? 'template' : undefined;
  if (!kind) return;
  const targetNo = wikiPositiveInteger(query[kind === 'anime' ? 'animeNo' : 'templateNo']);
  if (targetNo) return wikiHistoryLocation({kind, targetNo, revision});
}
