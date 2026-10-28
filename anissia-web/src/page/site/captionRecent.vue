<template>

  <div class="as-page">

    <div class="as-page-head flex items-end justify-between gap-4">
      <div>
        <h1 class="as-page-title">최근 자막</h1>
        <div class="as-desc mt-1.5">최근 90일간 등록된 자막입니다.</div>
      </div>
      <div class="as-meta shrink-0 pb-1">총 <b class="text-ink-2">{{list.totalElements}}</b> 작품</div>
    </div>

    <div class="grid items-start gap-4 cq-md:grid-cols-2 cq-xl:grid-cols-3">
      <RecentCaptionCard v-for="node in list.content" :key="`${node.animeNo}:${node.name}`" :caption="node"/>
    </div>

  </div>

</template>

<script setup lang="ts">

import {nextTick, onUnmounted, Ref, ref} from "vue";
import AnimeCaption from "../../domain/anime/AnimeCaption";
import RecentCaptionCard from "../../domain/anime/RecentCaptionCard.vue";
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
