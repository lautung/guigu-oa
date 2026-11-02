<template>
  <div class="shell-a" :class="{ 'is-narrow': narrow }">
    <header class="a-header">
      <button v-if="narrow" class="a-icon-btn" title="菜单" @click="drawerOpen = true">
        <el-icon><Menu /></el-icon>
      </button>
      <div class="a-brand">
        <span class="a-logo">云</span>
        <span class="a-brand-text">云尚办公</span>
      </div>
      <nav class="a-topnav">
        <button
          v-for="module in nav"
          :key="module.key"
          class="a-topnav-item"
          :class="{ active: module.key === moduleKey }"
          @click="switchModule(module.key)"
        >
          <el-icon><component :is="module.icon" /></el-icon>
          <span>{{ module.title }}</span>
        </button>
      </nav>
      <div class="a-tools">
        <menu-search v-if="showSearch" :items="flatNav" @select="openLeaf">
          <template #trigger>
            <button class="a-icon-btn" title="搜索菜单">
              <el-icon><Search /></el-icon>
            </button>
          </template>
        </menu-search>
        <button
          v-if="showFullscreen"
          class="a-icon-btn"
          title="全屏"
          @click="toggleFullscreen"
        >
          <el-icon><FullScreen /></el-icon>
        </button>
        <el-badge :value="2" class="a-badge">
          <button class="a-icon-btn" title="公告">
            <el-icon><Bell /></el-icon>
          </button>
        </el-badge>
        <el-dropdown trigger="click" @command="onUserCommand">
          <button class="a-user">
            <span class="a-avatar">{{ me.initial }}</span>
            <span class="a-user-name">{{ me.name }}</span>
            <el-icon><ArrowDown /></el-icon>
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="profile">个人中心</el-dropdown-item>
              <el-dropdown-item command="lock">锁屏</el-dropdown-item>
              <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </header>

    <div class="a-body">
      <div v-if="narrow && drawerOpen" class="a-backdrop" @click="drawerOpen = false"></div>
      <aside class="a-sidebar" :class="{ collapsed, open: drawerOpen }">
        <div class="a-sidebar-scroll">
          <template v-if="moduleKey === 'workbench'">
            <div class="a-side-label">快捷入口</div>
            <button
              v-for="item in workbenchLinks"
              :key="item.key"
              class="a-side-item"
              :class="{ active: activeKey === item.key }"
              @click="selectLeaf(item)"
            >
              <el-icon><component :is="item.icon" /></el-icon>
              <span>{{ item.title }}</span>
              <em v-if="item.key === 'pending'" class="a-side-count">12</em>
            </button>
            <div class="a-side-label">最近访问</div>
            <button
              v-for="item in recentLinks"
              :key="item.key"
              class="a-side-item"
              :class="{ active: activeKey === item.key }"
              @click="selectLeaf(item)"
            >
              <span class="a-side-dot"></span>
              <span>{{ item.title }}</span>
            </button>
          </template>

          <template v-else-if="currentModule">
            <div class="a-side-label">{{ currentModule.title }}</div>
            <button
              v-for="item in flatItems"
              :key="item.key"
              class="a-side-item"
              :class="{ active: activeKey === item.key }"
              :title="item.title"
              @click="selectLeaf(item)"
            >
              <el-icon><component :is="item.icon" /></el-icon>
              <span>{{ item.title }}</span>
            </button>
            <div v-for="group in groups" :key="group.key" class="a-side-group">
              <div class="a-side-group-title">
                <el-icon><component :is="group.icon" /></el-icon>
                <span>{{ group.title }}</span>
              </div>
              <button
                v-for="leaf in group.children"
                :key="leaf.key"
                class="a-side-item is-child"
                :class="{ active: activeKey === leaf.key }"
                @click="selectLeaf(leaf)"
              >
                <span>{{ leaf.title }}</span>
              </button>
            </div>
          </template>
        </div>
        <button class="a-collapse" @click="collapsed = !collapsed">
          {{ collapsed ? '»' : '«' }}
        </button>
      </aside>

      <main class="a-main">
        <div class="a-tabbar">
          <div class="a-tabs-scroll">
            <button
              v-for="tab in tabs"
              :key="tab.key"
              class="a-tab"
              :class="{ active: tab.key === activeKey }"
              @click="activeKey = tab.key"
              @click.middle="closeTab(tab.key)"
            >
              <el-icon v-if="tab.fixed" class="a-tab-pin"><HomeFilled /></el-icon>
              <span class="a-tab-title">{{ tab.title }}</span>
              <el-icon
                v-if="!tab.fixed"
                class="a-tab-close"
                @click.stop="closeTab(tab.key)"
              >
                <Close />
              </el-icon>
            </button>
          </div>
          <el-dropdown trigger="click" @command="tabCommand">
            <button class="a-tab-more" title="标签操作">
              <el-icon><ArrowDown /></el-icon>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="current">关闭当前</el-dropdown-item>
                <el-dropdown-item command="others">关闭其他</el-dropdown-item>
                <el-dropdown-item command="all">关闭全部</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>

        <div v-if="showBreadcrumb" class="a-crumb">
          <el-breadcrumb separator="/">
            <el-breadcrumb-item>首页</el-breadcrumb-item>
            <el-breadcrumb-item v-for="(title, index) in activeTab.trail" :key="index">
              {{ title }}
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>

        <div class="a-content">
          <workbench-a
            v-if="activeTab.key === 'workbench'"
            :state="state"
            @go="openByKey"
          />
          <list-a v-else-if="activeTab.key === 'instanceList'" :state="state" />
          <div v-else class="a-placeholder">
            <el-empty :description="`「${activeTab.title}」在原型中省略`">
              <p>这张票只钉外壳、工作台与列表页密度，其余页面沿用同一范式。</p>
            </el-empty>
          </div>
        </div>
      </main>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  ArrowDown,
  Bell,
  Close,
  FullScreen,
  HomeFilled,
  Menu,
  Search,
} from '@element-plus/icons-vue'
import MenuSearch from '../MenuSearch.vue'
import WorkbenchA from './a/WorkbenchA.vue'
import ListA from './a/ListA.vue'
import { flatNav, findNavItem, me, nav } from '../mock'

