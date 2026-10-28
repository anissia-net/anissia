<template>
  <div>
    <div class="flex flex-wrap items-center gap-4">
      <img v-if="thumbnail" :src="thumbnail" alt="현재 썸네일" width="64" height="64" class="w-16 h-16 rounded-ctl object-cover"/>
      <span v-else class="as-meta">썸네일이 없습니다.</span>
      <button type="button" class="as-input-btn py-2" @click="open">위키에서 이미지 선택</button>
    </div>
    <p class="as-meta mt-2">썸네일이 없으면 위키 저장 시 본문의 첫 업로드 이미지가 자동으로 지정됩니다.</p>
    <Teleport to="body">
      <dialog ref="dialog" class="as-wiki-dialog as-box" :aria-labelledby="headingId" @cancel="cancel" @close="closed">
        <div class="flex justify-between items-center gap-3 mb-3">
          <h2 :id="headingId" class="as-section-title">위키 이미지에서 썸네일 선택</h2>
          <button type="button" class="as-input-btn px-3 py-1" :disabled="saving" @click="close">닫기</button>
        </div>
        <p class="as-desc mb-4">이 작품의 위키와 포함된 템플릿에 있는 업로드 이미지 중에서 선택하세요. 긴 쪽의 양 끝을 잘라 가운데를 64 × 64로 표시합니다.</p>
        <p v-if="loading" class="as-empty" role="status">위키 이미지를 불러오는 중입니다.</p>
        <div v-else-if="loadError" class="space-y-2 mb-4">
          <p class="text-sm text-red-600" role="alert">{{loadError}}</p>
          <button type="button" class="as-link" @click="load">다시 시도</button>
        </div>
        <template v-else>
          <div v-if="currentThumbnail" class="flex items-center gap-3 mb-4 border-b border-line pb-4">
            <img :src="currentThumbnail" alt="현재 썸네일" width="64" height="64" class="w-16 h-16 rounded-ctl object-cover"/>
            <div><p class="text-sm">현재 썸네일</p><p v-if="!currentInImages" class="as-meta mt-1">현재 위키에서 제거된 이미지입니다. 새 이미지를 선택할 수 있습니다.</p></div>
          </div>
          <fieldset :disabled="saving" class="space-y-3">
            <legend class="sr-only">썸네일 이미지</legend>
            <label class="thumbnail-choice flex items-center gap-3 p-3" :class="{'is-selected': selected === null}">
              <input v-model="selected" type="radio" :name="headingId" :value="null"/>
              <span class="text-sm">썸네일 없음</span>
            </label>
            <div v-if="images.length" class="grid grid-cols-1 min-[420px]:grid-cols-2 gap-3">
              <label v-for="(image, index) in images" :key="image.id" class="thumbnail-choice flex items-center gap-3 p-3" :class="{'is-selected': selected === image.id}">
                <input v-model="selected" type="radio" :name="headingId" :value="image.id" :aria-label="image.description || `위키 이미지 ${index + 1}`"/>
                <img :src="image.thumbnailUrl!" alt="" width="64" height="64" loading="lazy" decoding="async" class="w-16 h-16 shrink-0 rounded-ctl object-cover"/>
                <span class="min-w-0 text-sm break-words">{{image.description || `위키 이미지 ${index + 1}`}}</span>
              </label>
            </div>
            <div v-else class="as-empty">
              <p>이 위키에 선택할 수 있는 이미지가 없습니다.</p>
              <p class="as-meta mt-2">위키 본문에 업로드한 이미지를 넣고 저장해 주세요.</p>
              <router-link :to="`/anime/${animeNo}`" class="as-link inline-block mt-3" @click="close">작품 위키로 이동</router-link>
            </div>
          </fieldset>
          <p class="as-meta mt-4">썸네일을 해제하면 다음 위키 저장 시 첫 이미지가 다시 지정됩니다.</p>
          <p v-if="saveError" class="text-sm text-red-600 mt-3" role="alert">{{saveError}}</p>
          <div class="flex justify-end gap-2 mt-5">
            <button type="button" class="as-input-btn py-2" :disabled="saving" @click="close">취소</button>
            <button type="button" class="as-btn-primary py-2" :disabled="saving || !changed" @click="save">{{saving ? '저장 중…' : '썸네일 저장'}}</button>
          </div>
        </template>
      </dialog>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import {computed, nextTick, onBeforeUnmount, ref, useId} from 'vue';
