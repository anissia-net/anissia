<template>
  <div>
    <template v-if="selectedImage">
      <button type="button" class="as-link text-sm mb-4" @click="closeDetail">이미지 목록으로</button>
      <WikiImageDetail :key="selectedImage.id" ref="detail" :image-id="selectedImage.id" :owner="owner" :used-in-draft="!!selectedImage.url && usedImageUrls?.includes(selectedImage.url)" @changed="imageChanged"/>
      <button v-if="selectable && selectedImage.status === 'READY' && selectedImage.attachmentActive !== false" type="button" class="as-btn-primary py-2 mt-4" @click="$emit('select', selectedImage)">본문에 넣기</button>
    </template>
    <template v-else>
      <div v-if="searchQuery === undefined" class="flex gap-2 mb-4" role="search">
        <input v-model="query" class="as-input-text px-3 py-2 flex-1 min-w-0" placeholder="이미지 검색" aria-label="이미지 검색" @keydown.enter.prevent.stop="search"/>
        <button type="button" class="as-input-btn px-4" @click="search">검색</button>
      </div>
      <div v-if="access?.canContribute || access?.isOperator || uploadMode" class="flex flex-wrap items-center justify-end gap-3 mb-4">
        <label v-if="access?.isOperator" class="flex items-center gap-2 text-sm mr-auto"><input v-model="showDeleted" type="checkbox"/>삭제된 이미지</label>
        <button v-if="uploadMode" type="button" class="as-btn-primary py-2" :aria-expanded="uploadOpen" @click="uploadOpen = !uploadOpen">{{uploadOpen ? '업로드 닫기' : uploadMode === 'direct' ? '이미지 업로드' : '이미지 등록 요청'}}</button>
        <button type="button" class="as-link text-sm" :disabled="loading" @click="load(false)">새로고침</button>
      </div>
      <WikiImageUpload v-if="uploadOpen && uploadMode" :key="`${owner.documentKind}:${owner.documentNo}`" selectable :owner="owner" class="mb-6" @uploaded="load(false)" @select="$emit('select', $event)"/>
      <div class="grid grid-cols-2 gap-3 cq-md:grid-cols-3 cq-xl:grid-cols-4">
        <article v-for="item in images" :key="item.id" class="as-card block min-w-0 overflow-hidden text-left">
          <button type="button" class="block w-full text-left hover:bg-canvas" :title="item.description || '위키 이미지'" @click="selectImage(item)">
            <div class="h-36 cq-md:h-44 bg-canvas p-2 flex items-center justify-center">
              <img v-if="item.url" :src="wikiImageDisplayUrl(item.url, item.revision)" alt="" loading="lazy" decoding="async" class="w-full h-full object-contain"/>
              <span v-else class="as-meta">{{item.status === 'DELETED' ? '삭제된 이미지' : '파일 처리 중'}}</span>
            </div>
            <p class="px-3 pt-3 text-sm font-medium truncate">{{item.description || '위키 이미지'}}</p>
          </button>
          <button type="button" class="as-link text-xs p-3" @click="selectedImage = item">이미지 정보</button>
        </article>
      </div>
      <p v-if="loading" role="status" class="as-empty">이미지를 불러오는 중입니다.</p>
      <p v-else-if="!images.length && !listError" class="as-empty">{{searchedQuery ? '검색결과가 없습니다.' : '등록된 이미지가 없습니다.'}}</p>
      <p v-if="listError" class="text-sm text-red-600 mt-4" role="alert">{{listError}} <button type="button" class="as-link" @click="load(retryAppend)">다시 시도</button></p>
      <WikiInfiniteScroll :enabled="hasMore && !loading && !listError" @load="load(true)"/>
    </template>
  </div>
</template>

<script setup lang="ts">
import {computed, onBeforeUnmount, ref, watch} from 'vue';
import {sessionStore} from '../session/sessionStore';
import {wikiRemote, wikiErrorMessage, type WikiAccess, type WikiImage} from './wikiRemote';
import {wikiImageDisplayUrl} from './wikiImageVersion';
import type {WikiImageOwner} from './wikiImageOwner';
import {wikiImageUploadMode} from './wikiImageAccess';
import WikiImageUpload from './WikiImageUpload.vue';
import WikiImageDetail from './WikiImageDetail.vue';
import WikiInfiniteScroll from './WikiInfiniteScroll.vue';

const props = defineProps<{selectable?: boolean; searchQuery?: string; owner: WikiImageOwner; usedImageUrls?: string[]}>();
const emit = defineEmits<{select: [image: WikiImage]}>();
const session = sessionStore(), access = ref<WikiAccess>();
const uploadMode = computed(() => wikiImageUploadMode(access.value));
const query = ref(''), submittedQuery = ref('');
const searchedQuery = computed(() => (props.searchQuery ?? submittedQuery.value).trim());
watch(searchedQuery, value => { query.value = value; }, {immediate: true});
const images = ref<WikiImage[]>([]), selectedImage = ref<WikiImage>();
const detail = ref<InstanceType<typeof WikiImageDetail>>();
const listError = ref(''), loading = ref(false), hasMore = ref(false), showDeleted = ref(false), uploadOpen = ref(false);
let page = 0, generation = 0, accessGeneration = 0;
let active = true, retryAppend = false;

function selectImage(item: WikiImage) {
  if (props.selectable && access.value?.canContribute && item.status === 'READY') emit('select', item);
  else selectedImage.value = item;
}
function imageChanged(item: WikiImage) {
  selectedImage.value = item.attachmentActive === false ? undefined : item;
  if (item.attachmentActive === false) void load(false);
}
function confirmLeave() { return detail.value?.confirmLeave() ?? true; }
function dirty() { return detail.value?.dirty() ?? false; }
defineExpose({confirmLeave, dirty});
function closeDetail() { if (confirmLeave()) { selectedImage.value = undefined; void load(false); } }
function search() {
  const q = query.value.trim();
  if (q === searchedQuery.value) void load(false);
  else submittedQuery.value = q;
}
async function load(append: boolean) {
  if (append && (loading.value || !hasMore.value)) return;
  const expected = ++generation;
  const requestedPage = append ? page + 1 : 0;
  loading.value = true; listError.value = ''; retryAppend = append;
  if (!append) { images.value = []; hasMore.value = false; }
  try {
    const result = await wikiRemote.images(props.owner, requestedPage, searchedQuery.value, showDeleted.value && !!access.value?.isOperator);
    if (!active || expected !== generation) return;
    images.value = append ? [...images.value, ...result.content] : result.content;
    page = requestedPage;
    hasMore.value = result.content.length > 0 && images.value.length < result.totalElements;
  } catch (error) { if (active && expected === generation) listError.value = wikiErrorMessage(error); }
  finally { if (expected === generation) loading.value = false; }
}
watch([() => session.user.email, () => session.user.roles.join(',')], async () => {
  const expected = ++accessGeneration;
  access.value = undefined; showDeleted.value = false; uploadOpen.value = false; selectedImage.value = undefined;
  ++generation; images.value = []; hasMore.value = false; loading.value = true;
  try { const value = await wikiRemote.access(); if (active && expected === accessGeneration) access.value = value; }
  catch (error) { if (active && expected === accessGeneration) access.value = {canContribute: false, isOperator: false, message: wikiErrorMessage(error)}; }
  if (active && expected === accessGeneration) void load(false);
}, {immediate: true});
watch([searchedQuery, showDeleted, () => props.owner.documentKind, () => props.owner.documentNo], () => { selectedImage.value = undefined; if (access.value) void load(false); });
onBeforeUnmount(() => { active = false; ++generation; ++accessGeneration; });
</script>
