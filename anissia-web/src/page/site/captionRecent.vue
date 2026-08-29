<template>

  <div class="as-page">

    <div class="as-page-head flex items-end justify-between gap-4">
      <div>
        <h1 class="as-page-title">최근 자막</h1>
        <div class="as-desc mt-1.5">최근 90일간 등록된 자막입니다.</div>
      </div>
      <div class="as-meta shrink-0 pb-1">총 <b class="text-ink-2">{{list.totalElements}}</b> 작품</div>
    </div>

    <div class="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
      <div v-for="node in list.content" class="as-card p-5">
        <router-link :to="`/anime?animeNo=${node.animeNo}`" class="block text-md font-semibold text-ink hover:text-brand transition-colors truncate">
          {{node.subject}}
        </router-link>
        <a :href="node.website" target="_blank" class="mt-3.5 flex items-center gap-2.5 text-sm">
          <span class="as-tag-xs shrink-0"><i class="fa-regular fa-closed-captioning mr-1 opacity-70"></i>{{node.episodeText}}</span>
          <span class="font-semibold text-brand truncate">{{node.name}}</span>
          <span class="ml-auto shrink-0 as-meta">{{node.updDtText}}</span>
        </a>
      </div>
    </div>

  </div>

</template>

<script setup lang="ts">

import {nextTick, onUnmounted, Ref, ref} from "vue";
import AnimeCaption from "../../domain/anime/AnimeCaption";
import animeRemote from "../../domain/anime/remote/animeRemote";
import PageData from "../../common/PageData";
import { ScrollLoader } from "raon";

const page = ref(0);
const list = ref(PageData.empty()) as Ref<PageData<AnimeCaption>>;

const sl = new ScrollLoader().onNeedNextPage(() => {
  page.value++;
  loadList();
});

function loadList() {
  const isFirstPage = page.value == 0;

  animeRemote.getCaptionRecentPage(page.value).then(pageData => {
    if (isFirstPage) {
      list.value = pageData;
    } else {
      list.value = list.value.merge(pageData);
    }
    nextTick(() => sl.watch(pageData.next))
  });
}

loadList();

onUnmounted(() => sl.destroy());

</script>
