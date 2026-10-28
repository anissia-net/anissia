<template>
  <dialog ref="dialog" class="as-wiki-dialog as-box" @cancel="$emit('close')">
    <div class="flex items-center justify-between gap-4 mb-4"><h2 class="as-section-title">다른 사용자의 수정과 합치기</h2><button type="button" class="as-input-btn px-3 py-1" @click="$emit('close')">취소</button></div>
    <p class="as-desc mb-4">겹친 변경을 확인해 주세요. 합친 내용은 편집기에 반영되며, 저장 버튼을 눌러야 저장됩니다.</p>
    <div ref="root"></div>
    <p v-if="error" class="text-sm text-red-600 mt-3" role="alert">{{error}}</p>
  </dialog>
</template>

<script setup lang="ts">
import {onBeforeUnmount, onMounted, ref} from 'vue';
import {mountMerge, type MergeMount} from 'nabi-note/merge';
import {normalizeWikiBlocks, wikiWings} from './wikiNote';
import {wikiErrorMessage, type WikiBody, type WikiTemplate} from './wikiRemote';

const props = defineProps<{base: WikiBody; local: WikiBody; incoming: WikiBody; templates: WikiTemplate[]; allowTemplates: boolean}>();
const emit = defineEmits<{close: []; resolved: [body: WikiBody]}>();
const root = ref<HTMLElement>(), dialog = ref<HTMLDialogElement>(), error = ref('');
let merge: MergeMount | undefined;
onMounted(() => {
  dialog.value?.showModal();
  try {
    const normalize = (body: WikiBody) => normalizeWikiBlocks(body, props.templates, props.allowTemplates);
    merge = mountMerge({root: root.value!, base: normalize(props.base), local: normalize(props.local), incoming: normalize(props.incoming), wings: wikiWings(() => props.templates, props.allowTemplates), locale: 'ko', allowLocalUrls: false,
      onConfirm: result => { emit('resolved', {schemaVersion: 1, blocks: result.json}); },
      onError: value => { error.value = wikiErrorMessage(value); },
    });
  } catch (value) { error.value = wikiErrorMessage(value); }
});
onBeforeUnmount(() => merge?.unmount());
</script>
