import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useShellStore } from '@/shell/shell-state'
import { SHELL_DENSITY_KEY, SHELL_THEME_KEY } from '@/shell/shell-preferences'

function freshStore() {
  setActivePinia(createPinia())
  return useShellStore()
}

describe('外壳状态', () => {
  beforeEach(() => {
    localStorage.clear()
    document.documentElement.classList.remove('dark')
  })

  it('defaults to light theme and small density', () => {
    const shell = freshStore()

    expect(shell.theme).toBe('light')
    expect(shell.density).toBe('small')
    expect(shell.tabs).toEqual([{ path: '/workbench', title: '工作台' }])
    expect(shell.collapsed).toBe(false)
    expect(shell.drawerOpen).toBe(false)
  })

  it('persists theme and density, and applies html.dark', () => {
    const shell = freshStore()

    shell.setTheme('dark')
    shell.setDensity('large')

    expect(localStorage.getItem(SHELL_THEME_KEY)).toBe('dark')
    expect(localStorage.getItem(SHELL_DENSITY_KEY)).toBe('large')
    expect(document.documentElement.classList.contains('dark')).toBe(true)

    const reloaded = freshStore()
    expect(reloaded.theme).toBe('dark')
    expect(reloaded.density).toBe('large')

    reloaded.setTheme('light')
    expect(document.documentElement.classList.contains('dark')).toBe(false)
  })

  it('ignores malformed stored preferences', () => {
    localStorage.setItem(SHELL_THEME_KEY, 'neon')
    localStorage.setItem(SHELL_DENSITY_KEY, 'huge')

    const shell = freshStore()

    expect(shell.theme).toBe('light')
    expect(shell.density).toBe('small')
  })

  it('syncs the active module and opens a tab for each route', () => {
    const shell = freshStore()

    shell.syncRoute({ path: '/work/pending', fullPath: '/work/pending', title: '待处理' })
    expect(shell.activeModule).toBe('work')
    expect(shell.activePath).toBe('/work/pending')
    expect(shell.tabs.map((tab) => tab.path)).toEqual(['/workbench', '/work/pending'])

    shell.syncRoute({
      path: '/system/sysUser',
      fullPath: '/system/sysUser?page=2',
      title: '用户管理',
    })
    expect(shell.activeModule).toBe('system')
    expect(shell.tabs.at(-1)).toEqual({ path: '/system/sysUser?page=2', title: '用户管理' })

    shell.syncRoute({ path: '/workbench', fullPath: '/workbench', title: '工作台' })
    expect(shell.tabs.map((tab) => tab.path)).toEqual([
      '/workbench',
      '/work/pending',
      '/system/sysUser?page=2',
    ])
    expect(shell.activeModule).toBe('workbench')
  })

  it('does not persist tabs across a new store', () => {
    const shell = freshStore()
    shell.syncRoute({ path: '/work/pending', fullPath: '/work/pending', title: '待处理' })

    const reloaded = freshStore()

    expect(reloaded.tabs).toEqual([{ path: '/workbench', title: '工作台' }])
    expect(localStorage.getItem('guigu_oa_shell_tabs')).toBeNull()
  })

  it('closes tabs and returns the path to navigate to', () => {
    const shell = freshStore()
    shell.syncRoute({ path: '/work/pending', fullPath: '/work/pending', title: '待处理' })
    shell.syncRoute({ path: '/work/processed', fullPath: '/work/processed', title: '已处理' })

    expect(shell.closeTab('/work/pending')).toBe('/work/processed')
    expect(shell.tabs.map((tab) => tab.path)).toEqual(['/workbench', '/work/processed'])

    expect(shell.closeOtherTabs()).toBe('/work/processed')
    expect(shell.tabs.map((tab) => tab.path)).toEqual(['/workbench', '/work/processed'])

    expect(shell.closeAllTabs()).toBe('/workbench')
    expect(shell.tabs).toEqual([{ path: '/workbench', title: '工作台' }])
  })

  it('toggles the sidebar and drawer', () => {
    const shell = freshStore()

    shell.toggleCollapsed()
    expect(shell.collapsed).toBe(true)
    shell.toggleCollapsed()
    expect(shell.collapsed).toBe(false)

    shell.toggleDrawer()
    expect(shell.drawerOpen).toBe(true)
    shell.closeDrawer()
    expect(shell.drawerOpen).toBe(false)
  })
})
