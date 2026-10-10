<template>

  <div class="as-app min-h-screen flex flex-col">

    <header class="relative z-30 w-full mt-2.5">
      <div class="as-container">
        <div class="as-header-bar flex justify-between items-center h-[52px] pl-4 pr-1.5">

        <router-link to="/" class="group flex items-center py-1.5 rounded-ctl">
          <span class="as-wordmark ml-[0.1rem] text-[15px] font-black">ANISSIA</span>
        </router-link>

        <nav class="flex items-center gap-0.5">
          <router-link to="/schedule" class="nav-link hidden md:inline-flex">
            <i class="fa-regular fa-calendar-days"></i><span>편성표</span>
          </router-link>
          <router-link to="/anime" class="nav-link hidden md:inline-flex">
            <i class="fa-regular fa-circle-play"></i><span>애니</span>
          </router-link>
          <router-link to="/inquiry" class="nav-link hidden md:inline-flex">
            <i class="fa-regular fa-comment-dots"></i><span>질문답변</span>
          </router-link>

          <span class="hidden md:inline-block w-px h-5 mx-2 bg-line"></span>

          <router-link v-if="user.isAdmin && !url('/admin')" to="/admin" class="nav-icon" title="관리자">
            <i class="fa-solid fa-sliders"></i>
          </router-link>
          <button class="nav-icon" @click="toggleTheme()" title="테마 변경" aria-label="테마 변경">
            <span class="dark:hidden"><i class="fa-solid fa-sun"></i></span>
            <span class="hidden dark:inline"><i class="fa-solid fa-moon"></i></span>
          </button>
          <button class="nav-icon" :class="{'is-on': user.isLogin}" @click="onHeaderMenu = true" title="메뉴" aria-label="메뉴">
            <i v-if="user.isLogin" class="fa-regular fa-circle-user"></i>
            <i v-else class="fa-solid fa-bars"></i>
          </button>
        </nav>

        </div>
      </div>
    </header>

    <transition name="as-menu">
      <div v-if="onHeaderMenu" class="pop-close z-40 fixed inset-0 bg-black/25 backdrop-blur-[2px]" @click="doCloseHeaderMenu">
        <div class="as-container flex justify-end">
          <div class="pop-not-close as-menu-panel mt-[70px] w-[248px] p-2">

            <div v-if="user.isLogin" class="px-3 pt-2 pb-3 mb-1 border-b border-line">
              <div class="text-[11px] uppercase tracking-wider text-ink-3">로그인 계정</div>
              <div class="mt-1 text-sm font-semibold text-ink truncate">{{ user.name }}</div>
            </div>

            <ul class="text-sm font-medium">
              <li v-if="!user.isLogin"><router-link to="/login" class="pop-close as-menu-item"><i class="fa-solid fa-right-to-bracket"></i> 로그인</router-link></li>
              <li v-if="user.isLogin"><router-link to="/account" class="pop-close as-menu-item"><i class="fa-regular fa-address-card"></i> 회원정보</router-link></li>
              <li><router-link to="/schedule" class="pop-close as-menu-item"><i class="fa-regular fa-calendar-days"></i> 애니 편성표</router-link></li>
              <li><router-link to="/anime" class="pop-close as-menu-item"><i class="fa-regular fa-circle-play"></i> 애니정보</router-link></li>
              <li><router-link to="/caption/recent" class="pop-close as-menu-item"><i class="fa-regular fa-closed-captioning"></i> 최근 자막</router-link></li>
              <li><router-link to="/translator/apply" class="pop-close as-menu-item"><i class="fa-solid fa-signature"></i> 자막제작자 신청</router-link></li>
              <li><router-link to="/introduce" class="pop-close as-menu-item"><i class="fa-regular fa-star"></i> 애니시아 소개</router-link></li>
              <li><router-link to="/notice" class="pop-close as-menu-item"><i class="fa-regular fa-bell"></i> 공지사항</router-link></li>
              <li><router-link to="/inquiry" class="pop-close as-menu-item"><i class="fa-regular fa-comment-dots"></i> 질문답변</router-link></li>
              <li v-if="user.isLogin" class="mt-1 pt-1 border-t border-line">
                <span @click="logout" class="pop-close as-menu-item is-danger"><i class="fa-solid fa-right-from-bracket"></i> 로그아웃</span>
              </li>
            </ul>

          </div>
        </div>
      </div>
    </transition>

    <main class="flex-1 pb-16">
      <router-view/>
    </main>

    <transition name="as-fade">
      <div v-if="ajaxState.state == 'loading'" class="fixed inset-0 z-50 flex items-center justify-center backdrop-blur-[3px] bg-canvas/40">
        <div class="text-center">
          <span class="as-spinner"></span>
          <div class="mt-6 text-sm tracking-[.25em] uppercase text-ink-3">loading</div>
        </div>
      </div>
      <div v-else-if="ajaxState.state == 'error'" class="fixed inset-0 z-50 flex items-center justify-center backdrop-blur-[3px] bg-canvas/50 px-6">
        <div class="as-box as-frost max-w-[440px] w-full p-8 text-center">
          <div class="mx-auto w-14 h-14 rounded-full grid place-items-center bg-brand-soft text-brand text-2xl">
            <i class="fa-solid fa-screwdriver-wrench"></i>
          </div>
          <div class="mt-5 text-xl font-bold text-ink">서버 연결 실패</div>
          <div class="mt-3 text-sm leading-[1.9] text-ink-2">
            현재 애니시아 서버에 연결할 수 없습니다.
            <br/>빠르게 정상화 하도록 하겠습니다.
          </div>
          <a href="mailto:auth@anissia.net" class="as-input-btn mt-6 py-2 inline-block">auth@anissia.net</a>
        </div>
      </div>
    </transition>

  </div>

