import { describe, expect, it } from 'vitest'

import router, { constantRoutes } from '@/router'
import { ROUTE_GROUP_ENTRIES } from '@/router/routes'
import { PATHS } from '@/router/paths'

describe('route table', () => {
  it('keeps child paths relative to their parent', () => {
    const work = constantRoutes.find((route) => route.path === PATHS.WORK)
    expect(work.children[0].path).toBe('start')
    expect(work.children.map((child) => child.path)).toContain('pending/:id/:taskId')
    expect(work.redirect).toBe(PATHS.WORK_PENDING)
  })

  it('declares group membership for every visible menu route', () => {
    const paths = ROUTE_GROUP_ENTRIES.map((entry) => entry.path)

    expect(new Set(paths)).toEqual(
      new Set([
        PATHS.WORK,
        PATHS.WORK_START,
        PATHS.WORK_START_APPLY,
        PATHS.WORK_PENDING,
        PATHS.WORK_PENDING_DETAIL,
        PATHS.WORK_PROCESSED,
        PATHS.WORK_PROCESSED_DETAIL,
        PATHS.WORK_STARTED,
        PATHS.WORK_STARTED_DETAIL,
        PATHS.WORK_COPIED,
        PATHS.WORK_COPIED_DETAIL,
        PATHS.WORK_PROFILE,
        PATHS.SYSTEM,
        PATHS.SYSTEM_SYS_USER,
        PATHS.SYSTEM_SYS_ROLE,
        PATHS.SYSTEM_SYS_MENU,
        PATHS.SYSTEM_ASSIGN_AUTH,
        PATHS.PROCESS_SET,
        PATHS.PROCESS_SET_PROCESS_TYPE,
        PATHS.PROCESS_SET_PROCESS_TEMPLATE,
        PATHS.PROCESS_SET_TEMPLATE_SET,
        PATHS.PROCESS_MGR,
        PATHS.PROCESS_MGR_PROCESS,
        PATHS.PROCESS_MGR_PROCESS_DETAIL,
      ]),
    )
    expect(
      ROUTE_GROUP_ENTRIES.filter((entry) => entry.group === 'work').every((entry) =>
        entry.path.startsWith(PATHS.WORK),
      ),
    ).toBe(true)
  })

  it('remembers which parent a hidden page belongs to', () => {
    const assignAuth = ROUTE_GROUP_ENTRIES.find((entry) => entry.path === PATHS.SYSTEM_ASSIGN_AUTH)
    const templateSet = ROUTE_GROUP_ENTRIES.find(
      (entry) => entry.path === PATHS.PROCESS_SET_TEMPLATE_SET,
    )

    expect(assignAuth.hiddenParentPath).toBe(PATHS.SYSTEM_SYS_ROLE)
    expect(templateSet.hiddenParentPath).toBe(PATHS.PROCESS_SET_PROCESS_TEMPLATE)
  })

  it('installs the constant routes on the singleton router', () => {
    expect(router.options.routes).toHaveLength(constantRoutes.length)
    expect(router.getRoutes().some((route) => route.path === PATHS.LOGIN)).toBe(true)
    expect(router.options.scrollBehavior()).toEqual({ top: 0 })
  })

  it('resolves every lazily loaded route component', async () => {
    const loaders = []
    const walk = (routes) => {
      for (const route of routes) {
        if (typeof route.component === 'function') loaders.push(route.component)
        if (route.children) walk(route.children)
      }
    }
    walk(constantRoutes)

    expect(loaders.length).toBeGreaterThan(10)
    const loaded = await Promise.all(loaders.map((load) => load()))
    loaded.forEach((module) => expect(module.default).toBeTruthy())
  })
})
