<template>
  <div class="shell-c" :class="{ 'is-narrow': narrow, 'is-collapsed': collapsed }">
    <header class="c-header">
      <button v-if="narrow" class="c-icon-btn" title="菜单" @click="drawerOpen = true">
        <el-icon><Menu /></el-icon>
      </button>
      <div class="c-brand">
        <span class="c-logo">云</span>
        <span class="c-brand-text">云尚办公</span>
      </div>
      <button class="c-fold" :title="collapsed ? '展开侧栏' : '收起侧栏'" @click="collapsed = !collapsed">
        <el-icon><component :is="collapsed ? Expand : Fold" /></el-icon>
      </button>
      <nav class="c-topnav">
        <button
          v-for="module in nav"
          :key="module.key"
          class="c-topnav-item"
          :class="{ active: module.key === moduleKey }"
          @click="switchModule(module.key)"
        >
          {{ module.title }}
        </button>
      </nav>
      <div class="c-tools">
        <menu-search v-if="showSearch" :items="flatNav" @select="openLeaf">
          <template #trigger>
            <button class="c-search">
              <el-icon><Search /></el-icon>
              <span>搜索菜单</span>
              <kbd>⌘K</kbd>
            </button>
          </template>
        </menu-search>
        <button
          v-if="showFullscreen"
          class="c-icon-btn"
          title="全屏"
          @click="toggleFullscreen"
        >
          <el-icon><FullScreen /></el-icon>
        </button>
        <el-badge :value="2" class="c-badge">
          <button class="c-icon-btn" title="公告">
            <el-icon><Bell /></el-icon>
          </button>
        </el-badge>
        <el-dropdown trigger="click" @command="onUserCommand">
          <button class="c-user">
            <span class="c-avatar">{{ me.initial }}</span>
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

    <div class="c-tabstrip">
      <div class="c-tabs-scroll">
        <button
          v-for="tab in tabs"
          :key="tab.key"
          class="c-tab"
          :class="{ active: tab.key === activeKey }"
          @click="activeKey = tab.key"
          @click.middle="closeTab(tab.key)"
        >
          <el-icon v-if="tab.fixed" class="c-pin"><HomeFilled /></el-icon>
          <span class="c-tab-title">{{ tab.title }}</span>
          <el-icon
            v-if="!tab.fixed"
            class="c-tab-close"
            @click.stop="closeTab(tab.key)"
          >
            <Close />
          </el-icon>
        </button>
      </div>
      <el-dropdown trigger="click" @command="tabCommand">
        <button class="c-tab-more" title="标签操作">
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

    <div class="c-body">
      <div v-if="narrow && drawerOpen" class="c-backdrop" @click="drawerOpen = false"></div>
      <aside class="c-sidebar" :class="{ open: drawerOpen }">
        <div class="c-side-scroll">
          <template v-if="moduleKey === 'workbench'">
            <div class="c-side-title">快捷</div>
            <button
              v-for="item in workbenchLinks"
              :key="item.key"
              class="c-side-item"
              :class="{ active: activeKey === item.key }"
              @click="selectLeaf(item)"
            >
              <el-icon><component :is="item.icon" /></el-icon>
              <span>{{ item.title }}</span>
              <em v-if="item.key === 'pending'" class="c-count">12</em>
            </button>
            <div class="c-side-title c-side-title-gap">最近</div>
            <button
              v-for="item in recentLinks"
              :key="item.key"
              class="c-side-item is-plain"
              :class="{ active: activeKey === item.key }"
              @click="selectLeaf(item)"
            >
              <span>{{ item.title }}</span>
            </button>
          </template>

          <template v-else-if="currentModule">
            <div class="c-side-title">{{ currentModule.title }}</div>
            <button
              v-for="item in flatItems"
              :key="item.key"
              class="c-side-item"
              :class="{ active: activeKey === item.key }"
              @click="selectLeaf(item)"
            >
              <el-icon><component :is="item.icon" /></el-icon>
              <span>{{ item.title }}</span>
            </button>
            <div v-for="group in groups" :key="group.key" class="c-side-group">
              <div class="c-side-group-title">{{ group.title }}</div>
              <button
                v-for="leaf in group.children"
                :key="leaf.key"
                class="c-side-item is-child"
                :class="{ active: activeKey === leaf.key }"
                @click="selectLeaf(leaf)"
              >
                <span>{{ leaf.title }}</span>
              </button>
            </div>
          </template>
        </div>
      </aside>

      <main class="c-main">
        <div v-if="showBreadcrumb" class="c-crumb">
          <span>首页</span>
          <template v-for="(title, index) in activeTab.trail" :key="index">
            <span class="c-crumb-sep">/</span>
            <span :class="{ current: index === activeTab.trail.length - 1 }">{{ title }}</span>
          </template>
        </div>
        <div class="c-content">
          <workbench-c
            v-if="activeTab.key === 'workbench'"
            :state="state"
            @go="openByKey"
          />
          <list-c v-else-if="activeTab.key === 'instanceList'" :state="state" />
          <div v-else class="c-placeholder">
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
  Expand,
  Fold,
  FullScreen,
  HomeFilled,
  Menu,
  Search,
} from '@element-plus/icons-vue'
import MenuSearch from '../MenuSearch.vue'
import WorkbenchC from './c/WorkbenchC.vue'
import ListC from './c/ListC.vue'
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
  if (existing) activeKey.value = item.key
  else {
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
  if (item) openLeaf(item)
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
.shell-c {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--el-bg-color-page);
  color: var(--el-text-color-primary);
  overflow: hidden;
}

