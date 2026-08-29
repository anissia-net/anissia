<template>
  <div id="sc2015" @click="e => evtClickClosePopup(e)" :class="colorMode">
    <div class="title">
      <div class="title-main"><a href="/" target="_blank">애니<span class="mob-hide">메이션</span> 편성표</a></div>
      <div class="color-mode" v-if="isWeb">
        <svg id="dark-mode" @click="applyColorMode('dark')" aria-hidden="true" focusable="false" data-prefix="fas" data-icon="moon" class="svg-inline--fa fa-moon fa-w-16" role="img" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 576 512"><path d="M316.9 18C311.6 7 300.4 0 288.1 0s-23.4 7-28.8 18L195 150.3 51.4 171.5c-12 1.8-22 10.2-25.7 21.7s-.7 24.2 7.9 32.7L137.8 329 113.2 474.7c-2 12 3 24.2 12.9 31.3s23 8 33.8 2.3l128.3-68.5 128.3 68.5c10.8 5.7 23.9 4.9 33.8-2.3s14.9-19.3 12.9-31.3L438.5 329 542.7 225.9c8.6-8.5 11.7-21.2 7.9-32.7s-13.7-19.9-25.7-21.7L381.2 150.3 316.9 18z"/></svg>
        <svg id="light-mode" @click="applyColorMode('light')" aria-hidden="true" focusable="false" data-prefix="fas" data-icon="sun" class="svg-inline--fa fa-sun fa-w-16" role="img" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512"><path d="M256 160c-52.9 0-96 43.1-96 96s43.1 96 96 96 96-43.1 96-96-43.1-96-96-96zm246.4 80.5l-94.7-47.3 33.5-100.4c4.5-13.6-8.4-26.5-21.9-21.9l-100.4 33.5-47.4-94.8c-6.4-12.8-24.6-12.8-31 0l-47.3 94.7L92.7 70.8c-13.6-4.5-26.5 8.4-21.9 21.9l33.5 100.4-94.7 47.4c-12.8 6.4-12.8 24.6 0 31l94.7 47.3-33.5 100.5c-4.5 13.6 8.4 26.5 21.9 21.9l100.4-33.5 47.3 94.7c6.4 12.8 24.6 12.8 31 0l47.3-94.7 100.4 33.5c13.6 4.5 26.5-8.4 21.9-21.9l-33.5-100.4 94.7-47.3c13-6.5 13-24.7.2-31.1zm-155.9 106c-49.9 49.9-131.1 49.9-181 0-49.9-49.9-49.9-131.1 0-181 49.9-49.9 131.1-49.9 181 0 49.9 49.9 49.9 131.1 0 181z"></path></svg>
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
          <td class="tod" :class="{'tod-date': !isPureWeek()}">
            <span v-if="isPureWeek()">{{node.scheduleTime}}</span>
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
    <div class="popup" v-if="animeNow != null">
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
    <div id="user-style"></div>
    <div id="sc2015-theme-error" style="display:none">
      <table>
        <tbody><tr><td>애니편성표의 커스텀코드가 잘못되었습니다.<br/><br/><a href="/schedule" target="_blank">애니시아</a>에서 재설정 하시기 바랍니다.</td></tr></tbody>
      </table>
    </div>
  </div>
</template>

<script setup lang="ts">
// 2019-08-17: 2015년 소스를 vuejs 에 맞게 변경
// 2020-12-25: 다크모드 추가 (2015 Version 유지)
// 2022-10-03: Vue3 컴포넌트 스타일로 변경. (CSS 는 스킨 호환성을 위해 post-css 로 변경하지 않음)
import {onBeforeUnmount, onMounted, Ref, ref} from "vue";
import AnimeCaption from "../../domain/anime/AnimeCaption";
import Anime from "../../domain/anime/Anime";
import anissia from "../../common/anissia";
import animeRemote from "../../domain/anime/remote/animeRemote";

const weekList = ref(['日', '月', '火', '水', '木', '金', '土', '外', '新']);
const weekNow = ref(-1);
const isWeb = ref(true);
const animeList = ref([]) as Ref<Anime[]>;
const animeNow = ref(null) as Ref<Anime|null>;
const captionList = ref([]) as Ref<AnimeCaption[]>;
const colorMode = ref('light');

// method
function isPureWeek() {
  return anissia.isPureWeek(weekNow.value);
}
function getAnimeList(week: number): void {
  weekNow.value = week;
  animeRemote.getScheduleAnimeList(week).then((list) => animeList.value = list);
}
function getCaptionList(anime: Anime) {
  animeRemote.getAnimeCaptionList(anime.animeNo).then((list) => {
    captionList.value = list;
    animeNow.value = anime;
  });
}
function evtClickClosePopup(event: MouseEvent) {
  if (animeNow.value != null && (event.target as HTMLElement).closest('body,.box')?.tagName == 'BODY') {
    animeNow.value = null;
  }
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

// mounted
onMounted(() => {
  applyColorMode(null);
  getAnimeList(new Date().getDay());
  (window as any).colorMode = applyColorMode;
  window.addEventListener('keydown', evtKeyClosePopup, true);
  const theme = location.hash.length > 1 ? location.hash : 'ffffff5987b6fffffff2f2f2497ba79cb3c7ffffffffffff555555f8f8f82474cecb3434000000000000777777111111777777111111c3b443070707999999000000cccccc3a7da3';
  // custom theme
  ((window as any).repaint = ((colors: string) => {
    const c: string[] = colors.match(/[0-9a-f]{6}/ig) || [];
    if (c.filter((e: string) => /^[0-9a-f]{6}$/i.test(e)).length === 24) {
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
      (document.getElementById('user-style') as any).innerHTML = `<style>
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
      (document.querySelector('#sc2015-theme-error') as any).style.display = '';
    }
  }))(theme);
});

onBeforeUnmount(() => {
  window.removeEventListener('keydown', evtKeyClosePopup, true);
});
</script>

<style scoped>
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

@media (min-width: 601px) {
  #sc2015 .main { top:104px; }
  #sc2015 .list td.tod.tod-date { width:106px; }
  #sc2015 .popup .box { width: 400px; margin: 120px auto 0 auto; }
}

@media (max-width: 600px) {
  #sc2015 .main { top:92px; }
  #sc2015 .title { height: 52px }
  #sc2015 .title .color-mode svg { width:28px; height:28px; }
  #sc2015 .mob-hide { display: none; }
  #sc2015 .popup .box { margin: 80px 24px 0; }
  #sc2015 .popup .box .subject { font-size:18px; }
}

@media (max-width: 300px) {
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