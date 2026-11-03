import { defineStore } from 'pinia'
import { PATHS } from '@/router/paths'
import { activeModuleKey, type ShellModuleKey } from './shell-nav'
import {
  closeAllTabs,
  closeCurrentTab,
  closeOtherTabs,
  closeShellTab,
  initialShellTabs,
  isFixedTab,
  openShellTab,
} from './shell-tabs'
import {
  applyShellTheme,
  readShellPreferences,
  writeShellDensity,
  writeShellTheme,
  type ShellDensity,
  type ShellTheme,
} from './shell-preferences'

interface ShellRouteInput {
  path: string
  fullPath?: string
  title?: string
}

export const useShellStore = defineStore('shell', {
  state: () => {
    const preferences = readShellPreferences()
    return {
      tabs: initialShellTabs(),
      activePath: PATHS.WORKBENCH as string,
      activeModule: null as ShellModuleKey | null,
      collapsed: false,
      drawerOpen: false,
      theme: preferences.theme as ShellTheme,
      density: preferences.density as ShellDensity,
    }
  },
  actions: {
    syncRoute({ path, fullPath, title }: ShellRouteInput): void {
      const tabPath = isFixedTab(fullPath || path) ? PATHS.WORKBENCH : fullPath || path
      this.activePath = tabPath
      this.activeModule = activeModuleKey(path)
      this.tabs = openShellTab(this.tabs, { path: tabPath, title: title || path })
    },
    closeTab(path: string): string {
      const outcome = closeShellTab(this.tabs, this.activePath, path)
      this.tabs = outcome.tabs
      this.activePath = outcome.activePath
      return outcome.activePath
    },
    closeCurrentTab(): string {
      const outcome = closeCurrentTab(this.tabs, this.activePath)
      this.tabs = outcome.tabs
      this.activePath = outcome.activePath
      return outcome.activePath
    },
    closeOtherTabs(): string {
      const outcome = closeOtherTabs(this.tabs, this.activePath)
      this.tabs = outcome.tabs
      this.activePath = outcome.activePath
      return outcome.activePath
    },
    closeAllTabs(): string {
      const outcome = closeAllTabs(this.tabs)
      this.tabs = outcome.tabs
      this.activePath = outcome.activePath
      return outcome.activePath
    },
    setActiveModule(key: ShellModuleKey | null): void {
      this.activeModule = key
    },
    toggleCollapsed(): void {
      this.collapsed = !this.collapsed
    },
    toggleDrawer(): void {
      this.drawerOpen = !this.drawerOpen
    },
    closeDrawer(): void {
      this.drawerOpen = false
    },
    setTheme(theme: ShellTheme): void {
      this.theme = theme
      writeShellTheme(theme)
      applyShellTheme(theme)
    },
    setDensity(density: ShellDensity): void {
      this.density = density
      writeShellDensity(density)
    },
    applyTheme(): void {
      applyShellTheme(this.theme)
    },
  },
})
