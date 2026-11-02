<template>
  <div class="shell-b" :class="{ 'is-narrow': narrow }">
    <header class="b-header">
      <button v-if="narrow" class="b-icon-btn" title="菜单" @click="drawerOpen = true">
        <el-icon><Menu /></el-icon>
      </button>
      <div class="b-brand">
        <span class="b-logo">云</span>
        <span class="b-brand-text">云尚办公</span>
      </div>
      <nav class="b-topnav">
        <button
          v-for="module in nav"
          :key="module.key"
          class="b-topnav-item"
          :class="{ active: module.key === moduleKey }"
          @click="switchModule(module.key)"
        >
          {{ module.title }}
        </button>
      </nav>
      <div class="b-tools">
        <menu-search v-if="showSearch" :items="flatNav" @select="openLeaf">
          <template #trigger>
            <button class="b-search-trigger">
              <el-icon><Search /></el-icon>
              <span>搜索菜单</span>
            </button>
          </template>
        </menu-search>
        <button
          v-if="showFullscreen"
          class="b-icon-btn"
          title="全屏"
          @click="toggleFullscreen"
        >
          <el-icon><FullScreen /></el-icon>
        </button>
        <el-badge :value="2" class="b-badge">
          <button class="b-icon-btn" title="公告">
            <el-icon><Bell /></el-icon>
          </button>
        </el-badge>
        <el-dropdown trigger="click" @command="onUserCommand">
          <button class="b-user">
            <span class="b-avatar">{{ me.initial }}</span>
            <span class="b-user-name">{{ me.name }}</span>
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

    <div class="b-body">
      <div v-if="narrow && drawerOpen" class="b-backdrop" @click="drawerOpen = false"></div>
      <aside class="b-sidebar" :class="{ open: drawerOpen }">
        <div class="b-side-scroll">
          <template v-if="moduleKey === 'workbench'">
            <div class="b-side-title">工作台</div>
            <button
              v-for="item in workbenchLinks"
              :key="item.key"
              class="b-side-item"
              :class="{ active: activeKey === item.key }"
              @click="selectLeaf(item)"
            >
              <el-icon><component :is="item.icon" /></el-icon>
              <span>{{ item.title }}</span>
              <em v-if="item.key === 'pending'" class="b-count">12</em>
            </button>
            <div class="b-side-title b-side-title-gap">最近访问</div>
            <button
              v-for="item in recentLinks"
              :key="item.key"
              class="b-side-item is-plain"
              :class="{ active: activeKey === item.key }"
              @click="selectLeaf(item)"
            >
              <span>{{ item.title }}</span>
            </button>
          </template>

          <template v-else-if="currentModule">
            <div class="b-side-title">{{ currentModule.title }}</div>
            <button
              v-for="item in flatItems"
              :key="item.key"
              class="b-side-item"
              :class="{ active: activeKey === item.key }"
              @click="selectLeaf(item)"
            >
              <el-icon><component :is="item.icon" /></el-icon>
              <span>{{ item.title }}</span>
            </button>
            <div v-for="group in groups" :key="group.key" class="b-side-group">
              <div class="b-side-group-title">{{ group.title }}</div>
              <button
                v-for="leaf in group.children"
                :key="leaf.key"
                class="b-side-item is-child"
                :class="{ active: activeKey === leaf.key }"
                @click="selectLeaf(leaf)"
              >
                <span>{{ leaf.title }}</span>
              </button>
            </div>
          </template>
        </div>
      </aside>

      <main class="b-main">
        <div class="b-tabbar">
          <div class="b-tabs-scroll">
            <button
              v-for="tab in tabs"
              :key="tab.key"
              class="b-tab"
              :class="{ active: tab.key === activeKey }"
              @click="activeKey = tab.key"
              @click.middle="closeTab(tab.key)"
              @contextmenu.prevent="openContextMenu($event, tab)"
            >
              <span class="b-tab-dot"></span>
              <span class="b-tab-title">{{ tab.title }}</span>
              <el-icon
                v-if="!tab.fixed"
                class="b-tab-close"
                @click.stop="closeTab(tab.key)"
              >
                <Close />
              </el-icon>
            </button>
          </div>
          <el-dropdown trigger="click" @command="runTabCommand">
            <button class="b-tab-more" title="标签操作">
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

        <div v-if="showBreadcrumb" class="b-crumb">
          <span>首页</span>
          <template v-for="(title, index) in activeTab.trail" :key="index">
            <span class="b-crumb-sep">/</span>
            <span :class="{ current: index === activeTab.trail.length - 1 }">{{ title }}</span>
          </template>
        </div>

        <div class="b-content">
          <workbench-b
            v-if="activeTab.key === 'workbench'"
            :state="state"
            @go="openByKey"
          />
          <list-b v-else-if="activeTab.key === 'instanceList'" :state="state" />
          <div v-else class="b-placeholder">
            <el-empty :description="`「${activeTab.title}」在原型中省略`">
              <p>这张票只钉外壳、工作台与列表页密度，其余页面沿用同一范式。</p>
            </el-empty>
          </div>
        </div>
      </main>
    </div>

    <div
      v-if="ctx.visible"
      class="b-ctx-mask"
      @click="ctx.visible = false"
      @contextmenu.prevent="ctx.visible = false"
    ></div>
    <div v-if="ctx.visible" class="b-ctx" :style="{ left: `${ctx.x}px`, top: `${ctx.y}px` }">
      <button @click="ctxCommand('current')">关闭当前</button>
      <button @click="ctxCommand('others')">关闭其他</button>
      <button @click="ctxCommand('all')">关闭全部</button>
    </div>
  </div>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  ArrowDown,
  Bell,
  Close,
  FullScreen,
  Menu,
  Search,
} from '@element-plus/icons-vue'
import MenuSearch from '../MenuSearch.vue'
import WorkbenchB from './b/WorkbenchB.vue'
import ListB from './b/ListB.vue'
import { flatNav, findNavItem, me, nav } from '../mock'

