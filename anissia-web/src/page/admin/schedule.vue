<template>

  <div class="as-page">

    <div class="as-segment">
      <button type="button" v-for="(week, idx) in weekList" :key="week" @click="getAnimeList(idx)" class="font-semibold text-md! px-0.5! py-2.5!" :class="({'is-on': idx == weekNow})">{{week}}</button>
    </div>

    <div class="mt-4">
      <div class="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
        <div v-for="(node, i) in animeList" class="p-5 as-card">
          <div>
            <router-link :to="`/admin/anime?animeNo=${node.animeNo}`">
              <div v-if="node.scheduleTime != '-'" class="text-sm font-semibold text-brand">
                {{node.scheduleTime}}
              </div>
              <div class="text-md mt-1 font-semibold text-ink">
                <span v-if="node.subjectPrefix">[<b class="text-brand">{{node.subjectPrefix}}</b>] </span>
                {{node.subject}}
              </div>
              <div class="text-xs mt-1.5 text-ink-3" lang="ja" v-if="node.originalSubject">{{node.originalSubject}}</div>
            </router-link>
          </div>
          <div class="mt-4 flex flex-wrap gap-1.5">
            <span class="as-tag-xs" v-for="tag in node.tags" :key="tag">{{tag}}</span>
            <span class="as-tag-xs" v-for="tag in node.genres.split(/,/g)" :key="tag"><router-link :to="`/anime?q=%23${encodeURIComponent(tag)}`">{{tag}}</router-link></span>
            <span class="as-tag-xs" v-if="node.website"><a :href="node.website" target="_blank"><i class="fa-solid fa-globe"></i></a></span>
            <span class="as-tag-xs" v-if="node.x"><a :href="node.x" target="_blank" class="fa-brands fa-x-twitter"></a></span>
            <span class="as-tag-xs" v-if="node.captionCount"><i class="fa-regular fa-closed-captioning mr-1 opacity-70"></i>{{node.captionCount}}</span>
          </div>
        </div>
      </div>
    </div>

  </div>

</template>

<script setup lang="ts">
import {ref} from "vue";
import Anime from "../../domain/anime/Anime";
import anissia from "../../common/anissia";
import animeRemote from "../../domain/anime/remote/animeRemote";

const weekList = ref(['日', '月', '火', '水', '木', '金', '土', '外', '新']);
const weekNow = ref(-1);
const animeList = ref([] as Anime[]);

function isPureWeek() {
  return anissia.isPureWeek(weekNow.value);
}

function getAnimeList(week: number): void {
  weekNow.value = week;
  animeRemote.getAdminScheduleAnimeList(week).then((list) => animeList.value = list);
}

getAnimeList(new Date().getDay());

</script>
