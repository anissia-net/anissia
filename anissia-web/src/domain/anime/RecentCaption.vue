<template>

  <div class="grid gap-4 md:grid-cols-2">
    <div v-for="node in list" class="as-card p-4 anissia-home-reduce-6">
      <router-link :to="`/anime?animeNo=${node.animeNo}`" class="block font-semibold text-[15px] text-ink hover:text-brand transition-colors truncate">
        {{node.subject}}
      </router-link>
      <a :href="node.website" target="_blank" class="mt-3 flex items-center gap-2.5 text-sm group">
        <span class="as-tag-xs shrink-0"><i class="fa-regular fa-closed-captioning mr-1 opacity-70"></i>{{node.episodeText}}</span>
        <span class="font-semibold text-brand truncate">{{node.name}}</span>
        <span class="ml-auto shrink-0 text-xs text-ink-3 tabular-nums">{{node.updDtText}}</span>
      </a>
    </div>
  </div>

</template>

<script setup lang="ts">
import {Ref, ref} from "vue";
import animeRemote from "./remote/animeRemote";
import AnimeCaption from "./AnimeCaption";

const list = ref([]) as Ref<AnimeCaption[]>;
animeRemote.getCaptionRecent().then(data => list.value = data);
</script>
