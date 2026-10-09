<template>
  <div>

    <nav v-choice class="as-segment admin-tabs">
      <a v-for="t in tabs" :key="t.id" :class="{'is-on': tab == t.id}" @click="tab = t.id">
        <img :src="t.icon" alt=""/><span>{{ t.name }}</span>
      </a>
    </nav>

    <div v-if="tab == 'anime'" class="mt-8">

      <table class="as-table as-table-kv">
        <thead>
          <tr><th colspan="2" class="text-center">애니메이션 편집</th></tr>
        </thead>
        <tbody>
          <tr>
            <th>제목</th>
            <td><input type="text" v-model="subject" placeholder="제목" class="p-2.5 as-input-text"></td>
          </tr>
          <tr>
            <th>원제</th>
            <td><input type="text" value="葬送のフリーレン 第2期" placeholder="원제" class="p-2.5 as-input-text" lang="ja"></td>
          </tr>
          <tr>
            <th>장르</th>
            <td>
              <div @click="genreOpen = !genreOpen" class="cursor-pointer">
                <span v-for="g in genres" :key="g" class="tu-chip-sm is-on">{{ g }}</span>
              </div>
              <div v-if="genreOpen" class="mt-3">
                <span v-for="g in allGenres" :key="g" class="tu-chip-sm" :class="{'is-on': genres.includes(g)}" @click="toggleGenre(g)">{{ g }}</span>
              </div>
            </td>
          </tr>
          <tr>
            <th>상태</th>
            <td>
              <div v-choice class="as-segment as-segment-inline">
                <label v-for="s in statuses" :key="s.v" :class="{'is-on': status == s.v}" @click="status = s.v">{{ s.t }}</label>
              </div>
            </td>
          </tr>
          <tr>
            <th>요일</th>
            <td>
              <div v-choice class="as-segment as-segment-inline">
                <label v-for="(w, i) in weeks" :key="w" :class="{'is-on': week == i}" @click="week = i">{{ w }}</label>
              </div>
            </td>
          </tr>
          <tr>
            <th>시간</th>
            <td><input type="time" value="23:00" class="p-2 as-input-text w-min!"/></td>
          </tr>
          <tr>
            <th>시작일</th>
            <td>
              <div class="flex flex-wrap items-center gap-x-4 gap-y-2">
                <div v-choice class="as-segment as-segment-inline">
                  <label v-for="d in dateTypes" :key="d" :class="{'is-on': dateType == d}" @click="dateType = d">{{ d }}</label>
                </div>
                <div v-if="dateType != 'N/A'" class="flex items-center gap-1.5 text-sm text-ink-2">
                  <input type="text" value="2026" maxlength="4" class="as-input-text w-[58px]! py-1.5 text-center text-xs!"> 년
                  <template v-if="dateType != 'Y'">
                    <input type="text" value="10" maxlength="2" class="as-input-text w-[40px]! py-1.5 text-center text-xs!"> 월
                    <template v-if="dateType != 'YM'">
                      <input type="text" value="03" maxlength="2" class="as-input-text w-[40px]! py-1.5 text-center text-xs!"> 일
                    </template>
                  </template>
                </div>
              </div>
            </td>
          </tr>
          <tr>
            <th>웹사이트</th>
            <td><input type="text" value="https://frieren-anime.jp" class="p-2.5 as-input-text"></td>
          </tr>
          <tr>
            <th>자막참여자</th>
            <td>
              <span v-for="c in ['코코렛', '수퍼소닉EX', '별명따위']" :key="c" class="mr-4 text-ink-2">{{ c }}</span>
              <button class="as-input-btn py-1.5">자막참여</button>
            </td>
          </tr>
        </tbody>
      </table>
      <div class="mt-4 flex justify-between">
        <button class="as-input-btn py-2 tu-btn-danger"><i class="fa-solid fa-trash mr-1.5 text-xs"></i>삭제</button>
        <button class="as-btn-primary py-2" @click="toast.success('애니메이션이 수정되었습니다.')">저장</button>
      </div>

      <div v-choice class="as-segment mt-12 max-w-[360px]">
        <button :class="{'is-on': listState == 'list'}" @click="listState = 'list'">전체</button>
        <button :class="{'is-on': listState == 'delist'}" @click="listState = 'delist'">삭제대기</button>
      </div>

      <div class="relative mt-6">
        <div class="flex absolute inset-y-0 left-0 items-center pl-3 pointer-events-none text-ink-3">
          <i class="fa-solid fa-magnifying-glass"></i>
        </div>
        <input type="text" class="p-3 pl-9 as-input-text" placeholder="애니메이션 검색 : 검색어 #장르 @제작자 /완결">
        <button class="as-input-btn absolute px-4 py-1.5 right-2 bottom-1.5">신규</button>
      </div>

      <div class="mt-4">
        <div class="as-meta text-right mb-3">총 <b class="text-ink-2">1,284</b> 작품</div>
        <div class="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          <div v-for="a in animes" :key="a.subject" class="p-5 as-card">
            <button v-if="listState == 'delist'" class="as-input-btn float-right py-1.5 text-xs!">복원</button>
            <div class="text-md font-semibold text-ink">{{ a.subject }}</div>
            <div class="text-xs mt-1.5 text-ink-3" lang="ja">{{ a.original }}</div>
            <div class="mt-4 flex flex-wrap gap-1.5">
              <span class="as-tag-xs tu-tag-brand">{{ a.tag }}</span>
              <span v-for="g in a.genres" :key="g" class="as-tag-xs">{{ g }}</span>
              <span class="as-tag-xs"><i class="fa-solid fa-globe"></i></span>
              <span class="as-tag-xs"><i class="fa-brands fa-x-twitter"></i></span>
              <span class="as-tag-xs"><i class="fa-regular fa-closed-captioning mr-1 opacity-70"></i>{{ a.captions }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div v-else-if="tab == 'caption'" class="mt-8">
      <div v-choice class="as-segment max-w-[360px]">
        <button :class="{'is-on': captionState == 1}" @click="captionState = 1">방영중</button>
        <button :class="{'is-on': captionState == 0}" @click="captionState = 0">완료</button>
      </div>
      <div class="mt-5 grid gap-4 md:grid-cols-2 xl:grid-cols-3">
        <div v-for="c in captionList" :key="c.subject" class="p-5 as-card">
          <div class="flex items-start gap-2 text-lg pt-1 px-1 text-ink font-semibold">
            <span class="flex-1">{{ c.subject }}</span>
            <i class="fa-solid fa-xmark p-[3px] cursor-pointer opacity-40 hover:opacity-100 hover:text-danger transition"></i>
          </div>
          <div class="mt-4 flex">
            <button class="w-[60px] mr-2 as-input-btn" @click="c.episode = Math.max(0, c.episode - 1)">◀</button>
            <input type="number" v-model="c.episode" class="text-center p-2.5 as-input-text">
            <button class="w-[60px] ml-2 as-input-btn" @click="c.episode++">▶</button>
          </div>
          <div class="mt-4 flex">
            <input type="datetime-local" :value="c.updDt" class="text-center p-2.5 as-input-text"/>
            <button class="w-[60px] ml-2 as-input-btn">현재</button>
          </div>
          <div class="mt-4 flex">
            <input type="text" :value="c.website" placeholder="https://example.com" class="p-2.5 as-input-text">
            <button class="w-[60px] ml-2 as-btn-primary">저장</button>
          </div>
        </div>
      </div>
    </div>

    <div v-else-if="tab == 'panel'" class="mt-8 grid gap-6 lg:grid-cols-3 items-start">
      <div class="lg:col-span-2">
        <ActivePanel mode="admin"/>
      </div>
      <div class="as-sections">
        <div v-for="k in kpis" :key="k.label" class="as-card p-5">
          <div class="as-sub-title">{{ k.label }}</div>
          <div class="mt-2 flex items-baseline gap-2">
            <span class="text-3xl font-bold text-ink tabular-nums">{{ k.value }}</span>
            <span class="text-xs font-semibold" :class="k.up ? 'text-success' : 'text-danger'">{{ k.diff }}</span>
          </div>
        </div>
      </div>
    </div>

    <div v-else class="mt-8 as-box p-10 text-center">
      <div class="mx-auto w-14 h-14 rounded-2xl grid place-items-center bg-brand-soft text-brand text-2xl">
        <i class="fa-regular fa-calendar-days"></i>
      </div>
      <p class="as-desc mt-4">편성표 관리 화면은 기존 편성표 스타일을 따로 쓰므로 이 미리보기에서 제외합니다.</p>
    </div>

  </div>
</template>

<script setup lang="ts">
import {ref} from "vue";
import ActivePanel from "../../../domain/activePanel/ActivePanel.vue";
import toast from "../../../common/toast";
import tabPanel from "../../admin/layout/tab-panel.svg";
import tabAnime from "../../admin/layout/tab-anime.svg";
import tabSchedule from "../../admin/layout/tab-schedule.svg";
import tabCaption from "../../admin/layout/tab-caption.svg";

const tabs = [
  {id: 'panel', name: '관리패널', icon: tabPanel},
  {id: 'anime', name: '애니메이션', icon: tabAnime},
  {id: 'schedule', name: '편성표', icon: tabSchedule},
  {id: 'caption', name: '자막', icon: tabCaption},
];
const tab = ref('anime');

const subject = ref('장송의 프리렌 2기');
const genreOpen = ref(false);
const allGenres = ['SF', '드라마', '로맨스', '모험', '미스터리', '스포츠', '아이돌', '액션', '이세계', '일상', '코미디', '판타지', '호러'];
const genres = ref(['판타지', '모험', '드라마']);
const statuses = [{v: 'ON', t: '편성표'}, {v: 'OFF', t: '편성표-결방'}, {v: 'END', t: '완결'}];
const status = ref('ON');
const weeks = ['일', '월', '화', '수', '목', '금', '토', '외', '신작'];
const week = ref(5);
const dateTypes = ['N/A', 'Y', 'YM', 'YMD'];
const dateType = ref('YMD');
const listState = ref('list');
const captionState = ref(1);

function toggleGenre(g: string) {
  genres.value = genres.value.includes(g) ? genres.value.filter(e => e != g) : [...genres.value, g];
}

const animes = [
  {subject: '장송의 프리렌 2기', original: '葬送のフリーレン 第2期', tag: '금 23:00', genres: ['판타지', '모험'], captions: 4},
  {subject: '전생했더니 검이었습니다 2기', original: '転生したら剣でした 2', tag: '수 22:30', genres: ['판타지'], captions: 2},
  {subject: '하늘은 붉은 강가', original: '天は赤い河のほとり', tag: '목 24:00', genres: ['로맨스'], captions: 1},
];

const captionList = ref([
  {subject: 'FX 전사 쿠루미', episode: 2, updDt: '2026-10-08T23:40', website: 'https://cocolet.example.com/kurumi-02'},
  {subject: 'NANA -나나-', episode: 1, updDt: '2026-10-08T01:10', website: ''},
  {subject: '하늘은 붉은 강가', episode: 14, updDt: '2026-10-07T22:05', website: 'https://kiriyu.example.com/14'},
]);

const kpis = [
  {label: '방영중 작품', value: '142', diff: '+12', up: true},
  {label: '이번 주 자막', value: '318', diff: '+8.4%', up: true},
  {label: '심사 대기', value: '3', diff: '-2', up: false},
];
</script>
