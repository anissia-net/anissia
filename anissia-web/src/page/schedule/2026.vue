<template>
  <div id="sc2026" :class="[mode, {ready}]" :style="vars" @pointermove="onPointerMove">
    <div class="aura" aria-hidden="true"></div>

    <div class="wrap">
      <header class="head">
        <a class="wordmark" href="/" target="_blank"><span class="narrow-hide">애니</span><span class="mid-hide">메이션</span> 편성표</a>
        <span class="date">{{todayText}}</span>
        <button type="button" class="mode-btn" @click="toggleMode()" :aria-label="mode == 'dark' ? '라이트 모드' : '다크 모드'">
          <i class="fa-solid" :class="mode == 'dark' ? 'fa-moon' : 'fa-sun'"></i>
        </button>
      </header>

      <nav class="week glass" :ref="e => navRef = e as HTMLElement">
        <span class="pill" :style="pillStyle"></span>
        <button v-for="(name, idx) in weekList" :key="name" type="button" class="week-btn"
                :class="{on: idx == weekNow, today: idx == todayWeek}" @click="getAnimeList(idx)">{{name}}</button>
      </nav>

      <ol class="line" :key="listKey" v-if="loaded">
        <template v-for="(row, idx) in rows" :key="row.key">
          <li v-if="row.anime == null" class="now" :style="{'--i': idx}">
            <span class="now-time">{{nowText}}</span>
            <span class="now-rule"></span>
          </li>
          <li v-else class="row" :class="{past: row.past, next: row.next, cont: !row.label.main && row.timed}" :style="{'--i': idx}">
            <div class="time">
              <span class="time-main">{{row.label.main}}</span>
              <span class="time-sub" v-if="row.label.sub">{{row.label.sub}}</span>
            </div>
            <div class="rail"><i class="dot"></i></div>
            <article class="card glass" role="button" tabindex="0" @click="getCaptionList(row.anime)" @keydown.enter="getCaptionList(row.anime)">
              <div class="subject">
                <span class="prefix" v-if="row.anime.subjectPrefix">{{row.anime.subjectPrefix}}</span>{{row.anime.subject}}
              </div>
              <div class="original" v-if="row.anime.originalSubject">{{row.anime.originalSubject}}</div>
              <div class="meta" v-if="row.genres.length || row.anime.website || row.anime.x || row.anime.captionCount">
                <a v-for="tag in row.genres" :key="tag" class="tag" :href="`/anime?q=%23${encodeURIComponent(tag)}`" target="_blank" @click.stop>{{tag}}</a>
                <span class="meta-end">
                  <a v-if="row.anime.website" class="icon" :href="row.anime.website" target="_blank" @click.stop aria-label="공식 사이트"><i class="fa-solid fa-house"></i></a>
                  <a v-if="row.anime.x" class="icon" :href="row.anime.x" target="_blank" @click.stop aria-label="X"><i class="fa-brands fa-x-twitter"></i></a>
                  <span v-if="row.anime.captionCount" class="cc"><i class="fa-solid fa-closed-captioning"></i>{{row.anime.captionCount}}</span>
                </span>
              </div>
            </article>
          </li>
        </template>
        <li v-if="!rows.length" class="empty">편성된 작품이 없습니다.</li>
      </ol>
    </div>

    <transition name="sc-pop">
      <div class="popup" v-if="animeNow != null" @click.self="animeNow = null">
        <section class="sheet glass" role="dialog" aria-modal="true">
          <button type="button" class="close" @click="animeNow = null" aria-label="닫기"><i class="fa-solid fa-xmark"></i></button>
          <a class="sheet-title" :href="`/anime?animeNo=${animeNow.animeNo}`" target="_blank">{{animeNow.subject}}</a>
          <div class="sheet-original" v-if="animeNow.originalSubject">{{animeNow.originalSubject}}</div>
          <div class="sheet-tags" v-if="animeNow.tags.length">
            <span v-for="tag in animeNow.tags" :key="tag" class="tag">{{tag}}</span>
          </div>
          <ul class="captions" v-if="captionList.length">
            <li v-for="node in captionList" :key="node.name">
              <component :is="node.website ? 'a' : 'div'" class="caption" :href="node.website || undefined" :target="node.website ? '_blank' : undefined">
                <span class="ep" :class="{wait: !node.website}">{{node.episodeText}}</span>
                <span class="name">{{node.name}}</span>
                <span class="ago">{{node.updDtText}}</span>
              </component>
            </li>
          </ul>
          <div v-else class="captions-empty">자막 제작자가 없습니다.</div>
        </section>
      </div>
    </transition>

    <div v-if="ajaxState.state == 'loading'" class="state">
      <i class="fa-solid fa-gear fa-spin"></i>
      <div class="state-title">loading...</div>
    </div>
    <div v-else-if="ajaxState.state == 'error'" class="state">
      <i class="fa-solid fa-screwdriver-wrench"></i>
      <div class="state-title">서버 연결 실패</div>
      <div class="state-desc">현재 애니시아 서버에 연결할 수 없습니다.<br/>빠르게 정상화 하도록 하겠습니다.<br/>문의 : auth@anissia.net</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import {computed, nextTick, onBeforeUnmount, onMounted, Ref, ref} from "vue";
