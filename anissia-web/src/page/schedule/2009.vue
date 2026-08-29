<template>
  <div id="sc2009" @click="evtClickClosePopup">
    <a href="/" target="_blank" id="mark">&nbsp;</a>

    <nav>
      <div v-for="bcType in weekList" :key="bcType" @click="getAnimeList(bcType)" :style="{ backgroundPosition: `${bcType * -55}px ${bcType === weekNow ? -20 : 0}px` }"></div>
    </nav>

    <article>
      <div class="node">
        <div class="tod">{{ isPureWeek() ? '시간' : '날짜' }}</div>
        <div class="subject ti">제목</div>
        <div class="genres">분류</div>
      </div>
      <div v-for="node in animeList" :key="node.animeNo"
           class="node node-hover" @click="getCaptionList(node);">
        <div class="tod">{{node.scheduleTime}}</div>
        <div class="subject">
          <span v-if="node.subjectPrefix">[<b>{{node.subjectPrefix}}</b>] </span> {{node.subject}}
        </div>
        <div class="genres">{{node.genres.replace(',', ' / ')}}</div>
      </div>
    </article>

    <section v-if="animeNow != null">
      <div class="box">
        <a v-if="animeNow.website" :href="animeNow.website" target="_blank" class="official">공식 사이트</a>
        <div v-for="node in captionList" :key="`${node.name}`">
          <span v-if="node.website === ''">
            <div class="caption"><span>{{node.episodeText}}</span> {{node.name}} <span>{{node.updDtText}}</span></div>
          </span>
          <a v-else :href="node.website" target="_blank">
            <div class="caption"><span>{{node.episodeText}}</span> {{node.name}} <span>{{node.updDtText}}</span></div>
          </a>
        </div>
        <div v-if="captionList.length == 0" class="non-caption">
          자막이 없습니다.
        </div>
      </div>A
    </section>

  </div>
</template>

<script setup lang="ts">
// 2009년 소스를 vuejs 에 맞게 변경 (IE6 호환성 제거)
import {onBeforeUnmount, onMounted, Ref, ref} from "vue";
import animeRemote from "../../domain/anime/remote/animeRemote";
import Anime from "../../domain/anime/Anime";
import anissia from "../../common/anissia";
import AnimeCaption from "../../domain/anime/AnimeCaption";

// data
const weekList = ref([...Array(9).keys()]);
const weekNow = ref(-1);
const animeList = ref([]) as Ref<Anime[]>;
const animeNow = ref(null) as Ref<Anime|null>;
const captionList = ref([]) as Ref<AnimeCaption[]>;

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

onMounted(() => {
  // created
  getAnimeList(new Date().getDay());
  window.addEventListener('keydown', evtKeyClosePopup, true);
});

onBeforeUnmount(() => {
  window.removeEventListener('keydown', evtKeyClosePopup, true);
});
</script>

<style scoped>

#sc2009 {
  position: absolute; top:0; right:0; bottom:0; left:0; background-color: #444;
  background-image: url('/src/page/schedule/2009/bg.jpg'); background-repeat: no-repeat;
  font-family: "Malgun Gothic", "Dotum", sans-serif; min-width:530px; box-sizing: border-box;
  ::-webkit-scrollbar{ width:0; height:0 }
  a { text-decoration: none }
  > * { position: absolute; }
  #mark { cursor: pointer; top: 10px; left: 200px; width: 130px; height: 36px; }

  nav, article { width: 498px; left: 16px; border: 1px solid #a75; }
  nav {
    height: 22px; top: 48px; overflow: visible;
    > div { width: 55px; height: 20px; background-image: url('/src/page/schedule/2009/menu.png'); float: left; cursor: pointer; }
  }

  article {
    height: 458px; top: 76px; background-color: rgba(200, 200, 255, 0.1); overflow: auto;
    > .node {
      background-color: rgba(235, 200, 200, 0.5); border: 1px solid #c99; overflow: auto;
      margin: 5px 4px; padding: 4px 0; font-weight: bold; font-size: 12px;
      &.node-hover { cursor: pointer; }
      &.node-hover:hover { background-color: rgba(245, 210, 210, 0.9); }
      > div {
        float: left; text-align: center;
        &.tod { width: 14%; }
        &.subject {
          width: 54%; padding-left: 1%;
          &:not(.ti) { text-align: left }
        }
        &.genres { width: 25%; }
      }
    }
  }

  section {
    position: absolute; top: 0; left: 0; width: 530px; height: 550px; padding-bottom:50px;
    background-color: rgba(0, 0, 0, 0.4); display: flex; align-items: center; justify-content: center;
    .box {
      background-color:rgba(255, 255, 255, 0.85); line-height: 1.5; padding: 18px 18px 18px;
      a {
        color: #444;
        &.official { display: block;  color: #d43676; font-size:14px; text-align: center; padding: 0px 0px 3px; }
      }
      div.caption {
        padding: 4px 8px 3px; font-size: 12px; margin-top: 3px; color: #4354ab;
        > span { margin:0 4px; font-weight: normal; color: #000; }
        &:hover { background: #fff; }
      }
      div.non-caption { color:#444; padding: 14px 24px; font-weight: normal; font-size:12px; }
    }
  }
}

</style>