<template>
  <div ref="root" class="nabi-content" v-html="html"></div>
</template>

<script setup lang="ts">
import {computed, onBeforeUnmount, onMounted, ref, watch} from "vue";
import {attachViewer} from "nabi-note/viewer";
import type {ViewerAttachment} from "nabi-note/viewer";
import {sanitizeNote} from "../../common/sanitize";

const props = defineProps({
  content: String
});

const root = ref<HTMLElement>();

/**
 * 저장값은 나비가 만든 것이라는 보장이 없으므로 그릴 때마다 거른다.
 * 세울 때 한 번이 아니라 값이 갈릴 때마다 거른다 — 같은 자리에 다른 글이 들어오면 (목록에서 다른 토픽을
 * 고르거나 글이 새로 실리면) Vue 가 이 컴포넌트를 그대로 재사용하므로, 한 번만 거르면 첫 글이 굳는다.
 * 같은 문자열이면 computed 가 다시 계산하지 않으니 값이 그대로일 때 치르는 값도 없다.
 */
const html = computed(() => sanitizeNote(props.content ?? ''));

let viewer: ViewerAttachment | null = null;

/** 뷰어는 버튼을 꽂고 행 순서를 바꾸므로 언제나 읽기 전용 사본에만 붙인다. */
function attach() {
  viewer?.unmount();
  viewer = root.value ? attachViewer(root.value, {locale: 'ko'}) : null;
}

onMounted(attach);

/**
 * `v-html` 이 안쪽을 통째로 갈아 끼우면 뷰어가 꽂아 둔 것도 함께 날아간다 — 다시 붙여야 한다.
 * `flush: 'post'` 라 이 시점의 DOM 은 이미 새 글이다.
 */
watch(html, attach, {flush: 'post'});

onBeforeUnmount(() => {
  viewer?.unmount();
  viewer = null;
});
</script>
