<template>
  <div class="as-page">
    <p v-if="!loaded && !error" class="as-empty" role="status">게시글을 불러오는 중입니다.</p>
    <div v-if="error" class="as-empty" role="alert">{{error}} <button class="as-link" @click="loadViewForce">다시 시도</button></div>
    <div v-if="topicNo > 0">

      <div v-if="view && view.topicNo != 0">
        <div v-for="node in view.posts" :key="node.postNo" :class="node.root ? '' : 'mt-8'">
          <div v-if="node.isEditMode">
            <PostEditor :ticker="props.ticker" :placeholder="boardInfo.phTopic" :post="node" :reload="loadViewForce" />
          </div>
          <div v-else>
            <h1 v-if="node.root" class="as-page-title mb-4">{{node.topic}}</h1>
            <!--
              작성자 줄과 본문은 **두 장**이다 — 한 장 안에서 선으로 가르면 둘이 한 덩이로 붙어
              보인다. 사이의 빈 자리가 가르는 일을 대신하고, 여백은 두 장이 같은 값을 두른다.
            -->
            <div class="as-post as-post-head flex items-center gap-3 text-sm">
              <span class="w-7 h-7 shrink-0 rounded-full grid place-items-center bg-brand-soft text-brand text-xs">
                <as-icon name="user"/>
              </span>
              <span class="flex-1 font-medium text-ink truncate">{{node.name}}</span>
              <span class="as-meta">{{node.regDtText}}</span>
              <button v-if="node.canEdit(user)" type="button" @pointerenter="preloadEditor" @focus="preloadEditor" @click="node.isEditMode = true" class="post-act" title="수정" aria-label="게시글 수정"><as-icon name="edit"/></button>
              <button v-if="node.canDelete(user)" type="button" @click="doDelete(node)" class="post-act is-danger" title="삭제" aria-label="게시글 삭제"><as-icon name="trash"/></button>
            </div>
            <NoteView :content="node.content" :floating-outline="node.root" class="as-post as-post-body mt-2"/>
          </div>
        </div>
        <div v-if="boardInfo.canWritePost(user)" class="mt-10">
          <PostEditor :ticker="props.ticker" :post="newPost" :reload="loadViewForce" />
        </div>
      </div>
      <div v-else-if="loaded" class="as-empty">
        존재하지 않거나 삭제된 글입니다.
      </div>


    </div>

    <div v-else-if="topicNo == 0 && loaded">
      <PostEditor :ticker="props.ticker" :post="newTopic" :placeholder="boardInfo.phTopic" :reload="loadViewForce" />
    </div>


  </div>
</template>

<script setup lang="ts">
import {computed, defineAsyncComponent, onMounted, onUnmounted, ref} from 'vue';
import {useRoute, useRouter} from 'vue-router';
import {sessionStore} from '../../domain/session/sessionStore';
import boardRemote from '../../domain/board/remote/boardRemote';
import {BoardInfo} from '../../domain/board/BoardInfo';
import {Topic} from '../../domain/board/Topic';
import {Post} from '../../domain/board/Post';
import {topicUpdates} from '../../domain/board/topicUpdates';
import {useListReturn} from '../../common/useListReturn';
import toast from '../../common/toast';

const props = defineProps<{ticker: string}>();
const route = useRoute(), router = useRouter();
const topicNo = route.path.endsWith('/new') ? 0 : Number(route.params.topicNo);
const loadEditor = () => import('../../domain/board/PostEditor.vue');
const PostEditor = defineAsyncComponent(loadEditor);
const NoteView = defineAsyncComponent(() => import('../../domain/board/NoteView.vue'));
const boardInfo = ref(new BoardInfo());
const view = ref<Topic | null>(null);
const newTopic = ref(Post.getNewTopic());
const newPost = ref(Post.getNewPost(new Topic()));
const loaded = ref(false), error = ref('');
const user = computed(() => sessionStore().user);
const {returnToList} = useListReturn(`/${props.ticker}`);
let active = true, request = 0;

function preloadEditor() { void loadEditor().catch(() => {}); }

async function loadViewForce() {
  const expected = ++request;
  error.value = '';
  try {
    const info = await boardRemote.getTicker(props.ticker);
    if (!active || expected !== request) return;
    boardInfo.value = info;
    if (topicNo === 0) {
      if (!info.canWriteTopic(user.value)) { void router.replace(`/${props.ticker}`); return; }
    } else {
      const node = await boardRemote.getTopic(props.ticker, topicNo);
      if (!active || expected !== request) return;
      view.value = node;
      newPost.value = Post.getNewPost(node);
      if (node.topicNo) topicUpdates.publish(node.topicNo, node);
    }
    loaded.value = true;
  } catch {
    if (active && expected === request) error.value = '게시글을 불러오지 못했습니다.';
  }
}

function doDelete(post: Post) {
  if (!confirm('글을 삭제하시겠습니까?')) {
    return;
  }
  if (post.root) {
    boardRemote.deleteTopic(post.topicNo).then(result => {
      if (result.success) {
        topicUpdates.publish(post.topicNo, null);
        returnToList();
      } else {
        toast.error(result.messageNotNull);
      }
    });
  } else {
    boardRemote.deletePost(post.postNo).then(result => {
      if (result.success) {
        loadViewForce();
      } else {
        toast.error(result.messageNotNull);
      }
    });
  }
}

function onKeyup(event: KeyboardEvent) {
    const post = view?.value?.posts?.[0];
    switch (event.key) {
        case 'e':
        {
            if (post && post.canEdit(user.value) && !post.isEditMode && !(event.target as HTMLElement)?.closest('input, textarea, [contenteditable]')) {
                post.isEditMode = true;
                window.scrollTo(0, 0);
            }
        }
    }
}

void loadViewForce();
onMounted(() => document.addEventListener('keyup', onKeyup, true));
onUnmounted(() => {
  active = false;
  document.removeEventListener('keyup', onKeyup, true);
});
</script>

<style scoped>
@reference "../../common/tailwind.pcss";

.post-act {
  @apply inline-flex items-center justify-center shrink-0 w-9 h-9 rounded-md transition-colors;
  font-size: 1rem;
  color: var(--as-ink-3);
  &:is(:hover, :focus-visible) { color: var(--as-ink); background: var(--as-muted) }
  &.is-danger:is(:hover, :focus-visible) { color: var(--as-danger) }
}
</style>