const props = defineProps({
  page: { type: String, default: 'workbench' },
  narrow: { type: Boolean, default: false },
  state: { type: String, default: 'ready' },
  showBreadcrumb: { type: Boolean, default: true },
  showFullscreen: { type: Boolean, default: true },
  showSearch: { type: Boolean, default: true },
})

const collapsed = ref(false)
const drawerOpen = ref(false)

const workbenchLinks = [
  { key: 'pending', title: '待处理', icon: 'AlarmClock' },
  { key: 'start', title: '发起审批', icon: 'EditPen' },
  { key: 'mine', title: '已发起', icon: 'Promotion' },
  { key: 'cc', title: '抄送我的', icon: 'Share' },
  { key: 'profile', title: '基本信息', icon: 'User' },
]
const recentLinks = [findNavItem('instanceList'), findNavItem('template'), findNavItem('notice')]

function makeTab(key) {
  if (key === 'workbench') {
    return { key: 'workbench', title: '工作台', fixed: true, moduleKey: 'workbench', trail: ['工作台'] }
  }
  const item = findNavItem(key)
  return {
    key: item.key,
    title: item.title,
    fixed: false,
    moduleKey: item.moduleKey,
    trail: item.trail,
  }
}

const tabs = ref([makeTab('workbench'), makeTab('pending'), makeTab('instanceList')])
const activeKey = ref('workbench')

const activeTab = computed(() => tabs.value.find((tab) => tab.key === activeKey.value) || tabs.value[0])
const moduleKey = computed(() => activeTab.value.moduleKey)
const currentModule = computed(() => nav.find((module) => module.key === moduleKey.value))

const flatItems = computed(() =>
  (currentModule.value?.children || []).filter((child) => !child.children),
)
const groups = computed(() =>
  (currentModule.value?.children || []).filter((child) => child.children),
)

function openByKey(key) {
  const item = findNavItem(key)
  if (item) openLeaf(item)
}

function openLeaf(item) {
  const existing = tabs.value.find((tab) => tab.key === item.key)
  if (existing) {
    activeKey.value = item.key
  } else {
    tabs.value.push({
      key: item.key,
      title: item.title,
      fixed: false,
      moduleKey: item.moduleKey,
      trail: item.trail,
    })
    activeKey.value = item.key
  }
  drawerOpen.value = false
}

function selectLeaf(item) {
  if (!item) return
  openLeaf(item)
}

function switchModule(key) {
  const module = nav.find((item) => item.key === key)
  if (!module) return
  if (key === 'workbench') {
    activeKey.value = 'workbench'
    return
  }
  const first = flatNav.find((item) => item.moduleKey === key)
  if (first) openLeaf(first)
}

function closeTab(key) {
  if (key === 'workbench') return
  const index = tabs.value.findIndex((tab) => tab.key === key)
  if (index < 0) return
  const wasActive = activeKey.value === key
  tabs.value.splice(index, 1)
  if (wasActive) {
    const next = tabs.value[index] || tabs.value[index - 1] || tabs.value[0]
    activeKey.value = next.key
  }
}

