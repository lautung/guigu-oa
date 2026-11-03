<template>
  <button
    v-if="supported"
    type="button"
    class="fullscreen-btn"
    :title="active ? '退出全屏' : '全屏'"
    :aria-label="active ? '退出全屏' : '全屏'"
    @click="toggle"
  >
    <el-icon><component :is="shellIcon(active ? 'ExitFullScreen' : 'FullScreen')" /></el-icon>
  </button>
</template>

<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'
import { fullscreenSupported, toggleFullscreen, type FullscreenDocument } from './fullscreen'
import { shellIcon } from './shell-icons'

const doc = typeof document === 'undefined' ? null : (document as unknown as FullscreenDocument)
const supported = fullscreenSupported(doc)
const active = ref(!!doc?.fullscreenElement)

function sync(): void {
  active.value = !!doc?.fullscreenElement
}

function toggle(): void {
  void toggleFullscreen(doc)
    .then((next) => {
      active.value = next
    })
    .catch(() => undefined)
}

if (typeof document !== 'undefined') {
  document.addEventListener('fullscreenchange', sync)
  onBeforeUnmount(() => document.removeEventListener('fullscreenchange', sync))
}
</script>

<style lang="scss" scoped>
.fullscreen-btn {
  width: 32px;
  height: 32px;
  padding: 0;
  display: grid;
  place-items: center;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--el-text-color-regular);
  cursor: pointer;
}

.fullscreen-btn:hover,
.fullscreen-btn:focus-visible {
  background: var(--el-fill-color-light);
  color: var(--el-text-color-primary);
  outline: none;
}
</style>
