import type {WikiBody, WikiDocument, WikiTemplate} from './wikiRemote';

export interface WikiDraft {
  version: 1;
  baseline: WikiDocument;
  body: WikiBody;
  templates: WikiTemplate[];
  title: string;
  summary: string;
  savedAt: string;
}

export function wikiDraftKey(account: string, path: string): string | undefined {
  return account ? `anissia:wiki:draft:1:${account}:${path}` : undefined;
}

export function readWikiDraft(key?: string): WikiDraft | undefined {
  if (!key) return;
  try {
    const raw = sessionStorage.getItem(key);
    if (!raw || raw.length > 2_000_000) return;
    const draft = JSON.parse(raw) as WikiDraft;
    if (draft.version !== 1 || draft.body?.schemaVersion !== 1 || !Array.isArray(draft.body.blocks) ||
        draft.baseline?.body?.schemaVersion !== 1 || !Array.isArray(draft.baseline.body.blocks) ||
        !Number.isSafeInteger(draft.baseline.revision) || !Array.isArray(draft.templates) ||
        typeof draft.title !== 'string' || typeof draft.summary !== 'string' || typeof draft.savedAt !== 'string') return;
    return draft;
  } catch { return; }
}

export function writeWikiDraft(key: string | undefined, draft: WikiDraft): boolean {
  if (!key) return false;
  try {
    const raw = JSON.stringify(draft);
    if (raw.length > 2_000_000) return false;
    sessionStorage.setItem(key, raw);
    return true;
  } catch { return false; }
}

export function removeWikiDraft(key?: string): void {
  if (!key) return;
  try { sessionStorage.removeItem(key); } catch { /* Browser storage may be unavailable. */ }
}
