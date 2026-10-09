<template>

  <div class="as-page">

    <div v-if="anime" class="mb-10">
      <div v-if="anime.animeNo > 0 || (anime.animeNo == 0 && animeNo == 0)">

        <table class="as-table as-table-kv">
          <thead>
            <tr>
              <th colspan="2" class="text-center">애니메이션 {{anime.animeNo != 0 ? '편집' : '신규등록'}}</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <th>제목</th>
              <td>
                <input type="text" v-model="anime.subject" name="subject" placeholder="제목" class="p-2.5 as-input-text">
              </td>
            </tr>
            <tr>
              <th>원제</th>
              <td>
                <input type="text" v-model="anime.originalSubject" name="originalSubject" placeholder="원제" class="p-2.5 as-input-text" lang="ja">
              </td>
            </tr>
            <tr>
              <th>장르</th>
              <td>
                <div @click="toggleGenreOpen">
                  <div v-for="genre in anime.editGenres" :key="genre" class="chip">
                    {{genre}}
                  </div>
                  <div v-if="anime.editGenres.length == 0" class="py-1.5 inline-block cursor-pointer text-sm text-ink-3">
                    선택된 장르가 없습니다.
                  </div>
                </div>
                <div v-if="anime?.editGenresOpen" class="mt-3">
                  <div v-for="genre in genres" :key="genre" @click="toggleGenre(genre)" class="chip" :class="{'is-on': anime.editGenres.indexOf(genre) != -1}">
                    {{genre}}
                  </div>
                </div>

              </td>
            </tr>
            <tr>
              <th>상태</th>
              <td>
                <div v-choice class="as-segment as-segment-inline">
                <label :class="{'is-on': anime.status == 'ON'}">
                  <input type="radio" name="status" v-model="anime.status" value="ON" class="hidden" />편성표
                </label>
                <label :class="{'is-on': anime.status == 'OFF'}">
                  <input type="radio" name="status" v-model="anime.status" value="OFF" class="hidden" />편성표-결방
                </label>
                <label :class="{'is-on': anime.status == 'END'}">
                  <input type="radio" name="status" v-model="anime.status" value="END" class="hidden" />완결
                </label>
                </div>
              </td>
            </tr>
            <tr>
              <th>요일</th>
              <td>
                <div v-choice class="as-segment as-segment-inline">
                <label v-for="(week, i) in weekList" :key="week" :class="{'is-on': (i+'') == anime.week}">
                  <input type="radio" name="week" v-model="anime.week" :value="i+''" class="hidden" />
                  {{week}}
                </label>
                </div>
              </td>
            </tr>
            <tr v-if="anime.week != '7'">
              <th>시간</th>
              <td>
                <input type="time" v-model="anime.time" class="p-2 as-input-text w-min!"/>
              </td>
            </tr>
            <tr>
              <th>시작일</th>
              <td>
                <div class="date-edit">
                  <div v-choice class="as-segment as-segment-inline">
                    <label v-for="dateType in dateTypeList" :key="dateType" :class="{'is-on': dateType == anime.editStartDateType}">
                      <input type="radio" name="startDate" v-model="anime.editStartDateType" :value="dateType" class="hidden" />
                      {{dateType}}
                    </label>
                  </div>
                  <div v-if="anime.editStartDateType != 'N/A'" class="date-fields">
                    <input type="text" v-model="anime.editStartDateYear" maxlength="4" placeholder="YYYY" class="as-input-text w-[58px]! py-1.5 text-center text-xs!"> 년
                    <template v-if="anime.editStartDateType != 'Y'">
                      <input type="text" v-model="anime.editStartDateMonth" maxlength="2" placeholder="MM" class="as-input-text w-[40px]! py-1.5 text-center text-xs!"> 월
                      <template v-if="anime.editStartDateType != 'YM'">
                        <input type="text" v-model="anime.editStartDateDate" maxlength="2" placeholder="DD" class="as-input-text w-[40px]! py-1.5 text-center text-xs!"> 일
                      </template>
                    </template>
                  </div>
                </div>
              </td>
            </tr>
            <tr>
              <th>종료일</th>
              <td>
                <div class="date-edit">
                  <div v-choice class="as-segment as-segment-inline">
                    <label v-for="dateType in dateTypeList" :key="dateType" :class="{'is-on': dateType == anime.editEndDateType}">
                      <input type="radio" name="endDate" v-model="anime.editEndDateType" :value="dateType" class="hidden" />
                      {{dateType}}
                    </label>
                  </div>
                  <div v-if="anime.editEndDateType != 'N/A'" class="date-fields">
                    <input type="text" v-model="anime.editEndDateYear" maxlength="4" placeholder="YYYY" class="as-input-text w-[58px]! py-1.5 text-center text-xs!"> 년
                    <template v-if="anime.editEndDateType != 'Y'">
                      <input type="text" v-model="anime.editEndDateMonth" maxlength="2" placeholder="MM" class="as-input-text w-[40px]! py-1.5 text-center text-xs!"> 월
                      <template v-if="anime.editEndDateType != 'YM'">
                        <input type="text" v-model="anime.editEndDateDate" maxlength="2" placeholder="DD" class="as-input-text w-[40px]! py-1.5 text-center text-xs!"> 일
                      </template>
                    </template>
                  </div>
                </div>
              </td>
            </tr>
            <tr>
              <th>웹사이트</th>
              <td>
                <input type="text" v-model="anime.website" name="website" placeholder="웹사이트" class="p-2.5 as-input-text">
              </td>
            </tr>
            <tr>
              <th>X</th>
              <td>
                <input type="text" v-model="anime.x" name="x" placeholder="X" class="p-2.5 as-input-text">
              </td>
            </tr>
            <tr v-if="anime.animeNo > 0">
              <th>비고</th>
              <td>
                <input type="text" v-model="anime.note" name="x" placeholder="비고" class="p-2.5 as-input-text">
              </td>
            </tr>
            <tr v-if="anime.animeNo != 0">
              <th>자막참여자</th>
              <td>
                <span v-for="cp in anime.captions" :key="cp.name" class="mr-4">{{cp.name}}</span>
                <input type="button" value="자막참여" @click="addCaption()" class="as-input-btn p-1.5" />
              </td>
            </tr>
          </tbody>
        </table>
        <div class="mt-4 flex justify-between gap-2">
          <input type="button" value="삭제" @click="doDelete()" class="as-input-btn py-2 hover:text-danger!" />
          <input type="button" value="저장" @click="doSave()" class="as-btn-primary py-2" />
        </div>


      </div>
      <div v-else>
        <div class="text-xl text-center my-32">
          존재하지 않거나 삭제된 애니메이션 입니다.
        </div>
      </div>

    </div>

    <div v-choice class="as-segment mt-2">
      <router-link to="/admin/anime" :class="{'is-on': state === 'list'}">
        전체
      </router-link>
      <router-link to="/admin/anime?state=delist" :class="{'is-on': state === 'delist'}">
        삭제대기
      </router-link>
    </div>

    <div class="relative mt-6">

      <div v-if="state == 'list'" class="relative">
        <div class="flex absolute inset-y-0 left-0 items-center pl-3 pointer-events-none">
          <i class="fa-solid fa-magnifying-glass"></i>
        </div>
        <input type="text" id="default-search" autocomplete="off" class="p-3 pl-9 as-input-text" placeholder="애니메이션 검색 : 검색어 #장르 @제작자 /완결"  v-model="query" @click="autocorrectOn = true" @keydown="keyAutocorrect" @keyup="loadAutocorrect">
        <router-link to="/admin/anime?animeNo=0" type="button" class="as-input-btn absolute px-4 py-1.5 right-2 bottom-1.5" >신규</router-link>
      </div>

      <div v-if="autocorrectOn && autocorrect.length">
        <ul class="autocorrect-list as-box mt-2 py-1.5 overflow-hidden shadow-pop">
          <li v-for="(node, i) in autocorrect" class="mx-1.5 rounded-ctl transition-colors cursor-pointer" :class="autocorrectIndex == i ? 'bg-brand-soft' : ''" @mouseover="autocorrectIndex = i">
            <router-link :to="`/admin/anime?animeNo=${node.key}`" class="flex items-center gap-2.5 px-3 py-2 text-sm" :class="autocorrectIndex == i ? 'text-brand' : 'text-ink-2'">
              <i class="fa-solid fa-arrow-right-long text-[11px] transition-opacity" :class="autocorrectIndex != i ? 'opacity-0' : 'opacity-100'"></i>
              <span v-html="node.hl"></span>
            </router-link>
          </li>
        </ul>
      </div>

      <div v-if="!list.empty" class="mt-4">
        <div class="as-meta text-right mb-3">
          총 <b class="text-ink-2">{{list.totalElements}}</b> 작품
        </div>
        <div class="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          <div v-for="(node, i) in list.content" class="p-5 as-card flex flex-col">
            <div>
              <router-link v-if="!node.agendaNo" :to="toAnimeViewUrl(node.animeNo)">
                <div class="text-md font-semibold text-ink">{{node.subject}}</div>
                <div class="text-xs mt-1.5 text-ink-3" lang="ja" v-if="node.originalSubject">{{node.originalSubject}}</div>
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
              <span class="as-tag-xs" v-if="node.website"><a :href="node.website" target="_blank"><i class="fa-solid fa-globe"></i></a></span>
              <span class="as-tag-xs" v-if="node.x"><a :href="node.x" target="_blank" class="fa-brands fa-x-twitter"></a></span>
              <span class="as-tag-xs" v-if="node.captionCount"><i class="fa-regular fa-closed-captioning mr-1 opacity-70"></i>{{node.captionCount}}</span>
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

