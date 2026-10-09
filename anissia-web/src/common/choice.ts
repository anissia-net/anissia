import type {Directive} from "vue";

interface ChoiceState {
  mutation: MutationObserver;
  resize: ResizeObserver;
}

const states = new WeakMap<HTMLElement, ChoiceState>();

function update(el: HTMLElement) {
  const on = el.querySelector<HTMLElement>(':scope > .is-on');
  if (!on) {
    if (el.classList.contains('has-choice')) el.classList.remove('has-choice');
    return;
  }
  const box = el.getBoundingClientRect();
  const rect = on.getBoundingClientRect();
  el.style.setProperty('--choice-x', `${rect.left - box.left - el.clientLeft}px`);
  el.style.setProperty('--choice-y', `${rect.top - box.top - el.clientTop}px`);
  el.style.setProperty('--choice-w', `${rect.width}px`);
  el.style.setProperty('--choice-h', `${rect.height}px`);
  if (!el.classList.contains('has-choice')) el.classList.add('has-choice');
  if (!('choiceReady' in el.dataset)) {
    setTimeout(() => el.dataset.choiceReady = '', 60);
  }
}

/** 한 칸만 고르는 단추 묶음(`.is-on`)에 미끄러지는 선택 판을 깐다. */
export const vChoice: Directive<HTMLElement> = {
  mounted(el) {
    el.classList.add('as-choice');
    const mutation = new MutationObserver(() => update(el));
    mutation.observe(el, {subtree: true, childList: true, attributes: true, attributeFilter: ['class']});
    const resize = new ResizeObserver(() => update(el));
    resize.observe(el);
    states.set(el, {mutation, resize});
    update(el);
    document.fonts?.ready.then(() => update(el));
  },
  updated(el) {
    if (!el.classList.contains('as-choice')) el.classList.add('as-choice');
    update(el);
  },
  beforeUnmount(el) {
    const state = states.get(el);
    state?.mutation.disconnect();
    state?.resize.disconnect();
    states.delete(el);
  },
};
