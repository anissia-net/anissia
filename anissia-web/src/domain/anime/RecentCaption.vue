<template>

  <div class="grid items-start gap-4 cq-md:grid-cols-2">
    <RecentCaptionCard v-for="node in list" :key="`${node.animeNo}:${node.name}`" :caption="node" class="anissia-home-reduce-6"/>
  </div>

</template>

<script setup lang="ts">
import {Ref, ref} from "vue";
import animeRemote from "./remote/animeRemote";
import AnimeCaption from "./AnimeCaption";
import RecentCaptionCard from "./RecentCaptionCard.vue";

const list = ref([]) as Ref<AnimeCaption[]>;
animeRemote.getCaptionRecent().then(data => list.value = data);
</script>
