<template>

  <div class="as-page">

    <div v-choice class="as-segment mt-2">
      <router-link to="/admin/anime" :class="{'is-on': state === 'list'}">
        전체
      </router-link>
      <router-link to="/admin/anime?state=delist" :class="{'is-on': state === 'delist'}">
        삭제대기
      </router-link>
    </div>

    <div class="relative mt-6">

      <div v-if="state == 'list'" class="as-input-with-icon">
        <as-icon name="search"/>
        <input type="text" id="default-search" autocomplete="off" class="py-3 pr-24 as-input-text" aria-label="애니메이션 검색" placeholder="애니메이션 검색 : 검색어 #장르 @제작자 /완결"  v-model="query" @click="autocorrectOn = true" @keydown="keyAutocorrect" @compositionstart="clearAutocorrect" @input="loadAutocorrect">
        <router-link to="/admin/anime/new" type="button" class="as-input-btn absolute px-4 py-1.5 right-2 bottom-1.5" >신규</router-link>
      </div>

      <div v-if="autocorrectOn && autocorrect.length">
        <ul class="autocorrect-list as-box mt-2 py-1.5 overflow-hidden shadow-pop">
          <li v-for="(node, i) in autocorrect" :key="node.key" class="mx-1.5 rounded-ctl transition-colors cursor-pointer" :class="autocorrectIndex == i ? 'bg-brand-soft' : ''" @mouseover="autocorrectIndex = i">
            <router-link :to="`/admin/anime/${node.key}`" class="flex items-center gap-2.5 px-3 py-2 text-sm" :class="autocorrectIndex == i ? 'text-brand' : 'text-ink-2'">
              <as-icon name="arrow-right-long" class="text-[0.6875rem] transition-opacity" :class="autocorrectIndex != i ? 'opacity-0' : 'opacity-100'"/>
              <span v-html="node.hl"></span>
            </router-link>
          </li>
        </ul>
      </div>

      <div v-if="!list.empty" class="mt-4">
        <div class="as-meta text-right mb-3">
          총 <b class="text-ink-2">{{list.totalElements}}</b> 작품
        </div>
        <div class="grid gap-4 cq-md:grid-cols-2 cq-xl:grid-cols-3">
          <div v-for="node in list.content" :key="node.agendaNo || node.animeNo" class="p-5 as-card flex flex-col">
            <div>
              <router-link v-if="!node.agendaNo" :to="toAnimeViewUrl(node.animeNo)" class="flex items-start gap-3">
                <img v-if="node.thumbnail" :src="node.thumbnail" alt="" width="64" height="64" loading="lazy" decoding="async" class="w-16 h-16 shrink-0 rounded-ctl object-cover"/>
                <div class="min-w-0">
                  <div class="text-md font-semibold text-ink">{{node.subject}}</div>
                  <div class="text-xs mt-1.5 text-ink-3" lang="ja" v-if="node.originalSubject">{{node.originalSubject}}</div>
                </div>
              </router-link>
              <div v-else>
                <input type="button" value="복원" @click="doRecover(node)" class="as-input-btn float-right py-1.5 text-xs!" />
                <div class="text-md font-semibold text-ink">{{node.subject}}</div>
                <div class="text-xs mt-1.5 text-ink-3" lang="ja" v-if="node.originalSubject">{{node.originalSubject}}</div>
              </div>
            </div>
            <div class="mt-auto pt-4 flex flex-wrap gap-1.5">
              <span class="as-tag-xs" v-for="tag in node.tags" :key="tag">{{tag}}</span>
              <span class="as-tag-xs" v-for="tag in node.genres.split(/,/g)" :key="tag"><router-link :to="`/admin/anime?q=%23${encodeURIComponent(tag)}`">{{tag}}</router-link></span>
              <a class="as-tag-xs as-icon-link" v-if="node.website" :href="node.website" target="_blank" :aria-label="`${node.subject} 공식 사이트 (새 창)`" :title="`${node.subject} 공식 사이트 (새 창)`"><as-icon name="globe"/></a>
              <a class="as-tag-xs as-icon-link" v-if="node.x" :href="node.x" target="_blank" :aria-label="`${node.subject} X 계정 (새 창)`" :title="`${node.subject} X 계정 (새 창)`"><as-icon name="x-logo"/></a>
              <span class="as-tag-xs" v-if="node.captionCount"><as-icon name="cc" class="mr-1 opacity-70"/>{{node.captionCount}}</span>
            </div>
          </div>
        </div>
      </div>
      <div v-else-if="list.loaded">
        <div class="text-xl text-center my-32">
          <b>{{getNowSearchedQuery()}}</b>에 대한 검색결과가 없습니다.
        </div>
      </div>


    </div>

  </div>

