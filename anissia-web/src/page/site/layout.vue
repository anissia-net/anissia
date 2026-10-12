<template>

  <div class="as-app min-h-screen flex flex-col">

    <header class="relative z-30 w-full mt-2.5">
      <div class="as-container">
        <div class="as-header-bar flex justify-between items-center h-[3.25rem] pl-4 pr-1.5">

        <router-link to="/" class="group flex items-center py-1.5 rounded-ctl">
          <span class="as-wordmark ml-[0.1rem] text-[0.9375rem] font-black">ANISSIA</span>
        </router-link>

        <nav class="flex items-center gap-0.5">
          <router-link to="/schedule" class="nav-link hidden cq-md:inline-flex">
            <as-icon name="calendar"/><span>편성표</span>
          </router-link>
          <router-link to="/anime" class="nav-link hidden cq-md:inline-flex">
            <as-icon name="circle-play"/><span>애니</span>
          </router-link>
          <router-link to="/inquiry" class="nav-link hidden cq-md:inline-flex">
            <as-icon name="chat-dots"/><span>질문답변</span>
          </router-link>

          <span class="hidden cq-md:inline-block w-[0.0625rem] h-5 mx-2 bg-line"></span>

          <button v-if="gyroAsk" class="nav-icon" @click="allowGyro()" title="기울기 효과 켜기" aria-label="기울기 효과 켜기">
            <as-icon name="mobile"/>
          </button>
          <router-link v-if="user.isAdmin && !url('/admin')" to="/admin" class="nav-icon" title="관리자">
            <as-icon name="sliders"/>
          </router-link>
          <button class="nav-icon" @click="toggleTheme()" title="테마 변경" aria-label="테마 변경">
            <span class="dark:hidden"><as-icon name="sun"/></span>
            <span class="hidden dark:inline"><as-icon name="moon"/></span>
          </button>
          <button class="nav-icon" :class="{'is-on': user.isLogin}" @click="onHeaderMenu = true" title="메뉴" aria-label="메뉴">
            <as-icon v-if="user.isLogin" name="circle-user"/>
            <as-icon v-else name="bars"/>
          </button>
        </nav>

        </div>
      </div>
    </header>

    <transition name="as-menu">
      <div v-if="onHeaderMenu" class="pop-close z-40 fixed inset-0 bg-black/25 backdrop-blur-[0.125rem]" @click="doCloseHeaderMenu">
        <div class="as-container flex justify-end">
          <div class="pop-not-close as-menu-panel mt-[4.375rem] w-[15.5rem]">
            <div class="as-menu-scroll p-2">

              <ul class="text-sm font-medium">
                <li>
                  <div class="as-menu-zoom" role="group" aria-label="화면 배율">
                    <button type="button" class="as-menu-zoom-out" aria-label="화면 축소" title="화면 축소" :disabled="zoom === ZOOMS[0]" @click="changeZoom(-1)"><as-icon name="zoom-out"/></button>
                    <button type="button" class="as-menu-zoom-value" aria-label="화면 배율 100%로 초기화" title="100%로 초기화" @click="changeZoom(0)">{{ zoom }}%</button>
                    <button type="button" class="as-menu-zoom-in" aria-label="화면 확대" title="화면 확대" :disabled="zoom === ZOOMS[ZOOMS.length - 1]" @click="changeZoom(1)"><as-icon name="zoom-in"/></button>
                  </div>
                </li>
                <li v-if="!user.isLogin"><router-link to="/login" class="pop-close as-menu-item"><as-icon name="login"/> 로그인</router-link></li>
                <li v-if="user.isLogin"><router-link to="/account" class="pop-close as-menu-item"><as-icon name="address-card"/> 회원정보</router-link></li>
                <li><router-link to="/schedule" class="pop-close as-menu-item"><as-icon name="calendar"/> 애니 편성표</router-link></li>
                <li><router-link to="/anime" class="pop-close as-menu-item"><as-icon name="circle-play"/> 애니정보</router-link></li>
                <li><router-link to="/caption/recent" class="pop-close as-menu-item"><as-icon name="cc"/> 최근 자막</router-link></li>
                <li><router-link to="/translator/apply" class="pop-close as-menu-item"><as-icon name="signature"/> 자막제작자 신청</router-link></li>
                <li><router-link to="/introduce" class="pop-close as-menu-item"><as-icon name="star"/> 애니시아 소개</router-link></li>
                <li><router-link to="/notice" class="pop-close as-menu-item"><as-icon name="bell"/> 공지사항</router-link></li>
                <li><router-link to="/inquiry" class="pop-close as-menu-item"><as-icon name="chat-dots"/> 질문답변</router-link></li>
                <li v-if="user.isLogin" class="mt-1 pt-1 border-t-[0.0625rem] border-line">
                  <span @click="logout" class="pop-close as-menu-item is-danger"><as-icon name="logout"/> 로그아웃</span>
                </li>
              </ul>

            </div>
          </div>
        </div>
      </div>
    </transition>

    <main class="flex-1 pb-16">
      <router-view/>
    </main>

    <transition name="as-fade">
      <div v-if="ajaxState.state == 'loading'" class="fixed inset-0 z-50 flex items-center justify-center backdrop-blur-[0.1875rem] bg-canvas/40">
        <div class="text-center">
          <span class="as-spinner"></span>
          <div class="mt-6 text-sm tracking-[.25em] uppercase text-ink-3">loading</div>
        </div>
      </div>
      <div v-else-if="ajaxState.state == 'error'" class="fixed inset-0 z-50 flex items-center justify-center backdrop-blur-[0.1875rem] bg-canvas/50 px-6">
        <div class="as-box as-frost max-w-[27.5rem] w-full p-8 text-center">
          <div class="mx-auto w-14 h-14 rounded-full grid place-items-center bg-brand-soft text-brand text-2xl">
            <as-icon name="wrench"/>
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
import {allowGyro, gyroAsk} from "../../common/edgeLight";
import {useDisplayZoom, ZOOMS} from "../../common/display";


