import type {Directive} from "vue";

interface ChoiceState {
  mutation: MutationObserver;
  resize: ResizeObserver;
  frame: number;
  readyTimer: ReturnType<typeof setTimeout> | null;
}

const states = new WeakMap<HTMLElement, ChoiceState>();

function update(el: HTMLElement) {
  const state = states.get(el);
  if (!state) return;
  const on = el.querySelector<HTMLElement>(':scope > .is-on');
  if (!on) {
    if (el.classList.contains('has-choice')) el.classList.remove('has-choice');
    return;
  }
  const box = el.getBoundingClientRect();
  const rect = on.getBoundingClientRect();
  const values = {
    '--choice-x': `${rect.left - box.left - el.clientLeft}px`,
    '--choice-y': `${rect.top - box.top - el.clientTop}px`,
    '--choice-w': `${rect.width}px`,
    '--choice-h': `${rect.height}px`,
  };
  for (const [name, value] of Object.entries(values)) {
    if (el.style.getPropertyValue(name) !== value) el.style.setProperty(name, value);
  }
  if (!el.classList.contains('has-choice')) el.classList.add('has-choice');
  if (!('choiceReady' in el.dataset) && state.readyTimer === null) {
    state.readyTimer = setTimeout(() => {
      state.readyTimer = null;
      el.dataset.choiceReady = '';
    }, 60);
  }
}

function scheduleUpdate(el: HTMLElement) {
  const state = states.get(el);
  if (!state || state.frame) return;
  state.frame = requestAnimationFrame(() => {
    state.frame = 0;
    update(el);
  });
}

/** 한 칸만 고르는 단추 묶음(`.is-on`)에 미끄러지는 선택 판을 깐다. */
export const vChoice: Directive<HTMLElement> = {
  mounted(el) {
    el.classList.add('as-choice');
    const mutation = new MutationObserver(() => scheduleUpdate(el));
    mutation.observe(el, {subtree: true, childList: true, attributes: true, attributeFilter: ['class']});
    const resize = new ResizeObserver(() => scheduleUpdate(el));
    resize.observe(el);
    states.set(el, {mutation, resize, frame: 0, readyTimer: null});
    update(el);
    document.fonts?.ready.then(() => scheduleUpdate(el));
  },
  updated(el) {
    if (!el.classList.contains('as-choice')) el.classList.add('as-choice');
    scheduleUpdate(el);
  },
  beforeUnmount(el) {
    const state = states.get(el);
    state?.mutation.disconnect();
    state?.resize.disconnect();
    if (state?.frame) cancelAnimationFrame(state.frame);
    if (state?.readyTimer !== null && state?.readyTimer !== undefined) clearTimeout(state.readyTimer);
    states.delete(el);
  },
};
