import {mountOutline} from "nabi-note/viewer";
import {iconSvg} from "./icon/icons";

export function mountNoteOutline(
  content: HTMLElement,
  controls: HTMLElement,
) {
  const button = content.ownerDocument.createElement('button');
  button.type = 'button';
  button.className = 'as-note-outline-trigger';
  button.setAttribute('aria-label', '목차');
  button.title = '목차';
  const icon = content.ownerDocument.createElement('span');
  icon.className = 'as-icon';
  icon.setAttribute('aria-hidden', 'true');
  icon.innerHTML = iconSvg('bars');
  button.append(icon);
  button.hidden = true;
  controls.append(button);

  const outline = mountOutline(content, {
    locale: 'ko',
    container: content.ownerDocument.body,
    placement: 'above',
    trigger: button,
    onAvailabilityChange: available => {
      button.hidden = !available;
      controls.hidden = !available;
    },
  });
  outline.panel.classList.add('as-note-outline-floating-panel');
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
