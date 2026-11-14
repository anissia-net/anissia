import type {WikiBody} from './wikiRemote';

// Only known text/formatting containers can be blank. Media, templates and future
// block types must keep their document visible even when they have no text.
const textWings = new Set(['p', 'br', 'tf', 'fs', 'tc', 'hl', 'b', 'i', 'u', 's', 'sup', 'sub', 'a',
  'ul', 'li', 'ol', 'oli', 'tl', 'tli', 'quote', 'float', 'details', 'summary', 'code', 'tr', 'td']);

export function isWikiBodyEmpty(body: WikiBody): boolean {
  function isEmpty(node: unknown): boolean {
    if (typeof node === 'string') return !node.trim();
    if (!node || typeof node !== 'object' || Array.isArray(node)) return false;
    const {w, ch} = node as {w?: unknown; ch?: unknown};
    return typeof w === 'string' && textWings.has(w) &&
      (ch === undefined || (Array.isArray(ch) && ch.every(isEmpty)));
  }
  return body.schemaVersion === 1 && Array.isArray(body.blocks) && body.blocks.every(isEmpty);
}
