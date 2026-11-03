<template>
  <header class="top-bar">
    <div class="top-bar-left">
      <button
        v-if="narrow"
        type="button"
        class="shell-icon-btn menu-toggle"
        title="菜单"
        aria-label="菜单"
        @click="$emit('toggle-menu')"
      >
        <el-icon><component :is="shellIcon('Menu')" /></el-icon>
      </button>
      <div class="brand">
        <span class="brand-logo">云</span>
        <span class="brand-text">云尚办公</span>
      </div>
      <nav class="top-nav" aria-label="一级模块">
        <button
          v-for="module in modules"
          :key="module.key"
          type="button"
          class="top-nav-item"
          :class="{ active: module.key === activeModule, disabled: !module.firstPath }"
          :disabled="!module.firstPath"
          :aria-disabled="!module.firstPath"
          @click="$emit('switch-module', module.key)"
        >
          <el-icon><component :is="shellIcon(module.icon)" /></el-icon>
          <span>{{ module.title }}</span>
        </button>
      </nav>
    </div>

    <div class="top-tools">
      <el-tag v-if="desktopHint" class="desktop-hint" size="small" type="warning">
        建议桌面
      </el-tag>
      <fullscreen-button />
      <menu-search />
      <notice-bell />
      <user-menu />
    </div>
  </header>
</template>

<script setup lang="ts">
import NoticeBell from './NoticeBell.vue'
import FullscreenButton from './FullscreenButton.vue'
import MenuSearch from './MenuSearch.vue'
import UserMenu from './UserMenu.vue'
import { shellIcon } from './shell-icons'
import type { ShellModule, ShellModuleKey } from './shell-nav'

defineProps<{
  modules: ShellModule[]
  activeModule: ShellModuleKey | null
  narrow: boolean
  desktopHint: boolean
}>()

defineEmits<{
  (event: 'switch-module', key: ShellModuleKey): void
  (event: 'toggle-menu'): void
}>()
</script>

<style lang="scss" scoped>
.top-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  height: 52px;
  flex-shrink: 0;
  padding: 0 14px;
  background: var(--el-bg-color);
  border-bottom: 1px solid var(--el-border-color-light);
}

.top-bar-left {
  min-width: 0;
  flex: 1;
  display: flex;
  align-items: center;
  gap: 12px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 196px;
  flex-shrink: 0;
}

.brand-logo {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  display: grid;
  place-items: center;
  background: var(--el-color-primary);
  color: var(--el-color-white);
  font-weight: 700;
  font-size: 14px;
}

.brand-text {
  font-size: 16px;
  font-weight: 700;
  letter-spacing: 0.02em;
  white-space: nowrap;
}

.top-nav {
  flex: 1;
  min-width: 0;
  display: flex;
  gap: 2px;
  overflow-x: auto;
  scrollbar-width: none;
}

.top-nav::-webkit-scrollbar {
  display: none;
}

.top-nav-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 7px 13px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--el-text-color-regular);
  font-size: 14px;
  white-space: nowrap;
  cursor: pointer;
}

.top-nav-item:hover {
  background: var(--el-fill-color-light);
}

.top-nav-item.active {
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  font-weight: 600;
}

.top-nav-item.disabled {
  opacity: 0.45;
  cursor: default;
}

.top-nav-item.disabled:hover {
  background: transparent;
}

.top-tools {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}

.shell-icon-btn {
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

.shell-icon-btn:hover,
.shell-icon-btn:focus-visible {
  background: var(--el-fill-color-light);
  color: var(--el-text-color-primary);
  outline: none;
}

@media (max-width: 1180px) {
  .brand {
    width: auto;
  }

  .brand-text {
    display: none;
  }
}

@media (max-width: 899px) {
  .top-bar {
    gap: 8px;
  }
}
</style>
