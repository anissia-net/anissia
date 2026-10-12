<template>
  <i class="as-icon" :class="{'as-icon-spin': spin}" v-html="iconSvg(name)"></i>
</template>

<script setup lang="ts">
import {iconSvg, type IconName} from './icons';

defineProps<{name: IconName, spin?: boolean}>();
</script>