import {nextTick, onUnmounted, Ref, ref} from "vue";
import PageData from "../../common/PageData";
import Anime from "../../domain/anime/Anime";
import animeRemote from "../../domain/anime/remote/animeRemote";
import AnimeAutocorrect from "../../domain/anime/AnimeAutocorrect";
import {onBeforeRouteUpdate, useRouter} from "vue-router";
import {Locate} from "raon";
import { ScrollLoader } from "raon";
import anissia from "../../common/anissia";
import toast from "../../common/toast";

const list = ref(PageData.empty().notLoaded()) as Ref<PageData<Anime>>;
const anime = ref(null) as Ref<Anime|null>;
const animeNo = ref(-1);
let lastAnimeNo = -1;
const page = ref(0);
const query = ref<string>(new Locate().getParameter('q', '') as string);
const autocorrect = ref([]) as Ref<AnimeAutocorrect[]>;
const autocorrectOn = ref(false);
const autocorrectIndex = ref(-1);
let autocorrectQuery = '';
const router = useRouter();
const weekList = ref(['日', '月', '火', '水', '木', '金', '土', '外', '新']);
const dateTypeList = ref(['YMD', 'YM', 'Y', 'N/A']);
const genres = ref([]) as Ref<string[]>;
const state = ref('list');

const sl = new ScrollLoader().onNeedNextPage(() => {
  page.value++;
  loadList();
});

