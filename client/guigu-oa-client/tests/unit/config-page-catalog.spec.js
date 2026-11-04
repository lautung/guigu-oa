import { describe, expect, it } from 'vitest'
import { ROUTE_GROUP_ENTRIES, constantRoutes } from '@/router/routes'
import { PATHS } from '@/router/paths'
import { createMemoryHistory, createRouter } from 'vue-router'

describe('路由组归属与配置页', () => {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: constantRoutes,
  })
  const routes = router.getRoutes()
  const groupPaths = ROUTE_GROUP_ENTRIES.map((entry) => entry.path)

  it('groups every declared route that belongs to a menu group', () => {
    const ungrouped = routes
      .filter((route) => route.meta.menuGroup && !groupPaths.includes(route.path))
      .map((route) => route.path)

    expect(ungrouped).toEqual([])
  })

  it('resolves every concrete group entry to a declared route', () => {
    const unresolved = groupPaths
      .filter((path) => !path.includes(':'))
      .filter((path) => router.resolve(path).matched.length === 0)

    expect(unresolved).toEqual([])
  })

  it('keeps hidden 配置 pages pointing at a visible 配置 parent', () => {
    const hidden = ROUTE_GROUP_ENTRIES.filter((entry) => entry.hiddenParentPath)

    expect(hidden.map((entry) => entry.path).sort()).toEqual([
      '/system/assignAuth',
      '/system/processSet/templateSet',
    ])
    hidden.forEach((entry) => {
      const parent = router.resolve(entry.hiddenParentPath)
      expect(parent.matched.length).toBeGreaterThan(0)
      expect(parent.meta.menuGroup).toBe('config')
    })
  })

  it('classifies 办事 and 配置 routes from the route table itself', () => {
    const byPath = Object.fromEntries(ROUTE_GROUP_ENTRIES.map((entry) => [entry.path, entry.group]))

    expect(byPath['/work/pending']).toBe('work')
    expect(byPath['/profile']).toBeUndefined()
    expect(byPath['/system/sysUser']).toBe('config')
    expect(byPath['/system/processMgr/process']).toBe('config')
  })

  it('routes every authenticated page through the one shell root', () => {
    const shell = constantRoutes.find((route) => route.path === '/')
    const hosted = constantRoutes.filter((route) => route.path !== PATHS.LOGIN && route.children)

    expect(hosted).toHaveLength(1)
    expect(hosted[0]).toBe(shell)
    expect(shell.children.map((route) => route.path)).toContain(PATHS.WORKBENCH)
    expect(shell.children.map((route) => route.path)).toContain(PATHS.SYSTEM)
    expect(shell.children.map((route) => route.path)).toContain(PATHS.NOTICE)
  })
})
