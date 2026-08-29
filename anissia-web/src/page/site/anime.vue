<template>

  <div class="as-page">

    <div v-if="anime" class="mb-12">
      <div v-if="anime.animeNo != 0" class="as-box overflow-hidden">

        <div class="p-6 md:p-8">
          <div class="flex flex-wrap items-center gap-x-3 gap-y-2">
            <span class="status-pill" :class="{'is-on': anime.status == 'ON'}">
              <span class="dot"></span>
              <span v-if="anime.status == 'ON' && anime.pureWeek">방영중 · 매주 ({{anime.weekText}}) {{anime.timeText}}</span>
              <span v-else-if="anime.status == 'ON' && !anime.pureWeek">{{anime.statusText}} ({{anime.weekText}})</span>
              <span v-else>{{anime.statusText}}</span>
            </span>
            <span v-if="anime.period" class="as-meta">{{anime.period}}</span>
          </div>

          <h1 class="as-page-title mt-4">{{anime.subject}}</h1>
          <div v-if="anime.originalSubject" class="as-desc mt-1.5" lang="ja">{{anime.originalSubject}}</div>

          <div class="mt-5 flex flex-wrap items-center gap-1.5">
            <router-link class="as-tag-xs" v-for="tag in anime.genres.split(/,/g)" :key="tag" :to="`/anime?q=%23${encodeURIComponent(tag)}`">{{tag}}</router-link>
            <span v-if="anime.website || anime.x" class="w-px h-4 mx-1 bg-line-2"></span>
            <a v-if="anime.website" :href="anime.website" target="_blank" class="as-tag-xs" :title="anime.website"><i class="fa-solid fa-globe mr-1.5 opacity-70"></i>공식 사이트</a>
            <a v-if="anime.x" :href="anime.x" target="_blank" class="as-tag-xs" :title="anime.x"><i class="fa-brands fa-x-twitter opacity-70"></i></a>
          </div>
        </div>

        <div v-if="anime.captions.length" class="px-6 md:px-8 pt-4 pb-3 border-t border-line">
          <div class="as-sub-title py-2">자막정보</div>
          <ul>
            <li v-for="caption in anime.captions" :key="caption.name" class="as-row flex items-center gap-4 py-2.5 text-sm">
              <span class="w-16 shrink-0">
                <a v-if="caption.website" :href="caption.website" target="_blank" class="as-link font-semibold">{{caption.episodeText}}</a>
                <span v-else class="text-ink-3">준비중</span>
              </span>
              <router-link :to="`/anime?q=%40${encodeURIComponent(caption.name)}`" class="as-link flex-1 min-w-0 truncate">{{caption.name}}</router-link>
              <span class="as-meta whitespace-nowrap">{{caption.updDtText}}</span>
            </li>
          </ul>
        </div>

      </div>
      <div v-else class="as-empty">
        존재하지 않거나 삭제된 애니메이션 입니다.
      </div>

    </div>

    <div class="relative">

      <label for="default-search" class="sr-only">애니검색</label>
      <div class="relative">
        <div class="flex absolute inset-y-0 left-0 items-center pl-4 pointer-events-none text-ink-3">
          <i class="fa-solid fa-magnifying-glass"></i>
        </div>
        <input type="text" id="default-search" autocomplete="off" class="py-3.5 pl-11 pr-24 as-input-text text-base!" placeholder="애니검색  #장르  @제작자  /완결  /도움말"  v-model="query" @click="autocorrectOn = true" @keydown="keyAutocorrect" @keyup="loadAutocorrect">
        <button type="button" @click="searchAnime()" class="as-btn-primary absolute py-1.5 right-2 top-1/2 -translate-y-1/2">검색</button>
      </div>

      <div v-if="autocorrectOn && autocorrect.length" class="relative z-20">
        <ul class="autocorrect-list as-box mt-2 py-1.5 overflow-hidden shadow-pop">
          <li v-for="(node, i) in autocorrect" :key="node.key" @mouseover="autocorrectIndex = i"
              class="mx-1.5 rounded-ctl transition-colors" :class="autocorrectIndex == i ? 'bg-brand-soft' : ''">
            <router-link :to="`/anime?animeNo=${node.key}`" class="flex items-center gap-2.5 px-3 py-2 text-sm"
                         :class="autocorrectIndex == i ? 'text-brand' : 'text-ink-2'">
              <i class="fa-solid fa-arrow-right-long text-[11px] transition-opacity" :class="autocorrectIndex != i ? 'opacity-0' : 'opacity-100'"></i>
              <span v-html="node.hl"></span>
            </router-link>
          </li>
        </ul>
      </div>

      <div v-if="searchHelpOn" class="as-box p-6 mt-4">
        <div class="flex items-center justify-between mb-2">
          <router-link to="/notice?topicNo=141" class="as-section-title">애니메이션 검색 도움말</router-link>
          <button @click="searchHelpOn = false" class="nav-icon-sm" aria-label="닫기"><i class="fa-solid fa-xmark"></i></button>
        </div>
        <div class="grid gap-x-8 gap-y-6 md:grid-cols-2 xl:grid-cols-3 mt-5">

          <div class="help-item">
            <div class="help-label">키워드 검색 (AND 조건)</div>
            <div class="help-query"><i class="fa-solid fa-magnifying-glass"></i> 비밥 카우</div>
            <div class="help-desc">
              단어중 "비밥" "카우" 포함시 검색됩니다.<br/>
              검색결과) 카우보이비밥
            </div>
          </div>

          <div class="help-item">
            <div class="help-label">띄어쓰기</div>
            <div class="help-query"><i class="fa-solid fa-magnifying-glass"></i> 체인소 맨</div>
            <div class="help-desc">
              단어중 "체인소" "맨" 포함시 검색됩니다.<br/>
              검색결과) 체인소 맨
            </div>
          </div>

          <div class="help-item">
            <div class="help-label">형태소</div>
            <div class="help-query"><i class="fa-solid fa-magnifying-glass"></i> 체인소맨</div>
            <div class="help-desc">
              단어중 "체인소맨"이 포함시 검색됩니다.<br/>
              형태소 분석기가 "체인소맨"을 "체인소 맨"으로 분해하지 못할 경우 "체인소 맨"이 검색되지 않습니다.<br/>
              쉽게 말해서 띄어쓰기를 최대한 하여 검색하는 것을 추천합니다.
            </div>
          </div>

          <div class="help-item">
            <div class="help-label">장르검색 (AND 조건)</div>
            <div class="help-query"><i class="fa-solid fa-magnifying-glass"></i> #판타지 #모험</div>
            <div class="help-desc">판타지이면서 동시에 모험인 장르를 검색합니다.</div>
          </div>

          <div class="help-item">
            <div class="help-label">자막제작자 검색 (OR 조건)</div>
            <div class="help-query"><i class="fa-solid fa-magnifying-glass"></i> @철수 @영희</div>
            <div class="help-desc">자막제작자가 철수이거나 영희인 경우 모두를 검색합니다.</div>
          </div>

          <div class="help-item">
            <div class="help-label">완결 작품만 검색 (최신순)</div>
            <div class="help-query"><i class="fa-solid fa-magnifying-glass"></i> /완결</div>
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
        <div class="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          <div v-for="(node, i) in list.content" :key="node.animeNo" class="as-card p-5 flex flex-col">
            <router-link :to="toAnimeViewUrl(node.animeNo)" class="group block">
              <div class="text-md font-semibold text-ink group-hover:text-brand transition-colors">{{node.subject}}</div>
              <div class="text-xs mt-1.5 text-ink-3" lang="ja" v-if="node.originalSubject">{{node.originalSubject}}</div>
            </router-link>
            <div class="mt-4 pt-0 flex flex-wrap gap-1.5">
              <span class="as-tag-xs" v-for="tag in node.tags" :key="tag">{{tag}}</span>
              <router-link class="as-tag-xs" v-for="tag in node.genres.split(/,/g)" :key="tag" :to="`/anime?q=%23${encodeURIComponent(tag)}`">{{tag}}</router-link>
              <a class="as-tag-xs" v-if="node.website" :href="node.website" target="_blank"><i class="fa-solid fa-globe"></i></a>
              <a class="as-tag-xs" v-if="node.x" :href="node.x" target="_blank"><i class="fa-brands fa-x-twitter"></i></a>
              <span class="as-tag-xs" v-if="node.captionCount"><i class="fa-regular fa-closed-captioning mr-1 opacity-70"></i>{{node.captionCount}}</span>
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
import {nextTick, onUnmounted, Ref, ref} from "vue";
import PageData from "../../common/PageData";
import Anime from "../../domain/anime/Anime";
import animeRemote from "../../domain/anime/remote/animeRemote";
import AnimeAutocorrect from "../../domain/anime/AnimeAutocorrect";
import {onBeforeRouteUpdate, useRouter} from "vue-router";
import {Locate} from "raon";
import { ScrollLoader } from "raon";