const props = defineProps({
  page: { type: String, default: 'workbench' },
  narrow: { type: Boolean, default: false },
  state: { type: String, default: 'ready' },
  showBreadcrumb: { type: Boolean, default: true },
  showFullscreen: { type: Boolean, default: true },
  showSearch: { type: Boolean, default: true },
})

const drawerOpen = ref(false)
const ctx = reactive({ visible: false, x: 0, y: 0, key: '' })

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

function closeTab(key, activateNeighbour = true) {
  if (key === 'workbench') return
  const index = tabs.value.findIndex((tab) => tab.key === key)
  if (index < 0) return
  const wasActive = activeKey.value === key
  tabs.value.splice(index, 1)
  if (wasActive && activateNeighbour) {
    const next = tabs.value[index] || tabs.value[index - 1] || tabs.value[0]
    activeKey.value = next.key
  }
}

function runTabCommand(command, targetKey = activeKey.value) {
  ctx.visible = false
  if (command === 'current') {
    closeTab(targetKey)
    return
  }
  if (command === 'others') {
    tabs.value = tabs.value.filter((tab) => tab.fixed || tab.key === targetKey)
    activeKey.value = targetKey
    return
  }
  if (command === 'all') {
    tabs.value = tabs.value.filter((tab) => tab.fixed)
    activeKey.value = 'workbench'
  }
}

function openContextMenu(event, tab) {
  ctx.visible = true
  ctx.x = Math.min(event.clientX, window.innerWidth - 150)
  ctx.y = Math.min(event.clientY, window.innerHeight - 140)
  ctx.key = tab.key
}

function ctxCommand(command) {
  runTabCommand(command, ctx.key)
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
.shell-b {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--el-bg-color-page);
  color: var(--el-text-color-primary);
  overflow: hidden;
}

