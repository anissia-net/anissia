<template>
  <div class="as-page tu-page">

    <div class="tu-picker as-glass">
      <div v-for="g in groups" :key="g.id" class="tu-picker-row">
        <span class="tu-picker-label">{{ g.name }}</span>
        <div class="tu-picker-list">
          <button v-for="v in variants.filter(e => e.group == g.id)" :key="v.id" class="tu-chip" :class="{'is-on': v.id == variant.id}" @click="select({v: v.id})">
            <span class="tu-chip-dots"><span v-for="c in v.swatch" :style="{background: c}"></span></span>
            <span class="truncate">{{ v.name }}</span>
          </button>
        </div>
      </div>
      <div class="tu-picker-row">
        <span class="tu-picker-label">화면</span>
        <div class="tu-picker-list">
          <button v-for="p in screens" :key="p.id" class="tu-chip" :class="{'is-on': p.id == screen.id}" @click="select({p: p.id})">
            {{ p.name }}
          </button>
        </div>
      </div>
    </div>

    <div class="tu-desc as-glass">
      <b>{{ variant.name }}</b>
      <span v-for="t in variant.tags" :key="t" class="as-tag-xs ml-1.5">{{ t }}</span>
      <p class="mt-1">{{ variant.desc }}</p>
    </div>

    <div class="mt-8">
      <component :is="screen.component"/>
    </div>

  </div>
</template>

<script setup lang="ts">
import {computed, onBeforeUnmount, onMounted, watch} from "vue";
import {useRoute, useRouter} from "vue-router";
import "./testUi/testUi.pcss";
import TuHome from "./testUi/TuHome.vue";
import TuBoard from "./testUi/TuBoard.vue";
import TuApply from "./testUi/TuApply.vue";
import TuAnime from "./testUi/TuAnime.vue";
import TuAdmin from "./testUi/TuAdmin.vue";
import TuParts from "./testUi/TuParts.vue";

interface Variant {
  id: string;
  group: 'glass' | 'matte';
  name: string;
  tags: string[];
  desc: string;
  swatch: string[];
  glass?: boolean;
  matte?: boolean;
  font?: 'serif' | 'plex';
  num?: boolean;
}

const groups = [
  {id: 'glass', name: '리퀴드 글라스'},
  {id: 'matte', name: '매트 · 플랫'},
];

