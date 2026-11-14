<template>
  <div class="as-page">
    <header class="flex flex-wrap justify-between items-start gap-3 mb-5">
      <div><h1 class="as-page-title">{{creating ? '문서 요청 작성' : '문서 요청'}}</h1><p class="as-desc mt-2">요청 내용은 작성자와 운영진만 볼 수 있습니다.</p></div>
      <router-link v-if="!creating && !requestId && access?.canContribute" to="/wiki/requests/new" class="as-btn-primary py-2">새 요청</router-link>
    </header>
    <WikiNavigation class="mb-6"/>
    <template v-if="session.user.isLogin">
      <router-link v-if="creating || requestId" to="/wiki/requests" class="as-link text-sm inline-block mb-5">요청 목록</router-link>
      <WikiRequestForm v-if="creating"/>
      <WikiRequestDetail v-else-if="requestId" :request-id="requestId"/>
      <template v-else>
        <div class="flex flex-wrap items-center justify-between gap-3 mb-4">
          <p class="as-desc">{{access?.isOperator ? '모든 회원의 요청을 확인하고 처리할 수 있습니다.' : '내가 작성한 요청입니다.'}}</p>
          <label class="text-sm flex items-center gap-2">상태<select v-model="status" class="as-input-text px-3 py-2"><option value="">전체</option><option v-for="(label, key) in wikiRequestStatuses" :key="key" :value="key">{{label}}</option></select></label>
        </div>
        <div class="as-box overflow-hidden">
          <ol class="divide-y divide-line">
            <li v-for="item in items" :key="item.id" class="p-4 cq-md:p-5">
              <div class="flex flex-wrap items-start justify-between gap-3"><router-link :to="`/wiki/requests/${item.id}`" class="as-link font-semibold break-words">{{item.title}}</router-link><span class="as-tag-xs">{{wikiRequestStatuses[item.status]}}</span></div>
              <p class="as-meta mt-2">{{wikiRequestKinds[item.kind]}} · {{item.createdBy}} · {{wikiDate(item.createdAt)}}</p>
            </li>
          </ol>
          <p v-if="!loading && !items.length && !error" class="as-empty">작성된 요청이 없습니다.</p>
        </div>
        <p v-if="loading" class="as-empty" role="status">요청을 불러오는 중입니다.</p>
        <p v-if="error" class="text-sm text-red-600 mt-4" role="alert">{{error}} <button type="button" class="as-link" @click="load(retryAppend)">다시 시도</button></p>
        <WikiInfiniteScroll :enabled="hasMore && !loading && !error" @load="load(true)"/>
      </template>
    </template>
    <div v-else class="as-box p-6"><p class="as-desc mb-4">문서 요청을 확인하려면 로그인해 주세요.</p><router-link :to="{path: '/login', query: {path: route.fullPath}}" class="as-btn-primary py-2 inline-block">로그인</router-link></div>
  </div>
</template>

<script setup lang="ts">
import {computed, onBeforeUnmount, ref, watch} from 'vue';
import {useRoute} from 'vue-router';
import {sessionStore} from '../../domain/session/sessionStore';
import WikiNavigation from '../../domain/wiki/WikiNavigation.vue';
import WikiInfiniteScroll from '../../domain/wiki/WikiInfiniteScroll.vue';
import WikiRequestForm from '../../domain/wiki/WikiRequestForm.vue';
import WikiRequestDetail from '../../domain/wiki/WikiRequestDetail.vue';
import {wikiDate, wikiErrorMessage, wikiRemote, type WikiAccess} from '../../domain/wiki/wikiRemote';
import {wikiRequestKinds, wikiRequestStatuses, wikiRequestRemote, type WikiRequest} from '../../domain/wiki/wikiRequestRemote';
const route = useRoute(), session = sessionStore();
const creating = computed(() => route.path === '/wiki/requests/new');
const requestId = computed(() => Number(route.params.requestId) || 0);
const access = ref<WikiAccess>(), status = ref(''), items = ref<WikiRequest[]>([]);
const loading = ref(false), error = ref(''), hasMore = ref(false);
let active = true, generation = 0, accessGeneration = 0, page = 0, retryAppend = false;
async function load(append = false) {
  if (!session.user.isLogin || creating.value || requestId.value || (append && loading.value)) return;
  const expected = ++generation, next = append ? page + 1 : 0;
  loading.value = true; error.value = ''; retryAppend = append;
  if (!append) { items.value = []; hasMore.value = false; }
  try {
    const result = await wikiRequestRemote.list(next, status.value);
    if (active && expected === generation) { items.value = append ? [...items.value, ...result.content] : result.content; page = next; hasMore.value = result.content.length > 0 && items.value.length < result.totalElements; }
  } catch (cause) { if (active && expected === generation) error.value = wikiErrorMessage(cause); }
  finally { if (expected === generation) loading.value = false; }
}
watch([() => session.user.email, () => session.user.roles.join(',')], async () => {
  const expected = ++accessGeneration;
  ++generation; access.value = undefined; items.value = []; error.value = ''; loading.value = false;
  if (!session.user.isLogin) return;
  try { const value = await wikiRemote.access(); if (active && expected === accessGeneration) access.value = value; }
  catch (cause) { if (active && expected === accessGeneration) error.value = wikiErrorMessage(cause); }
  if (active && expected === accessGeneration) void load();
}, {immediate: true});
watch(status, () => load());
watch([creating, requestId], () => { ++generation; loading.value = false; if (!creating.value && !requestId.value) void load(); });
onBeforeUnmount(() => { active = false; ++generation; ++accessGeneration; });
</script>