</template>

<script setup lang="ts">

import {nextTick, onDeactivated, onUnmounted, Ref, ref} from "vue";
import PageData from "../../common/PageData";
import Anime from "../../domain/anime/Anime";
import animeRemote from "../../domain/anime/remote/animeRemote";
import AnimeAutocorrect from "../../domain/anime/AnimeAutocorrect";
import {useRouter} from "vue-router";
import {Locate} from "raon";
import toast from "../../common/toast";

import {useListScroll} from '../../common/useListScroll';
import {animeUpdates} from '../../domain/anime/animeUpdates';

defineOptions({name: 'AdminAnimeList'});
const list = ref(PageData.empty().notLoaded()) as Ref<PageData<Anime>>;
const page = ref(0);
const query = ref<string>(new Locate().getParameter('q', '') as string);
const autocorrect = ref([]) as Ref<AnimeAutocorrect[]>;
const autocorrectOn = ref(false);
const autocorrectIndex = ref(-1);
let autocorrectQuery = '';
let autocorrectTimer: ReturnType<typeof setTimeout> | undefined;
let autocorrectRequest = 0;
let listRequest = 0;
let listQuery = '';
const router = useRouter();
const state = ref('list');

const sl = useListScroll(() => {
  page.value++;
  loadList();
});

let listKey = '';

function clear(locate: Locate) {
  state.value = locate.getParameter('state', 'list') == 'list' ? 'list' : 'delist';
}

function load(locate: Locate = new Locate()) {
  clearAutocorrect();
  const q = locate.getParameter('q', '')!!;
  query.value = q;
  const key = `${state.value}|${q}`;
  if (key != listKey) {
    listKey = key;
    listQuery = q;
    page.value = 0;
    loadList();
  }
}

animeUpdates.subscribe((id, changes) => {
  if (state.value !== 'list') return;
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

  if (state.value === 'list') {
    animeRemote.getAnimeList(page.value, listQuery).then(pageData => {
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
  } else if (state.value === 'delist') {
    animeRemote.getAdminAnimeDelist().then(pageData => {
      if (request === listRequest) list.value = pageData;
    });
  }
}



function searchAnime() {
  clearAutocorrect();
  router.push(`/admin/anime?q=${encodeURIComponent(query.value)}`);
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
        searchAnime();
      } else {
        router.push(`/admin/anime/${autocorrect.value[autocorrectIndex.value].key}`)
      }
      return;
  }
}

function toAnimeViewUrl(animeNo: number) {
  return `/admin/anime/${animeNo}`;
}

function getNowSearchedQuery() {
  return listQuery;
}

function doRecover(anime: Anime) {
  if (confirm(`${anime.subject}을(를) 복원하시겠습니까?\n임의조작시 권한박탈의 사유가됩니다.`)) {
    animeRemote.recoverAdminAnime(anime.agendaNo).then(result => {
      if (result.code == 'ok') {
        list.value.content = list.value.content.filter(item => item.agendaNo !== anime.agendaNo);
        list.value.totalElements--;
        router.push(`/admin/anime/${result.data}`);
      } else if (result.message) {
        toast.error(result.message);
      }
    });
  }
}

clear(new Locate());
load();

onDeactivated(clearAutocorrect);

onUnmounted(() => {
  clearAutocorrect();
  listRequest++;
});
</script>

<style scoped>
@reference "../../common/tailwind.pcss";

.autocorrect-list :deep(b) {
  color: var(--as-brand);
  font-weight: 700;
}
</style>
