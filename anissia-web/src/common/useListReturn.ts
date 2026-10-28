import {useRouter} from 'vue-router';

export function useListReturn(fallback: string, paths = [fallback]) {
  const router = useRouter();
  const back = router.options.history.state.back;
  const fromList = typeof back === 'string' && paths.includes(back.split(/[?#]/)[0]);
  const listPath = fromList ? back as string : fallback;

  function returnToList() {
    if (fromList && router.options.history.state.back === back) router.back();
    else void router.push(listPath);
  }

  return {returnToList};
}
