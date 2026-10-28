<template>
  <div class="as-page">
    <h1 class="as-page-title mb-4">위키 최근 변경</h1><WikiNavigation class="mb-6"/>
    <div class="as-box overflow-hidden">
      <ol class="divide-y divide-line">
        <li v-for="item in items" :key="`${item.kind}:${item.targetNo}:${item.revision}`" class="p-4 cq-md:p-5 flex items-start gap-3">
          <router-link v-if="item.thumbnail" :to="wikiLocation(item)" class="shrink-0" :aria-label="item.title">
            <img :src="item.thumbnail" alt="" width="64" height="64" loading="lazy" decoding="async" class="w-16 h-16 rounded-ctl object-cover"/>
          </router-link>
          <div class="min-w-0 flex-1">
            <div class="flex flex-wrap items-baseline justify-between gap-2">
              <router-link :to="wikiLocation(item)" class="as-link font-semibold break-words">{{item.title}}</router-link>
              <span class="as-meta">{{item.kind === 'anime' ? '애니 위키' : '템플릿'}} · <router-link :to="wikiLocation({...item, scope: 'history'})" class="as-link">{{item.revision}}판</router-link><span v-if="item.deleted"> · 삭제됨</span></span>
            </div>
            <p class="text-sm mt-2 break-words">{{item.summary || '내용 수정'}}</p>
            <p class="as-meta mt-2">{{item.updatedBy}} · {{wikiDate(item.updatedAt)}}</p>
          </div>
        </li>
      </ol>
      <p v-if="!loading && !items.length && !error" class="as-empty">아직 변경 이력이 없습니다.</p>
    </div>
    <p v-if="loading" role="status" class="as-empty">최근 변경을 불러오는 중입니다.</p>
    <p v-if="error" class="text-sm text-red-600 mt-4" role="alert">{{error}} <button type="button" class="as-link" @click="load(retryAppend)">다시 시도</button></p>
    <WikiInfiniteScroll :enabled="hasMore && !loading && !error" @load="load(true)"/>
  </div>
</template>
<script setup lang="ts">
import {onBeforeUnmount, ref, watch} from 'vue';
import {sessionStore} from '../../domain/session/sessionStore';
import WikiNavigation from '../../domain/wiki/WikiNavigation.vue';
import WikiInfiniteScroll from '../../domain/wiki/WikiInfiniteScroll.vue';
import {wikiDate, wikiErrorMessage, wikiLocation, wikiRemote, type WikiPage, type WikiRecent} from '../../domain/wiki/wikiRemote';
const session = sessionStore();
const items = ref<WikiRecent[]>([]), hasMore = ref(false), loading = ref(false), error = ref('');
let page = 0, generation = 0, active = true, retryAppend = false;
async function load(append: boolean) {
  if (append && (loading.value || !hasMore.value)) return;
  const expected = ++generation, next = append ? page + 1 : 0;
  loading.value = true; error.value = ''; retryAppend = append;
  if (!append) { items.value = []; hasMore.value = false; }
  try {
    const value = await wikiRemote.request<WikiPage<WikiRecent>>(`/recent?page=${next}`);
    if (active && expected === generation) {
      items.value = append ? [...items.value, ...value.content] : value.content;
      hasMore.value = value.content.length > 0 && items.value.length < value.totalElements;
      page = next;
    }
  } catch (value) { if (active && expected === generation) error.value = wikiErrorMessage(value); }
  finally { if (expected === generation) loading.value = false; }
}
watch([() => session.user.email, () => session.user.roles.join(',')], () => load(false), {immediate: true});
onBeforeUnmount(() => { active = false; ++generation; });
</script>