const variants: Variant[] = [
  {id: 'sapphire', group: 'glass', name: 'A. 현재 적용', tags: ['비교 기준'], swatch: ['#f3f5f9', '#2c4c8c', '#121a2b'],
    desc: '지금 사이트에 적용된 색.'},
  {id: 'pearl', group: 'glass', name: 'G1. Pearl Sapphire', tags: ['중립 펄', '사파이어 포인트'], glass: true, swatch: ['#f3f4f7', '#2f4f9a', '#121726'],
    desc: '바탕은 무채색 펄, 파랑은 단추·링크에만. 파란 계열을 남긴다면 가장 눈이 편한 안.'},
  {id: 'emerald', group: 'glass', name: 'G2. Emerald Atelier', tags: ['에메랄드', '세리프 제목'], glass: true, font: 'serif', swatch: ['#f3f4f1', '#0f6b55', '#10201a'],
    desc: '옅은 회백색 위 두꺼운 유리, 깊은 에메랄드 포인트와 세리프 제목. 차분하고 지적이다.'},
  {id: 'lavender', group: 'glass', name: 'G3. Lavender Frost', tags: ['라벤더', 'IBM Plex', '호버 굴절'], glass: true, font: 'plex', swatch: ['#f4f3f7', '#6050b0', '#17142a'],
    desc: '라벤더 포인트에 유리 테두리가 연보라·분홍·하늘빛으로 굴절되고, 카드에 올리면 빛이 흐른다.'},
  {id: 'rose', group: 'glass', name: 'G4. Rosé Quartz', tags: ['로즈', '세리프 제목'], glass: true, font: 'serif', swatch: ['#f7f3f3', '#a8475f', '#241417'],
    desc: '아주 옅은 블러시 바탕, 더스티 로즈 포인트. 부드럽지만 세리프 제목으로 격을 잡는다.'},
  {id: 'graphite', group: 'glass', name: 'G5. Graphite Glass', tags: ['무채색', 'IBM Plex', '섹션 번호'], glass: true, font: 'plex', num: true, swatch: ['#eeeff1', '#26282e', '#111215'],
    desc: '색을 완전히 빼고 유리·그림자·먹색만으로. 가장 모던하고 질리지 않는다.'},
  {id: 'champagne', group: 'matte', name: 'M1. Champagne Noir', tags: ['아이보리', '먹색 단추', '금빛 액자선', '세리프'], matte: true, font: 'serif', swatch: ['#f6f2ea', '#8f6a2a', '#17140f'],
    desc: '아이보리 종이에 금빛 안쪽 액자선, 먹색 단추에 샴페인 글자. 가장 호텔·부티크 같은 고급감.'},
  {id: 'bordeaux', group: 'matte', name: 'M2. Bordeaux Linen', tags: ['린넨', '버건디', '세리프'], matte: true, font: 'serif', swatch: ['#f3efe9', '#7d2438', '#1d1214'],
    desc: '린넨 결 바탕에 버건디 포인트. 와인 라벨 같은 깊이.'},
  {id: 'sage', group: 'matte', name: 'M3. Sage Paper', tags: ['세이지', 'IBM Plex', '섹션 번호'], matte: true, font: 'plex', num: true, swatch: ['#f2f3ee', '#4c6a55', '#151a15'],
    desc: '점 무늬 종이에 세이지 그린. 자연스럽고 편안한데 번호와 괘선으로 정돈돼 보인다.'},
  {id: 'terracotta', group: 'matte', name: 'M4. Terracotta Clay', tags: ['테라코타', '굵은 산세리프'], matte: true, swatch: ['#f5efe8', '#a9512e', '#21160f'],
    desc: '모래빛 바탕, 테라코타 포인트, 아주 굵은 제목. 따뜻하고 힘 있다.'},
  {id: 'swiss', group: 'matte', name: 'M5. Swiss Mono', tags: ['흑백', '시그널 레드', 'IBM Plex', '섹션 번호'], font: 'plex', num: true, swatch: ['#f6f6f5', '#d0342c', '#111111'],
    desc: '그림자 없는 흑백 격자에 빨강 한 점. 스위스 포스터처럼 엄격하고 모던하다.'},
];

const screens = [
  {id: 'home', name: '홈', component: TuHome},
  {id: 'board', name: '게시판 · 댓글', component: TuBoard},
  {id: 'apply', name: '자막 신청', component: TuApply},
  {id: 'anime', name: '애니 정보', component: TuAnime},
  {id: 'admin', name: '어드민', component: TuAdmin},
  {id: 'parts', name: '컴포넌트', component: TuParts},
];

const route = useRoute();
const router = useRouter();

function pick<T extends {id: string}>(list: T[], id: unknown, fallback: string): T {
  return list.find(e => e.id == id) ?? list.find(e => e.id == fallback)!!;
}

const variant = computed(() => pick(variants, route.query.v, 'pearl'));
const screen = computed(() => pick(screens, route.query.p, 'home'));

function select(q: {v?: string; p?: string}) {
  router.replace({query: {v: variant.value.id, p: screen.value.id, ...q}});
}

const html = document.documentElement;
const flags = ['ui', 'uiShape', 'uiGlass', 'uiMatte', 'uiFont', 'uiNum'];
let wasDark = false;
let active = false;

function setFlag(name: string, value: string | false | undefined) {
  if (value === false || value === undefined) {
    delete html.dataset[name];
  } else {
    html.dataset[name] = value;
  }
}

function apply() {
  if (!active) return;
  const v = variant.value;
  setFlag('ui', v.id);
  setFlag('uiShape', 'editorial');
  setFlag('uiGlass', !!v.glass && '');
  setFlag('uiMatte', !!v.matte && '');
  setFlag('uiFont', v.font);
  setFlag('uiNum', !!v.num && '');
}

watch(variant, apply);

onMounted(() => {
  wasDark = html.classList.contains('dark');
  html.classList.replace('dark', 'light');
  active = true;
  apply();
});

onBeforeUnmount(() => {
  active = false;
  flags.forEach(e => delete html.dataset[e]);
  if (wasDark) {
    html.classList.replace('light', 'dark');
  }
});
</script>