function init() {
  animeRemote.getGenres().then(_genres => genres.value = _genres)
}

function clear(locate: Locate) {
  state.value = locate.getParameter('state', 'list') == 'list' ? 'list' : 'delist';
  page.value = 0;
}

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

function loadAnime(locate: Locate, forced: boolean = false) {
  if (forced) {
    lastAnimeNo = -1;
  }
  const no = animeNo.value = locate.getIntParameter('animeNo', -1);
  if (no > 0) {
    if (lastAnimeNo != no) {
      lastAnimeNo = no;
      animeRemote.getAnime(no).then(node => anime.value = node.bindEdit());
    }
  } else if (no == 0) {
    lastAnimeNo = -1;
    anime.value = new Anime().bindEdit();
  } else {
    lastAnimeNo = -1;
    anime.value = null;
  }
}

function loadList() {
  const isFirstPage = page.value == 0;

  if (state.value === 'list') {
    animeRemote.getAnimeList(page.value, query.value).then(pageData => {
      if (isFirstPage) {
        list.value = pageData;
      } else {
        list.value = list.value.merge(pageData);
      }
      nextTick(() => sl.watch(pageData.next))
    });
  } else if (state.value === 'delist') {
    animeRemote.getAdminAnimeDelist().then(pageData => {
      list.value = pageData;
    });
  }
}



