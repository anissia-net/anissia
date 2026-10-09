import {codeAttach, codeWing, wings} from "nabi-note";
import type {Wing} from "nabi-note";

/**
 * 게시판이 쓰는 wing 한 벌.
 *
 * `allBasic()` 은 호스트가 아무것도 안 꿰어도 도는 것들이다 — 공식 29 중 26. 빠지는 셋이 마침
 * 이 게시판이 뺄 것과 같다:
 * - `upload` — 올릴 곳(`mountUpload`)이 없어서 단추만 서고 아무 일도 일어나지 않는다.
 * - `save`·`open` — 게시글은 파일이 아니라 글로 저장된다. 글 한 편을 `.nabi`·`.nhtml`·`.md` 로
 *   내리고 올리는 문(`mountFile`)은 이 게시판이 쓰는 길이 아니다.
 * 하나씩 `drop` 하던 것을 0.8 부터는 이 한 낱말로 적는다 — 나비가 "꿰어야 도는 것" 을 스스로
 * 표시하므로(`basic`), 다음에 그런 wing 이 늘어도 여기 목록을 고칠 일이 없다.
 * 단추가 없으니 `mod+s`·`mod+o` 도 서지 않는다 — 브라우저의 "페이지 저장" 이 그대로 산다.
 *
 * `code` 는 색칠을 켜서 다시 넣는다: 카탈로그의 것은 `attach` 가 비어 있어 편집 화면에서만 색이
 * 빠지는데, 읽는 쪽 `attachViewer` 는 언제나 칠하므로 그대로 두면 쓸 때와 읽을 때가 달라 보인다.
 * 저장값을 무손실로 다시 열려면 에디터·뷰어·sanitize 가 모두 같은 집합을 봐야 한다.
 */
const WINGS: readonly Wing[] = wings()
  .allBasic()
  .use('diff')
  .drop('code')
  .use({...codeWing, attach: codeAttach})
  .build();

export function noteWings(): readonly Wing[] {
  return WINGS;
}

const EMBED_SELECTOR = 'img,video,audio,iframe,embed,object,svg,canvas,hr';

export function isBlankNote(html: string): boolean {
  const body = new DOMParser().parseFromString(html ?? '', 'text/html').body;
  return !body.querySelector(EMBED_SELECTOR) && !(body.textContent ?? '').replace(/​/g, '').trim();
}
