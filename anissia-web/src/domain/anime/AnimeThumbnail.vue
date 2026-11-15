<template>
  <span class="flex shrink-0 overflow-hidden rounded-ctl bg-surface-2"
        :style="{width: `${width / 16}rem`, height: `${height / 16}rem`}">
    <img v-if="!failed" :key="source" :src="source" :alt="alt" :width="width" :height="height"
         :loading="loading" decoding="async" class="block w-full h-full object-contain" @error="onError"/>
  </span>
</template>

<script setup lang="ts">
import {computed, ref, watch} from 'vue';
import {animeThumbnailImageUrl} from './animeThumbnailImage';

const props = withDefaults(defineProps<{
  src: string;
  alt?: string;
  width?: number;
  height?: number;
  loading?: 'lazy' | 'eager';
}>(), {alt: '', width: 64, height: 64, loading: 'lazy'});

const fallback = ref(false), failed = ref(false);
const source = computed(() => fallback.value ? props.src : animeThumbnailImageUrl(props.src));
watch(() => props.src, () => { fallback.value = false; failed.value = false; });

function onError() {
  if (source.value !== props.src) fallback.value = true;
  else failed.value = true;
}
</script>
