<template>
  <div class="sc2015-host" :class="{embedded}">
    <div id="sc2015" :class="colorMode">
      <div class="title">
        <div class="title-main"><a href="/" target="_blank">애니<span class="mob-hide">메이션</span> 편성표</a></div>
        <div class="color-mode" v-if="isWeb">
          <svg id="dark-mode" @click="applyColorMode('dark')" aria-hidden="true" focusable="false" viewBox="0 0 24 24"><path d="M12 3.1 14.3 9.4 21 9.7 15.7 13.8 17.6 20.3 12 16.5 6.4 20.3 8.3 13.8 3 9.7 9.7 9.4z"/></svg>
          <svg id="light-mode" @click="applyColorMode('light')" aria-hidden="true" focusable="false" viewBox="0 0 24 24"><circle cx="12" cy="12" r="5"/><path d="M23 12l-4-1.8v3.6zM12 23l-1.8-4h3.6zM1 12l4-1.8v3.6zM12 1l-1.8 4h3.6zM19.8 19.8l-4.1-1.6 2.5-2.5zM4.2 19.8l1.6-4.1 2.5 2.5zM4.2 4.2l4.1 1.6-2.5 2.5zM19.8 4.2l-1.6 4.1-2.5-2.5z"/></svg>
        </div>
      </div>
      <table class="nav">
        <tbody>
          <tr>
            <th v-for="(name, idx) in weekList" :key="name" @click="getAnimeList(idx)" :class="{'sel': (weekNow == idx)}">{{name}}</th>
          </tr>
        </tbody>
      </table>
      <div class="main">
        <table class="list">
          <tr v-for="node in animeList" :key="node.animeNo">
            <td class="tod" :class="{'tod-date': !pureWeek}">
              <span v-if="pureWeek">{{node.scheduleTime}}</span>
              <span v-else>
                <span>{{node.scheduleTime.length === 10 ? node.scheduleTime.substring(0, 4) : node.scheduleTime}}</span><span
                  class="mob-hide">{{node.scheduleTime.length === 10 ? node.scheduleTime.substring(4) : ''}}</span>
              </span>
            </td>
            <td class="subject" @click="getCaptionList(node);">
              <span class="prefix" v-if="node.subjectPrefix">[<b>{{node.subjectPrefix}}</b>] </span>
              {{node.subject}}
            </td>
            <td class="genres mob-hide">{{node.genres.replace(',', ' / ')}}</td></tr>
        </table>
      </div>
      <div class="popup" v-if="animeNow != null" @click.self="animeNow = null">
        <div class="box">
          <a target="_blank" v-if="animeNow.website.length > 10" :href="animeNow.website" class="subject">{{animeNow.subject}}</a>
          <span class="subject" v-else>{{animeNow.subject}}</span>
          <div class="date" v-if="animeNow.period">{{animeNow.period}}</div>
          <div v-for="node in captionList" :key="`${node.name}`">
            <a v-if="node.website !== ''" class="node" :href="node.website" target="_blank">
              <span>{{node.episodeText}}</span>
              <b>&nbsp;{{node.name}}&nbsp;</b>
              <span class="time">{{node.updDtText}}</span>
            </a>
            <div v-else class="node">
              <span>{{node.episodeText}}</span>
              <b>&nbsp;{{node.name}}&nbsp;</b>
              <span class="time">{{node.updDtText}}</span>
            </div>
          </div>
          <div v-if="captionList.length === 0" class="node-empty">자막이 없습니다.</div>
        </div>
      </div>
      <div ref="userStyle"></div>
      <div id="sc2015-theme-error" v-if="themeError">
        <table>
          <tbody><tr><td>애니편성표의 커스텀코드가 잘못되었습니다.<br/><br/><a href="/schedule" target="_blank">애니시아</a>에서 재설정 하시기 바랍니다.</td></tr></tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
// 2019-08-17: 2015년 소스를 vuejs 에 맞게 변경
// 2020-12-25: 다크모드 추가 (2015 Version 유지)
// 2022-10-03: Vue3 컴포넌트 스타일로 변경. (CSS 는 스킨 호환성을 위해 post-css 로 변경하지 않음)
import {computed, onBeforeUnmount, onMounted, ref, shallowRef, watch} from "vue";
import AnimeCaption from "../../domain/anime/AnimeCaption";
import Anime from "../../domain/anime/Anime";
import anissia from "../../common/anissia";
import animeRemote from "../../domain/anime/remote/animeRemote";

const props = withDefaults(defineProps<{embedded?: boolean, themeCode?: string}>(), {embedded: false});
const userStyle = ref<HTMLElement | null>(null);
const themeError = ref(false);