const list = ref(PageData.empty().notLoaded()) as Ref<PageData<Anime>>;
const anime = ref(null) as Ref<Anime|null>;
let lastAnimeNo = -1;
const page = ref(0);
const query = ref<string>(new Locate().getParameter('q', '') as string);
const autocorrect = ref([]) as Ref<AnimeAutocorrect[]>;
const autocorrectOn = ref(false);
const searchHelpOn = ref(false);
const autocorrectIndex = ref(-1);
let autocorrectQuery = '';
const router = useRouter();

const sl = new ScrollLoader().onNeedNextPage(() => {
  page.value++;
  loadList();
});

function load(locate: Locate = new Locate()) {
  autocorrectOn.value = false;
  const q = locate.getParameter('q', '')!!;
  if (query.value != q) {
    page.value = 0;
  }
  query.value = q;
  loadAnime(locate);
  loadList();
}

function loadAnime(locate: Locate) {
  const animeNo = locate.getIntParameter('animeNo', -1);
  if (animeNo > 0) {
    if (lastAnimeNo != animeNo) {
      lastAnimeNo = animeNo;
      animeRemote.getAnime(animeNo).then(node => anime.value = node);
    }
  } else {
    lastAnimeNo = -1;
    anime.value = null;
  }
}

function loadList() {
  const isFirstPage = page.value == 0;

  animeRemote.getAnimeList(page.value, query.value).then(pageData => {
    if (isFirstPage) {
      list.value = pageData;
    } else {
      list.value = list.value.merge(pageData);
    }
    nextTick(() => sl.watch(pageData.next))
  });
}

