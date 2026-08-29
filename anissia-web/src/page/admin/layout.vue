<template>

  <div class="as-container pt-6">
    <div class="grid grid-cols-4 gap-3 md:gap-4">
      <router-link to="/admin" class="admin-tab as-card" :class="{'is-on': url('/admin', true)}">
        <img src="./layout/tab-panel.svg"/>
        <h5>관리패널</h5>
      </router-link>
      <router-link to="/admin/anime" class="admin-tab as-card" :class="{'is-on': url('/admin/anime')}">
        <img src="./layout/tab-anime.svg"/>
        <h5>애니메이션</h5>
      </router-link>
      <router-link to="/admin/schedule" class="admin-tab as-card" :class="{'is-on': url('/admin/schedule')}">
        <img src="./layout/tab-schedule.svg"/>
        <h5>편성표</h5>
      </router-link>
      <router-link to="/admin/caption" class="admin-tab as-card" :class="{'is-on': url('/admin/caption')}">
        <img src="./layout/tab-caption.svg"/>
        <h5>자막</h5>
      </router-link>
    </div>
  </div>
  <div>
    <router-view />
  </div>

</template>

<script setup lang="ts">
import anissia from "../../common/anissia";

const url = anissia.url
</script>

<style>
@reference "../../common/tailwind.pcss";

.admin-tab {
  @apply block py-5 px-3 text-center;
  img {
    @apply w-11 md:w-12 inline-block transition-all duration-200;
    filter: grayscale(100%);
    opacity: .45;
  }
  h5 {
    @apply mt-3.5 hidden md:block text-sm font-semibold transition-colors duration-200;
    color: var(--as-ink-3);
  }
  &:hover {
    img { opacity: .8; filter: grayscale(60%) }
    h5 { color: var(--as-ink-2) }
  }
  &.is-on {
    border-color: color-mix(in oklab, var(--as-brand) 40%, transparent);
    background: var(--as-brand-soft);
    img { opacity: 1; filter: none }
    h5 { color: var(--as-brand) }
    &:hover { transform: none }
  }
}
</style>