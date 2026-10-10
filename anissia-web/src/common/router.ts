import {createRouter, createWebHistory} from 'vue-router'

import siteLayout from '../page/site/layout.vue'

const p301 = () => import('../page/301.vue')
const p404 = () => import('../page/404.vue')
const p500 = () => import('../page/500.vue')

const sc2026 = () => import('../page/schedule/2026.vue')
const sc2024 = () => import('../page/schedule/2024.vue')
const sc2015 = () => import('../page/schedule/2015.vue')
const sc2009 = () => import('../page/schedule/2009.vue')

const home = () => import('../page/site/home.vue')
const schedule = () => import('../page/site/schedule.vue')
const anime = () => import('../page/site/anime.vue')
const translatorApply = () => import('../page/site/translatorApply.vue')
const captionRecent = () => import('../page/site/captionRecent.vue')
const introduce = () => import('../page/site/introduce.vue')
const notice = () => import('../page/site/notice.vue')
const inquiry = () => import('../page/site/inquiry.vue')
const login = () => import('../page/site/login.vue')
const register = () => import('../page/site/register.vue')
const recover = () => import('../page/site/recover.vue')
const account = () => import('../page/site/account.vue')

const adminLayout = () => import('../page/admin/layout.vue')
const adminHome = () => import('../page/admin/home.vue')
const adminAnime = () => import('../page/admin/anime.vue')
const adminSchedule = () => import('../page/admin/schedule.vue')
const adminCaption = () => import('../page/admin/caption.vue')

const defaultTitle = import.meta.env.VITE_TITLE;
const trackingId = import.meta.env.VITE_GA_TRACKING_ID;
const env = import.meta.env.VITE_ENV;

(window as any)['dataLayer'] = (window as any)['dataLayer'] || [];
(window as any)['gtag'] = function() { (window as any)['dataLayer'].push(arguments); }

const router = createRouter({
  history: createWebHistory(),
  scrollBehavior(to, from, savedPosition) {
    return new Promise((resolve, reject) => {
      setTimeout(() => resolve({ left: 0, top: 0 }), 1);
    });
  },
  routes: [
    { path: '/schedule/2026', component: sc2026, meta: { title: '애니편성표 2026' } },
    { path: '/schedule/2024', component: sc2024, meta: { title: '애니편성표 2024' } },
    { path: '/schedule/2015', component: sc2015, meta: { title: '애니편성표 2015' } },
    { path: '/schedule/2009', component: sc2009, meta: { title: '애니편성표 2009' } },
    {
      path: '/', component: siteLayout,
      children: [
        { path: '/', component: home, meta: { title: '애니시아' } },
        { path: '/schedule', component: schedule, meta: { title: '애니편성표 - 애니시아' } },
        { path: '/anime', component: anime, meta: { title: '애니정보 - 애니시아' } },
        { path: '/caption/recent', component: captionRecent, meta: { title: '최근자막 - 애니시아' } },
        { path: '/translator/apply', component: translatorApply, meta: { title: '자막제작자 신청 - 애니시아' } },
        { path: '/introduce', component: introduce, meta: { title: '소개 - 애니시아' } },
        { path: '/notice', component: notice, meta: { title: '공지 - 애니시아' } },
        { path: '/inquiry', component: inquiry, meta: { title: '질문/답변 - 애니시아' } },
        { path: '/login', component: login, meta: { title: '로그인 - 애니시아' } },
        { path: '/register/:token', component: register, meta: { title: '회원가입 - 애니시아' } },
        { path: '/register', component: register, meta: { title: '회원가입 - 애니시아' } },
        { path: '/recover', component: recover, meta: { title: '계정복구 - 애니시아' } },
        { path: '/recover/:token', component: recover, meta: { title: '계정복구 - 애니시아' } },
        { path: '/account', component: account, meta: { title: '계정관리 - 애니시아' } },
        {
          path: '/admin', component: adminLayout,
          children: [
            { path: '/admin', component: adminHome, meta: { title: '관리자 - 애니시아' } },
            { path: '/admin/anime', component: adminAnime, meta: { title: '애니메이션관리 - 애니시아' } },
            { path: '/admin/schedule', component: adminSchedule, meta: { title: '애니편성표 관리 - 애니시아' } },
            { path: '/admin/caption', component: adminCaption, meta: { title: '자막 관리 - 애니시아' } },
          ]
        },
        { path: '/anitime/:path(.*)', component: p301, meta: { title: '주소이전 - 애니시아' } },
        { path: '/500', component: p500, meta: { title: '에러 예시 페이지 - 애니시아' } },
        { path: '/:path(.*)', component: p404, meta: { title: '404 - 애니시아' } },
      ]
    },
  ],
});

const warmed = new Set<string>();

/** 그 주소의 페이지 코드를 미리 받아 둔다 — 누른 뒤 코드를 받느라 멈춰 있지 않게. */
export function prefetchRoute(path: string) {
  if (warmed.has(path)) return;
  warmed.add(path);
  for (const record of router.resolve(path).matched) {
    const component = record.components?.default;
    if (typeof component == 'function') (component as () => Promise<unknown>)().catch(() => warmed.delete(path));
  }
}

/** 링크에 손이 가면(마우스 올림·터치 시작·초점) 그 페이지 코드를 미리 받는다. */
export function installPrefetch() {
  const warm = (e: Event) => {
    const a = (e.target as Element | null)?.closest?.('a[href]') as HTMLAnchorElement | null;
    if (a && a.origin == location.origin && a.target != '_blank') prefetchRoute(a.pathname);
  };
  for (const type of ['pointerover', 'touchstart', 'focusin']) {
    addEventListener(type, warm, {passive: true, capture: true});
  }
}

router.afterEach((to, from) => {
  // @ts-ignore
  document.title = to.meta.title || defaultTitle;
  const path = to.fullPath.indexOf('#') == -1 ? to.fullPath : to.fullPath.substring(0, to.fullPath.indexOf('#'));

  const gtag = (window as any).gtag;
  gtag('set', 'title', document.title);
  gtag('js', new Date());
  gtag('config', trackingId, {'page_path': path});
});

export default router;