import {isElement, type AttachHost, type Command, type ElementNode, type NabiDoc, type NabiNode} from 'nabi-note';

function findPath(nodes: readonly NabiNode[], key: string, prefix: number[] = []): number[] | undefined {
  for (const [index, node] of nodes.entries()) {
    if (!isElement(node)) continue;
    const path = [...prefix, index];
    if (node._id === key) return path;
    const found = findPath(node.ch, key, path);
    if (found) return found;
  }
}
function at(doc: NabiDoc, path: readonly number[]): ElementNode | undefined {
  let nodes: readonly NabiNode[] = doc, node: NabiNode | undefined;
  for (const index of path) { node = nodes[index]; if (!isElement(node)) return; nodes = node.ch; }
  return isElement(node) ? node : undefined;
}
function replace(nodes: readonly NabiNode[], path: readonly number[], values: readonly NabiNode[]): NabiNode[] {
  const index = path[0]!, copy = [...nodes];
  if (path.length === 1) copy.splice(index, 1, ...values);
  else { const node = copy[index]; if (isElement(node)) copy[index] = {...node, ch: replace(node.ch, path.slice(1), values)}; }
  return copy;
}

/** Keep template references and node keys intact, and let Nabi record one undoable command. */
export const moveWikiTemplate: Command = (doc, _selection, args, env) => {
  const source = findPath(doc, String(args.sourceKey)), target = findPath(doc, String(args.targetKey));
  if (!source || !target || source.every((value, i) => target[i] === value)) return null;
  const block = at(doc, source), destination = at(doc, target);
  if (block?.w !== 'p' || block.ch.length !== 1 || !isElement(block.ch[0]) || block.ch[0].w !== 'exTemplateInclude' || destination?.w !== 'p') return null;
  const parent = source.length > 1 ? at(doc, source.slice(0, -1)) : undefined;
  const targetParent = target.length > 1 ? at(doc, target.slice(0, -1)) : undefined;
  if ((parent && !env.blockHolders.has(parent.w)) || (targetParent && !env.blockHolders.has(targetParent.w))) return null;
  const after = args.after === true;
  if (source.length === target.length && source.slice(0, -1).every((value, i) => target[i] === value)
    && source.at(-1) === target.at(-1)! + (after ? 1 : -1)) return null;
  const removed = replace(doc, source, parent?.ch.length === 1 ? [{w: 'p', ch: []}] : []) as ElementNode[];
  const shiftedTarget = findPath(removed, String(args.targetKey));
  if (!shiftedTarget) return null;
  const result = replace(removed, shiftedTarget, after ? [destination, block] : [block, destination]) as ElementNode[];
  const path = [...shiftedTarget];
  if (after) path[path.length - 1]! += 1;
  return {doc: result, selection: {anchor: {path, offset: 0}, focus: {path, offset: 1}}};
};

