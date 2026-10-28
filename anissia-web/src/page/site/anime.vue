<template>

  <div class="as-page">

    <div class="relative">

      <WikiNavigation :query="query" class="mb-3"/>

      <label for="default-search" class="sr-only">애니검색</label>
      <div class="as-input-with-icon">
        <as-icon name="search"/>
        <input type="text" id="default-search" autocomplete="off" class="py-3.5 pr-24 as-input-text" placeholder="애니검색  #장르  @제작자  /완결  /도움말" v-model="query" @click="autocorrectOn = true" @keydown="keyAutocorrect" @compositionstart="clearAutocorrect" @input="loadAutocorrect">
        <button type="button" @click="search()" class="as-btn-primary absolute py-1.5 right-2 top-1/2 -translate-y-1/2">검색</button>
      </div>

      <div v-if="autocorrectOn && autocorrect.length" class="relative z-20">
        <ul class="autocorrect-list as-box mt-2 py-1.5 overflow-hidden shadow-pop">
          <li v-for="(node, i) in autocorrect" :key="node.key" @mouseover="autocorrectIndex = i"
              class="mx-1.5 rounded-ctl transition-colors" :class="autocorrectIndex == i ? 'bg-brand-soft' : ''">
            <router-link :to="`/anime/${node.key}`" class="flex items-center gap-2.5 px-3 py-2 text-sm"
                         :class="autocorrectIndex == i ? 'text-brand' : 'text-ink-2'">
              <as-icon name="arrow-right-long" class="text-[0.6875rem] transition-opacity" :class="autocorrectIndex != i ? 'opacity-0' : 'opacity-100'"/>
              <span v-html="node.hl"></span>
            </router-link>
          </li>
        </ul>
      </div>

      <div v-if="searchHelpOn" class="as-box p-6 mt-4">
        <div class="flex items-center justify-between mb-2">
          <router-link to="/notice/141" class="as-section-title">애니메이션 검색 도움말</router-link>
          <button @click="searchHelpOn = false" class="nav-icon-sm" aria-label="닫기"><as-icon name="x"/></button>
        </div>
        <div class="grid gap-x-8 gap-y-6 cq-md:grid-cols-2 cq-xl:grid-cols-3 mt-5">

          <div class="help-item">
            <div class="help-label">키워드 검색 (AND 조건)</div>
            <div class="help-query"><as-icon name="search"/> 비밥 카우</div>
            <div class="help-desc">
              단어중 "비밥" "카우" 포함시 검색됩니다.<br/>
              검색결과) 카우보이비밥
            </div>
          </div>

          <div class="help-item">
            <div class="help-label">띄어쓰기</div>
            <div class="help-query"><as-icon name="search"/> 체인소 맨</div>
            <div class="help-desc">
              단어중 "체인소" "맨" 포함시 검색됩니다.<br/>
              검색결과) 체인소 맨
            </div>
          </div>

          <div class="help-item">
            <div class="help-label">형태소</div>
            <div class="help-query"><as-icon name="search"/> 체인소맨</div>
            <div class="help-desc">
              단어중 "체인소맨"이 포함시 검색됩니다.<br/>
              형태소 분석기가 "체인소맨"을 "체인소 맨"으로 분해하지 못할 경우 "체인소 맨"이 검색되지 않습니다.<br/>
              쉽게 말해서 띄어쓰기를 최대한 하여 검색하는 것을 추천합니다.
            </div>
          </div>

          <div class="help-item">
            <div class="help-label">장르검색 (AND 조건)</div>
            <div class="help-query"><as-icon name="search"/> #판타지 #모험</div>
            <div class="help-desc">판타지이면서 동시에 모험인 장르를 검색합니다.</div>
          </div>

          <div class="help-item">
            <div class="help-label">자막제작자 검색 (OR 조건)</div>
            <div class="help-query"><as-icon name="search"/> @철수 @영희</div>
            <div class="help-desc">자막제작자가 철수이거나 영희인 경우 모두를 검색합니다.</div>
          </div>

          <div class="help-item">
            <div class="help-label">완결 작품만 검색 (최신순)</div>
            <div class="help-query"><as-icon name="search"/> /완결</div>
            <div class="help-desc">
              완결된 작품만 검색됩니다.<br/>
              자동으로 최신순으로 정렬됩니다.
            </div>
          </div>

        </div>
      </div>

      <div v-if="!list.empty" class="mt-8">
        <div class="as-meta text-right mb-3">
          총 <b class="text-ink-2">{{list.totalElements}}</b> 작품
        </div>
        <div class="anime-list">
          <div v-for="node in list.content" :key="node.animeNo" class="as-card p-3.5 flex items-start gap-2.5">
            <img v-if="node.thumbnail" :src="node.thumbnail" alt="" width="60" height="60" loading="lazy" decoding="async" class="w-15 h-15 mt-1 shrink-0 rounded-ctl object-cover"/>
            <div class="min-w-0 flex-1">
              <div class="text-md font-semibold leading-5 text-ink">
                <router-link :to="toAnimeViewUrl(node.animeNo)" class="hover:text-brand transition-colors">{{node.subject}}</router-link>
              </div>
              <div class="text-2xs mt-1 leading-4 text-ink-3" lang="ja" v-if="node.originalSubject">
                <router-link :to="toAnimeViewUrl(node.animeNo)" class="hover:text-brand transition-colors">{{node.originalSubject}}</router-link>
              </div>
              <div class="mt-1.5 flex flex-wrap gap-1">
                <span class="as-tag-xs as-tag-compact" v-for="tag in node.tags" :key="tag">{{tag}}</span>
                <router-link class="as-tag-xs as-tag-compact" v-for="tag in node.genres.split(/,/g)" :key="tag" :to="`/anime?q=%23${encodeURIComponent(tag)}`">{{tag}}</router-link>
                <a class="as-tag-xs as-tag-compact as-icon-link" v-if="node.website" :href="node.website" target="_blank" :aria-label="`${node.subject} 공식 사이트 (새 창)`" :title="`${node.subject} 공식 사이트 (새 창)`"><as-icon name="globe"/></a>
                <a class="as-tag-xs as-tag-compact as-icon-link" v-if="node.x" :href="node.x" target="_blank" :aria-label="`${node.subject} X 계정 (새 창)`" :title="`${node.subject} X 계정 (새 창)`"><as-icon name="x-logo"/></a>
                <span class="as-tag-xs as-tag-compact" v-if="node.captionCount"><as-icon name="cc" class="opacity-70"/>{{node.captionCount}}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
      <div v-else-if="list.loaded" class="as-empty">
        <b class="text-ink-2">{{getNowSearchedQuery()}}</b>에 대한 검색결과가 없습니다.
      </div>


    </div>

  </div>