function tabCommand(command) {
  if (command === 'current') {
    if (activeKey.value !== 'workbench') closeTab(activeKey.value)
    return
  }
  if (command === 'others') {
    const current = activeKey.value
    tabs.value = tabs.value.filter((tab) => tab.fixed || tab.key === current)
    if (!tabs.value.some((tab) => tab.key === current)) activeKey.value = 'workbench'
    return
  }
  if (command === 'all') {
    tabs.value = tabs.value.filter((tab) => tab.fixed)
    activeKey.value = 'workbench'
  }
}

function toggleFullscreen() {
  if (document.fullscreenElement) document.exitFullscreen?.()
  else document.documentElement.requestFullscreen?.()
}

function onUserCommand(command) {
  if (command === 'logout') ElMessage.info('原型：退出登录')
  else ElMessage.info(`原型：${command === 'profile' ? '个人中心' : '锁屏'}`)
}

watch(
  () => props.page,
  (page) => {
    if (page === 'workbench') activeKey.value = 'workbench'
    if (page === 'instanceList') openByKey('instanceList')
  },
  { immediate: true },
)
</script>

<style scoped>
.shell-a {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--el-bg-color-page);
  color: var(--el-text-color-primary);
  overflow: hidden;
}

.a-header {
  min-width: 0;
  height: 52px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 14px;
  background: var(--el-bg-color);
  border-bottom: 1px solid var(--el-border-color-light);
}

.a-brand {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 196px;
  flex-shrink: 0;
}

.a-logo {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  display: grid;
  place-items: center;
  background: var(--el-color-primary);
  color: #fff;
  font-weight: 700;
  font-size: 14px;
}

.a-brand-text {
  font-size: 16px;
  font-weight: 700;
  letter-spacing: 0.02em;
}

.a-topnav {
  flex: 1;
  min-width: 0;
  display: flex;
  gap: 2px;
  overflow-x: auto;
  scrollbar-width: none;
}

.a-topnav::-webkit-scrollbar {
  display: none;
}

.a-topnav-item {
  display: flex;
  align-items: center;
  gap: 6px;
  border: 0;
  background: transparent;
  color: var(--el-text-color-regular);
  padding: 7px 13px;
  border-radius: 8px;
  font-size: 14px;
  white-space: nowrap;
  cursor: pointer;
}

.a-topnav-item:hover {
  background: var(--el-fill-color-light);
}

.a-topnav-item.active {
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  font-weight: 600;
}

.a-tools {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}

.a-icon-btn {
  width: 32px;
  height: 32px;
  border: 0;
  border-radius: 8px;
  display: grid;
  place-items: center;
  background: transparent;
  color: var(--el-text-color-regular);
  cursor: pointer;
}

.a-icon-btn:hover {
  background: var(--el-fill-color-light);
  color: var(--el-text-color-primary);
}

.a-badge :deep(.el-badge__content) {
  top: 6px;
  right: 8px;
}

.a-user {
  display: flex;
  align-items: center;
  gap: 6px;
  border: 0;
  background: transparent;
  padding: 3px 6px;
  border-radius: 8px;
  cursor: pointer;
  color: var(--el-text-color-regular);
}

.a-user:hover {
  background: var(--el-fill-color-light);
}

.a-avatar {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: var(--el-color-primary-light-8);
  color: var(--el-color-primary);
  font-size: 12px;
  font-weight: 600;
}

.a-user-name {
  font-size: 13px;
}

.a-body {
  flex: 1;
  min-height: 0;
  min-width: 0;
  display: flex;
}

.a-sidebar {
  width: 220px;
  flex-shrink: 0;
  background: #1f2733;
  color: #c8d1dc;
  display: flex;
  flex-direction: column;
  transition: width 0.16s ease;
  overflow: hidden;
}

.a-sidebar.collapsed {
  width: 62px;
}

.a-sidebar-scroll {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 12px 8px;
}

.a-side-label {
  padding: 6px 10px;
  font-size: 11px;
  color: #7c8899;
  letter-spacing: 0.06em;
  white-space: nowrap;
}

.a-side-item {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 9px;
  border: 0;
  background: transparent;
  color: #c8d1dc;
  padding: 8px 10px;
  border-radius: 7px;
  font-size: 13px;
  text-align: left;
  cursor: pointer;
  white-space: nowrap;
  overflow: hidden;
}

.a-side-item:hover {
  background: rgba(255, 255, 255, 0.06);
  color: #fff;
}