export function mountWikiTemplateMove(host: AttachHost) {
  const {root, nabi} = host, owner = root.ownerDocument, view = owner.defaultView!;
  const layer = root.closest('dialog') ?? owner.body;
  const handle = owner.createElement('button');
  handle.type = 'button'; handle.className = 'nabi-move-handle as-wiki-template-move'; handle.hidden = true;
  handle.dataset.nabiMove = 'exTemplateInclude';
  handle.title = '템플릿 이동 (Alt+↑ / Alt+↓)'; handle.setAttribute('aria-label', handle.title);
  handle.setAttribute('aria-keyshortcuts', 'Alt+ArrowUp Alt+ArrowDown');
  handle.innerHTML = '<svg viewBox="0 0 24 16" width="24" height="16" aria-hidden="true"><g fill="currentColor"><circle cx="6" cy="5" r="1.5"/><circle cx="12" cy="5" r="1.5"/><circle cx="18" cy="5" r="1.5"/><circle cx="6" cy="11" r="1.5"/><circle cx="12" cy="11" r="1.5"/><circle cx="18" cy="11" r="1.5"/></g></svg>';
  layer.append(handle);
  let key = '', frame = 0, disposed = false;
  let gesture: {pointer: number; x: number; y: number; startX: number; startY: number; doc: NabiDoc; moving: boolean} | undefined;
  let preview: HTMLElement | undefined, marker: HTMLElement | undefined;
  const element = () => key ? root.querySelector<HTMLElement>(`[data-key="${view.CSS.escape(key)}"]`) : null;
  const canEdit = () => root.isConnected && root.isContentEditable;
  const position = () => {
    frame = 0;
    if (disposed || gesture) return;
    const block = element(), template = block?.querySelector<HTMLElement>('[data-template-editor]');
    handle.hidden = !canEdit() || !template?.getClientRects().length;
    if (handle.hidden || !template) return;
    const rect = template.getBoundingClientRect(), bounds = root.getBoundingClientRect();
    handle.hidden = rect.top < Math.max(0, bounds.top) || rect.top >= Math.min(view.innerHeight, bounds.bottom);
    handle.style.left = `${Math.max(bounds.left, Math.min(rect.left + rect.width / 2 - 22, bounds.right - 44))}px`;
    handle.style.top = `${Math.max(0, rect.top - (handle.offsetHeight || 28) + 4)}px`;
  };
  const schedule = () => { if (!disposed && !frame && !gesture) frame = view.requestAnimationFrame(position); };
  const cancel = () => {
    const current = gesture; gesture = undefined;
    if (frame) view.cancelAnimationFrame(frame); frame = 0;
    if (current && handle.hasPointerCapture(current.pointer)) handle.releasePointerCapture(current.pointer);
    preview?.remove(); preview = undefined; marker?.remove(); marker = undefined;
    handle.classList.remove('is-moving'); handle.style.removeProperty('visibility'); schedule();
  };
  const targetAt = (x: number, y: number) => {
    let candidate = owner.elementFromPoint(x, y)?.closest<HTMLElement>('[data-key]');
    while (candidate && root.contains(candidate)) {
      const targetKey = candidate.dataset.key!, rect = candidate.getBoundingClientRect(), after = y >= rect.top + rect.height / 2;
      if (moveWikiTemplate(host.doc(), nabi.getSelection(), {sourceKey: key, targetKey, after}, host.env)) return {targetKey, after, rect};
      candidate = candidate.parentElement?.closest<HTMLElement>('[data-key]');
    }
  };
  const drawTarget = () => {
    if (!gesture) return;
    const target = targetAt(gesture.x, gesture.y);
    if (!marker) { marker = owner.createElement('div'); marker.className = 'as-wiki-template-drop'; layer.append(marker); }
    marker.hidden = !target;
    if (target) marker.style.cssText = `left:${target.rect.left}px;top:${(target.after ? target.rect.bottom : target.rect.top) - 1}px;width:${target.rect.width}px`;
  };
  const scroll = () => {
    frame = 0;
    if (!gesture?.moving) return;
    if (!canEdit() || gesture.doc !== host.doc()) { cancel(); return; }
    let scrolled = false;
    for (let container: HTMLElement | null = root; container; container = container.parentElement) {
      if (container.scrollHeight <= container.clientHeight || !/(auto|scroll)/.test(view.getComputedStyle(container).overflowY)) continue;
      const rect = container.getBoundingClientRect(), previous = container.scrollTop;
      container.scrollTop += gesture.y < Math.max(0, rect.top) + 48 ? -12 : gesture.y > Math.min(view.innerHeight, rect.bottom) - 48 ? 12 : 0;
      if (container.scrollTop !== previous) { scrolled = true; break; }
    }
    if (!scrolled) view.scrollBy(0, gesture.y < 48 ? -12 : gesture.y > view.innerHeight - 48 ? 12 : 0);
    drawTarget(); frame = view.requestAnimationFrame(scroll);
  };
  const down = (event: PointerEvent) => {
    if (event.button !== 0 || !event.isPrimary || !canEdit() || !element()) return;
    event.preventDefault();
    cancel();
    gesture = {pointer: event.pointerId, x: event.clientX, y: event.clientY, startX: event.clientX, startY: event.clientY, doc: host.doc(), moving: false};
    handle.setPointerCapture(event.pointerId);
  };
  const move = (event: PointerEvent) => {
    if (!gesture || gesture.pointer !== event.pointerId) return;
    event.preventDefault(); gesture.x = event.clientX; gesture.y = event.clientY;
    if (!gesture.moving && Math.hypot(gesture.x - gesture.startX, gesture.y - gesture.startY) >= 6) {
      const block = element()!, rect = block.getBoundingClientRect();
      gesture.moving = true;
      preview = owner.createElement('div'); preview.className = 'nabi-content nabi-move-preview'; preview.inert = true;
      preview.setAttribute('aria-hidden', 'true'); preview.append(block.cloneNode(true));
      for (const child of preview.querySelectorAll('[data-key], [id], [contenteditable]')) { child.removeAttribute('data-key'); child.removeAttribute('id'); child.removeAttribute('contenteditable'); }
      preview.style.cssText = `left:${rect.left}px;top:${rect.top}px;width:${rect.width}px;height:${rect.height}px`;
      layer.append(preview); handle.style.visibility = 'hidden'; handle.classList.add('is-moving');
      if (frame) view.cancelAnimationFrame(frame); frame = view.requestAnimationFrame(scroll);
    }
    if (preview) preview.style.transform = `translate(${gesture.x - gesture.startX}px, ${gesture.y - gesture.startY}px)`;
    if (gesture.moving) drawTarget();
  };
  const up = (event: PointerEvent) => {
    if (!gesture || gesture.pointer !== event.pointerId) return;
    const target = gesture.moving && gesture.doc === host.doc() && canEdit() ? targetAt(event.clientX, event.clientY) : undefined;
    cancel();
    if (target) nabi.applyCommand('moveWikiTemplate', {sourceKey: key, targetKey: target.targetKey, after: target.after}, 'pointer');
    handle.focus({preventScroll: true});
  };
  const keyboard = (event: KeyboardEvent) => {
    if (event.key === 'Escape' && gesture) { event.preventDefault(); event.stopPropagation(); cancel(); return; }
    if (event.target !== handle || !event.altKey || !['ArrowUp', 'ArrowDown'].includes(event.key)) return;
    event.preventDefault();
    const path = host.pathOfKey(key);
    if (!path) return;
    const sibling = [...path]; sibling[sibling.length - 1]! += event.key === 'ArrowDown' ? 1 : -1;
    const targetKey = at(host.doc(), sibling)?._id;
    if (targetKey) nabi.applyCommand('moveWikiTemplate', {sourceKey: key, targetKey, after: event.key === 'ArrowDown'}, 'keyboard');
    element()?.scrollIntoView({block: 'nearest'}); schedule();
  };
  const outside = (event: PointerEvent) => {
    if (gesture && event.pointerId !== gesture.pointer) cancel();
    if (event.target instanceof view.Node && !handle.contains(event.target) && !element()?.contains(event.target)
      && !(event.target instanceof view.Element && event.target.closest('.as-wiki-template-menu'))) { key = ''; schedule(); }
  };
  const prevent = (event: Event) => event.preventDefault();
  handle.addEventListener('pointerdown', down); handle.addEventListener('pointermove', move); handle.addEventListener('pointerup', up);
  handle.addEventListener('pointercancel', cancel); handle.addEventListener('lostpointercapture', cancel); handle.addEventListener('click', prevent);
  owner.addEventListener('keydown', keyboard, true); owner.addEventListener('pointerdown', outside, true);
  owner.addEventListener('scroll', schedule, true); view.addEventListener('resize', schedule); view.addEventListener('blur', cancel);
  const resize = new view.ResizeObserver(schedule); resize.observe(root);
  const changed = nabi.onChange(change => { if (change.doc) cancel(); schedule(); });
  return {
    select(template: HTMLElement) {
      key = template.closest<HTMLElement>('[data-nabi-p]')?.dataset.key ?? '';
      const path = host.pathOfKey(key);
      if (path) nabi.select({anchor: {path, offset: 0}, focus: {path, offset: 1}});
      schedule();
    },
    unmount() {
      disposed = true; cancel(); changed(); resize.disconnect(); handle.remove();
      owner.removeEventListener('keydown', keyboard, true); owner.removeEventListener('pointerdown', outside, true);
      owner.removeEventListener('scroll', schedule, true); view.removeEventListener('resize', schedule); view.removeEventListener('blur', cancel);
    },
  };
}
