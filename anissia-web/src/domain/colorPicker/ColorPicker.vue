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
  --vacp-width-color-space: 18.75rem;
  --vacp-spacing: 0.375rem;
  --vacp-font-size: 1rem;
  --vacp-width-border: 0.0625rem;
  border-radius: var(--radius-ctl); padding:0.5rem;
  background: var(--as-glass-strong) !important;
  border: 0.0625rem solid var(--as-glass-line) !important;
  input[type=text] { background: var(--as-surface-2) !important; border:0.0625rem solid var(--as-line-2) !important; color: var(--as-ink) !important; }

  button.vacp-copy-button,
  .vacp-range-input-label-text--hue,
  .vacp-color-input-label-text { display: none !important; }
  .vacp-range-input-group { grid-column: 1/-1 !important; }
}
</style>