.b-header {
  min-width: 0;
  height: 56px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 0 18px;
  background: var(--el-bg-color);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.b-brand {
  display: flex;
  align-items: center;
  gap: 9px;
  flex-shrink: 0;
}

.b-logo {
  width: 30px;
  height: 30px;
  border-radius: 9px;
  display: grid;
  place-items: center;
  background: var(--el-color-primary);
  color: #fff;
  font-weight: 700;
  font-size: 14px;
}

.b-brand-text {
  font-size: 16px;
  font-weight: 700;
}

.b-topnav {
  flex: 1;
  min-width: 0;
  display: flex;
  gap: 18px;
  overflow-x: auto;
  align-self: stretch;
  scrollbar-width: none;
}

.b-topnav::-webkit-scrollbar {
  display: none;
}

.b-topnav-item {
  position: relative;
  border: 0;
  background: transparent;
  color: var(--el-text-color-regular);
  font-size: 14px;
  padding: 0 2px;
  white-space: nowrap;
  cursor: pointer;
}

.b-topnav-item:hover {
  color: var(--el-text-color-primary);
}

.b-topnav-item.active {
  color: var(--el-color-primary);
  font-weight: 600;
}

.b-topnav-item.active::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: -1px;
  height: 2px;
  border-radius: 2px;
  background: var(--el-color-primary);
}

.b-tools {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}

.b-search-trigger {
  display: flex;
  align-items: center;
  gap: 6px;
  height: 30px;
  width: 168px;
  padding: 0 10px;
  border-radius: 8px;
  border: 1px solid var(--el-border-color);
  background: var(--el-fill-color-lighter);
  color: var(--el-text-color-placeholder);
  font-size: 12px;
  cursor: pointer;
  text-align: left;
}

.b-search-trigger:hover {
  border-color: var(--el-color-primary-light-5);
}

