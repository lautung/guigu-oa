<template>
  <el-config-provider :size="shell.density">
    <div class="app-shell" :class="{ 'is-narrow': narrow }">
      <app-top-bar
        :modules="navModules"
        :active-module="shell.activeModule"
        :narrow="narrow"
        :desktop-hint="showDesktopHint"
        @switch-module="switchModule"
        @toggle-menu="shell.toggleDrawer"
      />

      <div class="shell-body">
        <div v-if="narrow && shell.drawerOpen" class="shell-backdrop" @click="shell.closeDrawer" />
        <app-sidebar
          :module="activeModuleView"
          :active-path="route.path"
          :collapsed="shell.collapsed"
          :narrow="narrow"
          :drawer-open="shell.drawerOpen"
          @toggle-collapse="shell.toggleCollapsed"
          @navigate="shell.closeDrawer"
        />

        <div class="shell-main">
          <shell-tabs
            v-if="!narrow"
            :tabs="shell.tabs"
            :active-path="shell.activePath"
            @activate="activateTab"
            @close="closeTab"
            @command="handleTabCommand"
          />
          <shell-breadcrumb :crumbs="breadcrumb" />
          <main class="shell-content">
            <router-view />
          </main>
        </div>
      </div>
    </div>
  </el-config-provider>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/store/modules/user'
import AppTopBar from './AppTopBar.vue'
import AppSidebar from './AppSidebar.vue'
import ShellTabs from './ShellTabs.vue'
import ShellBreadcrumb from './ShellBreadcrumb.vue'
import { buildBreadcrumb } from './breadcrumb'
import {
  activeModuleKey,
  buildShellNav,
  lastTabPathForModule,
  type ShellModuleKey,
} from './shell-nav'
import { useShellStore } from './shell-state'

const NARROW_BREAKPOINT = 900

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const shell = useShellStore()

const viewportWidth = ref(typeof window === 'undefined' ? NARROW_BREAKPOINT : window.innerWidth)
const narrow = computed(() => viewportWidth.value < NARROW_BREAKPOINT)

const navModules = computed(() => buildShellNav(userStore.menus || []))
const activeModuleView = computed(
  () =>
    navModules.value.find((module) => module.key === shell.activeModule) ||
    navModules.value[0] ||
    null,
)
const showDesktopHint = computed(() => narrow.value && shell.activeModule === 'system')
const breadcrumb = computed(() =>
  buildBreadcrumb({
    path: route.path,
    title: typeof route.meta.title === 'string' ? route.meta.title : undefined,
    menus: userStore.menus || [],
  }),
)

function syncRoute(): void {
  shell.syncRoute({
    path: route.path,
    fullPath: route.fullPath,
    title: typeof route.meta.title === 'string' ? route.meta.title : route.path,
  })
}

function handleResize(): void {
  viewportWidth.value = window.innerWidth
}

function switchModule(key: ShellModuleKey): void {
  const module = navModules.value.find((candidate) => candidate.key === key)
  if (!module) return
  shell.closeDrawer()
  if (shell.activeModule === key) return

  const target = lastTabPathForModule(shell.tabs, key) || module.firstPath
  if (target && target !== route.fullPath) void router.push(target)
}

function activateTab(path: string): void {
  if (path !== route.fullPath) void router.push(path)
}

function closeTab(path: string): void {
  const next = shell.closeTab(path)
  if (next !== route.fullPath) void router.push(next)
}

function handleTabCommand(command: string): void {
  if (command === 'current') {
    const next = shell.closeCurrentTab()
    if (next !== route.fullPath) void router.push(next)
    return
  }
  if (command === 'others') {
    const next = shell.closeOtherTabs()
    if (next !== route.fullPath) void router.push(next)
    return
  }
  if (command === 'all') {
    const next = shell.closeAllTabs()
    if (next !== route.fullPath) void router.push(next)
  }
}

watch(
  () => route.fullPath,
  () => {
    syncRoute()
    if (narrow.value) shell.closeDrawer()
  },
  { immediate: true },
)

watch(narrow, (isNarrow) => {
  if (!isNarrow) shell.closeDrawer()
})

watch(
  navModules,
  (modules) => {
    if (shell.activeModule && !modules.some((module) => module.key === shell.activeModule)) {
      shell.setActiveModule(activeModuleKey(route.path))
    }
  },
  { deep: true },
)

shell.applyTheme()

onMounted(() => window.addEventListener('resize', handleResize))
onBeforeUnmount(() => window.removeEventListener('resize', handleResize))
</script>

<style lang="scss" scoped>
.app-shell {
  height: 100vh;
  height: 100dvh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--el-bg-color-page);
  color: var(--el-text-color-primary);
}

.shell-body {
  flex: 1;
  min-height: 0;
  min-width: 0;
  display: flex;
}

.shell-main {
  flex: 1;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.shell-content {
  flex: 1;
  min-height: 0;
  min-width: 0;
  overflow: auto;
  padding: 16px;
}

.shell-backdrop {
  position: fixed;
  inset: 52px 0 0;
  z-index: 110;
  background: var(--el-overlay-color-lighter);
}

@media (max-width: 899px) {
  .shell-content {
    padding: 12px;
  }
}
</style>
