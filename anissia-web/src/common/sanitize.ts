import {createNabiWith} from "nabi-note";
import type {Nabi} from "nabi-note";
import {noteWings} from "./note";

/**
 * 저장값을 나비의 어휘로 다시 지어서 거른다.
 *
 * 나비는 들어온 HTML 을 고쳐 쓰는 게 아니라 허용 어휘로 **다시 짓는다** — 목록에 없는 태그·속성은
 * 통과하지 못하는 게 아니라 애초에 지어지지 않는다. 그래서 `setHtml()` 로 넣고 `getHtml()` 로
 * 받으면 그것이 곧 거른 값이고, 편집기가 내보내는 것과 한 글자까지 같다.
 * 별도 sanitizer 를 두면 두 허용 목록이 갈라져 언젠가 어긋나므로 그렇게 하지 않는다.
 *
 * 저장값은 나비가 만든 것이라는 보장이 없으므로 (DB 행은 직접 고칠 수 있다) 그릴 때마다 거른다.
 */
let filter: Nabi | null = null;

export function sanitizeNote(dirty: string): string {
  const nabi = filter ??= createNabiWith(noteWings()).nabi;
  /*
   * 화면에 붙지 않은 나비다 — `getHtml()` 은 살아 있는 DOM 이 아니라 제 문서 나무에서 그리므로
   * `mountSurface` 없이도 돈다. 문서 하나를 갈아 끼우며 쓰는 것이 `setHtml` 의 쓰임 그대로라
   * 글마다 새로 세우지 않고 한 벌을 나눠 쓴다.
   */
  return nabi.setHtml(dirty ?? '') ? nabi.getHtml() : '';
}
