<template>
  <nav class="wiki-navigation" aria-label="검색 유형 및 위키 메뉴">
    <router-link :to="searchLocation('anime')" class="wiki-navigation-link" :class="{'is-active': active === 'anime'}"
                 :aria-current="active === 'anime' ? 'page' : undefined"><as-icon name="circle-play"/>애니</router-link>
    <div class="wiki-tools">
    <router-link v-for="item in categories" :key="item.type" :to="searchLocation(item.type)"
                 class="wiki-navigation-link" :class="{'is-active': active === item.type}"
                 :aria-current="active === item.type ? 'page' : undefined">
      <as-icon :name="item.icon"/>{{item.label}}
    </router-link>
    <router-link to="/wiki/recent" class="wiki-navigation-link" :class="{'is-active': active === 'recent'}"
                 :aria-current="active === 'recent' ? 'page' : undefined"><as-icon name="clock"/>최근 변경</router-link>
    </div>
  </nav>
</template>

<script setup lang="ts">
import {computed} from 'vue';
import {useRoute} from 'vue-router';

const props = withDefaults(defineProps<{query?: string}>(), {query: ''});
const route = useRoute();
const categories = [
  {type: 'templates', label: '템플릿', icon: 'template'},
  {type: 'images', label: '이미지', icon: 'image'},
] as const;
const active = computed(() => {
  if (route.path === '/wiki/recent') return 'recent';
  if (route.path === '/wiki/templates') return 'templates';
  if (route.path === '/wiki/images') return 'images';
  return 'anime';
});
function searchLocation(type: string) {
  return {path: type === 'anime' ? '/anime' : `/wiki/${type}`, query: props.query ? {q: props.query} : {}};
}
</script>

<style scoped>
.wiki-navigation {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: .25rem;
}
.wiki-navigation-link {
  display: inline-flex;
  align-items: center;
  gap: .375rem;
  padding: .5rem .625rem;
  border-radius: var(--radius-ctl);
  color: var(--as-ink-3);
  font-size: .8125rem;
  line-height: 1.25rem;
  font-weight: 600;
  white-space: nowrap;
  .as-icon { font-size: .75rem; }
  &:hover { color: var(--as-brand); background: var(--as-muted); }
  &.is-active { color: var(--as-brand); background: var(--as-brand-soft); }
}
.wiki-tools {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: .25rem;
  margin-left: auto;
}
</style>
