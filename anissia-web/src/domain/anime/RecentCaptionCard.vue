<template>
  <div class="as-card p-3.5 flex items-center gap-3">
    <router-link v-if="caption.thumbnail" :to="`/anime/${caption.animeNo}`" class="shrink-0 rounded-ctl" :aria-label="`${caption.subject} 작품 정보`">
      <AnimeThumbnail :src="caption.thumbnail" :width="48" :height="64"/>
    </router-link>
    <div class="min-w-0 flex-1">
      <div class="text-md font-semibold leading-5 text-ink truncate">
        <router-link :to="`/anime/${caption.animeNo}`" class="hover:text-brand transition-colors">{{caption.subject}}</router-link>
      </div>
      <div class="mt-2 flex items-center gap-2.5 text-sm">
        <span class="as-tag-xs as-tag-compact shrink-0">
          <a :href="caption.website" target="_blank" rel="noopener noreferrer" class="inline-flex items-center gap-1">
            <as-icon name="cc" class="opacity-70"/>{{caption.episodeText}}
          </a>
        </span>
        <a :href="caption.website" target="_blank" rel="noopener noreferrer" class="min-w-0 font-semibold text-brand truncate">{{caption.name}}</a>
        <a :href="caption.website" target="_blank" rel="noopener noreferrer" class="ml-auto shrink-0 text-xs text-ink-3 tabular-nums">{{caption.updDtText}}</a>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import AnimeCaption from "./AnimeCaption";
import AnimeThumbnail from "./AnimeThumbnail.vue";

defineProps<{caption: AnimeCaption}>();
</script>