const weekList = ['日', '月', '火', '水', '木', '金', '土', '外', '新'];
const weekNow = ref(-1);
const isWeb = ref(true);
const animeList = shallowRef<Anime[]>([]);
const animeNow = shallowRef<Anime | null>(null);
const captionList = shallowRef<AnimeCaption[]>([]);
const colorMode = ref('light');
const pureWeek = computed(() => anissia.isPureWeek(weekNow.value));

// method
function getAnimeList(week: number): void {
  weekNow.value = week;
  animeRemote.getScheduleAnimeList(week).then((list) => {
    if (weekNow.value == week) animeList.value = list;
  });
}
function getCaptionList(anime: Anime) {
  animeRemote.getAnimeCaptionList(anime.animeNo).then((list) => {
    captionList.value = list;
    animeNow.value = anime;
  });
}
function evtKeyClosePopup(event: KeyboardEvent) {
  if (animeNow.value !== null && event.key === 'Escape') {
    animeNow.value = null;
  }
}
function applyColorMode(mode: string|null) {
  if (mode == null) {
    if (isWeb.value) {
      try {
        mode = (localStorage.getItem('schedule2015ColorMode') as string | null);
        colorMode.value = mode != null ? mode : (matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light');
      } catch (e) {colorMode.value = 'light'; }
    } else {
      colorMode.value = 'light';
    }
  } else if (isWeb.value) {
    try {
      localStorage.setItem('schedule2015ColorMode', (colorMode.value = mode));
    } catch (e) { colorMode.value = 'light'; }
  }
}

let repaint: ((colors: string) => void) | undefined;
watch(() => props.themeCode, code => repaint?.(code ?? ''));
defineExpose({applyColorMode});

// mounted
onMounted(() => {
  applyColorMode(null);
  getAnimeList(new Date().getDay());
  if (!props.embedded) (window as any).colorMode = applyColorMode;
  window.addEventListener('keydown', evtKeyClosePopup, true);
  const theme = location.hash.length > 1 ? location.hash : 'ffffff5987b6fffffff2f2f2497ba79cb3c7ffffffffffff555555f8f8f82474cecb3434000000000000777777111111777777111111c3b443070707999999000000cccccc3a7da3';
  // custom theme
  repaint = (colors: string) => {
    const c: string[] = colors.match(/[0-9a-f]{6}/ig) || [];
    if (c.filter((e: string) => /^[0-9a-f]{6}$/i.test(e)).length === 24) {
      themeError.value = false;
      /**
       * c[light/dark]  description
       * c[0/12]    body background color
       * c[1/13]    subject background color
       * c[2/14]    subject front color
       * c[3/15]    navigator background color
       * c[4/16]    navigator front color
       * c[5/17]    week background color
       * c[6/18]    week front color
       * c[7/19]    list background color
       * c[8/20]    list front color
       * c[9/21]    list:hover background color
       * c[10/22]   list:hover front color
       * c[11/23]   subject prefix front color
       */
      userStyle.value!.innerHTML = `<style>
        #sc2015.light .main, #sc2015.light .popup .box { background: #${c[0]} }
        #sc2015.light .title, #sc2015.light .popup .box .subject { background: #${c[1]} }
        #sc2015.light .title a, #sc2015.light .popup .box .subject { color: #${c[2]} }
        #sc2015.light .title svg { fill:#${c[2]} }
        #sc2015.light .nav th, #sc2015.light .popup .box .date { background: #${c[3]}; color: #${c[4]} }
        #sc2015.light .nav th:hover, #sc2015.light .nav th.sel { background: #${c[5]}; color: #${c[6]} }
        #sc2015.light .list td, #sc2015.light .popup .box .node, #sc2015.light .popup .box .node-empty { background: #${c[7]}; color:#${c[8]}; }
        #sc2015.light .list tr:hover td, #sc2015.light .popup .box .node:hover { background: #${c[9]}; color:#${c[10]}; }
        #sc2015.light .list tr:not(:hover) td .prefix b { color:#${c[11]} }
        #sc2015.dark .main, #sc2015.dark .popup .box { background: #${c[12]} }
        #sc2015.dark .title, #sc2015.dark .popup .box .subject { background: #${c[13]} }
        #sc2015.dark .title a, #sc2015.dark .popup .box .subject { color: #${c[14]} }
        #sc2015.dark .title svg { fill:#${c[14]} }
        #sc2015.dark .nav th, #sc2015.dark .popup .box .date { background: #${c[15]}; color: #${c[16]} }
        #sc2015.dark .nav th:hover, #sc2015.dark .nav th.sel { background: #${c[17]}; color: #${c[18]} }
        #sc2015.dark .list td, #sc2015.dark .popup .box .node, #sc2015.dark .popup .box .node-empty { background: #${c[19]}; color:#${c[20]}; }
        #sc2015.dark .list tr:hover td, #sc2015.dark .popup .box .node:hover { background: #${c[21]}; color:#${c[22]}; }
        #sc2015.dark .list tr:not(:hover) td .prefix b { color:#${c[23]} }
        </style>`;
    } else {
      themeError.value = true;
    }
  };
  repaint(props.themeCode ?? theme);
  if (!props.embedded) (window as any).repaint = repaint;
});

onBeforeUnmount(() => {
  window.removeEventListener('keydown', evtKeyClosePopup, true);
  if ((window as any).colorMode === applyColorMode) delete (window as any).colorMode;
  if ((window as any).repaint === repaint) delete (window as any).repaint;
});
</script>

<style scoped>
.sc2015-host {
  container: schedule2015 / inline-size;
  contain: layout style;
  position: relative;
  width: 100%;
  height: 100dvh;
  isolation: isolate;
}
.sc2015-host.embedded { height: 100% }
@keyframes sc2015-popup {
  from { background-color: rgba(0, 0, 0, 0); } to { background-color: rgba(0, 0, 0, .3); }
}
#sc2015 { min-height: 100%; font-size:15px }
#sc2015 ::-webkit-scrollbar { display: none; }
#sc2015 a { text-decoration: none; }
#sc2015 table { width:100%; }
#sc2015 .title { font-weight: 200; height: 64px; text-align: center; display: flex; align-items: center; }
#sc2015 .title .title-main { font-size:24px; line-height: 1; flex: 1 1 0%; padding-left: 36px; text-align:center }
#sc2015 .title .color-mode { width:36px; }
#sc2015 .title svg { width:28px; height:28px; opacity: .4; transition: all .2s; }
#sc2015 .title svg:hover { opacity: 1; transform: rotate(22deg); }
#sc2015 .nav th { height: 40px; cursor: pointer; transition: background-color 0.2s, color 0.2s; }
#sc2015 .main { position: fixed; right:0; bottom:0; left:0; overflow-y:auto }
#sc2015 .list { font-size:14px; }
#sc2015 .list td { height: 48px; line-height: 1.8; padding:0; transition: background-color 0.2s, color 0.2s; }
#sc2015 .list td.tod { width:64px; text-align: center; }
#sc2015 .list td.genres { text-align: right; padding-right:14px; }
#sc2015 .list td.subject { cursor: pointer; }
#sc2015 .list td.subject b { font-weight: normal }

#sc2015 .popup {
  position: fixed; top:0; right:0; bottom:0; left:0; backdrop-filter: blur(3px);
  animation-name: sc2015-popup; animation-duration: .2s; animation-iteration-count: 1; animation-fill-mode: forwards;
}
#sc2015 .popup .box { font-weight: 400; transition: height 0.2s }
#sc2015 .popup .box .date { padding: 8px; line-height: 1.2; text-align: center; }
#sc2015 .popup .box .node,
#sc2015 .popup .box .node-empty { font-size: 13px; text-align: center; font-weight: 400; line-height: 40px; margin:0 0 1px; }
#sc2015 .popup .box .node b { line-height: 2.5; }
#sc2015 .popup .box .node-empty { padding: 40px 0 50px; text-align: center; }
#sc2015 .popup .box a,
#sc2015 .popup .box span.subject { text-decoration: none; display: block; text-align: center; }
#sc2015 .popup .box .subject { font-size: 18px; line-height: 2; padding: 0 12px; font-weight: 100; }

