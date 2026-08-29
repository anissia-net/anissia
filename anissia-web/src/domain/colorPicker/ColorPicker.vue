<template>
  <ColorPicker alpha-channel="hide" :visible-formats="['hex']" :color="color" @color-change="updateColor" />
</template>

<script setup lang="ts">
import {ColorPicker} from 'vue-accessible-color-picker'

const props = defineProps<{ color: string, callback: Function }>()

function updateColor(e: any) {
  const hex = e.cssColor.substring(1);
  if (hex.match(/^[\da-f]{6}$/)) {
    props.callback(hex);
  } else if (hex.match(/^[\da-f]{3}$/)) {
    props.callback(hex.replace(/(.)/g, '$1$1'));
  }
}
</script>

<style>
@import url('vue-accessible-color-picker/styles');
.vacp-color-picker {
  border-radius: var(--radius-ctl); padding:8px;
  background: var(--as-glass-strong) !important;
  border: 1px solid var(--as-glass-line) !important;
  input[type=text] { background: var(--as-surface-2) !important; border:1px solid var(--as-line-2) !important; color: var(--as-ink) !important; }

  button.vacp-copy-button,
  .vacp-range-input-label-text--hue,
  .vacp-color-input-label-text { display: none !important; }
  .vacp-range-input-group { grid-column: 1/-1 !important; }
}
</style>
