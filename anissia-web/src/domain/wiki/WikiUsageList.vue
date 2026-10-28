<template>
  <div class="mt-5 border-t border-line pt-4">
    <h3 class="font-semibold mb-3">사용처<span v-if="total" class="as-meta ml-2">{{total}}개</span></h3>
    <ul class="space-y-2 text-sm"><li v-for="item in items" :key="`${item.kind}:${item.targetNo}`"><router-link :to="wikiLocation(item)" class="as-link">{{item.title}}</router-link><span class="as-meta ml-2">{{item.kind === 'anime' ? '애니 위키' : '템플릿'}}</span></li></ul>
    <p v-if="loading" class="as-desc" role="status">사용처를 불러오는 중입니다.</p>
    <p v-else-if="!items.length && !error" class="as-desc">현재 사용하는 문서가 없습니다.</p>
    <p v-if="error" class="text-sm text-red-600" role="alert">{{error}} <button type="button" class="as-link" @click="load(false)">다시 시도</button></p>
    <button v-if="items.length < total && !loading" type="button" class="as-input-btn py-1.5 mt-3" @click="load(true)">사용처 더 보기</button>
  </div>
</template>
<script setup lang="ts">
import {onBeforeUnmount, ref, watch} from 'vue';
import {wikiErrorMessage, wikiLocation, wikiRemote, type WikiPage, type WikiUsage} from './wikiRemote';
const props = defineProps<{endpoint: string}>();
const items = ref<WikiUsage[]>([]), loading = ref(false), error = ref(''), total = ref(0);
let page = 0, generation = 0, active = true;
async function load(append: boolean) {
  if (append && loading.value) return;
  const expected = ++generation, next = append ? page + 1 : 0;
  loading.value = true; error.value = '';
  if (!append) { items.value = []; total.value = 0; }
  try {
    const value = await wikiRemote.request<WikiPage<WikiUsage>>(`${props.endpoint}?page=${next}`);
    if (active && expected === generation) { items.value = append ? [...items.value, ...value.content] : value.content; total.value = value.totalElements; page = next; }
  } catch (value) { if (active && expected === generation) error.value = wikiErrorMessage(value); }
  finally { if (expected === generation) loading.value = false; }
}
watch(() => props.endpoint, () => load(false), {immediate: true});
onBeforeUnmount(() => { active = false; ++generation; });
</script>