const router = useRouter();
const route = useRoute();
const session = sessionStore();

const ajaxState = ajaxStateStore();
const {zoom, changeZoom} = useDisplayZoom();

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
  @apply items-center gap-2 px-3.5 py-2 text-sm font-medium;
  border-radius: var(--radius-ctl);
  color: var(--as-ink-2);
  i { @apply text-[0.8125rem] opacity-70 }
  &.router-link-active {
    color: var(--as-brand);
    background: var(--as-brand-soft);
    i { @apply opacity-100 }
  }
}

.nav-icon {
  @apply inline-flex items-center justify-center w-9 h-9 text-[0.9375rem];
  border-radius: var(--radius-ctl);
  color: var(--as-ink-2);
  &.is-on { color: var(--as-brand) }
}

.as-menu-panel {
  max-width: 100%;
  max-height: calc(100dvh - 5rem);
  overflow: hidden;
  background: var(--as-glass-strong);
  -webkit-backdrop-filter: blur(1.5rem) saturate(170%);
  backdrop-filter: blur(1.5rem) saturate(170%);
  position: relative;
  border-radius: var(--radius-card);
  box-shadow: var(--as-shadow-3);
}

/* 테두리는 스크롤하지 않는 프레임에 두고, 내용과 스크롤바만 안쪽에서 움직인다. */
.as-menu-scroll {
  container: header-menu / inline-size;
  max-height: inherit;
  overflow-y: auto;
  overscroll-behavior: contain;
  border-radius: inherit;
}

.as-menu-item {
  @apply flex items-center gap-3 px-3 py-2.5 cursor-pointer;
  border-radius: var(--radius-ctl);
  color: var(--as-ink-2);
  i { @apply w-4 text-center text-[0.8125rem] opacity-70 }
  &.is-danger { --as-menu-hover: var(--as-danger) }
}

.as-menu-zoom {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  width: 100%;
  margin-bottom: .25rem;
  align-items: center;
  gap: .125rem;
  padding: .125rem .75rem .375rem;
  border-bottom: .0625rem solid var(--as-line);
  color: var(--as-ink-2);

  button {
    display: grid;
    align-items: center;
    min-width: 0;
    min-height: 2.5rem;
    padding-inline: 0;
    border-radius: var(--radius-ctl);
    &:focus-visible { outline: .125rem solid var(--as-brand); outline-offset: -.125rem }
    &:disabled { opacity: .35; cursor: not-allowed }
  }

  .as-icon { width: 1rem; font-size: 1rem }
}

.as-menu-zoom-out { justify-items: start; cursor: zoom-out }
.as-menu-zoom-in { justify-items: end; cursor: zoom-in }
.as-menu-zoom-value { justify-items: center; font-variant-numeric: tabular-nums; cursor: pointer }

.nav-link, .nav-icon, .as-menu-item, .as-menu-zoom button {
  transition: color .22s ease;

  .as-icon {
    transition: opacity .22s ease, transform .22s var(--ease-out-quint);
  }

  &:is(:hover, :focus-visible):not(:disabled) {
    color: var(--as-menu-hover, var(--as-brand-2));
    .as-icon {
      opacity: 1;
      transform: translateY(-.0625rem) scale(1.08);
    }
  }

  @media (prefers-reduced-motion: reduce) {
    transition: none;
    .as-icon { transition: none }
    &:is(:hover, :focus-visible):not(:disabled) .as-icon { transform: none }
  }
}

@container header-menu (width < 12rem) {
  .as-menu-zoom { grid-template-columns: repeat(2, minmax(0, 1fr)) }
  .as-menu-zoom-value { grid-column: 1 / -1; grid-row: 1 }
}

.as-spinner {
  @apply inline-block w-10 h-10 rounded-full;
  border: 0.125rem solid var(--as-line-2);
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
.as-menu-enter-from .as-menu-panel { transform: translateY(-0.5rem) scale(.97); opacity: 0 }

</style>
