<template>

  <div class="as-page">

    <div v-choice class="as-segment mt-2">
      <router-link to="/admin/caption" :class="{'is-on': state === 1}">
        방영중
      </router-link>
      <router-link to="/admin/caption?state=0" :class="{'is-on': state === 0}">
        완료
      </router-link>
    </div>

    <div class="mt-5">
      <div class="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
        <div v-for="node in list.content" class="p-5 as-card">
          <div class="anissia-node-hover-view-del text-lg pt-1 px-1">
            {{node.subject}}
            <i @click="doDelete(node)" class="fa-solid fa-xmark inline-block p-[3px] ml-1 cursor-pointer opacity-40 hover:opacity-100 hover:text-danger transition"></i>
          </div>
          <div class="mt-4 flex">
            <input type="button" value="◀" @click="moveEpisode(node, false)" class="w-[60px] mr-2 as-input-btn" />
            <input type="number" name="episode" min="0" v-model="node.episode" class="text-center p-2.5 as-input-text">
            <input type="button" value="▶" @click="moveEpisode(node, true)" class="w-[60px] ml-2 as-input-btn" />
          </div>
          <div class="mt-4 flex">
            <input type="datetime-local" v-model="node.updDt" pattern="[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}" class="text-center p-2.5 as-input-text" />
            <input type="button" value="현재" @click="setNow(node)" class="w-[60px] ml-2 as-input-btn" />
          </div>
          <div class="mt-4 flex">
            <input type="text" name="website" v-model="node.website" placeholder="https://example.com" class="p-2.5 as-input-text">
            <input type="button" value="저장" @click="doSave(node)" class="w-[60px] ml-2 as-input-btn" />
          </div>

        </div>
      </div>
      <div v-if="list.content.length == 0 && list.loaded">
        <div class="as-empty">진행중인 자막이 없습니다.</div>
      </div>
    </div>

  </div>

</template>

<script setup lang="ts">
import {nextTick, Ref, ref} from "vue";
import {DateFormat, ScrollLoader} from "raon";
import PageData from "../../common/PageData";
import animeRemote from "../../domain/anime/remote/animeRemote";
import AnimeCaption from "../../domain/anime/AnimeCaption";
import {onBeforeRouteUpdate} from "vue-router";
import {Locate} from "raon";
import anissia from "../../common/anissia";
import toast from "../../common/toast";


const page = ref(0);
const state = ref(0);
const list = ref(PageData.empty().notLoaded()) as Ref<PageData<AnimeCaption>>;

const sl = new ScrollLoader().onNeedNextPage(() => {
  page.value++;
  load();
});

function clear(locate: Locate) {
  state.value = locate.getParameter('state', '1') == '0' ? 0 : 1;
  page.value = 0;
}

function load() {
  const isFirstPage = page.value == 0;

  animeRemote.getAdminCaptionList(state.value, page.value).then(pageData => {
    if (isFirstPage) {
      list.value = pageData;
    } else {
      list.value = list.value.merge(pageData);
    }
    nextTick(() => sl.watch(pageData.next))
  });
}

function moveEpisode(caption: AnimeCaption, isUp: boolean) {
  const episode = Number(caption.episode);
  if (isUp) {
    caption.episode = ((isNaN(episode) ? 0 : Math.floor(episode)) + 1).toString();
  } else {
    caption.episode = Math.max(0, (isNaN(episode) ? 1 : Math.ceil(episode)) - 1).toString();
  }
}

function setNow(caption: AnimeCaption) {
  caption.updDt = new DateFormat().format("yyyy-MM-ddTHH:mm");
}

function doSave(caption: AnimeCaption) {
  animeRemote.updateAdminCaption(caption).then(result => toast.result(result));
}

function doDelete(caption: AnimeCaption) {
  if (confirm("자막을 종료하시겠습니까?")) {
    animeRemote.deleteAdminCaption(caption.animeNo).then(result => result.code == 'ok' ? load() : toast.error(result.message));
  }
}

clear(new Locate());
load();

onBeforeRouteUpdate((to, from, next) => {
  clear(new Locate(to.fullPath));
  next();
  load();
});

</script>

<style scoped>
.anissia-node-hover-view-del:not(:hover) i { display: none }
input::-webkit-outer-spin-button,
input::-webkit-inner-spin-button {
  -webkit-appearance: none;
}

</style>