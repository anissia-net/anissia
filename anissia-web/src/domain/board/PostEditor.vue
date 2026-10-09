<template>

  <!--
    읽는 글과 같은 짜임 위에 선다 — 고쳐 쓸 때 글이 다른 자리로 옮겨간 것처럼 보이면 안 된다.
    읽는 쪽이 [작성자 줄] + [본문] 두 장이므로 여기도 [주제] + [본문] 두 장이고, 여백도 같은 값이다.
  -->
  <div>
    <!-- 종이가 이미 테두리를 두르므로 입력칸은 제 테두리를 벗는다 — 상자 안의 상자는 한 겹이 낫다. -->
    <div v-if="post.root" class="as-post as-post-head mb-2">
      <input type="text" v-model="topic" placeholder="주제" class="as-input-bare text-base! font-medium">
    </div>

    <!--
      본문 종이는 여백을 두르지 않는다 — 편집기가 종이를 **꽉 채운다**. 그래야 붙는 단추 줄이
      종이 폭 그대로 덮고, 종이 안에 편집기가 한 겹 더 들어앉은 것처럼 보이지 않는다.
      쓰는 자리의 안쪽 여백은 `nabi.pcss` 에서 `.nabi .nabi-content` 가 직접 두르므로 글이 서는
      자리는 읽는 화면과 그대로 같다.
    -->
    <div class="as-post as-post-editor">
      <!--
        나비는 껍데기를 짓지 않는다 — 세 자리만 호스트가 세우고 나머지(줄·판·떠 있는 상자)는 mount 가 채운다.
        `.nabi` 는 색·모양 토큰이 걸리고 전체화면이 물리는 상자, `.nabi-toolbar` 는 단추 줄과 상황 줄을
        한 덩이로 붙여 두는 자리 (따로 붙으면 상황 줄이 뜨고 질 때마다 화면이 밀린다), `.nabi-content` 는
        실제로 쓰는 자리다.
      -->
      <div ref="rootElement" class="nabi">
        <div ref="chromeElement" class="nabi-toolbar">
          <div ref="toolbarElement"></div>
          <div ref="contextElement"></div>
        </div>
        <div ref="surfaceElement" class="nabi-content" contenteditable="true"></div>
      </div>

      <!-- 종이가 여백을 안 두르므로 단추 줄은 제 여백을 스스로 챙긴다. -->
      <div class="as-post-editor-actions flex items-center justify-end gap-2">
        <input v-if="!post.isNewPost" type="button" value="취소" @click="doCancel" class="as-input-btn py-2"/>
        <input type="button" value="작성완료" @click="doSave" class="as-btn-primary py-2"/>
      </div>
    </div>
  </div>

</template>

<script setup lang="ts">
import {computed, onBeforeUnmount, onMounted, Ref, ref, watch} from "vue";
import {Post} from "./Post";
import {useRouter} from "vue-router";
import {
  browserHistoryStorage,
  createNabiWith,
  mountContextToolbar,
  mountHints,
  mountLocalHistory,
  mountPickedMark,
  mountSticky,
  mountSurface,
  mountToolbar,
  mountViewTools,
  openHistoryPanel,
  renderStoredHtml,
  watchSettle,
} from "nabi-note";
import type {Nabi} from "nabi-note";
import {mountDiffWing, type DiffWingMount} from "nabi-note/diff";
import {attachViewer} from "nabi-note/viewer";
import {isBlankNote, noteWings} from "../../common/note";
import boardRemote from "./remote/boardRemote";
import toast from "../../common/toast";

const props = defineProps({
  post: Post,
  ticker: String,
  placeholder: String,
  reload: Function
});

const post = computed(() => props.post) as Ref<Post>;
const topic = ref('');
const content = ref('');
const router = useRouter();

const rootElement = ref<HTMLElement>();
const chromeElement = ref<HTMLElement>();
const toolbarElement = ref<HTMLElement>();
const contextElement = ref<HTMLElement>();
const surfaceElement = ref<HTMLElement>();

