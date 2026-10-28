import {wikiLocation} from './wikiLocation';

export function mountWikiTemplateMenu(surface: HTMLElement, exists: (templateNo: string) => boolean) {
  const owner = surface.ownerDocument, view = owner.defaultView!;
  let dismiss: (() => void) | undefined;
  const close = () => { dismiss?.(); dismiss = undefined; };
  const targetOf = (event: Event) => event.target instanceof view.Element
    ? event.target.closest<HTMLElement>('[data-template-editor]') : null;

  function open(target: HTMLElement) {
    close();
    const templateNo = target.dataset.templateNo;
    if (!templateNo || !exists(templateNo)) return;
    const panel = owner.createElement('div');
    panel.className = 'nabi-panel as-wiki-template-menu';
    panel.contentEditable = 'false';
    panel.setAttribute('popover', 'manual');
    panel.setAttribute('role', 'dialog');
    panel.setAttribute('aria-label', '템플릿 메뉴');
    const edit = owner.createElement('a');
    edit.href = `${wikiLocation({kind: 'template', targetNo: Number(templateNo)})}&edit=1`;
    edit.target = '_blank';
    edit.rel = 'noopener';
    edit.className = 'nabi-btn';
    edit.textContent = '템플릿 수정';
    panel.append(edit);
    (surface.closest('.nabi') ?? owner.body).append(panel);

    const position = () => {
      if (!target.isConnected) { close(); return; }
      const rect = target.getBoundingClientRect(), box = panel.getBoundingClientRect();
      const rem = parseFloat(view.getComputedStyle(owner.documentElement).fontSize) || 16;
      panel.style.left = `${Math.max(rem, Math.min(rect.right - box.width - rem * .375, view.innerWidth - box.width - rem))}px`;
      panel.style.top = `${Math.max(rem, Math.min(rect.top + rem * .375, view.innerHeight - box.height - rem))}px`;
    };
    const outside = (event: PointerEvent) => {
      if (event.target instanceof view.Node && !panel.contains(event.target) && !target.contains(event.target)) close();
    };
    const escape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') { event.preventDefault(); event.stopPropagation(); close(); target.focus(); }
    };
    dismiss = () => {
      panel.hidePopover(); panel.remove(); target.classList.remove('is-template-selected');
      owner.removeEventListener('pointerdown', outside);
      owner.removeEventListener('keydown', escape, true);
      owner.removeEventListener('scroll', position, true);
      view.removeEventListener('resize', position);
    };
    owner.addEventListener('pointerdown', outside);
    owner.addEventListener('keydown', escape, true);
    owner.addEventListener('scroll', position, true);
    view.addEventListener('resize', position);
    target.classList.add('is-template-selected');
    panel.showPopover(); position(); edit.focus();
  }

  const click = (event: MouseEvent) => {
    const target = targetOf(event);
    if (!target || !surface.contains(target)) return;
    event.preventDefault(); event.stopPropagation(); open(target);
  };
  const key = (event: KeyboardEvent) => {
    const target = targetOf(event);
    if (!target || !surface.contains(target) || !['Enter', ' '].includes(event.key)) return;
    event.preventDefault(); event.stopPropagation(); open(target);
  };
  const blockDrag = (event: Event) => {
    if (targetOf(event)) { event.preventDefault(); event.stopPropagation(); }
  };
  surface.addEventListener('click', click, true);
  surface.addEventListener('keydown', key, true);
  surface.addEventListener('dragstart', blockDrag, true);
  surface.addEventListener('selectstart', blockDrag, true);
  return () => {
    close();
    surface.removeEventListener('click', click, true);
    surface.removeEventListener('keydown', key, true);
    surface.removeEventListener('dragstart', blockDrag, true);
    surface.removeEventListener('selectstart', blockDrag, true);
  };
}
