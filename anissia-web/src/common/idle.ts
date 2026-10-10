/** 첫 화면을 그린 뒤 한가할 때 부른다. */
export function whenIdle(fn: () => void) {
  if ('requestIdleCallback' in window) {
    requestIdleCallback(() => fn(), {timeout: 3000});
  } else {
    setTimeout(fn, 1500);
  }
}