#sc2015-theme-error { position: fixed; top:0; left:0; right:0; bottom:0; background: #000; }
#sc2015-theme-error table { width:100%; height:100%; }
#sc2015-theme-error table td { text-align: center; color:#fff; line-height: 1; font-size: 1.5em; }

@container schedule2015 (min-width: 601px) {
  #sc2015 .main { top:104px; }
  #sc2015 .list td.tod.tod-date { width:106px; }
  #sc2015 .popup .box { width: 400px; margin: 120px auto 0 auto; }
}

@container schedule2015 (max-width: 600px) {
  #sc2015 .main { top:92px; }
  #sc2015 .title { height: 52px }
  #sc2015 .title .color-mode svg { width:28px; height:28px; }
  #sc2015 .mob-hide { display: none; }
  #sc2015 .popup .box { margin: 80px 24px 0; }
  #sc2015 .popup .box .subject { font-size:18px; }
}

@container schedule2015 (max-width: 300px) {
  #sc2015 .main { top:92px; }
  #sc2015 .title { height: 52px }
  #sc2015 .title .color-mode svg { width:28px; height:24px; }
  #sc2015 .mob-hide { display: none; }
  #sc2015 .popup .box { margin: 80px 24px 0; }
  #sc2015 .popup .box .subject { font-size:12px; padding:4px; }
  #sc2015 .popup .box .date { font-size:12px; padding:4px; }
  #sc2015 .popup .box .node { font-size:14px; }
  #sc2015 .popup .box .node .time { display:none }
}

#sc2015.light #light-mode, #sc2015.dark #dark-mode { display: none }
</style>
