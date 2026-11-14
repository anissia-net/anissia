export type WikiImageVersions = Record<string, number>;

export function wikiImageDisplayUrl(source: string, revision?: number): string {
  if (!Number.isSafeInteger(revision) || revision! <= 0) return source;
  const hashAt = source.indexOf('#');
  const hash = hashAt >= 0 ? source.slice(hashAt) : '';
  const base = hashAt >= 0 ? source.slice(0, hashAt) : source;
  const queryAt = base.indexOf('?');
  const path = queryAt >= 0 ? base.slice(0, queryAt) : base;
  const query = new URLSearchParams(queryAt >= 0 ? base.slice(queryAt + 1) : '');
  query.set('v', String(revision));
  return `${path}?${query}${hash}`;
}

export function versionWikiImages(root: ParentNode, versions: WikiImageVersions = {}): void {
  for (const image of root.querySelectorAll<HTMLImageElement>('img[src]')) {
    const source = image.getAttribute('data-wiki-canonical-src') ?? image.getAttribute('src')!;
    const versioned = wikiImageDisplayUrl(source, versions[source]);
    if (versioned !== source) image.setAttribute('data-wiki-canonical-src', source);
    else image.removeAttribute('data-wiki-canonical-src');
    if (image.getAttribute('src') !== versioned) image.setAttribute('src', versioned);
  }
}

export function wikiImageVersionHtml(html: string, versions?: WikiImageVersions): string {
  if (!versions || !Object.keys(versions).length) return html;
  const template = document.createElement('template');
  template.innerHTML = html;
  versionWikiImages(template.content, versions);
  return template.innerHTML;
}
