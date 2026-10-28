<template>
  <div>
    <form v-if="searchQuery === undefined" class="flex gap-2 mb-4" @submit.prevent.stop="search">
      <input v-model="query" class="as-input-text px-3 py-2 flex-1 min-w-0" placeholder="이미지 검색" aria-label="이미지 검색"/>
      <button type="submit" class="as-input-btn px-4">검색</button>
    </form>
    <div v-if="access?.canContribute || (access?.isOperator && !selectable)" class="flex flex-wrap items-center justify-end gap-3 mb-4">
      <label v-if="access?.isOperator && !selectable" class="flex items-center gap-2 text-sm mr-auto"><input v-model="showDeleted" type="checkbox"/>삭제된 이미지</label>
      <template v-if="access?.canContribute">
        <button v-if="selectable" type="button" class="as-btn-primary py-2" :aria-expanded="uploadOpen" @click="uploadOpen = !uploadOpen">{{uploadOpen ? '업로드 닫기' : '이미지 업로드'}}</button>
        <router-link v-else :to="imageLocation({upload: '1'})" class="as-btn-primary py-2">이미지 업로드</router-link>
      </template>
    </div>
    <WikiImageUpload v-if="selectable && uploadOpen && access?.canContribute" selectable class="mb-6" @uploaded="load(false)" @select="$emit('select', $event)"/>
    <div class="grid grid-cols-2 gap-3 cq-md:grid-cols-3 cq-xl:grid-cols-4">
      <component :is="selectable && access?.canContribute && item.status === 'READY' ? 'button' : RouterLink"
                 v-for="item in images" :key="item.id"
                 :to="selectable && access?.canContribute && item.status === 'READY' ? undefined : imageLocation({imageId: item.id})"
                 :type="selectable && access?.canContribute && item.status === 'READY' ? 'button' : undefined"
                 class="as-card block min-w-0 overflow-hidden text-left hover:border-brand transition-colors"
                 :title="item.description || '위키 이미지'"
                 @click="selectImage(item)">
        <div class="h-36 cq-md:h-44 bg-canvas p-2 flex items-center justify-center">
          <img v-if="item.url" :src="item.url" alt="" loading="lazy" decoding="async" class="w-full h-full object-contain"/>
          <span v-else class="as-meta">{{item.status === 'DELETED' ? '삭제된 이미지' : '파일 처리 중'}}</span>
        </div>
        <p class="p-3 text-sm font-medium truncate">{{item.description || '위키 이미지'}}</p>
      </component>
    </div>
    <p v-if="loading" role="status" class="as-empty">이미지를 불러오는 중입니다.</p>
    <p v-else-if="!images.length && !listError" class="as-empty">{{searchedQuery ? '검색결과가 없습니다.' : '등록된 이미지가 없습니다.'}}</p>
    <p v-if="listError" class="text-sm text-red-600 mt-4" role="alert">{{listError}} <button type="button" class="as-link" @click="load(retryAppend)">다시 시도</button></p>
    <WikiInfiniteScroll :enabled="hasMore && !loading && !listError" @load="load(true)"/>
  </div>
</template>

<script setup lang="ts">
import {computed, onBeforeUnmount, ref, watch} from 'vue';
import {RouterLink, useRoute, useRouter} from 'vue-router';
import {sessionStore} from '../session/sessionStore';
import {wikiRemote, wikiErrorMessage, type WikiAccess, type WikiImage} from './wikiRemote';
import WikiImageUpload from './WikiImageUpload.vue';
import WikiInfiniteScroll from './WikiInfiniteScroll.vue';

const props = defineProps<{selectable?: boolean; searchQuery?: string}>();
const emit = defineEmits<{select: [image: WikiImage]}>();
const session = sessionStore(), access = ref<WikiAccess>();
const route = useRoute(), router = useRouter();
const query = ref(''), submittedQuery = ref('');
const searchedQuery = computed(() => (props.searchQuery ?? (props.selectable ? submittedQuery.value : typeof route.query.q === 'string' ? route.query.q : '')).trim());
watch(searchedQuery, value => { query.value = value; }, {immediate: true});
const images = ref<WikiImage[]>([]);
const listError = ref(''), loading = ref(false), hasMore = ref(false), showDeleted = ref(false), uploadOpen = ref(false);
let page = 0, generation = 0, accessGeneration = 0;
let active = true, retryAppend = false;

function imageLocation(params: Record<string, string>) {
  return {path: '/wiki/images', query: {...params, ...(searchedQuery.value ? {q: searchedQuery.value} : {})}};
}
function selectImage(item: WikiImage) {
  if (props.selectable && access.value?.canContribute && item.status === 'READY') emit('select', item);
}
function search() {
  const q = query.value.trim();
  if (q === searchedQuery.value) void load(false);
  else if (props.selectable) submittedQuery.value = q;
  else void router.push({path: '/wiki/images', query: q ? {q} : {}});
}
async function load(append: boolean) {
  if (append && (loading.value || !hasMore.value)) return;
  const expected = ++generation;
  const requestedPage = append ? page + 1 : 0;
  loading.value = true; listError.value = ''; retryAppend = append;
  if (!append) { images.value = []; hasMore.value = false; }
  try {
    const result = await wikiRemote.images(requestedPage, searchedQuery.value, showDeleted.value && !!access.value?.isOperator);
    if (!active || expected !== generation) return;
    images.value = append ? [...images.value, ...result.content] : result.content;
    page = requestedPage;
    hasMore.value = result.content.length > 0 && images.value.length < result.totalElements;
  } catch (error) { if (active && expected === generation) listError.value = wikiErrorMessage(error); }
  finally { if (expected === generation) loading.value = false; }
}
watch([() => session.user.email, () => session.user.roles.join(',')], async () => {
  const expected = ++accessGeneration;
  access.value = undefined; showDeleted.value = false; uploadOpen.value = false;
  ++generation; images.value = []; hasMore.value = false; loading.value = true;
  try { const value = await wikiRemote.access(); if (active && expected === accessGeneration) access.value = value; }
  catch (error) { if (active && expected === accessGeneration) access.value = {canContribute: false, isOperator: false, message: wikiErrorMessage(error)}; }
  if (active && expected === accessGeneration) void load(false);
}, {immediate: true});
watch([searchedQuery, showDeleted], () => { if (access.value) void load(false); });
onBeforeUnmount(() => { active = false; ++generation; ++accessGeneration; });
</script>