import AnimeCaption from "../../domain/anime/AnimeCaption";
import Anime from "../../domain/anime/Anime";
import animeRemote from "../../domain/anime/remote/animeRemote";
import anissia from "../../common/anissia";
import {ajaxStateStore} from "../../common/ajaxStateStore";
import {decodeTheme, palette, Theme} from "./2026/palette";

interface Row {
  key: string,
  anime: Anime | null,
  label: { main: string, sub: string },
  genres: string[],
  timed: boolean,
  past: boolean,
  next: boolean,
}

const weekList = ['日', '月', '火', '水', '木', '金', '土', '外', '新'];
const weekNow = ref(-1);
const todayWeek = ref(new Date().getDay());
const animeList = ref([]) as Ref<Anime[]>;
const animeNow = ref(null) as Ref<Anime | null>;
const captionList = ref([]) as Ref<AnimeCaption[]>;
const ajaxState = ajaxStateStore();
const mode = ref('light');
const theme = ref(decodeTheme(location.hash)) as Ref<Theme>;
const loaded = ref(false);
const listKey = ref(0);
const ready = ref(false);
const navRef = ref(null) as Ref<HTMLElement | null>;
const pillStyle = ref({} as Record<string, string>);
const now = ref(new Date());

const vars = computed(() => palette(theme.value[mode.value == 'dark' ? 'dark' : 'light'], theme.value.glow, mode.value == 'dark'));

const pad = (n: number) => String(n).padStart(2, '0');
const nowText = computed(() => `${pad(now.value.getHours())}:${pad(now.value.getMinutes())}`);
const todayText = computed(() => `${now.value.getMonth() + 1}월 ${now.value.getDate()}일 (${'일월화수목금토'[now.value.getDay()]})`);

function timeLabel(time: string): { main: string, sub: string } {
  if (/^\d{1,2}:\d{2}$/.test(time)) return {main: time, sub: ''};
  const m = time.match(/^(\d{4})(?:-(\d{2}))?(?:-(\d{2}))?$/);
  if (!m) return {main: '', sub: ''};
  if (m[3]) return {main: `${m[2]}.${m[3]}`, sub: m[1]};
  if (m[2]) return {main: `${Number(m[2])}월`, sub: m[1]};
  return {main: '미정', sub: m[1]};
}

const rows = computed(() => {
  const isToday = weekNow.value == todayWeek.value && anissia.isPureWeek(weekNow.value);
  const nextIdx = isToday ? animeList.value.findIndex(e => e.scheduleTime.localeCompare(nowText.value) >= 0) : -1;
  const list: Row[] = [];
  let prev = '';
  animeList.value.forEach((anime, idx) => {
    const time = anime.scheduleTime;
    const label = timeLabel(time);
    const same = label.main != '' && time == prev;
    prev = time;
    if (idx == nextIdx) {
      list.push({key: 'now', anime: null, label: {main: '', sub: ''}, genres: [], timed: false, past: false, next: false});
    }
    list.push({
      key: String(anime.animeNo),
      anime,
      label: same ? {main: '', sub: ''} : label,
      genres: anime.genres.split(',').filter(e => e),
      timed: label.main != '',
      past: isToday && (nextIdx == -1 || idx < nextIdx),
      next: idx == nextIdx,
    });
  });
  return list;
});

function getAnimeList(week: number): void {
  weekNow.value = week;
  nextTick(movePill);
  animeRemote.getScheduleAnimeList(week).then((list) => {
    if (weekNow.value != week) return;
    animeList.value = list;
    loaded.value = true;
    listKey.value++;
  });
}

