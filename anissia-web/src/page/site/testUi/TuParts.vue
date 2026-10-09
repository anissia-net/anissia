<template>
  <div class="grid gap-8 lg:grid-cols-3 items-start">

    <section class="as-sections">
      <div>
        <div class="tu-section-head"><span class="as-section-title">로그인</span></div>
        <div class="as-box p-7">
          <div class="space-y-5">
            <div>
              <label class="as-sub-title mb-2">계정</label>
              <input type="email" class="px-4 py-3 as-input-text" placeholder="user@example.com">
            </div>
            <div>
              <label class="as-sub-title mb-2">암호</label>
              <input type="password" class="px-4 py-3 as-input-text" placeholder="••••••••">
            </div>
            <div class="flex items-center justify-between pt-1">
              <label class="as-switch">
                <input type="checkbox" checked>
                <span class="as-switch-track"></span>
                <span class="ml-3 text-sm font-medium text-ink-2">자동로그인</span>
              </label>
              <span class="text-sm font-medium as-link cursor-pointer">암호분실</span>
            </div>
            <button class="as-btn-primary py-3 w-full text-base!">로그인</button>
          </div>
        </div>
      </div>

      <div>
        <div class="tu-section-head"><span class="as-section-title">메뉴</span></div>
        <div class="as-menu-panel p-2">
          <div class="px-3 pt-2 pb-3 mb-1 border-b border-line">
            <div class="text-[11px] uppercase tracking-wider text-ink-3">로그인 계정</div>
            <div class="mt-1 text-sm font-semibold text-ink truncate">유진</div>
          </div>
          <ul class="text-sm font-medium">
            <li><span class="as-menu-item"><i class="fa-regular fa-address-card"></i> 회원정보</span></li>
            <li><span class="as-menu-item"><i class="fa-regular fa-calendar-days"></i> 애니 편성표</span></li>
            <li><span class="as-menu-item"><i class="fa-regular fa-closed-captioning"></i> 최근 자막</span></li>
            <li><span class="as-menu-item"><i class="fa-solid fa-signature"></i> 자막제작자 신청</span></li>
            <li class="mt-1 pt-1 border-t border-line"><span class="as-menu-item is-danger"><i class="fa-solid fa-right-from-bracket"></i> 로그아웃</span></li>
          </ul>
        </div>
      </div>
    </section>

    <section class="as-sections">
      <div>
        <div class="tu-section-head"><span class="as-section-title">확인 창</span></div>
        <div class="tu-dialog-stage">
          <div class="tu-dialog as-menu-panel">
            <div class="mx-auto w-12 h-12 rounded-full grid place-items-center tu-danger-soft text-xl"><i class="fa-solid fa-trash"></i></div>
            <div class="mt-4 text-center font-semibold text-ink">글을 삭제할까요?</div>
            <p class="as-desc mt-1.5 text-center">삭제한 글은 되돌릴 수 없습니다.</p>
            <div class="mt-5 grid grid-cols-2 gap-2">
              <button class="as-input-btn py-2.5">취소</button>
              <button class="as-btn-primary py-2.5 tu-btn-danger-solid">삭제</button>
            </div>
          </div>
        </div>
      </div>

      <div>
        <div class="tu-section-head"><span class="as-section-title">알림</span></div>
        <div class="space-y-2.5">
          <div class="tu-alert is-info"><i class="fa-solid fa-circle-info"></i><span>운영기록이 3건 추가되었습니다. <b>확인하기</b></span></div>
          <div class="tu-alert is-success"><i class="fa-solid fa-circle-check"></i><span>자막 제작자 심사가 통과되었습니다.</span></div>
          <div class="tu-alert is-danger"><i class="fa-solid fa-triangle-exclamation"></i><span>서버와 연결이 원활하지 않습니다.<br>잠시 후 다시 시도해주세요.</span></div>
        </div>
      </div>

      <div>
        <div class="tu-section-head"><span class="as-section-title">토스트</span></div>
        <div class="flex gap-2">
          <button class="as-input-btn py-2 flex-1" @click="toast.success('복사되었습니다.')"><i class="fa-solid fa-circle-check mr-1.5 text-success"></i>성공</button>
          <button class="as-input-btn py-2 flex-1" @click="toast.error('제목을 입력해 주세요.\n내용을 입력해 주세요.')"><i class="fa-solid fa-circle-exclamation mr-1.5 text-danger"></i>에러</button>
        </div>
      </div>
    </section>

    <section class="as-sections">
      <div>
        <div class="tu-section-head"><span class="as-section-title">상태</span></div>
        <div class="as-box p-5 space-y-4">
          <div class="flex flex-wrap gap-2">
            <span class="tu-status is-on"><span class="dot"></span>방영중</span>
            <span class="tu-status is-act"><span class="dot"></span>심사중</span>
            <span class="tu-status"><span class="dot"></span>완결</span>
            <span class="tu-badge">운영자</span>
            <span class="tu-badge"><i class="fa-solid fa-thumbtack mr-1"></i>공지</span>
          </div>
          <div>
            <div class="flex justify-between as-meta mb-1.5"><span>분기 자막 진행률</span><span>72%</span></div>
            <div class="tu-progress"><span style="width: 72%"></span></div>
          </div>
          <div class="flex items-center gap-4">
            <span class="as-spinner w-8! h-8!"></span>
            <span class="as-desc">불러오는 중…</span>
          </div>
        </div>
      </div>

      <div>
        <div class="tu-section-head"><span class="as-section-title">입력</span></div>
        <div class="as-box p-5 space-y-3">
          <input type="text" class="as-input-text px-3.5 py-2.5" placeholder="애니메이션 제목 검색">
          <textarea class="as-input-text px-3.5 py-2.5" rows="3" placeholder="의견을 남겨주세요"></textarea>
          <div v-choice class="as-segment">
            <button v-for="s in ['주간', '분기', '연간']" :key="s" :class="{'is-on': segment == s}" @click="segment = s">{{ s }}</button>
          </div>
          <input type="range" class="as-range" min="0" max="100" v-model="range">
          <div class="flex flex-wrap gap-2">
            <span class="as-tag">액션</span><span class="as-tag">판타지</span><span class="as-tag">일상</span>
          </div>
        </div>
      </div>

      <div>
        <div class="tu-section-head"><span class="as-section-title">빈 화면</span></div>
        <div class="as-box">
          <div class="as-empty py-12! text-sm!">
            <i class="fa-regular fa-folder-open text-3xl block mb-3 opacity-50"></i>
            진행중인 자막이 없습니다.
          </div>
        </div>
      </div>
    </section>

    <section class="lg:col-span-3">
      <div class="tu-section-head"><span class="as-section-title">목록 표</span></div>
      <table class="as-table">
        <thead>
          <tr><th>#</th><th>제목</th><th>요일</th><th>상태</th><th class="text-right">자막</th></tr>
        </thead>
        <tbody>
          <tr v-for="(r, i) in rows" :key="r.t">
            <td class="tabular-nums">{{ i + 1 }}</td>
            <td class="font-medium text-ink">{{ r.t }}</td>
            <td>{{ r.w }}</td>
            <td><span class="tu-status" :class="r.on ? 'is-on' : ''"><span class="dot"></span>{{ r.on ? '방영중' : '완결' }}</span></td>
            <td class="text-right tabular-nums">{{ r.c }}</td>
          </tr>
        </tbody>
      </table>
    </section>

  </div>
</template>

<script setup lang="ts">
import {ref} from "vue";
import toast from "../../../common/toast";

const segment = ref('분기');
const range = ref(64);
const rows = [
  {t: '장송의 프리렌 2기', w: '금 23:00', on: true, c: 4},
  {t: '전생했더니 검이었습니다 2기', w: '수 22:30', on: true, c: 2},
  {t: 'NANA -나나-', w: '-', on: false, c: 5},
];
</script>
