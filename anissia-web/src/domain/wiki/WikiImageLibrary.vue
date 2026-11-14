<template>
  <div>
    <template v-if="selectedImage">
      <button type="button" class="as-link text-sm mb-4" @click="closeDetail">이미지 목록으로</button>
      <WikiImageDetail :key="selectedImage.id" ref="detail" :image-id="selectedImage.id" @changed="imageChanged"/>
      <button v-if="selectable && !archive && selectedImage.status === 'READY'" type="button" class="as-btn-primary py-2 mt-4" @click="$emit('select', selectedImage)">본문에 넣기</button>
    </template>
    <template v-else-if="access && !owner && !access.isOperator">
      <p class="as-desc">이미지는 각 위키 문서나 템플릿의 편집 화면에서 확인할 수 있습니다.</p>
    </template>
    <template v-else>
      <div v-if="owner && access?.isOperator && access.canUploadImage === true" class="flex flex-wrap gap-3 mb-4 text-sm">
        <button type="button" class="as-input-btn py-2" :aria-pressed="!archive" @click="archive = false">이 문서의 이미지</button>
        <button type="button" class="as-input-btn py-2" :aria-pressed="archive" @click="archive = true">보관함에서 가져오기</button>
      </div>
      <p v-if="archive" class="as-desc mb-4">문서에 소속되지 않은 기존 이미지의 보관함입니다. 선택한 이미지를 이 문서로 복사합니다.</p>
      <p v-else-if="!owner" class="as-desc mb-4">문서에 소속되지 않은 기존 이미지를 보관합니다. 문서 편집 화면의 이미지 영역에서 가져올 수 있습니다.</p>
      <div v-if="searchQuery === undefined" class="flex gap-2 mb-4" role="search">
        <input v-model="query" class="as-input-text px-3 py-2 flex-1 min-w-0" placeholder="이미지 검색" aria-label="이미지 검색" @keydown.enter.prevent.stop="search"/>
        <button type="button" class="as-input-btn px-4" @click="search">검색</button>
      </div>
      <div v-if="access?.canContribute || access?.isOperator || uploadMode" class="flex flex-wrap items-center justify-end gap-3 mb-4">
        <label v-if="access?.isOperator && !archive" class="flex items-center gap-2 text-sm mr-auto"><input v-model="showDeleted" type="checkbox"/>삭제된 이미지</label>
        <button v-if="owner && !archive && uploadMode" type="button" class="as-btn-primary py-2" :aria-expanded="uploadOpen" @click="uploadOpen = !uploadOpen">{{uploadOpen ? '업로드 닫기' : uploadMode === 'direct' ? '이미지 업로드' : '이미지 등록 요청'}}</button>
        <button type="button" class="as-link text-sm" :disabled="loading" @click="load(false)">새로고침</button>
      </div>
      <WikiImageUpload v-if="owner && uploadOpen && !archive && uploadMode" :key="`${owner.documentKind}:${owner.documentNo}`" selectable :owner="owner" class="mb-6" @uploaded="load(false)" @select="$emit('select', $event)"/>
      <div class="grid grid-cols-2 gap-3 cq-md:grid-cols-3 cq-xl:grid-cols-4">
        <article v-for="item in images" :key="item.id" class="as-card block min-w-0 overflow-hidden text-left">
          <button type="button" class="block w-full text-left hover:bg-canvas" :title="item.description || '위키 이미지'" :disabled="!!attaching" @click="selectImage(item)">
            <div class="h-36 cq-md:h-44 bg-canvas p-2 flex items-center justify-center">
              <img v-if="item.url" :src="wikiImageDisplayUrl(item.url, item.revision)" alt="" loading="lazy" decoding="async" class="w-full h-full object-contain"/>
              <span v-else class="as-meta">{{item.status === 'DELETED' ? '삭제된 이미지' : '파일 처리 중'}}</span>
            </div>
            <p class="px-3 pt-3 text-sm font-medium truncate">{{item.description || '위키 이미지'}}</p>
            <p v-if="archive" class="px-3 pt-1 text-xs as-meta">{{attaching === item.id ? '가져오는 중…' : '이 문서로 가져오기'}}</p>
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
import {useRoute, useRouter} from 'vue-router';
import {sessionStore} from '../session/sessionStore';
import {wikiRemote, wikiErrorMessage, type WikiAccess, type WikiImage} from './wikiRemote';
import {wikiImageDisplayUrl} from './wikiImageVersion';
import type {WikiImageOwner} from './wikiImageOwner';
import {wikiImageUploadMode} from './wikiImageAccess';
import WikiImageUpload from './WikiImageUpload.vue';
import WikiImageDetail from './WikiImageDetail.vue';
import WikiInfiniteScroll from './WikiInfiniteScroll.vue';