function getCaptionList(anime: Anime) {
  animeRemote.getAnimeCaptionList(anime.animeNo).then((list) => {
    captionList.value = list;
    animeNow.value = anime;
  });
}

function movePill() {
  const btn = navRef.value?.querySelectorAll<HTMLElement>('.week-btn')[weekNow.value];
  if (!btn) return;
  pillStyle.value = {width: `${btn.offsetWidth}px`, height: `${btn.offsetHeight}px`, transform: `translate(${btn.offsetLeft}px, ${btn.offsetTop}px)`};
}

function onPointerMove(e: PointerEvent) {
  const el = (e.target as Element | null)?.closest?.('.glass') as HTMLElement | null;
  if (!el) return;
  const rect = el.getBoundingClientRect();
  el.style.setProperty('--mx', `${e.clientX - rect.left}px`);
  el.style.setProperty('--my', `${e.clientY - rect.top}px`);
}

function evtKeyClosePopup(event: KeyboardEvent) {
  if (animeNow.value !== null && event.key === 'Escape') {
    animeNow.value = null;
  }
}

function applyColorMode(value: string | null) {
  if (value == null) {
    try {
      value = localStorage.getItem('schedule2026ColorMode') || (matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light');
    } catch (e) { value = 'light'; }
  }
  mode.value = value == 'dark' ? 'dark' : 'light';
  try { localStorage.setItem('schedule2026ColorMode', mode.value); } catch (e) { /* 저장소 없음 */ }
}

function toggleMode() {
  applyColorMode(mode.value == 'light' ? 'dark' : 'light');
}

let instantTimer = 0;
// 미리보기에서 색을 끄는 동안은 전환 효과 없이 바로 칠한다.
function repaint(code: string) {
  ready.value = false;
  theme.value = decodeTheme(code);
  clearTimeout(instantTimer);
  instantTimer = window.setTimeout(() => ready.value = true, 150);
}

let clock = 0;
let resize: ResizeObserver | null = null;

onMounted(() => {
  applyColorMode(null);
  getAnimeList(todayWeek.value);
  (window as any).colorMode = applyColorMode;
  (window as any).repaint = repaint;
  window.addEventListener('keydown', evtKeyClosePopup, true);
  clock = window.setInterval(() => {
    now.value = new Date();
    todayWeek.value = now.value.getDay();
  }, 30000);
  resize = new ResizeObserver(movePill);
  if (navRef.value) resize.observe(navRef.value);
  requestAnimationFrame(() => requestAnimationFrame(() => ready.value = true));
});

onBeforeUnmount(() => {
  window.removeEventListener('keydown', evtKeyClosePopup, true);
  clearInterval(clock);
  resize?.disconnect();
});
</script>

<style>
html:has(#sc2026) {
  overflow: hidden;
  body, body::before { background: none }
}
:where(#sc2026) :where(a) { color: inherit; text-decoration: none }
:where(#sc2026) :where(button) { font: inherit; color: inherit; background: transparent; border: 0; padding: 0; cursor: pointer }
</style>

<style scoped>
#sc2026 {
  position: fixed;
  inset: 0;
  overflow-y: auto;
  overflow-x: hidden;
  color-scheme: var(--scheme);
  background: var(--canvas);
  color: var(--ink);
  font-family: "Noto Sans KR", "Noto Sans JP", -apple-system, BlinkMacSystemFont, "Malgun Gothic", sans-serif;
  font-size: 14px;
  line-height: 1.6;
  -webkit-font-smoothing: antialiased;
  scrollbar-width: thin;
  scrollbar-color: var(--scroll) transparent;
}
#sc2026::-webkit-scrollbar { width: 10px }
#sc2026::-webkit-scrollbar-track { background: transparent }
#sc2026::-webkit-scrollbar-thumb { background: var(--scroll); border: 3px solid transparent; background-clip: content-box; border-radius: 999px }
#sc2026::-webkit-scrollbar-thumb:hover { background-color: var(--scroll-hover) }
#sc2026 ::selection { background: var(--selection); color: var(--ink) }
#sc2026 :focus-visible { outline: 2px solid var(--hi); outline-offset: 2px }

#sc2026 { transition: background-color .3s ease, color .3s ease }
#sc2026:not(.ready) *, #sc2026:not(.ready) *::before, #sc2026:not(.ready) *::after { transition: none !important }

.aura {
  position: fixed;
  inset: 0;
  pointer-events: none;
  background:
    radial-gradient(52rem 34rem at 10% -12%, var(--bg-1), transparent 62%),
    radial-gradient(44rem 30rem at 95% 4%, var(--bg-2), transparent 64%),
    radial-gradient(60rem 40rem at 50% 118%, var(--bg-3), transparent 62%);
}

.wrap {
  position: relative;
  max-width: 880px;
  margin: 0 auto;
  padding: 0 16px 40px;
}

/* 머리 */
.head {
  display: flex;
  align-items: center;
  gap: 12px;
  height: 60px;
}
.wordmark {
  flex: 1;
  min-width: 0;
  font-size: 19px;
  font-weight: 700;
  letter-spacing: -.02em;
  white-space: nowrap;
  background: var(--wordmark);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}
.date {
  font-size: 12px;
  color: var(--ink-3);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
.mode-btn {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border-radius: 999px;
  color: var(--ink-3);
  transition: color .2s ease, background-color .2s ease, transform .3s cubic-bezier(.22, 1, .36, 1);
}
.mode-btn:hover { color: var(--hi); background: var(--hi-soft); transform: rotate(18deg) }

/* 유리면 + 테두리 빛 */
.glass {
  position: relative;
  background: var(--surface);
  -webkit-backdrop-filter: blur(16px) saturate(160%);
  backdrop-filter: blur(16px) saturate(160%);
  border-radius: 12px;
  box-shadow: var(--shadow-1);
  transition: background-color .3s ease, box-shadow .3s ease;
}
.glass::before, .glass::after {
  content: "";
  position: absolute;
  inset: 0;
  z-index: 2;
  padding: 1px;
  border-radius: inherit;
  -webkit-mask: linear-gradient(#000 0 0) content-box, linear-gradient(#000 0 0);
  -webkit-mask-composite: xor;
  mask-composite: exclude;
  pointer-events: none;
}
.glass::before { background: var(--edge) }
.glass::after {
  background: radial-gradient(50% 50% at var(--mx, 50%) var(--my, 0),
    color-mix(in srgb, var(--edge-glow) 55%, transparent) 0%,
    color-mix(in srgb, var(--edge-glow) 30%, transparent) 40%,
    transparent 100%);
  opacity: 0;
  transition: opacity .35s ease;
}
.glass:hover::after { opacity: 1 }

/* 요일 */
.week {
  position: sticky;
  top: 8px;
  z-index: 10;
  display: flex;
  padding: 4px;
  gap: 2px;
  border-radius: 12px;
  background: var(--surface-solid);
}
.pill {
  position: absolute;
  top: 0;
  left: 0;
  border-radius: 9px;
  background: var(--gradient);
  box-shadow: var(--shadow-hi);
  transition: transform .42s cubic-bezier(.22, .8, .25, 1), width .42s cubic-bezier(.22, .8, .25, 1);
}
.week-btn {
  position: relative;
  z-index: 1;
  flex: 1;
  height: 36px;
  border-radius: 9px;
  font-size: 15px;
  font-weight: 600;
  color: var(--ink-3);
  transition: color .25s ease;
}
.week-btn:hover { color: var(--ink) }
.week-btn.on { color: var(--on-hi) }
.week-btn.today:not(.on)::after {
  content: "";
  position: absolute;
  left: 50%;
  bottom: 3px;
  width: 4px;
  height: 4px;
  margin-left: -2px;
  border-radius: 999px;
  background: var(--hi);
}

/* 타임라인 */
.line {
  list-style: none;
  margin: 18px 0 0;
  padding: 0;
}
.row, .now {
  display: grid;
  grid-template-columns: 54px 20px minmax(0, 1fr);
  animation: sc-rise .5s cubic-bezier(.22, 1, .36, 1) both;
  animation-delay: calc(min(var(--i), 14) * 28ms);
}
.row { min-height: 0 }
.row + .row, .now + .row { margin-top: 10px }
.row.cont { margin-top: 6px }

.time {
  padding-top: 13px;
  text-align: right;
  line-height: 1.15;
  font-variant-numeric: tabular-nums;
}
.time-main {
  display: block;
  font-size: 14px;
  font-weight: 700;
  letter-spacing: -.01em;
  color: var(--ink-2);
}
.time-sub {
  display: block;
  margin-top: 3px;
  font-size: 10.5px;
  color: var(--ink-3);
}

.rail { position: relative }
.rail::before {
  content: "";
  position: absolute;
  top: -10px;
  bottom: -10px;
  left: 50%;
  width: 1px;
  margin-left: -.5px;
  background: var(--line-2);
}
.row:first-child .rail::before { top: 18px }
.row:last-child .rail::before { bottom: auto; height: 28px }
.row.cont .rail::before { top: -6px }
.dot {
  position: absolute;
  top: 17px;
  left: 50%;
  width: 9px;
  height: 9px;
  margin-left: -4.5px;
  border-radius: 999px;
  background: var(--canvas);
  box-shadow: inset 0 0 0 2px var(--ink-3);
}
.row.cont .dot { top: 19px; width: 5px; height: 5px; margin-left: -2.5px; background: var(--ink-3); box-shadow: none }
.row.next .dot {
  background: var(--hi);
  box-shadow: 0 0 0 4px var(--hi-soft), 0 0 14px var(--hi);
  animation: sc-pulse 2.4s ease-in-out infinite;
}
.row.next .time-main { color: var(--hi) }
.row.past .time-main { color: var(--ink-3); font-weight: 500 }
.row.past .card { opacity: .78 }
.row.past .card:hover { opacity: 1 }

.now { align-items: center; margin-top: 10px }
.now-time {
  grid-column: 1;
  text-align: right;
  font-size: 11px;
  font-weight: 700;
  color: var(--hi);
  font-variant-numeric: tabular-nums;
}
.now-rule {
  grid-column: 2 / 4;
  height: 1px;
  margin-left: 6px;
  background: linear-gradient(90deg, var(--hi), transparent);
}

.card {
  margin-left: 6px;
  padding: 12px 14px;
  cursor: pointer;
  transition: transform .22s cubic-bezier(.22, 1, .36, 1), background-color .2s ease, box-shadow .22s ease, opacity .2s ease;
}
.card:hover {
  transform: translateY(-1px);
  background: var(--surface-hover);
  box-shadow: var(--shadow-2);
}
.subject {
  font-size: 15px;
  font-weight: 600;
  line-height: 1.5;
  letter-spacing: -.01em;
  color: var(--ink);
  word-break: keep-all;
  overflow-wrap: anywhere;
}
.prefix {
  display: inline-block;
  margin-right: 7px;
  padding: 1px 7px;
  border-radius: 999px;
  font-size: 11.5px;
  font-weight: 700;
  line-height: 1.6;
  vertical-align: 1px;
  color: var(--hi);
  background: var(--hi-soft);
  box-shadow: inset 0 0 0 1px var(--hi-ring);
}
.original {
  margin-top: 2px;
  font-size: 12px;
  color: var(--ink-3);
  overflow-wrap: anywhere;
}
.meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 5px;
  margin-top: 9px;
}
.meta-end {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-left: auto;
}
.tag {
  display: inline-block;
  padding: 3px 8px;
  border-radius: 6px;
  font-size: 11.5px;
  line-height: 1.3;
  color: var(--ink-2);
  box-shadow: inset 0 0 0 1px var(--line-2);
  transition: background-color .18s ease, color .18s ease, box-shadow .18s ease;
}
a.tag:hover { color: var(--hi); background: var(--hi-soft); box-shadow: inset 0 0 0 1px var(--hi-ring) }
.icon {
  display: grid;
  place-items: center;
  width: 26px;
  height: 24px;
  border-radius: 6px;
  font-size: 11.5px;
  color: var(--ink-3);
  transition: color .18s ease, background-color .18s ease;
}
.icon:hover { color: var(--hi); background: var(--hi-soft) }
.cc {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 3px 8px;
  border-radius: 999px;
  font-size: 11.5px;
  font-weight: 700;
  line-height: 1.3;
  color: var(--on-hi);
  background: var(--gradient);
}

.empty {
  padding: 80px 0;
  text-align: center;
  color: var(--ink-3);
}

/* 자막 팝업 */
.popup {
  position: fixed;
  inset: 0;
  z-index: 30;
  overflow-y: auto;
  padding: 72px 16px 32px;
  background: var(--backdrop);
  -webkit-backdrop-filter: blur(6px);
  backdrop-filter: blur(6px);
}
.sheet {
  max-width: 440px;
  margin: 0 auto;
  padding: 22px 20px 16px;
  border-radius: 16px;
  background: var(--surface-solid);
  box-shadow: var(--shadow-3);
  text-align: center;
}
.close {
  position: absolute;
  top: 10px;
  right: 10px;
  z-index: 3;
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border-radius: 999px;
  color: var(--ink-3);
}
.close:hover { color: var(--ink); background: var(--hi-soft) }
.sheet-title {
  display: block;
  padding: 0 22px;
  font-size: 18px;
  font-weight: 700;
  line-height: 1.45;
  letter-spacing: -.015em;
  color: var(--ink);
  transition: color .2s ease;
}
.sheet-title:hover { color: var(--hi) }
.sheet-original { margin-top: 4px; font-size: 12px; color: var(--ink-3) }
.sheet-tags { display: flex; flex-wrap: wrap; justify-content: center; gap: 5px; margin-top: 12px }
.captions {
  list-style: none;
  margin: 16px 0 0;
  padding: 10px 0 0;
  border-top: 1px solid var(--line);
  text-align: left;
}
.caption {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 10px;
  border-radius: 8px;
  font-size: 14px;
  transition: background-color .18s ease;
}
a.caption:hover { background: var(--hi-soft) }
a.caption:hover .name { color: var(--hi) }
.ep {
  flex-shrink: 0;
  min-width: 44px;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 11.5px;
  font-weight: 700;
  text-align: center;
  color: var(--on-hi);
  background: var(--gradient);
}
.ep.wait { color: var(--ink-3); background: transparent; box-shadow: inset 0 0 0 1px var(--line-2) }
.name { flex: 1; min-width: 0; font-weight: 600; color: var(--ink); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; transition: color .18s ease }
.ago { flex-shrink: 0; font-size: 12px; color: var(--ink-3) }
.captions-empty {
  margin-top: 16px;
  padding: 40px 0 36px;
  border-top: 1px solid var(--line);
  color: var(--ink-3);
}

/* 상태 */
.state {
  position: fixed;
  inset: 0;
  z-index: 40;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 18px;
  padding: 16px;
  text-align: center;
  color: var(--ink-2);
  background: var(--backdrop);
  -webkit-backdrop-filter: blur(4px);
  backdrop-filter: blur(4px);
}
.state > i { font-size: 72px; opacity: .7 }
.state-title { font-size: 26px; font-weight: 300 }
.state-desc { font-size: 14px; line-height: 2; color: var(--ink-3) }

.sc-pop-enter-active, .sc-pop-leave-active { transition: opacity .25s ease }
.sc-pop-enter-active .sheet, .sc-pop-leave-active .sheet { transition: transform .3s cubic-bezier(.22, 1, .36, 1) }
.sc-pop-enter-from, .sc-pop-leave-to { opacity: 0 }
.sc-pop-enter-from .sheet, .sc-pop-leave-to .sheet { transform: translateY(10px) scale(.98) }

@keyframes sc-rise {
  from { opacity: 0; transform: translateY(8px) }
}
@keyframes sc-pulse {
  50% { box-shadow: 0 0 0 7px transparent, 0 0 18px var(--hi) }
}

@media (min-width: 640px) {
  .wrap { padding: 0 24px 48px }
  .head { height: 68px }
  .wordmark { font-size: 21px }
  .row, .now { grid-template-columns: 64px 24px minmax(0, 1fr) }
  .card { padding: 14px 18px }
  .time-main { font-size: 15px }
}

@media (max-width: 420px) {
  .wrap { padding: 0 10px 32px }
  .head { height: 52px }
  .row, .now { grid-template-columns: 44px 16px minmax(0, 1fr) }
  .card { margin-left: 4px; padding: 10px 12px }
  .time-main { font-size: 13px }
  .subject { font-size: 14px }
  .week-btn { font-size: 14px }
  .date { display: none }
  .popup { padding: 40px 10px 24px }
}

@media (max-width: 340px) {
  .mid-hide { display: none }
  .week { padding: 3px; gap: 0 }
  .week-btn { height: 32px; font-size: 13px }
}

@media (max-width: 260px) {
  .narrow-hide { display: none }
  .row, .now { grid-template-columns: 0 12px minmax(0, 1fr) }
  .time { visibility: hidden }
}

@media (prefers-reduced-motion: reduce) {
  .row, .now, .row.next .dot { animation: none }
  .pill { transition: none }
}
</style>
