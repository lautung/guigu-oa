<template>
  <div class="shell-tabs">
    <div class="tabs-scroll">
      <button
        v-for="tab in tabs"
        :key="tab.path"
        type="button"
        class="shell-tab"
        :class="{ active: tab.path === activePath }"
        @click="$emit('activate', tab.path)"
        @mousedown.middle.prevent="$emit('close', tab.path)"
        @auxclick.middle.prevent="$emit('close', tab.path)"
      >
        <el-icon v-if="isFixedTab(tab.path)" class="tab-pin">
          <component :is="shellIcon('HomeFilled')" />
        </el-icon>
        <span class="tab-title" :title="displayTitle(tab)">{{ displayTitle(tab) }}</span>
        <el-icon
          v-if="!isFixedTab(tab.path)"
          class="tab-close"
          :title="t('shell.tabs.close')"
          @click.stop="$emit('close', tab.path)"
        >
          <component :is="shellIcon('Close')" />
        </el-icon>
      </button>
    </div>

    <el-dropdown trigger="click" @command="onCommand">
      <button
        type="button"
        class="tab-more"
        :title="t('shell.tabs.actions')"
        :aria-label="t('shell.tabs.actions')"
      >
        <el-icon><component :is="shellIcon('ArrowDown')" /></el-icon>
      </button>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item command="current">{{ t('shell.tabs.closeCurrent') }}</el-dropdown-item>
          <el-dropdown-item command="others">{{ t('shell.tabs.closeOthers') }}</el-dropdown-item>
          <el-dropdown-item command="all">{{ t('shell.tabs.closeAll') }}</el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
  </div>
</template>

<script setup lang="ts">
import { translate as t } from '@/i18n'
import { isFixedTab, type ShellTab } from './shell-tabs'
import { shellIcon } from './shell-icons'

defineProps<{
  tabs: ShellTab[]
  activePath: string
}>()

const emit = defineEmits<{
  (event: 'activate', path: string): void
  (event: 'close', path: string): void
  (event: 'command', command: string): void
}>()

function onCommand(command: string): void {
  emit('command', command)
}

/** 工作台标签是外壳固定项，标题跟随 locale；业务页标签保留路由标题。 */
function displayTitle(tab: ShellTab): string {
  return isFixedTab(tab.path) ? t('shell.modules.workbench') : tab.title
}

defineExpose({ onCommand })
</script>

<style lang="scss" scoped>
.shell-tabs {
  height: 38px;
  flex-shrink: 0;
  display: flex;
  align-items: stretch;
  gap: 6px;
  padding: 5px 8px 0;
  background: var(--el-bg-color);
  border-bottom: 1px solid var(--el-border-color-light);
}

.tabs-scroll {
  flex: 1;
  min-width: 0;
  display: flex;
  gap: 3px;
  overflow-x: auto;
  scrollbar-width: none;
}

.tabs-scroll::-webkit-scrollbar {
  display: none;
}

.shell-tab {
  display: flex;
  align-items: center;
  gap: 5px;
  height: 32px;
  max-width: 170px;
  padding: 0 9px;
  border: 1px solid var(--el-border-color-light);
  border-bottom: 0;
  border-radius: 6px 6px 0 0;
  background: var(--el-fill-color-light);
  color: var(--el-text-color-regular);
  font-size: 12px;
  white-space: nowrap;
  cursor: pointer;
}

.shell-tab:hover {
  color: var(--el-text-color-primary);
}

.shell-tab.active {
  background: var(--el-bg-color);
  color: var(--el-color-primary);
  box-shadow: inset 0 2px 0 var(--el-color-primary);
}

.tab-title {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tab-pin {
  flex-shrink: 0;
  color: var(--el-color-primary);
  font-size: 12px;
}

.tab-close {
  flex-shrink: 0;
  padding: 1px;
  border-radius: 50%;
  font-size: 11px;
}

.tab-close:hover {
  background: var(--el-fill-color-dark);
}

.tab-more {
  width: 28px;
  flex-shrink: 0;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--el-text-color-secondary);
  cursor: pointer;
}

.tab-more:hover,
.tab-more:focus-visible {
  background: var(--el-fill-color-light);
  outline: none;
}
</style>