.b-icon-btn {
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

.b-icon-btn:hover {
  background: var(--el-fill-color-light);
  color: var(--el-text-color-primary);
}

.b-badge :deep(.el-badge__content) {
  top: 6px;
  right: 8px;
}

.b-user {
  display: flex;
  align-items: center;
  gap: 7px;
  border: 0;
  background: transparent;
  padding: 3px 4px;
  border-radius: 8px;
  cursor: pointer;
  color: var(--el-text-color-regular);
}

.b-user:hover {
  background: var(--el-fill-color-light);
}

.b-avatar {
  width: 27px;
  height: 27px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  font-size: 12px;
  font-weight: 600;
}

.b-user-name {
  font-size: 13px;
}

.b-body {
  flex: 1;
  min-height: 0;
  min-width: 0;
  display: flex;
}

.b-sidebar {
  width: 208px;
  flex-shrink: 0;
  background: var(--el-bg-color);
  border-right: 1px solid var(--el-border-color-lighter);
  overflow: hidden;
}

.b-side-scroll {
  height: 100%;
  overflow-y: auto;
  padding: 14px 10px;
}

.b-side-title {
  padding: 4px 10px 8px;
  font-size: 11px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
  letter-spacing: 0.05em;
}

.b-side-title-gap {
  margin-top: 18px;
  padding-top: 14px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.b-side-item {
  position: relative;
  width: 100%;
  display: flex;
  align-items: center;
  gap: 9px;
  border: 0;
  background: transparent;
  color: var(--el-text-color-regular);
  padding: 8px 10px;
  border-radius: 8px;
  font-size: 13px;
  text-align: left;
  cursor: pointer;
  white-space: nowrap;
}

.b-side-item:hover {
  background: var(--el-fill-color-light);
  color: var(--el-text-color-primary);
}

.b-side-item.active {
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  font-weight: 600;
}

.b-side-item.active::before {
  content: '';
  position: absolute;
  left: 0;
  top: 8px;
  bottom: 8px;
  width: 3px;
  border-radius: 3px;
  background: var(--el-color-primary);
}

.b-side-item.is-child {
  padding-left: 28px;
  font-size: 12.5px;
}

.b-side-item.is-plain {
  color: var(--el-text-color-secondary);
  font-size: 12.5px;
}

.b-count {
  margin-left: auto;
  font-style: normal;
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 999px;
  background: var(--el-color-danger-light-9);
  color: var(--el-color-danger);
}

.b-side-group {
  margin-top: 10px;
}

.b-side-group-title {
  padding: 6px 10px;
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.b-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.b-tabbar {
  min-width: 0;
  height: 40px;
  flex-shrink: 0;
  display: flex;
  align-items: flex-end;
  gap: 6px;
  padding: 6px 12px 0;
  background: var(--el-bg-color-page);
}

.b-tabs-scroll {
  flex: 1;
  min-width: 0;
  display: flex;
  gap: 2px;
  overflow-x: auto;
  scrollbar-width: none;
}

.b-tabs-scroll::-webkit-scrollbar {
  display: none;
}

.b-tab {
  display: flex;
  align-items: center;
  gap: 6px;
  height: 34px;
  padding: 0 12px;
  border: 1px solid transparent;
  border-bottom: 0;
  border-radius: 9px 9px 0 0;
  background: transparent;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  cursor: pointer;
  white-space: nowrap;
  max-width: 180px;
}

.b-tab:hover {
  background: var(--el-fill-color-light);
}

.b-tab.active {
  background: var(--el-bg-color);
  border-color: var(--el-border-color-lighter);
  color: var(--el-text-color-primary);
  font-weight: 600;
}

.b-tab-dot {
  width: 6px;
  height: 6px;
  border-radius: 2px;
  background: var(--el-border-color);
  flex-shrink: 0;
}

.b-tab.active .b-tab-dot {
  background: var(--el-color-primary);
}

.b-tab-title {
  overflow: hidden;
  text-overflow: ellipsis;
}

.b-tab-close {
  font-size: 11px;
  border-radius: 50%;
  padding: 1px;
  opacity: 0;
}

.b-tab:hover .b-tab-close,
.b-tab.active .b-tab-close {
  opacity: 1;
}

.b-tab-close:hover {
  background: var(--el-fill-color-dark);
}

.b-tab-more {
  width: 30px;
  height: 30px;
  margin-bottom: 2px;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: var(--el-text-color-secondary);
  cursor: pointer;
}

.b-tab-more:hover {
  background: var(--el-fill-color-light);
}

.b-crumb {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 0 20px 8px;
  font-size: 11px;
  color: var(--el-text-color-secondary);
  background: var(--el-bg-color);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.b-crumb-sep {
  color: var(--el-border-color);
}

.b-crumb .current {
  color: var(--el-text-color-primary);
}

.b-content {
  flex: 1;
  min-height: 0;
  min-width: 0;
  overflow: auto;
  padding: 20px;
}

.b-placeholder {
  background: var(--el-bg-color);
  border-radius: 12px;
  border: 1px solid var(--el-border-color-lighter);
  padding: 60px 20px;
}

.b-backdrop {
  position: fixed;
  inset: 56px 0 0;
  background: rgba(15, 23, 42, 0.45);
  z-index: 110;
}

.b-ctx-mask {
  position: fixed;
  inset: 0;
  z-index: 3000;
}

.b-ctx {
  position: fixed;
  z-index: 3010;
  min-width: 132px;
  padding: 5px;
  border-radius: 9px;
  background: var(--el-bg-color-overlay);
  border: 1px solid var(--el-border-color-lighter);
  box-shadow: var(--el-box-shadow-light);
}

.b-ctx button {
  display: block;
  width: 100%;
  border: 0;
  background: transparent;
  padding: 7px 10px;
  border-radius: 6px;
  text-align: left;
  font-size: 12px;
  color: var(--el-text-color-regular);
  cursor: pointer;
}

.b-ctx button:hover {
  background: var(--el-fill-color-light);
  color: var(--el-color-primary);
}

@media (max-width: 1180px) {
  .b-brand-text {
    display: none;
  }
  .b-search-trigger {
    width: 34px;
    padding: 0;
    justify-content: center;
  }
  .b-search-trigger span {
    display: none;
  }
}

.is-narrow .b-header {
  gap: 8px;
  padding: 0 12px;
}

.is-narrow .b-brand-text {
  display: none;
}

.is-narrow .b-user-name {
  display: none;
}

.is-narrow .b-search-trigger {
  width: 34px;
  padding: 0;
  justify-content: center;
}

.is-narrow .b-search-trigger span {
  display: none;
}

.is-narrow .b-sidebar {
  position: fixed;
  left: 0;
  top: 56px;
  bottom: 0;
  width: 232px;
  z-index: 120;
  transform: translateX(-100%);
  transition: transform 0.18s ease;
}

.is-narrow .b-sidebar.open {
  transform: none;
  box-shadow: 12px 0 30px rgba(0, 0, 0, 0.25);
}

.is-narrow .b-tabbar {
  display: none;
}

.is-narrow .b-content {
  padding: 12px;
}
</style>