const placeholderText = computed(() => props.placeholder || '내용');

/*
 * 자리 표시 글자는 `mountSurface` 가 세울 때 한 번 읽고 만다.
 * 그런데 이 값(`boardInfo.phTopic`)은 편집기가 선 **뒤에** 도착한다 — 게시판 정보와 글은 서로 다른
 * 약속으로 따로 실려 오고, 글쓰기 화면은 게시판 정보를 기다리지 않고 먼저 선다.
 * 그래서 값이 갈리면 나비가 쓰는 것과 같은 문(`--nabi-placeholder`)으로 다시 적는다.
 */
watch(placeholderText, text => {
  surfaceElement.value?.style.setProperty('--nabi-placeholder', cssQuoted(text));
});

/*
 * 나비의 `cssQuoted` 와 같은 규칙이다. 그쪽은 `.d.ts` 에만 있고 진입점으로 나오지 않아 가져다 쓸 수 없다.
 * CSS 문자열이라 `\` 와 `"` 를 막고, 줄바꿈은 글자가 아니라 `\A` 로 적는다 — 0.7.2 의
 * `white-space: pre-line` 이 그걸 줄바꿈으로 그린다. 뒤의 빈칸은 escape 의 끝을 알리는 것이라 뺄 수 없다.
 */
function cssQuoted(value: string): string {
  return `"${value.replace(/\\/g, '\\\\').replace(/"/g, '\\"').replace(/\r\n?|\n/g, '\\A ')}"`;
}

let nabi: Nabi | null = null;
/** 세운 역순으로 걷는다. */
let unmounts: (() => void)[] = [];

function validate(root: boolean): boolean {
  const errors: string[] = [];
  if (root && !topic.value.trim()) {
    errors.push('제목을 입력해 주세요.');
  }
  if (isBlankNote(content.value)) {
    errors.push('내용을 입력해 주세요.');
  }
  if (errors.length) {
    toast.error(errors.join('\n'));
    return false;
  }
  return true;
}

function doSave() {
  const p = post.value;
  if (!validate(p.root)) {
    return;
  }
  if (p.root) {
    doSaveTopic(p);
  } else {
    doSavePost(p);
  }
}

function doSaveTopic(post: Post) {
  if (post.isNew) {
    boardRemote.createTopic(props.ticker!!, topic.value, content.value).then(result => {
      if (result.success) {
        router.push(`?topicNo=${result.data}`);
      } else {
        toast.error(result.messageNotNull);
      }
    });
  } else {
    boardRemote.updateTopic(post.topicNo, topic.value, content.value).then(result => {
      if (result.success) {
        post.topic = topic.value;
        post.content = content.value;
        post.isEditMode = false;
      } else {
        toast.error(result.messageNotNull);
      }
    });
  }
}

function doSavePost(post: Post) {
  if (post.isNew) {
    boardRemote.createPost(post.topicNo, content.value).then(result => {
      if (result.success) {
        (props.reload!!)();
        nabi?.setHtml('');
        content.value = '';
      } else {
        toast.error(result.messageNotNull);
      }
    })
  } else {
    boardRemote.updatePost(post.postNo, content.value).then(result => {
      if (result.success) {
        post.content = content.value;
        post.isEditMode = false;
      } else {
        toast.error(result.messageNotNull);
      }
    });
  }
}

function doCancel() {
  if (post.value.isNewTopic) {
    router.push(location.pathname);
  } else {
    post.value.isEditMode = false;
  }
}

