export interface WikiImageOwner { documentKind: 'anime' | 'template'; documentNo: number }

export function wikiImageOwner(kind: unknown, number: unknown): WikiImageOwner | undefined {
  if (kind !== 'anime' && kind !== 'template') return;
  if (typeof number !== 'string' && typeof number !== 'number') return;
  const documentNo = Number(number);
  if (!Number.isSafeInteger(documentNo) || documentNo <= 0) return;
  return {documentKind: kind, documentNo};
}

export function wikiImageOwnerFields(owner: WikiImageOwner): Record<string, string> {
  return {documentKind: owner.documentKind, documentNo: String(owner.documentNo)};
}

export function wikiImageOwnerLocation(owner: WikiImageOwner): string {
  return owner.documentKind === 'anime' ? `/anime/${owner.documentNo}` : `/wiki/templates?templateNo=${owner.documentNo}`;
}

export function wikiImageLocation(image: {id: string; documentKind?: unknown; documentNo?: unknown}): string | undefined {
  const owner = wikiImageOwner(image.documentKind, image.documentNo);
  if (!owner) return;
  const document = wikiImageOwnerLocation(owner);
  return `${document}${document.includes('?') ? '&' : '?'}imageId=${encodeURIComponent(image.id)}#wiki-images`;
}
