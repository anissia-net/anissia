<template>
  <div class="as-note-view as-post-body">
    <Teleport v-if="floatingOutline" to="body">
      <div ref="outlineRoot" class="as-note-outline-tools as-note-outline-floating" hidden></div>
    </Teleport>
    <div ref="root" class="nabi-content" v-html="html"></div>
  </div>
</template>

<script setup lang="ts">
import 'nabi-note/nabi.css';
import '../../common/nabi.pcss';
import {computed, onBeforeUnmount, onMounted, ref, watch} from 'vue';
import {attachViewer} from 'nabi-note/viewer';
import {mountNoteOutline} from '../../common/noteOutline';
import {renderWiki} from './wikiNote';
import type {WikiBody, WikiTemplate} from './wikiRemote';

const props = withDefaults(defineProps<{body: WikiBody; templates?: WikiTemplate[]; floatingOutline?: boolean}>(), {floatingOutline: true});
const root = ref<HTMLElement>();
const outlineRoot = ref<HTMLElement>();
const html = computed(() => renderWiki(props.body, props.templates));
let detach: (() => void) | undefined;
function attach() {
  detach?.();
  if (!root.value) return;
  const viewer = attachViewer(root.value, {locale: 'ko'});
  const outline = outlineRoot.value ? mountNoteOutline(root.value, outlineRoot.value) : undefined;
  detach = () => { outline?.unmount(); viewer.unmount(); };
}
onMounted(attach);
watch(html, attach, {flush: 'post'});
onBeforeUnmount(() => detach?.());
</script>