onMounted(() => {
  topic.value = post.value.topic;
  content.value = post.value.content;

  const surface = surfaceElement.value!!;
  /*
   * 이 게시판은 NABI TREE(JSON) 가 아니라 HTML 을 저장하므로 넣는 문도 받는 문도 HTML 쪽이다.
   * `ask` 를 비워 두면 confirm 이 언제나 `false` 라 "저장 안 된 글을 버리고 열까?" 가 늘 취소된다.
   * 셋째 자리인 `choose`(0.8 의 붙여넣기 후보 고르기)는 **적지 않는다** — `mountToolbar` 가
   * 코어의 고르는 판을 스스로 물려 주므로, 여기에 적으면 화면에 뜨는 판 대신 우리가 낸 답이
   * 이긴다. 브라우저에는 여러 개 중 하나를 묻는 기본 상자가 없어 적을 말도 마땅찮다.
   */
  const built = createNabiWith(noteWings(), {
    locale: 'ko',
    ask: {
      message: text => window.alert(text),
      confirm: text => window.confirm(text),
    },
  });
  nabi = built.nabi;
  const registry = built.registry;

  // 화면에 붙기 전에 글을 넣는다 — mountSurface 가 그때의 문서를 한 번에 그리므로 두 번 그리지 않는다.
  nabi.setHtml(content.value);

  const editSurface = mountSurface({nabi, registry, root: surface, locale: 'ko', placeholder: placeholderText.value});
  const settle = watchSettle(document, {surface});
  const shared = {nabi, registry, surface, settle, locale: 'ko'};

  const history = mountLocalHistory({nabi, storage: browserHistoryStorage(window)});
  let diff: DiffWingMount | null = null;

  const toolbar = mountToolbar({
    ...shared,
    root: toolbarElement.value!!,
    // 판이 필요한 단추는 호스트에게 넘어온다. 지금은 지역 기록 하나뿐이다.
    onHost: w => {
      if (w === 'localHistory') {
        openHistoryPanel({history, surface, locale: 'ko', sessionId: nabi!!.sessionId, render: renderRecord});
      } else if (w === 'diff') {
        diff?.open();
      }
    },
  });
  const context = mountContextToolbar({...shared, root: contextElement.value!!});
  const hints = mountHints({toolbar, context, root: chromeElement.value!!, surface});
  const viewTools = mountViewTools({
    nabi,
    surface,
    root: rootElement.value!!,
    container: toolbarElement.value!!,
    locale: 'ko',
    // 미리보기는 굳은 HTML 이라 읽는 쪽 동작(표 정렬·코드 색)이 저절로 붙지 않는다 — 읽는 화면과 같게 맞춘다.
    onBody: body => {
      const viewer = attachViewer(body, {locale: 'ko'});
      return () => viewer.unmount();
    },
  });
  diff = mountDiffWing({nabi, registry, surface, locale: 'ko'});
  /*
   * `nabi` 를 건네면 0.8 의 스티키가 **고친 뒤 스스로 겨눈다** — 가려진 만큼만 캐럿을 단추 줄
   * 밑에서 밀어낸다. 건네지 않으면 호스트가 `aim()` 을 부를 때만 움직이므로, 손가락 기기에서
   * 키보드가 올라온 채 줄이 늘면 쓰는 자리가 줄 뒤로 숨는다.
   */
  const sticky = mountSticky({nabi, root: rootElement.value!!, surface, chrome: chromeElement.value!!, settle});
  const picked = mountPickedMark({nabi, surface});

  const offChange = nabi.onChange(() => content.value = nabi!!.getHtml());

  /** 지역 기록의 `body` 는 `getJson()` 을 문자열로 굳힌 것이다. 손으로 고칠 수 있는 자리라 깨진 값도 받는다. */
  function renderRecord(record: {body: string}): string {
    try {
      return renderStoredHtml(JSON.parse(record.body), registry) ?? '';
    } catch {
      return '';
    }
  }

  unmounts = [
    offChange,
    () => diff?.unmount(),
    () => picked.unmount(),
    () => sticky.unmount(),
    () => viewTools.unmount(),
    () => hints.unmount(),
    () => context.unmount(),
    () => toolbar.unmount(),
    () => history.unmount(),
    () => settle.unmount(),
    () => editSurface.unmount(),
  ];
});

onBeforeUnmount(() => {
  for (const off of unmounts) {
    off();
  }
  unmounts = [];
  nabi = null;
});
</script>