import toast from '../../common/toast';
import {wikiRemote, wikiErrorMessage, type AnimeWikiImages, type WikiImage} from '../wiki/wikiRemote';
import '../wiki/wiki.pcss';

const props = defineProps<{animeNo: number; thumbnail: string | null}>();
const emit = defineEmits<{changed: [thumbnail: string | null]}>();
const headingId = useId();
const dialog = ref<HTMLDialogElement>();
const images = ref<WikiImage[]>([]);
const currentThumbnail = ref<string | null>(null);
const selected = ref<string | null>();
const loading = ref(false), saving = ref(false), loadError = ref(''), saveError = ref('');
const currentInImages = computed(() => images.value.some(image => image.thumbnailUrl === currentThumbnail.value));
const changed = computed(() => selected.value !== undefined && (selected.value === null
  ? currentThumbnail.value !== null
  : images.value.find(image => image.id === selected.value)?.thumbnailUrl !== currentThumbnail.value));
let active = true, generation = 0;
let controller: AbortController | undefined;

function apply(state: AnimeWikiImages) {
  images.value = state.images.filter(image => image.status === 'READY' && image.thumbnailUrl);
  currentThumbnail.value = state.thumbnail;
  selected.value = state.thumbnail ? images.value.find(image => image.thumbnailUrl === state.thumbnail)?.id : null;
  emit('changed', state.thumbnail);
}

async function open() {
  await nextTick();
  dialog.value?.showModal();
  void load();
}

async function load() {
  const expected = ++generation;
  controller?.abort();
  controller = new AbortController();
  loading.value = true; loadError.value = ''; saveError.value = '';
  try {
    const state = await wikiRemote.animeImages(props.animeNo, controller.signal);
    if (active && expected === generation) apply(state);
  } catch (error) {
    if (active && expected === generation) loadError.value = wikiErrorMessage(error);
  } finally { if (active && expected === generation) loading.value = false; }
}

async function save() {
  if (saving.value || !changed.value || selected.value === undefined) return;
  const expected = ++generation;
  saving.value = true; saveError.value = '';
  try {
    const state = await wikiRemote.setAnimeThumbnail(props.animeNo, selected.value);
    if (!active || expected !== generation) return;
    apply(state);
    toast.success(state.thumbnail ? '썸네일을 저장했습니다.' : '썸네일을 해제했습니다.');
    dialog.value?.close();
  } catch (error) {
    if (active && expected === generation) saveError.value = wikiErrorMessage(error);
  } finally { if (active) saving.value = false; }
}

function close() { if (!saving.value) dialog.value?.close(); }
function cancel(event: Event) { if (saving.value) event.preventDefault(); }
function closed() { ++generation; controller?.abort(); }
onBeforeUnmount(() => { active = false; ++generation; controller?.abort(); dialog.value?.close(); });
</script>

<style scoped>
.as-wiki-dialog { background: var(--as-canvas-2); }
.thumbnail-choice {
  border: 1px solid var(--as-line);
  border-radius: var(--as-radius-ctl, .25rem);
  cursor: pointer;
}
.thumbnail-choice:hover, .thumbnail-choice:focus-within { border-color: var(--as-brand); }
.thumbnail-choice.is-selected { border-color: var(--as-brand); background: var(--as-brand-soft); }
.thumbnail-choice input { accent-color: var(--as-brand); }
fieldset:disabled .thumbnail-choice { cursor: wait; opacity: .65; }
</style>