function searchAnime() {
  page.value = 0;
  autocorrectOn.value = false;
  const q = query.value;
  if (q.indexOf('/도움말') != -1) {
    searchHelpOn.value = true;
    query.value = query.value.split('/도움말').join('').trim();
    return;
  }
  searchHelpOn.value = false;
  router.push(`/anime?q=${encodeURIComponent(q)}`);
}

function loadAutocorrect(event: KeyboardEvent) {
  const word = (event.target as any).value;
  if (autocorrectQuery != word) {
    autocorrectOn.value = true;
    autocorrectIndex.value = -1;
    autocorrectQuery = word;
    animeRemote.getAnimeListAutocorrect(word).then(list => autocorrect.value = list);
  }
}

function keyAutocorrect(event: KeyboardEvent) {
  const key = event.key;
  const len = autocorrect.value.length;

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
      if (!autocorrect.value.length || autocorrectIndex.value == -1) {
        searchAnime();
      } else {
        router.push(`/anime?animeNo=${autocorrect.value[autocorrectIndex.value].key}`)
      }
      return;
  }
}

function toAnimeViewUrl(animeNo: number) {
  return new Locate().setParameter('animeNo', animeNo+'').fullPath
}

function getNowSearchedQuery() {
  return new Locate().getParameter('q', '');
}

load();

onBeforeRouteUpdate((to, from, next) => {
  load(new Locate(to.fullPath));
  next();
});

onUnmounted(() => {
  sl.destroy();
});
</script>

<style scoped>
@reference "../../common/tailwind.pcss";

.nav-icon-sm {
  @apply inline-flex items-center justify-center w-8 h-8 rounded-ctl text-sm transition-colors;
  color: var(--as-ink-3);
  &:hover { color: var(--as-ink); background: var(--as-muted) }
}

.status-pill {
  @apply inline-flex items-center gap-2 px-3 py-1.5 text-xs font-semibold;
  border-radius: 999px;
  background: var(--as-muted);
  color: var(--as-ink-2);
  .dot {
    width: 6px;
    height: 6px;
    border-radius: 999px;
    background: var(--as-ink-3);
  }
  &.is-on {
    background: color-mix(in oklab, var(--as-success) 13%, transparent);
    color: var(--as-success);
    .dot { background: var(--as-success) }
  }
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