</template>

<script setup lang="ts">
import {nextTick, onDeactivated, onUnmounted, Ref, ref} from "vue";
import PageData from "../../common/PageData";
import Anime from "../../domain/anime/Anime";
import WikiNavigation from "../../domain/wiki/WikiNavigation.vue";
import animeRemote from "../../domain/anime/remote/animeRemote";
import AnimeAutocorrect from "../../domain/anime/AnimeAutocorrect";
import {useRouter} from "vue-router";

import {useListScroll} from '../../common/useListScroll';
import {animeUpdates} from '../../domain/anime/animeUpdates';

defineOptions({name: 'AnimeList'});
const list = ref(PageData.empty().notLoaded()) as Ref<PageData<Anime>>;
const page = ref(0);
const query = ref('');
const searchedQuery = ref('');
const autocorrect = ref([]) as Ref<AnimeAutocorrect[]>;
const autocorrectOn = ref(false);
const searchHelpOn = ref(false);
const autocorrectIndex = ref(-1);
let autocorrectQuery = '';
let autocorrectTimer: ReturnType<typeof setTimeout> | undefined;
let autocorrectRequest = 0;
let listRequest = 0;
let listQuery: string | undefined;
const router = useRouter();

const sl = useListScroll(() => {
  page.value++;
  loadList();
});

function load(fullPath = window.location.href) {
  clearAutocorrect();
  searchHelpOn.value = false;
  const params = new URL(fullPath, window.location.origin).searchParams;
  const q = params.get('q') ?? '';
  query.value = q;
  searchedQuery.value = q;
  if (listQuery !== q) {
    listQuery = q;
    page.value = 0;
    list.value = PageData.empty<Anime>().notLoaded();
    loadList();
  }
}

