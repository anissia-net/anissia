<template>
  <div ref="sentinel" class="h-px" aria-hidden="true"></div>
</template>

<script setup lang="ts">
import {onBeforeUnmount, ref, watch} from 'vue';

const props = defineProps<{enabled: boolean}>();
const emit = defineEmits<{load: []}>();
const sentinel = ref<HTMLElement>();
const observer = new IntersectionObserver(entries => {
  if (props.enabled && entries.some(entry => entry.isIntersecting)) {
    observer.disconnect();
    emit('load');
  }
}, {rootMargin: '0px 0px 240px 0px'});

// Reobserve after each page renders so short lists also fill the viewport.
watch([sentinel, () => props.enabled], ([element, enabled]) => {
  observer.disconnect();
  if (element && enabled) observer.observe(element);
}, {flush: 'post'});
onBeforeUnmount(() => observer.disconnect());
</script>