.a-side-item.active {
  background: var(--el-color-primary);
  color: #fff;
}

.a-side-item.is-child {
  padding-left: 34px;
  color: #aab6c4;
}

.a-side-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #7c8899;
  flex-shrink: 0;
}

.a-side-count {
  margin-left: auto;
  font-style: normal;
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.16);
}

.a-side-group {
  margin-top: 6px;
}

.a-side-group-title {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 9px 10px 5px;
  font-size: 11px;
  color: #7c8899;
  white-space: nowrap;
}

.a-sidebar.collapsed .a-side-label,
.a-sidebar.collapsed .a-side-item span,
.a-sidebar.collapsed .a-side-group-title span,
.a-sidebar.collapsed .a-side-count {
  display: none;
}

.a-sidebar.collapsed .a-side-item {
  justify-content: center;
  padding: 9px 0;
}

.a-sidebar.collapsed .a-side-item.is-child {
  padding: 0;
  height: 0;
  overflow: hidden;
}

.a-sidebar.collapsed .a-side-group-title {
  justify-content: center;
  padding: 9px 0;
}

.a-collapse {
  height: 34px;
  border: 0;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
  background: transparent;
  color: #7c8899;
  cursor: pointer;
  font-size: 14px;
}

.a-collapse:hover {
  color: #fff;
}

.a-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.a-tabbar {
  height: 38px;
  flex-shrink: 0;
  display: flex;
  align-items: stretch;
  gap: 6px;
  padding: 5px 8px 0;
  background: var(--el-bg-color);
  border-bottom: 1px solid var(--el-border-color-light);
}

.a-tabs-scroll {
  flex: 1;
  min-width: 0;
  display: flex;
  gap: 3px;
  overflow-x: auto;
  scrollbar-width: none;
}

.a-tabs-scroll::-webkit-scrollbar {
  display: none;
}

.a-tab {
  display: flex;
  align-items: center;
  gap: 5px;
  height: 32px;
  padding: 0 9px;
  border: 1px solid var(--el-border-color-light);
  border-bottom: 0;
  border-radius: 6px 6px 0 0;
  background: var(--el-fill-color-light);
  color: var(--el-text-color-regular);
  font-size: 12px;
  cursor: pointer;
  white-space: nowrap;
  max-width: 170px;
}

.a-tab.active {
  background: var(--el-bg-color);
  color: var(--el-color-primary);
  box-shadow: inset 0 2px 0 var(--el-color-primary);
}

.a-tab-title {
  overflow: hidden;
  text-overflow: ellipsis;
}

.a-tab-pin {
  color: var(--el-color-primary);
  font-size: 12px;
}

.a-tab-close {
  font-size: 11px;
  border-radius: 50%;
  padding: 1px;
}

.a-tab-close:hover {
  background: var(--el-fill-color-dark);
}

.a-tab-more {
  width: 28px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--el-text-color-secondary);
  cursor: pointer;
}

.a-tab-more:hover {
  background: var(--el-fill-color-light);
}

.a-crumb {
  padding: 8px 16px;
  background: var(--el-bg-color);
  border-bottom: 1px solid var(--el-border-color-lighter);
  flex-shrink: 0;
}

.a-content {
  flex: 1;
  min-height: 0;
  min-width: 0;
  overflow: auto;
  padding: 16px;
}

.a-placeholder {
  background: var(--el-bg-color);
  border-radius: 10px;
  padding: 60px 20px;
  border: 1px solid var(--el-border-color-lighter);
}

.a-backdrop {
  position: fixed;
  inset: 52px 0 0;
  background: rgba(15, 23, 42, 0.45);
  z-index: 110;
}

@media (max-width: 1180px) {
  .a-brand {
    width: auto;
  }
  .a-brand-text {
    display: none;
  }
}

.is-narrow .a-header {
  gap: 8px;
}

.is-narrow .a-brand {
  width: auto;
}

.is-narrow .a-brand-text {
  display: none;
}

.is-narrow .a-user-name {
  display: none;
}

.is-narrow .a-sidebar {
  position: fixed;
  left: 0;
  top: 52px;
  bottom: 0;
  width: 240px;
  z-index: 120;
  transform: translateX(-100%);
  transition: transform 0.18s ease;
}

.is-narrow .a-sidebar.open {
  transform: none;
  box-shadow: 12px 0 30px rgba(0, 0, 0, 0.25);
}

.is-narrow .a-collapse {
  display: none;
}

.is-narrow .a-tabbar {
  display: none;
}

.is-narrow .a-content {
  padding: 12px;
}

.is-narrow .a-crumb {
  padding: 6px 12px;
}
</style>