</template>

<script setup lang="ts">

import {computed, onUnmounted, ref} from "vue";
import theme from "../../common/theme";
import anissia from "../../common/anissia";
import {sessionStore} from "../../domain/session/sessionStore";
import {onBeforeRouteUpdate, useRoute, useRouter} from "vue-router";
import sessionService from "../../domain/session/remote/sessionService";
import image_error from "./layout/image_error.svg";
import {ajaxStateStore} from "../../common/ajaxStateStore";


const router = useRouter();
const route = useRoute();
const session = sessionStore();

const ajaxState = ajaxStateStore();

const onHeaderMenu = ref(false)
const url = anissia.url;
const user = computed(() => session.user);

function logout() {
  sessionService.logout();
  sessionService.amendPathBySession(location.pathname, router);
}

function toggleTheme() {
  theme.toggle();

}
function isNotAdmin() {
  return !location.pathname.startsWith('/admin');
}

function imageLoadError(e: Event) {
  if (((e.target || {}) as HTMLElement).tagName == 'IMG') {
    const img = e.target as HTMLImageElement;
    // 편집기 안에서는 손대지 않는다
    if (!img.onerror && !img.closest('.nabi')) {
      img.src = image_error;
      img.title = '이미지를 찾을 수 없습니다.';
    }
  }
  return true;
}

function doCloseHeaderMenu(event: Event) {
  if (onHeaderMenu.value == true) {
    const closet = (event.target as HTMLElement).closest(".pop-close,.pop-not-close");
    if (closet?.matches('.pop-close')) {
      onHeaderMenu.value = false;
    }
  }
}

document.addEventListener('error', imageLoadError, true);

onBeforeRouteUpdate((to, from, next) => {
    sessionService.amendPathBySession(to.fullPath, router, next);
});

onUnmounted(() => {
  document.removeEventListener('error', imageLoadError, true);
});

</script>

<style lang="postcss">
@reference "../../common/tailwind.pcss";

.as-header-bar {
  background: var(--as-surface);
  isolation: isolate;
  position: relative;
  border-radius: var(--radius-card);
  box-shadow: var(--as-shadow-2);
}

.nav-link {
  @apply items-center gap-2 px-3.5 py-2 text-sm font-medium transition-colors duration-200;
  border-radius: var(--radius-ctl);
  color: var(--as-ink-2);
  i { @apply text-[13px] opacity-70 }
  &:hover { color: var(--as-ink); background: var(--as-muted) }
  &.router-link-active {
    color: var(--as-brand);
    background: var(--as-brand-soft);
    i { @apply opacity-100 }
  }
}

.nav-icon {
  @apply inline-flex items-center justify-center w-9 h-9 text-[15px] transition-colors duration-200;
  border-radius: var(--radius-ctl);
  color: var(--as-ink-2);
  &:hover { color: var(--as-ink); background: var(--as-muted) }
  &.is-on { color: var(--as-brand) }
}

.as-menu-panel {
  background: var(--as-glass-strong);
  -webkit-backdrop-filter: blur(24px) saturate(170%);
  backdrop-filter: blur(24px) saturate(170%);
  position: relative;
  border-radius: var(--radius-card);
  box-shadow: var(--as-shadow-3);
}

.as-menu-item {
  @apply flex items-center gap-3 px-3 py-2.5 cursor-pointer transition-colors duration-150;
  border-radius: var(--radius-ctl);
  color: var(--as-ink-2);
  i { @apply w-4 text-center text-[13px] opacity-70 }
  &:hover { color: var(--as-ink); background: var(--as-muted) }
  &.is-danger:hover { color: var(--as-danger) }
}

.as-spinner {
  @apply inline-block w-10 h-10 rounded-full;
  border: 2px solid var(--as-line-2);
  border-top-color: var(--as-brand);
  animation: as-spin .8s linear infinite;
}

@keyframes as-spin {
  to { transform: rotate(360deg) }
}

.as-fade-enter-active, .as-fade-leave-active { transition: opacity .2s ease }
.as-fade-enter-from, .as-fade-leave-to { opacity: 0 }

.as-menu-enter-active { transition: opacity .18s ease }
.as-menu-leave-active { transition: opacity .15s ease }
.as-menu-enter-from, .as-menu-leave-to { opacity: 0 }
.as-menu-enter-active .as-menu-panel { transition: transform .22s var(--ease-out-quint), opacity .22s ease }
.as-menu-enter-from .as-menu-panel { transform: translateY(-8px) scale(.97); opacity: 0 }

.as-fa-spin {
  animation: fa-spin 4s infinite linear !important;
}
.layout-popup-zoom {
  @apply fixed top-0 left-0 right-0 bottom-0 z-[201]
  backdrop-blur-[4px]
  pt-32 text-7xl text-center;
  background: color-mix(in oklab, var(--as-canvas) 35%, transparent);
  color: var(--as-ink);
}
</style>