const props = defineProps<{selectable?: boolean; searchQuery?: string; owner?: WikiImageOwner}>();
const emit = defineEmits<{select: [image: WikiImage]}>();
const session = sessionStore(), access = ref<WikiAccess>();
const uploadMode = computed(() => wikiImageUploadMode(access.value));
const route = useRoute(), router = useRouter();
const query = ref(''), submittedQuery = ref('');
const searchedQuery = computed(() => (props.searchQuery ?? (props.selectable || props.owner ? submittedQuery.value : typeof route.query.q === 'string' ? route.query.q : '')).trim());
watch(searchedQuery, value => { query.value = value; }, {immediate: true});
const images = ref<WikiImage[]>([]), selectedImage = ref<WikiImage>();
const detail = ref<InstanceType<typeof WikiImageDetail>>();
const listError = ref(''), loading = ref(false), hasMore = ref(false), showDeleted = ref(false), uploadOpen = ref(false), archive = ref(false), attaching = ref('');
let page = 0, generation = 0, accessGeneration = 0;
let active = true, retryAppend = false;

async function selectImage(item: WikiImage) {
  if (archive.value) {
    if (!props.owner || !access.value?.isOperator || access.value.canUploadImage !== true || item.status !== 'READY') { selectedImage.value = item; return; }
    if (attaching.value) return;
    attaching.value = item.id; listError.value = '';
    try {
      const result = await wikiRemote.attachImage(item.id, props.owner);
      if (!active) return;
      archive.value = false;
      emit('select', result);
    } catch (error) { if (active) listError.value = wikiErrorMessage(error); }
    finally { attaching.value = ''; }
  } else if (props.owner && props.selectable && access.value?.canContribute && item.status === 'READY') emit('select', item);
  else selectedImage.value = item;
}
function imageChanged(item: WikiImage) { selectedImage.value = item; }
function confirmLeave() { return !attaching.value && (detail.value?.confirmLeave() ?? true); }
function dirty() { return !!attaching.value || (detail.value?.dirty() ?? false); }
defineExpose({confirmLeave, dirty});
function closeDetail() { if (confirmLeave()) { selectedImage.value = undefined; void load(false); } }
function search() {
  const q = query.value.trim();
  if (q === searchedQuery.value) void load(false);
  else if (props.selectable || props.owner) submittedQuery.value = q;
  else void router.push({path: '/wiki/images', query: q ? {q} : {}});
}
async function load(append: boolean) {
  if (!props.owner && !access.value?.isOperator) { loading.value = false; images.value = []; hasMore.value = false; return; }
  if (append && (loading.value || !hasMore.value)) return;
  const expected = ++generation;
  const requestedPage = append ? page + 1 : 0;
  loading.value = true; listError.value = ''; retryAppend = append;
  if (!append) { images.value = []; hasMore.value = false; }
  try {
    const result = await wikiRemote.images(requestedPage, searchedQuery.value, showDeleted.value && !!access.value?.isOperator && !archive.value, archive.value ? undefined : props.owner);
    if (!active || expected !== generation) return;
    images.value = append ? [...images.value, ...result.content] : result.content;
    page = requestedPage;
    hasMore.value = result.content.length > 0 && images.value.length < result.totalElements;
  } catch (error) { if (active && expected === generation) listError.value = wikiErrorMessage(error); }
  finally { if (expected === generation) loading.value = false; }
}
watch([() => session.user.email, () => session.user.roles.join(',')], async () => {
  const expected = ++accessGeneration;
  access.value = undefined; showDeleted.value = false; uploadOpen.value = false; archive.value = false; selectedImage.value = undefined;
  ++generation; images.value = []; hasMore.value = false; loading.value = true;
  try { const value = await wikiRemote.access(); if (active && expected === accessGeneration) access.value = value; }
  catch (error) { if (active && expected === accessGeneration) access.value = {canContribute: false, isOperator: false, message: wikiErrorMessage(error)}; }
  if (active && expected === accessGeneration) void load(false);
}, {immediate: true});
watch([searchedQuery, showDeleted, archive, () => props.owner?.documentKind, () => props.owner?.documentNo], () => { selectedImage.value = undefined; if (access.value) void load(false); });
onBeforeUnmount(() => { active = false; ++generation; ++accessGeneration; });
</script>
