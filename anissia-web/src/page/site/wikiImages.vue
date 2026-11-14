<template>
  <div class="as-page">
    <p v-if="loading" class="as-empty" role="status">이미지가 속한 문서로 이동하고 있습니다.</p>
    <div v-else class="as-box p-5 space-y-3">
      <p class="as-desc" :role="error ? 'alert' : undefined">{{error || '이미지는 각 위키 문서나 템플릿의 편집 화면에서 관리합니다.'}}</p>
      <button v-if="error && imageId" type="button" class="as-link text-sm" @click="openDocument">다시 시도</button>
      <div class="flex gap-4 text-sm"><router-link to="/anime" class="as-link">작품 찾기</router-link><router-link to="/wiki/templates" class="as-link">템플릿 찾기</router-link></div>
    </div>
  </div>
</template>

<script setup lang="ts">
import {computed, onBeforeUnmount, ref, watch} from 'vue';
import {useRoute, useRouter} from 'vue-router';
import {wikiRemote, wikiErrorMessage} from '../../domain/wiki/wikiRemote';
import {wikiImageLocation} from '../../domain/wiki/wikiImageOwner';
const route = useRoute(), router = useRouter();
const imageId = computed(() => typeof route.query.imageId === 'string' && /^[a-f0-9]{32}$/.test(route.query.imageId) ? route.query.imageId : undefined);
const loading = ref(false), error = ref('');
let active = true, generation = 0;
async function openDocument() {
  const expected = ++generation;
  loading.value = !!imageId.value; error.value = '';
  if (!imageId.value) return;
  try {
    const image = await wikiRemote.image(imageId.value);
    if (!active || expected !== generation) return;
    const location = wikiImageLocation(image);
    if (!location) { error.value = '이 이미지에 연결된 문서가 없습니다.'; return; }
    await router.replace(location);
  } catch (cause) { if (active && expected === generation) error.value = wikiErrorMessage(cause); }
  finally { if (expected === generation) loading.value = false; }
}
watch(imageId, openDocument, {immediate: true});
onBeforeUnmount(() => { active = false; ++generation; });
</script>
