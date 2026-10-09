<template>
  <div class="flex flex-col lg:flex-row gap-8 items-start">

    <div class="flex-1 min-w-0 w-full as-sections">

      <section class="tu-hero as-box">
        <span class="as-sub-title">Anissia</span>
        <h1 class="as-page-title mt-1">이번 분기, 무엇을 볼까요</h1>
        <p class="as-desc mt-2">편성표·자막·랭킹을 한곳에서. 공지·문의·자막·랭킹은 실제 데이터입니다.</p>
        <div class="mt-5 flex flex-wrap items-center gap-2">
          <button class="as-btn-primary py-2.5"><i class="fa-regular fa-calendar-days mr-1.5"></i>편성표 보기</button>
          <button class="as-input-btn py-2.5">애니 검색</button>
          <button class="as-input-btn py-2.5" @click="toastSample"><i class="fa-regular fa-bell mr-1.5"></i>토스트</button>
          <span class="flex-1"></span>
          <span class="as-tag-xs">#판타지</span>
          <span class="as-tag-xs">#이세계</span>
          <span class="as-tag-xs">#일상</span>
        </div>
      </section>

      <div class="grid gap-8 md:grid-cols-2">
        <section>
          <div class="tu-section-head">
            <span class="as-section-title">공지사항</span>
            <span class="tu-more">더보기 <i class="fa-solid fa-chevron-right"></i></span>
          </div>
          <div class="as-box px-4">
            <RecentBoard ticker="notice" v-model="recentBoardData"/>
          </div>
        </section>
        <section>
          <div class="tu-section-head">
            <span class="as-section-title">문의 게시판</span>
            <span class="tu-more">더보기 <i class="fa-solid fa-chevron-right"></i></span>
          </div>
          <div class="as-box px-4">
            <RecentBoard ticker="inquiry" v-model="recentBoardData"/>
          </div>
        </section>
      </div>

      <section class="anissia-home-mode-mob">
        <div class="tu-section-head">
          <span class="as-section-title">최근 자막</span>
          <span class="tu-more">더보기 <i class="fa-solid fa-chevron-right"></i></span>
        </div>
        <RecentCaption/>
      </section>

      <section>
        <div class="tu-section-head">
          <span class="as-section-title">운영기록</span>
        </div>
        <div class="as-box px-4">
          <ActivePanel mode="public"/>
        </div>
      </section>

    </div>

    <aside class="w-full lg:w-[300px] shrink-0 as-sections">
      <section>
        <div class="tu-section-head">
          <span class="as-section-title">애니 랭킹</span>
        </div>
        <div class="as-box p-2">
          <Rank/>
        </div>
      </section>
      <section>
        <div class="as-card p-5 text-center">
          <div class="mx-auto w-12 h-12 rounded-2xl grid place-items-center bg-brand-soft text-brand text-xl">
            <i class="fa-solid fa-signature"></i>
          </div>
          <div class="mt-3 font-semibold text-ink">자막 제작자 모집</div>
          <p class="as-desc mt-1">4편 이상 작업물이 있다면 신청하세요.</p>
          <button class="as-btn-primary w-full py-2.5 mt-4">신청하기</button>
        </div>
      </section>
    </aside>

  </div>
</template>

<script setup lang="ts">
import {ref} from "vue";
import Rank from "../../../domain/rank/AnimeRank.vue";
import RecentCaption from "../../../domain/anime/RecentCaption.vue";
import RecentBoard from "../../../domain/board/RecentBoard.vue";
import ActivePanel from "../../../domain/activePanel/ActivePanel.vue";
import boardRemote from "../../../domain/board/remote/boardRemote";
import {RecentBoardData} from "../../../domain/board/RecentBoardData";
import toast from "../../../common/toast";

const recentBoardData = ref(new RecentBoardData());
boardRemote.getRecentHome().then(data => recentBoardData.value = data);

function toastSample() {
  toast.success('저장되었습니다.');
  toast.error('제목을 입력해 주세요.\n내용을 입력해 주세요.');
}
</script>
