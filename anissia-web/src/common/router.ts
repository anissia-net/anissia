import {createRouter, createWebHistory} from 'vue-router'
import {nextTick} from 'vue'

import {whenIdle} from './idle'
import {legacyWikiHistoryLocation} from '../domain/wiki/wikiLocation'
import {legacyDetailLocation} from './detailLocation'

const siteLayout = () => import('../page/site/layout.vue')

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
const animeDetail = () => import('../page/site/animeDetail.vue')
const boardTopic = () => import('../page/site/boardTopic.vue')
const notifications = () => import('../page/site/notifications.vue')
const wikiImages = () => import('../page/site/wikiImages.vue')
const wikiTemplates = () => import('../page/site/wikiTemplates.vue')
const wikiRecent = () => import('../page/site/wikiRecent.vue')
const wikiHistory = () => import('../page/site/wikiHistory.vue')
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
const adminAnimeDetail = () => import('../page/admin/animeDetail.vue')
const adminSchedule = () => import('../page/admin/schedule.vue')
const adminCaption = () => import('../page/admin/caption.vue')

const defaultTitle = import.meta.env.VITE_TITLE;
const trackingId = import.meta.env.VITE_GA_TRACKING_ID;

(window as any)['dataLayer'] = (window as any)['dataLayer'] || [];
(window as any)['gtag'] = function() { (window as any)['dataLayer'].push(arguments); }

const router = createRouter({
  history: createWebHistory(),
  async scrollBehavior(to, from, savedPosition) {
    await nextTick();
    // Cached masonry lists must be reinserted and measured before restoring scroll.
    await new Promise<void>(resolve => requestAnimationFrame(() => requestAnimationFrame(() => resolve())));
    return savedPosition ?? {left: 0, top: 0};
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
        { path: '/anime/:animeNo([1-9]\\d*)', component: animeDetail, meta: { title: '애니정보 - 애니시아' } },
        { path: '/wiki/images', component: wikiImages, meta: { title: '위키 이미지 - 애니시아' } },
        { path: '/wiki/templates', component: wikiTemplates, meta: { title: '위키 템플릿 - 애니시아' } },
        { path: '/wiki/recent', component: wikiRecent, meta: { title: '위키 최근 변경 - 애니시아' } },
        { path: '/wiki/history', component: wikiHistory, meta: { title: '변경 이력 - 애니시아' } },
        { path: '/notifications', component: notifications, meta: { title: '알림 - 애니시아' } },
        { path: '/favorites', redirect: to => ({path: '/notifications', query: to.query, hash: to.hash}) },
        { path: '/caption/recent', component: captionRecent, meta: { title: '최근자막 - 애니시아' } },
        { path: '/translator/apply', component: translatorApply, meta: { title: '자막제작자 신청 - 애니시아' } },
        { path: '/introduce', component: introduce, meta: { title: '소개 - 애니시아' } },
        { path: '/notice', component: notice, meta: { title: '공지 - 애니시아' } },
        { path: '/notice/new', component: boardTopic, props: {ticker: 'notice'}, meta: { title: '공지 작성 - 애니시아' } },
        { path: '/notice/:topicNo([1-9]\\d*)', component: boardTopic, props: {ticker: 'notice'}, meta: { title: '공지 - 애니시아' } },
        { path: '/inquiry', component: inquiry, meta: { title: '질문/답변 - 애니시아' } },
        { path: '/inquiry/new', component: boardTopic, props: {ticker: 'inquiry'}, meta: { title: '질문 작성 - 애니시아' } },
        { path: '/inquiry/:topicNo([1-9]\\d*)', component: boardTopic, props: {ticker: 'inquiry'}, meta: { title: '질문/답변 - 애니시아' } },
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
            { path: '/admin/anime/new', component: adminAnimeDetail, meta: { title: '애니메이션등록 - 애니시아' } },
            { path: '/admin/anime/:animeNo([1-9]\\d*)', component: adminAnimeDetail, meta: { title: '애니메이션편집 - 애니시아' } },
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

router.beforeEach(to => {
  if (to.path === '/anime' && (to.query.type === 'templates' || to.query.type === 'images')) {
    const {type, animeNo, ...query} = to.query;
    return {path: `/wiki/${type}`, query, hash: to.hash, replace: true};
  }
  const historyLocation = legacyWikiHistoryLocation(to.path, to.query);
  if (historyLocation) return {path: '/wiki/history', query: router.resolve(historyLocation).query, replace: true};
  const detailLocation = legacyDetailLocation(to.path, to.query);
  if (detailLocation) return {...detailLocation, query: detailLocation.query as typeof to.query, hash: to.hash, replace: true};
});

const warmed = new Set<string>();

export function prefetchRoute(path: string) {
  const connection = (navigator as Navigator & {connection?: {saveData?: boolean; effectiveType?: string}}).connection;
  if (connection?.saveData || /(^|-)2g$/.test(connection?.effectiveType ?? '')) return;
  if (warmed.has(path)) return;
  warmed.add(path);
  for (const record of router.resolve(path).matched) {
    const component = record.components?.default;
    if (typeof component == 'function') (component as () => Promise<unknown>)().catch(() => warmed.delete(path));
  }
}

export function installPrefetch() {
  const warm = (e: Event) => {
    const a = (e.target as Element | null)?.closest?.('a[href]') as HTMLAnchorElement | null;
    if (a && a.origin == location.origin && a.target != '_blank' && !a.hasAttribute('download')) prefetchRoute(a.pathname);
  };
  for (const type of ['pointerover', 'touchstart', 'focusin']) {
    addEventListener(type, warm, {passive: true, capture: true});
  }
}

export function installNoteNavigation() {
  document.addEventListener('click', (event: MouseEvent) => {
    if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
    const link = (event.target as Element | null)?.closest?.<HTMLAnchorElement>('a[href]');
    const content = link?.closest<HTMLElement>('.nabi-content');
    if (!content || content.isContentEditable || link.isContentEditable || link.hasAttribute('download')) return;
    if (link.origin !== location.origin || (link.target && link.target.toLowerCase() !== '_self')) return;
    // 같은 문서의 앵커는 브라우저가 해당 위치로 스크롤하게 둔다.
    if (link.hash && link.pathname === location.pathname && link.search === location.search) return;
    event.preventDefault();
    void router.push(link.pathname + link.search + link.hash);
  });
}

export function installAnalytics() {
  if (!trackingId) return;
  const load = () => whenIdle(() => {
    const script = document.createElement('script');
    script.async = true;
    script.src = `https://www.googletagmanager.com/gtag/js?id=${encodeURIComponent(trackingId)}`;
    document.head.append(script);
  });
  if (document.readyState === 'complete') load();
  else addEventListener('load', load, {once: true});
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
