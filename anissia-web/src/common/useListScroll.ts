import {onActivated, onDeactivated, onMounted, onUnmounted} from 'vue';
import {ScrollLoader} from 'raon';

/** Keep cached lists idle until their DOM and the router's scroll position are restored. */
export function useListScroll(loadNext: () => void) {
  let active = false;
  let next = false;
  let frame = 0;
  const loader = new ScrollLoader().onNeedNextPage(loadNext);

  function resume() {
    active = true;
    cancelAnimationFrame(frame);
    frame = requestAnimationFrame(() => {
      frame = requestAnimationFrame(() => {
        frame = requestAnimationFrame(() => {
          frame = 0;
          if (active) loader.watch(next);
        });
      });
    });
  }

  function pause() {
    active = false;
    cancelAnimationFrame(frame);
    frame = 0;
    loader.watch(false);
  }

  onMounted(resume);
  onActivated(resume);
  onDeactivated(pause);
  onUnmounted(() => { pause(); loader.destroy(); });

  return {
    watch(value: boolean) {
      next = value;
      loader.watch(active && !frame && next);
    },
  };
}
