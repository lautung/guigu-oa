<template>
  <aside
    class="app-sidebar"
    :class="{ collapsed: collapsed && !narrow, open: narrow && drawerOpen }"
  >
    <div class="sidebar-scroll">
      <div v-if="module" class="side-label">{{ module.title }}</div>

      <router-link
        v-for="item in module?.items || []"
        :key="item.path"
        :to="item.path"
        class="side-item"
        :class="{ active: isActive(item.path) }"
        :title="item.title"
        @click="$emit('navigate')"
      >
        <el-icon><component :is="shellIcon(item.icon)" /></el-icon>
        <span>{{ item.title }}</span>
      </router-link>

      <div v-for="group in module?.groups || []" :key="group.title" class="side-group">
        <router-link
          v-if="collapsed && !narrow && group.items[0]"
          :to="group.items[0].path"
          class="side-group-title is-link"
          :class="{ active: groupActive(group) }"
          :title="group.title"
          @click="$emit('navigate')"
        >
          <el-icon><component :is="shellIcon(group.icon)" /></el-icon>
          <span>{{ group.title }}</span>
        </router-link>
        <div v-else class="side-group-title">
          <el-icon><component :is="shellIcon(group.icon)" /></el-icon>
          <span>{{ group.title }}</span>
        </div>

        <router-link
          v-for="leaf in group.items"
          :key="leaf.path"
          :to="leaf.path"
          class="side-item is-child"
          :class="{ active: isActive(leaf.path) }"
          :title="leaf.title"
          @click="$emit('navigate')"
        >
          <span>{{ leaf.title }}</span>
        </router-link>
      </div>

      <p v-if="module?.emptyHint" class="side-empty">{{ module.emptyHint }}</p>
    </div>

    <button
      v-if="!narrow"
      type="button"
      class="collapse-btn"
      :title="collapsed ? t('shell.expandMenu') : t('shell.collapseMenu')"
      :aria-label="collapsed ? t('shell.expandMenu') : t('shell.collapseMenu')"
      @click="$emit('toggle-collapse')"
    >
      <el-icon><component :is="shellIcon(collapsed ? 'Expand' : 'Fold')" /></el-icon>
    </button>
  </aside>
</template>

<script setup lang="ts">
import { translate as t } from '@/i18n'
import { isShellLeafActive, type ShellModule, type ShellNavGroup } from './shell-nav'
import { shellIcon } from './shell-icons'

const props = defineProps<{
  module: ShellModule | null
  activePath: string
  collapsed: boolean
  narrow: boolean
  drawerOpen: boolean
}>()

defineEmits<{
  (event: 'toggle-collapse'): void
  (event: 'navigate'): void
}>()

function isActive(path: string): boolean {
  return isShellLeafActive(props.activePath, path)
}

function groupActive(group: ShellNavGroup): boolean {
  return group.items.some((leaf) => isActive(leaf.path))
}
</script>

<style lang="scss" scoped>
/* ADR 0059 的深色侧栏例外：颜色只在这里定义，主题切换不改变侧栏。 */
.app-sidebar {
  --shell-sidebar-bg: #1f2733;
  --shell-sidebar-text: #c8d1dc;
  --shell-sidebar-muted: #7c8899;
  --shell-sidebar-child-text: #aab6c4;
  --shell-sidebar-strong: #fff;
  --shell-sidebar-hover-bg: rgba(255, 255, 255, 0.06);
  --shell-sidebar-border: rgba(255, 255, 255, 0.08);
  --shell-sidebar-shadow: rgba(0, 0, 0, 0.25);

  width: 220px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--shell-sidebar-bg);
  color: var(--shell-sidebar-text);
  transition: width 0.16s ease;
}

.app-sidebar.collapsed {
  width: 62px;
}

.sidebar-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 12px 8px;
}

.side-label {
  padding: 6px 10px;
  font-size: 11px;
  color: var(--shell-sidebar-muted);
  letter-spacing: 0.06em;
  white-space: nowrap;
}

.side-empty {
  margin: 8px 10px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--shell-sidebar-muted);
}

.side-item {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 8px 10px;
  border-radius: 7px;
  color: var(--shell-sidebar-text);
  font-size: 13px;
  text-decoration: none;
  white-space: nowrap;
  overflow: hidden;
}

.side-item:hover {
  background: var(--shell-sidebar-hover-bg);
  color: var(--shell-sidebar-strong);
}

.side-item.active {
  background: var(--el-color-primary);
  color: var(--shell-sidebar-strong);
}

.side-item.is-child {
  padding-left: 34px;
  color: var(--shell-sidebar-child-text);
}

.side-group {
  margin-top: 6px;
}

.side-group-title {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 9px 10px 5px;
  color: var(--shell-sidebar-muted);
  font-size: 11px;
  white-space: nowrap;
  text-decoration: none;
}

.side-group-title.is-link:hover {
  color: var(--shell-sidebar-strong);
}

.side-group-title.active {
  color: var(--el-color-primary);
}

.collapse-btn {
  height: 34px;
  flex-shrink: 0;
  display: grid;
  place-items: center;
  border: 0;
  border-top: 1px solid var(--shell-sidebar-border);
  background: transparent;
  color: var(--shell-sidebar-muted);
  cursor: pointer;
}

.collapse-btn:hover,
.collapse-btn:focus-visible {
  color: var(--shell-sidebar-strong);
  outline: none;
}

.collapsed .side-label,
.collapsed .side-item span,
.collapsed .side-group-title span,
.collapsed .side-empty {
  display: none;
}

.collapsed .side-item {
  justify-content: center;
  padding: 9px 0;
}

.collapsed .side-item.is-child {
  display: none;
}

.collapsed .side-group-title {
  justify-content: center;
  padding: 9px 0;
}

.app-sidebar.open {
  transform: none;
}

@media (max-width: 899px) {
  .app-sidebar {
    position: fixed;
    top: 52px;
    bottom: 0;
    left: 0;
    width: 240px;
    z-index: 120;
    transform: translateX(-100%);
    transition: transform 0.18s ease;
    box-shadow: 12px 0 30px var(--shell-sidebar-shadow);
  }

  .app-sidebar.open {
    transform: none;
  }
}
</style>
