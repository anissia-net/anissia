import {mountOutline, type OutlineOptions} from "nabi-note/viewer";

export function mountNoteOutline(
  content: HTMLElement,
  controls: HTMLElement,
  options: Pick<OutlineOptions, 'container' | 'scrollContainer' | 'offset'> = {},
) {
  const button = content.ownerDocument.createElement('button');
  button.type = 'button';
  button.className = 'as-note-outline-trigger';
  button.textContent = '목차';
  button.hidden = true;
  controls.append(button);

  const outline = mountOutline(content, {
    locale: 'ko',
    container: content.ownerDocument.body,
    placement: 'beside',
    ...options,
    trigger: button,
    onAvailabilityChange: available => {
      button.hidden = !available;
      controls.hidden = !available;
    },
  });
  const toggle = () => outline.toggle();
  button.addEventListener('click', toggle);

  return {
    unmount() {
      button.removeEventListener('click', toggle);
      outline.unmount();
      button.remove();
      controls.hidden = true;
    },
  };
}
