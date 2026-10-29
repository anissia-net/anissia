<template>
  <div>
    <div class="flex items-center justify-between gap-4 mb-5">
      <h2 v-if="props.title" class="as-section-title">{{props.title}}</h2>
      <span v-else></span>
      <router-link v-if="boardInfo.canWriteTopic(user)" :to="`/${props.ticker}/new`" @pointerenter="preloadEditor" @focus="preloadEditor" class="as-btn-primary py-2">
        <as-icon name="pen" class="mr-1.5 text-[1rem]"/> 글쓰기
      </router-link>
    </div>

    <form class="flex gap-2 mb-5" @submit.prevent="search">
      <input v-model="query" type="search" maxlength="300" class="as-input-text px-3 py-2 flex-1 min-w-0" placeholder="제목·본문·댓글 검색" aria-label="게시판 검색" @compositionstart="composing = true" @compositionend="composing = false"/>
      <button type="submit" class="as-input-btn px-4">검색</button>
    </form>
    <p v-if="error" role="alert" class="text-sm text-red-600 mb-4">{{error}} <button class="as-link" type="button" @click="loadList">다시 시도</button></p>
    <p v-else-if="loading" role="status" class="as-empty">게시글을 불러오는 중입니다.</p>
    <p v-else-if="!list.content.length" class="as-empty">{{submittedQuery ? '검색결과가 없습니다.' : '등록된 글이 없습니다.'}}</p>
    <div class="grid gap-4 cq-md:grid-cols-2 cq-xl:grid-cols-3">
      <router-link v-for="node in list.content" :key="node.topicNo" :to="`/${props.ticker}/${node.topicNo}`" class="as-card group p-5 flex flex-col">
        <div class="text-md font-semibold text-ink group-hover:text-brand transition-colors line-clamp-2">
          {{node.topic}}
        </div>
        <div class="mt-auto pt-4 flex items-center gap-4 as-meta">
          <span class="inline-flex items-center gap-1.5 truncate"><as-icon name="user" class="opacity-60"/>{{node.name}}</span>
          <span class="inline-flex items-center gap-1.5"><as-icon name="chat" class="opacity-60"/>{{node.postCount}}</span>
          <span class="inline-flex items-center gap-1.5 ml-auto shrink-0"><as-icon name="clock" class="opacity-60"/>{{node.regDtText}}</span>
        </div>
      </router-link>
    </div>

  </div>

</template>

<script setup lang="ts">
import {computed, nextTick, onUnmounted, ref, watch} from 'vue';
import {useRoute, useRouter} from 'vue-router';
import PageData from '../../common/PageData';
import {useListScroll} from '../../common/useListScroll';
import {sessionStore} from '../session/sessionStore';
import boardRemote from './remote/boardRemote';
import {BoardInfo} from './BoardInfo';
import {Topic} from './Topic';
import {topicUpdates} from './topicUpdates';

const props = defineProps<{ticker: string; title?: string}>();
const boardInfo = ref(new BoardInfo());
const list = ref(PageData.empty<Topic>().notLoaded());
const user = computed(() => sessionStore().user);
const route = useRoute(), router = useRouter();
const submittedQuery = computed(() => typeof route.query.q === 'string' ? route.query.q.trim() : '');
const query = ref(''), error = ref(''), loading = ref(false), composing = ref(false);
let page = 0, active = true, generation = 0;
const sl = useListScroll(() => { if (!loading.value && !error.value) { page++; void loadList(); } });
watch(submittedQuery, value => { query.value = value; }, {immediate: true});
function search() {
  if (composing.value) return;
  const q = query.value.trim();
  if (q === submittedQuery.value) { page = 0; void loadList(); }
  else void router.push({path: route.path, query: {...route.query, q: q || undefined}});
}

function preloadEditor() { void import('./PostEditor.vue').catch(() => {}); }

async function loadList() {
  const first = page === 0, expected = ++generation;
  loading.value = true; error.value = ''; sl.watch(false);
  if (first) list.value = PageData.empty<Topic>();
  try {
    const data = await boardRemote.getList(props.ticker, page, submittedQuery.value);
    if (!active || expected !== generation) return;
    list.value = first ? data : list.value.merge(data);
    await nextTick();
    if (active && expected === generation) sl.watch(data.next);
  } catch {
    if (active && expected === generation) error.value = '게시글을 불러오지 못했습니다.';
  } finally { if (active && expected === generation) loading.value = false; }
}

topicUpdates.subscribe((id, changes) => {
  const item = list.value.content.find(item => item.topicNo === id);
  if (!item) return;
  if (changes) Object.assign(item, changes);
  else {
    list.value.content = list.value.content.filter(item => item.topicNo !== id);
    list.value.totalElements--;
  }
});

boardRemote.getTicker(props.ticker).then(info => { if (active) boardInfo.value = info; });
watch([submittedQuery, () => props.ticker], () => { page = 0; void loadList(); }, {immediate: true});
onUnmounted(() => { active = false; ++generation; });
</script>