function searchAnime() {
  page.value = 0;
  autocorrectOn.value = false;
  router.push(`/admin/anime?q=${encodeURIComponent(query.value)}`);
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

function toggleGenreOpen() {
  const ani = anime.value!!;
  ani.editGenresOpen = !ani.editGenresOpen;
}

function toggleGenre(genre: string) {
  const ani = anime.value!!;
  const genres = ani.editGenres;
  if (genres.indexOf(genre) != -1) {
    ani.editGenres = ani.editGenres.filter(e => e != genre);
  } else {
    if (genres.length >= 3) {
      ani.editGenres = [...ani.editGenres.slice(1, 3), genre];
    } else {
      ani.editGenres = [...ani.editGenres, genre];
    }
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
        router.push(`/admin/anime?animeNo=${autocorrect.value[autocorrectIndex.value].key}`)
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

function addCaption() {
  animeRemote.addAdminCaption(anime.value?.animeNo!!).then(result => {
    if (result.code == 'ok') {
      loadAnime(new Locate(), true);
    }
    toast.result(result);
  });
}

function doDelete() {
  if (confirm(`${anime.value?.subject}을(를) 삭제하시겠습니까?\n임의삭제시 권한박탈의 사유가됩니다.`)) {
    animeRemote.deleteAdminAnime(anime.value?.animeNo!!).then(result => {
      if (result.code == 'ok') {
        router.push(`/admin/anime`);
      } else if (result.message) {
        toast.error(result.message);
      }
    });
  }
}

function doRecover(anime: Anime) {
  if (confirm(`${anime.subject}을(를) 복원하시겠습니까?\n임의조작시 권한박탈의 사유가됩니다.`)) {
    animeRemote.recoverAdminAnime(anime.agendaNo).then(result => {
      if (result.code == 'ok') {
        router.push(`/admin/anime?animeNo=${result.data}`);
      } else if (result.message) {
        toast.error(result.message);
      }
    });
  }
}

function doSave() {
  const ani = (anime.value!!).applyEdit();
  const isNew = ani.animeNo == 0;
  if (confirm(`${ani.subject}을(를) ${isNew ? '추가' : '수정'} 하시겠습니까?\n임의조작시 권한박탈의 사유가됩니다.`)) {

    if (!anissia.checkAnimeDate(ani.editStartDateType, ani.startDate)) {
      toast.error('시작일을 입력해주세요.');
      return;
    }
    if (!anissia.checkAnimeDate(ani.editEndDateType, ani.endDate)) {
      toast.error('종료일을 입력해주세요.');
      return;
    }

    if (isNew) {
      animeRemote.addAdminAnime(ani).then(result => {
        if (result.code == 'ok') {
          router.push(`/admin/anime?animeNo=${result.data}`)
          toast.success('애니메이션이 추가되었습니다.');
        } else if (result.message) {
          toast.error(result.message);
        }
      });
    } else {
      animeRemote.updateAdminAnime(ani).then(result => {
        if (result.code == 'ok') {
          loadAnime(new Locate(), true);
          toast.success('애니메이션이 수정되었습니다.');
        } else if (result.message) {
          toast.error(result.message);
        }
      });
    }
  }
}

init();
clear(new Locate());
load();

onBeforeRouteUpdate((to, from, next) => {
  clear(new Locate(to.fullPath));
  load(new Locate(to.fullPath));
  next();
});

onUnmounted(() => {
  sl.destroy();
});
</script>

<style scoped>
@reference "../../common/tailwind.pcss";

.date-edit {
  @apply flex flex-wrap items-center gap-x-4 gap-y-2;
}

.date-fields {
  @apply flex items-center gap-1.5 text-sm;
  color: var(--as-ink-2);
}

.chip {
  @apply inline-block mr-2 mb-2 px-3 py-1.5 text-xs font-medium cursor-pointer select-none transition-all duration-150;
  border-radius: 4px;
  background: var(--as-muted);
  border: 1px solid var(--as-line);
  color: var(--as-ink-3);
  &:hover { border-color: var(--as-line-2); color: var(--as-ink-2) }
  &.is-on {
    background: var(--as-brand-soft);
    border-color: color-mix(in oklab, var(--as-brand) 35%, transparent);
    color: var(--as-brand);
  }
}

.autocorrect-list :deep(b) {
  color: var(--as-brand);
  font-weight: 700;
}
</style>
