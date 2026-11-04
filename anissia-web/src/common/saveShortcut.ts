const scopeSelector = '[data-save-scope]';
const boundarySelector = `${scopeSelector}, dialog, [role="dialog"]`;
const surfaceScopes = new WeakMap<Element, Element>();

// Nabi는 전체화면에서 편집기를 document.body로 옮기므로 원래 저장 영역을 기억한다.
export const vSaveSurface = {
  mounted(surface: HTMLElement) {
    const scope = surface.closest(scopeSelector);
    if (scope) surfaceScopes.set(surface, scope);
  },
};

function findScope(event: KeyboardEvent) {
  for (const element of event.composedPath()) {
    if (!(element instanceof Element)) continue;
    if (element.matches(boundarySelector)) return element;
    const owner = surfaceScopes.get(element);
    if (owner) return owner;
  }
  // 포커스가 본문으로 돌아왔을 때만 페이지의 단일 편집기로 보낸다.
  if (event.target !== document.body && event.target !== document.documentElement) return;
  if (document.querySelector('dialog[open], [aria-modal="true"]')) return;
  const editors = document.querySelectorAll('[data-save-scope="page"]');
  if (editors.length === 1) return editors[0];
}

export function onSaveShortcut(event: KeyboardEvent) {
  if (event.defaultPrevented || event.shiftKey || event.altKey) return;
  const mac = /Mac|iPhone|iPad|iPod/.test(navigator.platform);
  if (mac ? !event.metaKey || event.ctrlKey : !event.ctrlKey || event.metaKey) return;
  // 한글 입력 상태에서도 S 자리를 인식하되, 영문 자판 배열은 실제 글자를 따른다.
  const key = event.key.toLowerCase();
  if (key !== 's' && !(event.code === 'KeyS' && /^[^\x00-\x7f]$/u.test(key))) return;

  const scope = findScope(event);
  if (!scope?.matches(scopeSelector)) return;

  const button = Array.from(scope.querySelectorAll<HTMLElement>('[data-save-button]'))
    .find(button => button.closest(boundarySelector) === scope);
  if (!button) return;
  event.preventDefault();
  if (!event.repeat && !event.isComposing && !button.matches(':disabled') && !button.closest('[inert]')) button.click();
}
