<template>

  <div>

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
                <i class="fa-solid fa-user"></i>
              </span>
              <span class="flex-1 font-medium text-ink truncate">{{node.name}}</span>
              <span class="as-meta">{{node.regDtText}}</span>
              <button v-if="node.canEdit(user)" @click="node.isEditMode = true" class="post-act" title="수정"><i class="fa-solid fa-pen-to-square"></i></button>
              <button v-if="node.canDelete(user)" @click="doDelete(node)" class="post-act is-danger" title="삭제"><i class="fa-solid fa-trash"></i></button>
            </div>
            <NoteView :content="node.content" class="as-post as-post-body mt-2"/>
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

    <div v-else-if="topicNo == 0">
      <PostEditor :ticker="props.ticker" :post="newTopic" :placeholder="boardInfo.phTopic" :reload="loadViewForce" />
    </div>


    <div class="flex items-center justify-between gap-4 mb-5" :class="topicNo >= 0 ? 'mt-14' : ''">
      <h2 v-if="props.title" class="as-section-title">{{props.title}}</h2>
      <span v-else></span>
      <router-link v-if="boardInfo.canWriteTopic(user)" to="?topicNo=0" class="as-btn-primary py-2">
        <i class="fa-solid fa-pen mr-1.5 text-xs"></i> 글쓰기
      </router-link>
    </div>

    <div class="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
      <router-link v-for="(node, i) in list.content" :key="node.topicNo" :to="`?topicNo=${node.topicNo}`" class="as-card group p-5 block">
        <div class="text-md font-semibold text-ink group-hover:text-brand transition-colors line-clamp-2">
          {{node.topic}}
        </div>
        <div class="mt-4 flex items-center gap-4 as-meta">
          <span class="inline-flex items-center gap-1.5 truncate"><i class="fa-regular fa-user opacity-60"></i>{{node.name}}</span>
          <span class="inline-flex items-center gap-1.5"><i class="fa-regular fa-comment opacity-60"></i>{{node.postCount}}</span>
          <span class="inline-flex items-center gap-1.5 ml-auto shrink-0"><i class="fa-regular fa-clock opacity-60"></i>{{node.regDtText}}</span>
        </div>
      </router-link>
    </div>

  </div>

</template>

<script setup lang="ts">
import {computed, nextTick, onMounted, onUnmounted, Ref, ref} from "vue";
import PageData from "../../common/PageData";
import {onBeforeRouteUpdate, useRouter} from "vue-router";
import { ScrollLoader } from "raon";
import {sessionStore} from "../session/sessionStore";
import {Locate} from "raon";
import boardRemote from "./remote/boardRemote";
import {BoardInfo} from "./BoardInfo";
import {Topic} from "./Topic";
import {Post} from "./Post";
import PostEditor from "./PostEditor.vue";
import NoteView from "./NoteView.vue";
import toast from "../../common/toast";

const props = defineProps({
  ticker: String,
  title: String
});

const boardInfo = ref(new BoardInfo());
const topicNo = ref(0);
const list = ref(PageData.empty()) as Ref<PageData<Topic>>;
const view = ref(null) as Ref<Topic|null>;
const page = ref(0);
const router = useRouter();
const session = sessionStore();
const user = computed(() => session.user);

const sl = new ScrollLoader().onNeedNextPage(() => {
  page.value++;
  loadList();
});

const newTopic = ref(Post.getNewTopic());
const newPost = ref(Post.getNewPost(new Topic())) as Ref<Post>;

const loaded = ref(false);

let lastTopicNo = -1;

function init() {
  boardRemote.getTicker(props.ticker!!).then(e => boardInfo.value = e);
}

function clear() {
  lastTopicNo = -1;
  page.value = 0;
}

function load(locate: Locate = new Locate()) {
  loadView(locate);
  loadList();
}

function loadViewForce() {
  lastTopicNo = -1;
  loadView();
}

function loadView(locate: Locate = new Locate()) {
  const no = topicNo.value = locate.getIntParameter('topicNo', -1);
  if (no > 0) {
    if (lastTopicNo != no) {
      lastTopicNo = no;
      boardRemote.getTopic(props.ticker!!, no).then(node => {
        view.value = node;
        newPost.value = Post.getNewPost(node);
        loaded.value = true
      });
    } else {
      view.value = view.value;
    }
  } else if (no == 0) {
    if (!boardInfo.value.canWriteTopic(user.value)) {
      router.push(locate.path);
      return;
    }
    lastTopicNo = -1;
    view.value = null;
  } else {
    lastTopicNo = -1;
    view.value = null;
  }
}

function loadList() {
  const isFirstPage = page.value == 0;

  boardRemote.getList(props.ticker!!, page.value).then(pageData => {
      if (isFirstPage) {
        list.value = pageData;
      } else {
        list.value = list.value.merge(pageData);
      }
      nextTick(() => sl.watch(pageData.next))
  });
}

function doDelete(post: Post) {
  if (!confirm('글을 삭제하시겠습니까?')) {
    return;
  }
  if (post.root) {
    boardRemote.deleteTopic(post.topicNo).then(result => {
      if (result.success) {
        router.push(location.pathname);
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
            if (post && post.canEdit(user.value) && !post.isEditMode && ['TEXTAREA', 'INPUT'].indexOf(event.target?.['tagName']) == -1) {
                post.isEditMode = true;
                window.scrollTo(0, 0);
            }
        }
    }
}

init();
load();

onBeforeRouteUpdate((to, from, next) => {
  clear();
  load(new Locate(to.fullPath));
  next();
});

onUnmounted(() => {
  sl.destroy();
  document.removeEventListener('keyup', onKeyup, true);
});

onMounted(() => {
    document.addEventListener('keyup', onKeyup, true);
});
</script>

<style scoped>
@reference "../../common/tailwind.pcss";

.post-act {
  @apply inline-flex items-center justify-center w-7 h-7 rounded-md text-xs transition-colors;
  color: var(--as-ink-3);
  &:hover { color: var(--as-ink); background: var(--as-muted) }
  &.is-danger:hover { color: var(--as-danger) }
}
</style>