animeUpdates.subscribe((id, changes) => {
  const item = list.value.content.find(item => item.animeNo === id);
  if (!item) return;
  if (changes) Object.assign(item, changes);
  else {
    list.value.content = list.value.content.filter(item => item.animeNo !== id);
    list.value.totalElements--;
  }
});

function loadList() {
  const isFirstPage = page.value == 0;
  const request = ++listRequest;
  sl.watch(false);

  animeRemote.getAnimeList(page.value, listQuery ?? '').then(pageData => {
    if (request !== listRequest) return;
    if (isFirstPage) {
      list.value = pageData;
    } else {
      list.value = list.value.merge(pageData);
    }
    nextTick(() => {
      if (request === listRequest) sl.watch(pageData.next);
    });
  });
}

function search() {
  clearAutocorrect();
  const q = query.value;
  if (q.indexOf('/도움말') != -1) {
    searchHelpOn.value = true;
    query.value = query.value.split('/도움말').join('').trim();
    return;
  }
  searchHelpOn.value = false;
  router.push({path: '/anime', query: {q}});
}

function clearAutocorrect() {
  clearTimeout(autocorrectTimer);
  autocorrectRequest++;
  autocorrectQuery = '';
  autocorrectOn.value = false;
  autocorrectIndex.value = -1;
  autocorrect.value = [];
}

function loadAutocorrect(event: Event) {
  const input = event.target as HTMLInputElement & {composing?: boolean};
  if ((event as InputEvent).isComposing || input.composing) return;
  const word = input.value;
  if (autocorrectQuery === word) return;
  clearAutocorrect();
  autocorrectQuery = word;
  if (!word.trim()) return;
  autocorrectOn.value = true;
  const request = autocorrectRequest;
  autocorrectTimer = setTimeout(() => {
    animeRemote.getAnimeListAutocorrect(word).then(list => {
      if (request === autocorrectRequest) autocorrect.value = list;
    }).catch(() => {});
  }, 180);
}

function keyAutocorrect(event: KeyboardEvent) {
  if (event.isComposing || event.keyCode === 229) return;
  const key = event.key;
  const len = autocorrectOn.value ? autocorrect.value.length : 0;

  switch (key) {
    case 'ArrowUp':
      if (len) {
        event.preventDefault();
        if (autocorrectIndex.value == -1) { autocorrectIndex.value = len -1; } else { autocorrectIndex.value--; }
      }
      return;
    case 'ArrowDown':
      if (len) {
        event.preventDefault();
        if (autocorrectIndex.value >= (len -1)) { autocorrectIndex.value = -1; } else { autocorrectIndex.value++; }
      }
      return;
    case 'Enter':
      if (!len || autocorrectIndex.value == -1) {
        search();
      } else {
        router.push(`/anime/${autocorrect.value[autocorrectIndex.value].key}`)
      }
      return;
  }
}

function toAnimeViewUrl(animeNo: number) {
  return `/anime/${animeNo}`;
}

function getNowSearchedQuery() {
  return searchedQuery.value;
}

load();

onDeactivated(clearAutocorrect);

onUnmounted(() => {
  clearAutocorrect();
  listRequest++;
});
</script>

<style scoped>
@reference "../../common/tailwind.pcss";

.anime-list {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  align-items: start;
  gap: 1rem;
}

@container site (width >= 64rem) {
  .anime-list { grid-template-columns: repeat(2, minmax(0, 1fr)) }
}

.nav-icon-sm {
  @apply inline-flex items-center justify-center w-8 h-8 rounded-ctl text-sm transition-colors;
  color: var(--as-ink-3);
  &:hover { color: var(--as-ink); background: var(--as-muted) }
}

.help-item {
  .help-label {
    @apply text-2xs font-semibold uppercase;
    letter-spacing: .08em;
    color: var(--as-ink-3);
  }
  .help-query {
    @apply mt-2 text-sm font-semibold;
    color: var(--as-brand);
    i { @apply mr-1.5 text-xs opacity-70 }
  }
  .help-desc {
    @apply mt-2 text-sm leading-[1.8];
    color: var(--as-ink-2);
  }
}

.autocorrect-list :deep(b) {
  color: var(--as-brand);
  font-weight: 700;
}
</style>
