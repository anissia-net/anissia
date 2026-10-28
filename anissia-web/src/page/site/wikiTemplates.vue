<template>
  <div class="as-page">
    <h1 class="as-page-title mb-6">위키 템플릿</h1>
    <WikiNavigation :query="query" class="mb-6"/>
    <template v-if="selected || creating">
      <router-link :to="{path: '/wiki/templates', query: query ? {q: query} : {}}" class="as-link text-sm">템플릿 목록</router-link>
      <WikiSection :key="creating ? 'new' : selected" :template-no="selected || undefined" :new-template="creating" :edit-on-open="route.query.edit === '1'" @created="created" @deleted="deleted"/>
    </template>
    <WikiTemplateList v-else/>
  </div>
</template>

<script setup lang="ts">
import {computed} from 'vue';
import {useRoute, useRouter} from 'vue-router';
import WikiSection from '../../domain/wiki/WikiSection.vue';
import WikiTemplateList from '../../domain/wiki/WikiTemplateList.vue';
import WikiNavigation from '../../domain/wiki/WikiNavigation.vue';
import {type WikiDocument} from '../../domain/wiki/wikiRemote';
const route = useRoute(), router = useRouter();
const selected = computed(() => { const value = Number(route.query.templateNo); return Number.isSafeInteger(value) && value > 0 ? value : 0; });
const creating = computed(() => route.query.new === '1');
const query = computed(() => typeof route.query.q === 'string' ? route.query.q : '');
function created(item: WikiDocument) { void router.replace({path: '/wiki/templates', query: {templateNo: item.templateNo}}); }
function deleted() { void router.replace({path: '/wiki/templates', query: query.value ? {q: query.value} : {}}); }
</script>
