<template><div ref="root"></div></template>
<script setup lang="ts">
import {onBeforeUnmount, onMounted, ref, watch} from 'vue';
import {makeRegistry} from 'nabi-note';
import {mountDiff, type DiffMount} from 'nabi-note/diff';
import {expandWikiTemplates, wikiWings} from './wikiNote';
import {versionWikiImages} from './wikiImageVersion';
import type {WikiDocument} from './wikiRemote';
const props = defineProps<{before: WikiDocument; after: WikiDocument}>();
const root = ref<HTMLElement>();
let diff: DiffMount | undefined;
let images: MutationObserver | undefined;
function updateImages() { if (root.value) versionWikiImages(root.value, {...props.before.imageVersions, ...props.after.imageVersions}); }
onMounted(() => {
  diff = mountDiff({root: root.value!, before: expandWikiTemplates(props.before.body, props.before.templates), after: expandWikiTemplates(props.after.body, props.after.templates), registry: makeRegistry(wikiWings()), locale: 'ko', allowLocalUrls: false});
  updateImages();
  images = new MutationObserver(updateImages);
  images.observe(root.value!, {childList: true, subtree: true});
});
watch([() => props.before.imageVersions, () => props.after.imageVersions], updateImages, {deep: true});
onBeforeUnmount(() => { images?.disconnect(); diff?.unmount(); });
</script>
