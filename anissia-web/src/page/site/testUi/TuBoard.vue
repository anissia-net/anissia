<template>
  <div>

    <div v-choice class="as-segment max-w-[360px] mb-8">
      <button :class="{'is-on': mode == 'view'}" @click="mode = 'view'">글 보기 · 댓글</button>
      <button :class="{'is-on': mode == 'write'}" @click="mode = 'write'">새 글 쓰기</button>
    </div>

    <div v-if="mode == 'view'">
      <h1 class="as-page-title mb-4">4분기 편성표 갱신 및 자막 제작자 모집 안내</h1>
      <div class="as-post as-post-head flex items-center gap-3 text-sm">
        <span class="tu-avatar"><i class="fa-solid fa-user"></i></span>
        <span class="flex-1 font-medium text-ink truncate">애니시아</span>
        <span class="as-meta">2026-10-09 21:30</span>
        <button class="tu-post-act" title="수정"><i class="fa-solid fa-pen-to-square"></i></button>
        <button class="tu-post-act is-danger" title="삭제"><i class="fa-solid fa-trash"></i></button>
      </div>
      <NoteView :content="article" class="as-post as-post-body mt-2"/>

      <div class="mt-10 flex items-center gap-3">
        <span class="as-section-title">댓글</span>
        <span class="as-tag-xs">3</span>
      </div>

      <div v-for="c in comments" :key="c.name" class="mt-5">
        <div class="as-post as-post-head flex items-center gap-3 text-sm">
          <span class="tu-avatar" :class="{'is-admin': c.admin}"><i :class="c.admin ? 'fa-solid fa-shield-halved' : 'fa-solid fa-user'"></i></span>
          <span class="font-medium text-ink truncate">{{ c.name }}</span>
          <span v-if="c.admin" class="tu-badge">운영자</span>
          <span class="flex-1"></span>
          <span class="as-meta">{{ c.time }}</span>
          <button v-if="c.mine" class="tu-post-act" title="수정"><i class="fa-solid fa-pen-to-square"></i></button>
          <button v-if="c.mine" class="tu-post-act is-danger" title="삭제"><i class="fa-solid fa-trash"></i></button>
        </div>
        <NoteView :content="c.content" class="as-post as-post-body mt-2"/>
      </div>

      <div class="mt-10">
        <PostEditor ticker="inquiry" :post="newComment" placeholder="댓글을 입력하세요." :reload="noop"/>
      </div>
    </div>

    <div v-else>
      <PostEditor ticker="inquiry" :post="newTopic" placeholder="문의 내용을 적어주세요.&#10;작품 추가 요청은 제목과 방영 시기를 함께 적어주시면 빠릅니다." :reload="noop"/>
    </div>

    <div class="flex items-center justify-between gap-4 mt-14 mb-5">
      <h2 class="as-section-title">질문답변</h2>
      <button class="as-btn-primary py-2"><i class="fa-solid fa-pen mr-1.5 text-xs"></i> 글쓰기</button>
    </div>

    <div class="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
      <div v-for="t in topics" :key="t.topic" class="as-card group p-5 block cursor-pointer">
        <div class="flex items-start gap-2">
          <span v-if="t.fixed" class="tu-badge shrink-0 mt-0.5"><i class="fa-solid fa-thumbtack mr-1"></i>공지</span>
          <div class="text-md font-semibold text-ink group-hover:text-brand transition-colors line-clamp-2">{{ t.topic }}</div>
        </div>
        <div class="mt-4 flex items-center gap-4 as-meta">
          <span class="inline-flex items-center gap-1.5 truncate"><i class="fa-regular fa-user opacity-60"></i>{{ t.name }}</span>
          <span class="inline-flex items-center gap-1.5"><i class="fa-regular fa-comment opacity-60"></i>{{ t.count }}</span>
          <span class="inline-flex items-center gap-1.5 ml-auto shrink-0"><i class="fa-regular fa-clock opacity-60"></i>{{ t.time }}</span>
        </div>
      </div>
    </div>

    <div class="tu-pager mt-8">
      <button><i class="fa-solid fa-chevron-left"></i></button>
      <button v-for="n in 5" :key="n" :class="{'is-on': n == 1}">{{ n }}</button>
      <span>…</span>
      <button>24</button>
      <button><i class="fa-solid fa-chevron-right"></i></button>
    </div>

  </div>
</template>

<script setup lang="ts">
import {ref} from "vue";
import NoteView from "../../../domain/board/NoteView.vue";
import PostEditor from "../../../domain/board/PostEditor.vue";
import {Post} from "../../../domain/board/Post";
import {Topic} from "../../../domain/board/Topic";

const mode = ref('view');
const newComment = ref(Post.getNewPost(new Topic()));
const newTopic = ref(Post.getNewTopic());

function noop() {}

const article = `
<h2>4분기 편성표가 갱신되었습니다</h2>
<p>이번 분기에는 <strong>신작 38편</strong>, 2쿨 연속 <em>11편</em>이 편성되었습니다. 방영 시간이 바뀐 작품은 편성표에서 확인해 주세요.</p>
<ul><li>금요일 심야 편성 4편 추가</li><li>결방 일정은 각 작품 상세에 표시</li><li>완결 작품은 <code>/완결</code> 로 검색</li></ul>
<blockquote><p>자막 제작자 신청은 상시 받고 있습니다. 4편 이상의 작업물이 필요합니다.</p></blockquote>
<p>문의는 질문답변 게시판을 이용해 주세요.</p>`;

const comments = [
  {name: '유진', time: '2시간 전', content: '<p>금요일 심야 편성 정리 감사합니다! 프리렌 2기 시간도 맞네요.</p>'},
  {name: '애니시아', time: '1시간 전', admin: true, content: '<p>확인 감사합니다. 누락된 작품이 있으면 댓글로 알려주세요.</p>'},
  {name: '나', time: '방금', mine: true, content: '<p>혹시 <strong>마법소녀 육성계획 restart</strong> 자막 일정도 올라오나요?</p>'},
];

const topics = [
  {topic: '질문답변 게시판 이용 안내', name: '애니시아', count: 0, time: '2026-01-02', fixed: true},
  {topic: '작품 추가 요청', name: '유진', count: 1, time: '1일 전'},
  {topic: '자막 제작자 심사는 언제쯤 완료될까요?', name: '음음음', count: 3, time: '6일 전'},
  {topic: '슈퍼뒤에서담배피우는두사람 방영시간', name: 'misael', count: 2, time: '2026-08-26'},
  {topic: '블리치 천년혈전편(화진담)이 편성표에 없는 것 같습니다.', name: 'Brock', count: 1, time: '2026-07-26'},
  {topic: '자막러 분이 아직 안붙은 애니', name: '버닝데스', count: 3, time: '2026-07-15'},
];
</script>