.c-header {
  min-width: 0;
  height: 48px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 14px;
  background: var(--el-bg-color);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.c-brand {
  display: flex;
  align-items: center;
  gap: 7px;
  flex-shrink: 0;
}

.c-logo {
  width: 26px;
  height: 26px;
  border-radius: 7px;
  display: grid;
  place-items: center;
  background: var(--el-text-color-primary);
  color: var(--el-bg-color);
  font-weight: 700;
  font-size: 13px;
}

.c-brand-text {
  font-size: 14px;
  font-weight: 700;
}

.c-fold {
  width: 28px;
  height: 28px;
  border: 0;
  border-radius: 6px;
  display: grid;
  place-items: center;
  background: transparent;
  color: var(--el-text-color-secondary);
  cursor: pointer;
}

.c-fold:hover {
  background: var(--el-fill-color-light);
}

.c-topnav {
  flex: 1;
  min-width: 0;
  display: flex;
  gap: 2px;
  overflow-x: auto;
  scrollbar-width: none;
}

.c-topnav::-webkit-scrollbar {
  display: none;
}

.c-topnav-item {
  border: 0;
  background: transparent;
  color: var(--el-text-color-regular);
  font-size: 13px;
  padding: 6px 11px;
  border-radius: 6px;
  white-space: nowrap;
  cursor: pointer;
}

.c-topnav-item:hover {
  background: var(--el-fill-color-light);
}

.c-topnav-item.active {
  background: var(--el-text-color-primary);
  color: var(--el-bg-color);
  font-weight: 600;
}

.c-tools {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}

.c-search {
  display: flex;
  align-items: center;
  gap: 6px;
  height: 28px;
  padding: 0 8px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  background: var(--el-fill-color-lighter);
  color: var(--el-text-color-placeholder);
  font-size: 11px;
  cursor: pointer;
}

.c-search:hover {
  border-color: var(--el-color-primary-light-5);
}

.c-search kbd {
  font-family: inherit;
  font-size: 10px;
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  padding: 0 4px;
  color: var(--el-text-color-secondary);
}

.c-icon-btn {
  width: 30px;
  height: 30px;
  border: 0;
  border-radius: 6px;
  display: grid;
  place-items: center;
  background: transparent;
  color: var(--el-text-color-regular);
  cursor: pointer;
}

.c-icon-btn:hover {
  background: var(--el-fill-color-light);
}

.c-badge :deep(.el-badge__content) {
  top: 4px;
  right: 6px;
}

.c-user {
  border: 0;
  background: transparent;
  padding: 2px;
  cursor: pointer;
}

.c-avatar {
  width: 26px;
  height: 26px;
  border-radius: 7px;
  display: grid;
  place-items: center;
  background: var(--el-fill-color-dark);
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.c-tabstrip {
  min-width: 0;
  height: 33px;
  flex-shrink: 0;
  display: flex;
  align-items: stretch;
  gap: 4px;
  padding: 0 10px;
  background: var(--el-bg-color);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.c-tabs-scroll {
  flex: 1;
  min-width: 0;
  display: flex;
  gap: 14px;
  overflow-x: auto;
  scrollbar-width: none;
}

.c-tabs-scroll::-webkit-scrollbar {
  display: none;
}

.c-tab {
  position: relative;
  display: flex;
  align-items: center;
  gap: 4px;
  border: 0;
  background: transparent;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  padding: 0 1px;
  white-space: nowrap;
  cursor: pointer;
  max-width: 170px;
}

.c-tab.active {
  color: var(--el-color-primary);
  font-weight: 600;
}

.c-tab.active::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 2px;
  border-radius: 2px 2px 0 0;
  background: var(--el-color-primary);
}

.c-pin {
  font-size: 11px;
}

.c-tab-title {
  overflow: hidden;
  text-overflow: ellipsis;
}

.c-tab-close {
  font-size: 10px;
  border-radius: 50%;
  padding: 1px;
  opacity: 0;
}

.c-tab:hover .c-tab-close,
.c-tab.active .c-tab-close {
  opacity: 1;
}

.c-tab-close:hover {
  background: var(--el-fill-color-dark);
}

.c-tab-more {
  width: 24px;
  border: 0;
  background: transparent;
  color: var(--el-text-color-secondary);
  cursor: pointer;
}

.c-tab-more:hover {
  background: var(--el-fill-color-light);
}

.c-body {
  flex: 1;
  min-height: 0;
  min-width: 0;
  display: flex;
}

.c-sidebar {
  width: 176px;
  flex-shrink: 0;
  background: var(--el-bg-color);
  border-right: 1px solid var(--el-border-color-lighter);
  overflow: hidden;
  transition: width 0.15s ease;
}

.is-collapsed .c-sidebar {
  width: 0;
  border-right: 0;
}

.c-side-scroll {
  height: 100%;
  overflow-y: auto;
  padding: 10px 8px;
}

.c-side-title {
  padding: 4px 8px 6px;
  font-size: 10px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
  letter-spacing: 0.08em;
}

.c-side-title-gap {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.c-side-item {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 7px;
  border: 0;
  background: transparent;
  color: var(--el-text-color-regular);
  padding: 6px 8px;
  border-radius: 5px;
  font-size: 12px;
  text-align: left;
  cursor: pointer;
  white-space: nowrap;
}

.c-side-item:hover {
  background: var(--el-fill-color-light);
}

.c-side-item.active {
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  font-weight: 600;
}

.c-side-item.is-child {
  padding-left: 22px;
  font-size: 12px;
}

.c-side-item.is-plain {
  color: var(--el-text-color-secondary);
}

.c-count {
  margin-left: auto;
  font-style: normal;
  font-size: 10px;
  padding: 1px 5px;
  border-radius: 999px;
  background: var(--el-color-danger-light-9);
  color: var(--el-color-danger);
}

.c-side-group {
  margin-top: 8px;
}

.c-side-group-title {
  padding: 6px 8px 3px;
  font-size: 10px;
  color: var(--el-text-color-secondary);
}

.c-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.c-crumb {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px 0;
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.c-crumb-sep {
  color: var(--el-border-color);
}

.c-crumb .current {
  color: var(--el-text-color-primary);
}

.c-content {
  flex: 1;
  min-height: 0;
  min-width: 0;
  overflow: auto;
  padding: 10px 14px 16px;
}

.c-placeholder {
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  padding: 50px 20px;
}

.c-backdrop {
  position: fixed;
  inset: 81px 0 0;
  background: rgba(15, 23, 42, 0.45);
  z-index: 110;
}

.is-narrow .c-backdrop {
  inset: 48px 0 0;
}

@media (max-width: 1180px) {
  .c-brand-text {
    display: none;
  }
}

.is-narrow .c-header {
  gap: 6px;
  padding: 0 10px;
}

.is-narrow .c-brand-text {
  display: none;
}

.is-narrow .c-fold {
  display: none;
}

.is-narrow .c-search {
  width: 28px;
  padding: 0;
  justify-content: center;
}

.is-narrow .c-search span,
.is-narrow .c-search kbd {
  display: none;
}

.is-narrow .c-sidebar {
  position: fixed;
  left: 0;
  top: 48px;
  bottom: 0;
  width: 208px;
  z-index: 120;
  transform: translateX(-100%);
  transition: transform 0.18s ease;
}

.is-narrow .c-sidebar.open {
  transform: none;
  box-shadow: 12px 0 30px rgba(0, 0, 0, 0.25);
}

.is-narrow .c-tabstrip {
  display: none;
}

.is-narrow .c-content {
  padding: 8px 10px 14px;
}

.is-narrow .c-crumb {
  padding: 6px 10px 0;
}
</style>
