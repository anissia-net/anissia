<template>
  <div>

    <div class="as-box overflow-hidden mb-12">
      <div class="p-6 md:p-8">
        <div class="flex flex-wrap items-center gap-x-3 gap-y-2">
          <span class="tu-status is-on"><span class="dot"></span>방영중 · 매주 (금) 23:00</span>
          <span class="as-meta">2026-10-03 ~ 2026-12-26</span>
        </div>
        <h1 class="as-page-title mt-4">장송의 프리렌 2기</h1>
        <div class="as-desc mt-1.5" lang="ja">葬送のフリーレン 第2期</div>
        <div class="mt-5 flex flex-wrap items-center gap-1.5">
          <span class="as-tag-xs">판타지</span>
          <span class="as-tag-xs">모험</span>
          <span class="as-tag-xs">드라마</span>
          <span class="w-px h-4 mx-1 bg-line-2"></span>
          <span class="as-tag-xs"><i class="fa-solid fa-globe mr-1.5 opacity-70"></i>공식 사이트</span>
          <span class="as-tag-xs"><i class="fa-brands fa-x-twitter opacity-70"></i></span>
        </div>
      </div>
      <div class="px-6 md:px-8 pt-4 pb-3 border-t border-line">
        <div class="as-sub-title py-2">자막정보</div>
        <ul>
          <li v-for="c in captions" :key="c.name" class="as-row flex items-center gap-4 py-2.5 text-sm">
            <span class="w-16 shrink-0">
              <span v-if="c.ep" class="as-link font-semibold">{{ c.ep }}</span>
              <span v-else class="text-ink-3">준비중</span>
            </span>
            <span class="as-link flex-1 min-w-0 truncate">{{ c.name }}</span>
            <span class="as-meta whitespace-nowrap">{{ c.time }}</span>
          </li>
        </ul>
      </div>
    </div>

    <div class="relative">
      <div class="flex absolute inset-y-0 left-0 items-center pl-4 pointer-events-none text-ink-3 h-[52px]">
        <i class="fa-solid fa-magnifying-glass"></i>
      </div>
      <input type="text" v-model="query" class="py-3.5 pl-11 pr-24 as-input-text text-base!" placeholder="애니검색  #장르  @제작자  /완결  /도움말">
      <button class="as-btn-primary absolute py-1.5 right-2 top-[26px] -translate-y-1/2">검색</button>

      <ul class="as-box mt-2 py-1.5 overflow-hidden shadow-pop relative z-10">
        <li v-for="(a, i) in suggest" :key="a" class="mx-1.5 rounded-ctl transition-colors cursor-pointer" :class="index == i ? 'bg-brand-soft' : ''" @mouseover="index = i">
          <span class="flex items-center gap-2.5 px-3 py-2 text-sm" :class="index == i ? 'text-brand' : 'text-ink-2'">
            <i class="fa-solid fa-arrow-right-long text-[11px] transition-opacity" :class="index != i ? 'opacity-0' : 'opacity-100'"></i>
            <span><b class="text-brand">프리</b>{{ a }}</span>
          </span>
        </li>
      </ul>
    </div>

    <div class="mt-8">
      <div class="as-meta text-right mb-3">총 <b class="text-ink-2">1,284</b> 작품</div>
      <div class="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
        <div v-for="a in list" :key="a.subject" class="as-card p-5 flex flex-col">
          <div class="group block cursor-pointer">
            <div class="text-md font-semibold text-ink group-hover:text-brand transition-colors">{{ a.subject }}</div>
            <div class="text-xs mt-1.5 text-ink-3" lang="ja">{{ a.original }}</div>
          </div>
          <div class="mt-4 flex flex-wrap gap-1.5">
            <span v-if="a.status" class="as-tag-xs tu-tag-brand">{{ a.status }}</span>
            <span v-for="g in a.genres" :key="g" class="as-tag-xs">{{ g }}</span>
            <span class="as-tag-xs"><i class="fa-regular fa-closed-captioning mr-1 opacity-70"></i>{{ a.captions }}</span>
          </div>
        </div>
      </div>
    </div>

  </div>
</template>

<script setup lang="ts">
import {ref} from "vue";

const query = ref('프리');
const index = ref(0);
const suggest = ['렌 2기', '렌', '즈너 ~감옥의 소녀~'];

const captions = [
  {name: '코코렛', ep: '2화', time: '23시간 전'},
  {name: '수퍼소닉EX', ep: '2화', time: '1일 전'},
  {name: '별명따위', ep: '1화', time: '6일 전'},
  {name: '하늬', ep: '', time: '-'},
];

const list = [
  {subject: '장송의 프리렌 2기', original: '葬送のフリーレン 第2期', status: '방영중', genres: ['판타지', '모험'], captions: 4},
  {subject: '전생했더니 검이었습니다 2기', original: '転生したら剣でした 2', status: '방영중', genres: ['판타지', '이세계'], captions: 2},
  {subject: '하늘은 붉은 강가', original: '天は赤い河のほとり', status: '방영중', genres: ['로맨스', '판타지'], captions: 1},
  {subject: '탐정은 이미 죽었다 Season2', original: '探偵はもう、死んでいる。 Season2', status: '', genres: ['미스터리'], captions: 3},
  {subject: '마법소녀 육성계획 restart', original: '魔法少女育成計画 restart', status: '', genres: ['액션', '판타지'], captions: 2},
  {subject: 'NANA -나나-', original: 'NANA', status: '완결', genres: ['드라마', '음악'], captions: 5},
];
</script>
