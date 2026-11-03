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
      :title="collapsed ? '展开菜单' : '收起菜单'"
      :aria-label="collapsed ? '展开菜单' : '收起菜单'"
      @click="$emit('toggle-collapse')"
    >
      <el-icon><component :is="shellIcon(collapsed ? 'Expand' : 'Fold')" /></el-icon>
    </button>
  </aside>
</template>

<script setup lang="ts">
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
.app-sidebar {
  width: 220px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: #1f2733;
  color: #c8d1dc;
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
  color: #7c8899;
  letter-spacing: 0.06em;
  white-space: nowrap;
}

.side-empty {
  margin: 8px 10px;
  font-size: 12px;
  line-height: 1.5;
  color: #7c8899;
}

.side-item {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 8px 10px;
  border-radius: 7px;
  color: #c8d1dc;
  font-size: 13px;
  text-decoration: none;
  white-space: nowrap;
  overflow: hidden;
}

.side-item:hover {
  background: rgba(255, 255, 255, 0.06);
  color: #fff;
}

.side-item.active {
  background: var(--el-color-primary);
  color: #fff;
}

.side-item.is-child {
  padding-left: 34px;
  color: #aab6c4;
}

.side-group {
  margin-top: 6px;
}

.side-group-title {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 9px 10px 5px;
  color: #7c8899;
  font-size: 11px;
  white-space: nowrap;
  text-decoration: none;
}

.side-group-title.is-link:hover {
  color: #fff;
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
  border-top: 1px solid rgba(255, 255, 255, 0.08);
  background: transparent;
  color: #7c8899;
  cursor: pointer;
}

.collapse-btn:hover,
.collapse-btn:focus-visible {
  color: #fff;
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
    box-shadow: 12px 0 30px rgba(0, 0, 0, 0.25);
  }

  .app-sidebar.open {
    transform: none;
  }
}
</style>
