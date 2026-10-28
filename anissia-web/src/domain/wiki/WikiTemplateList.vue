<template>
  <div>
    <form class="flex gap-2 mb-4" @submit.prevent="search"><input v-model="query" class="as-input-text px-3 py-2 flex-1 min-w-0" placeholder="템플릿 검색" aria-label="템플릿 검색"/><button type="submit" class="as-input-btn px-4">검색</button></form>
    <div v-if="access?.canContribute || access?.isOperator" class="flex flex-wrap justify-between items-center gap-3 mb-4"><label v-if="access?.isOperator" class="text-sm flex items-center gap-2"><input v-model="showDeleted" type="checkbox"/>삭제된 템플릿</label><router-link v-if="access?.canContribute && !showDeleted" to="/wiki/templates?new=1" class="as-btn-primary py-2 ml-auto">새 템플릿</router-link></div>
    <div class="space-y-4"><article v-for="item in items" :key="item.templateNo" class="as-post overflow-hidden"><div class="px-5 py-4 border-b border-line"><router-link :to="`/wiki/templates?templateNo=${item.templateNo}`" class="as-link font-semibold">{{item.title}}</router-link><span v-if="item.deleted" class="as-meta ml-2">삭제됨</span><p class="as-meta mt-1">{{wikiDate(item.updatedAt)}}</p></div><WikiBodyView v-if="item.body" :body="item.body" :templates="[]" :floating-outline="false"/></article></div>
    <p v-if="loading" role="status" class="as-empty">템플릿을 불러오는 중입니다.</p>
    <p v-else-if="!items.length && !error" class="as-empty">{{searchedQuery ? '검색결과가 없습니다.' : '등록된 템플릿이 없습니다.'}}</p>
    <p v-if="error" class="text-sm text-red-600 mt-4" role="alert">{{error}} <button type="button" class="as-link" @click="load(retryAppend)">다시 시도</button></p>
    <WikiInfiniteScroll :enabled="hasMore && !loading && !error" @load="load(true)"/>
  </div>
</template>

<script setup lang="ts">
import {computed, onBeforeUnmount, ref, watch} from 'vue';
import {useRoute, useRouter} from 'vue-router';
import WikiBodyView from './WikiBodyView.vue';
import WikiInfiniteScroll from './WikiInfiniteScroll.vue';
import {sessionStore} from '../session/sessionStore';
import {wikiRemote, wikiDate, wikiErrorMessage, type WikiAccess, type WikiDocument} from './wikiRemote';

const route = useRoute(), router = useRouter();
const session = sessionStore(), access = ref<WikiAccess>(), showDeleted = ref(false);
const query = ref('');
const searchedQuery = computed(() => typeof route.query.q === 'string' ? route.query.q.trim() : '');
watch(searchedQuery, value => { query.value = value; }, {immediate: true});
const items = ref<WikiDocument[]>([]), loading = ref(false), error = ref(''), hasMore = ref(false);
let page = 0, generation = 0, active = true, accessGeneration = 0;
let retryAppend = false;
function search() {
  const q = query.value.trim();
  if (q === searchedQuery.value) void load(false);
  else void router.push({path: '/wiki/templates', query: q ? {q} : {}});
}
async function load(append: boolean) {
  if (append && (loading.value || !hasMore.value)) return;
  const expected = ++generation, requestedPage = append ? page + 1 : 0;
  loading.value = true; error.value = ''; retryAppend = append;
  if (!append) { items.value = []; hasMore.value = false; }
  try {
    const value = await wikiRemote.templates(requestedPage, searchedQuery.value, showDeleted.value && !!access.value?.isOperator);
    if (!active || expected !== generation) return;
    items.value = append ? [...items.value, ...value.content] : value.content; page = requestedPage; hasMore.value = value.content.length > 0 && items.value.length < value.totalElements;
  } catch (value) { if (active && expected === generation) error.value = wikiErrorMessage(value); }
  finally { if (expected === generation) loading.value = false; }
}
watch([searchedQuery, showDeleted], () => { if (access.value) void load(false); });
watch([() => session.user.email, () => session.user.roles.join(',')], async () => {
  const expected = ++accessGeneration;
  access.value = undefined; showDeleted.value = false; items.value = []; hasMore.value = false; loading.value = true; ++generation;
  try { const value = await wikiRemote.access(); if (active && expected === accessGeneration) access.value = value; }
  catch (value) { if (active && expected === accessGeneration) access.value = {canContribute: false, isOperator: false, message: wikiErrorMessage(value)}; }
  if (active && expected === accessGeneration) void load(false);
}, {immediate: true});
onBeforeUnmount(() => { active = false; ++generation; ++accessGeneration; });
</script>
