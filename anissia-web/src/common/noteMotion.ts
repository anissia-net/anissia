/** Animate the actual block positions after Nabi folds unchanged diff sections.
 * The capture phase records the old layout; the microtask sees Nabi's final layout. */
export function installNoteMotion(owner: Document = document) {
  const view = owner.defaultView!;
  const animations = new Map<HTMLElement, Animation>();
  const click = (event: MouseEvent) => {
    const button = event.target instanceof view.Element
      ? event.target.closest<HTMLButtonElement>('.nabi-diff-btn[aria-pressed]') : null;
    const root = button?.closest<HTMLElement>('.nabi-diff');
    if (!root || button.disabled || view.matchMedia('(prefers-reduced-motion: reduce)').matches) return;
    const pressed = button.getAttribute('aria-pressed');
    const blocks = [...root.querySelectorAll<HTMLElement>('.nabi-diff-block')];
    const before = new Map(blocks.map(block => [block, block.getClientRects().length ? block.getBoundingClientRect().top : null]));
    for (const block of blocks) { animations.get(block)?.cancel(); animations.delete(block); }
    queueMicrotask(() => {
      if (!root.isConnected || pressed === button.getAttribute('aria-pressed')) return;
      for (const block of blocks) {
        if (!block.getClientRects().length) continue;
        const top = before.get(block), offset = top === null ? -12 : top - block.getBoundingClientRect().top;
        if (!offset) continue;
        const animation = block.animate([
          {transform: `translateY(${offset}px)`, opacity: top === null ? .25 : 1},
          {transform: 'translateY(0)', opacity: 1},
        ], {duration: 240, easing: 'cubic-bezier(.2,.7,.2,1)'});
        animations.set(block, animation);
        animation.onfinish = () => { if (animations.get(block) === animation) animations.delete(block); };
      }
    });
  };
  owner.addEventListener('click', click, true);
  return () => { owner.removeEventListener('click', click, true); for (const animation of animations.values()) animation.cancel(); animations.clear(); };
}
