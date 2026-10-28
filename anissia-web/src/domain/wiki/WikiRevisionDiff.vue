<template><div ref="root"></div></template>
<script setup lang="ts">
import {onBeforeUnmount, onMounted, ref} from 'vue';
import {makeRegistry} from 'nabi-note';
import {mountDiff, type DiffMount} from 'nabi-note/diff';
import {expandWikiTemplates, wikiWings} from './wikiNote';
import type {WikiDocument} from './wikiRemote';
const props = defineProps<{before: WikiDocument; after: WikiDocument}>();
const root = ref<HTMLElement>();
let diff: DiffMount | undefined;
onMounted(() => { diff = mountDiff({root: root.value!, before: expandWikiTemplates(props.before.body, props.before.templates), after: expandWikiTemplates(props.after.body, props.after.templates), registry: makeRegistry(wikiWings()), locale: 'ko', allowLocalUrls: false}); });
onBeforeUnmount(() => diff?.unmount());
</script>
