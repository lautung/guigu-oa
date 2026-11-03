import { describe, expect, it } from 'vitest'

import router, { constantRoutes } from '@/router'
import { ROUTE_GROUP_ENTRIES } from '@/router/routes'
import { PATHS } from '@/router/paths'
import { getDefaultPostLoginRoute } from '@/navigation/constants'

describe('route table', () => {
  const shell = constantRoutes.find((route) => route.path === '/')

  it('keeps /login outside the single authenticated shell', () => {
    expect(constantRoutes.filter((route) => route.path === PATHS.LOGIN)).toHaveLength(1)
    expect(shell.children.some((route) => route.path === PATHS.LOGIN)).toBe(false)
    expect(shell.redirect).toBe(getDefaultPostLoginRoute())
    expect(shell.children.map((route) => route.path)).toEqual([
      PATHS.WORKBENCH,
      PATHS.MONITOR,
      PATHS.MONITOR_ONLINE,
      PATHS.MONITOR_SERVER,
      PATHS.MONITOR_DATA,
      PATHS.MONITOR_CACHE,
      PATHS.MONITOR_CACHE_LIST,
      PATHS.TOOL,
      PATHS.WORK,
      PATHS.NOTICE,
      PATHS.SYSTEM,
      PATHS.PROCESS_SET,
      PATHS.PROCESS_MGR,
    ])
  })

  it('registers 系统监控 and 系统工具 as empty placeholder pages', () => {
    const monitor = shell.children.find((route) => route.path === PATHS.MONITOR)
    const tool = shell.children.find((route) => route.path === PATHS.TOOL)

    expect(monitor.name).toBe('Monitor')
    expect(monitor.meta).toEqual({ title: '系统监控' })
    expect(tool.name).toBe('Tool')
    expect(tool.meta).toEqual({ title: '系统工具' })
    expect(monitor.meta.menuGroup).toBeUndefined()
    expect(tool.meta.menuGroup).toBeUndefined()
  })

  it('registers 在线用户 as the first grant-checked monitor page', () => {
    const online = shell.children.find((route) => route.path === PATHS.MONITOR_ONLINE)

    expect(online.name).toBe('MonitorOnline')
    expect(online.meta).toEqual({ title: '在线用户', menuGroup: 'config' })
  })

  it('registers 服务监控 and 数据监控 as grant-checked monitor pages (#240)', () => {
    const server = shell.children.find((route) => route.path === PATHS.MONITOR_SERVER)
    const data = shell.children.find((route) => route.path === PATHS.MONITOR_DATA)

    expect(server.name).toBe('MonitorServer')
    expect(server.meta).toEqual({ title: '服务监控', menuGroup: 'config' })
    expect(data.name).toBe('MonitorData')
    expect(data.meta).toEqual({ title: '数据监控', menuGroup: 'config' })
  })

  it('registers 缓存监控 and 缓存列表 as grant-checked monitor pages (#241)', () => {
    const cache = shell.children.find((route) => route.path === PATHS.MONITOR_CACHE)
    const cacheList = shell.children.find((route) => route.path === PATHS.MONITOR_CACHE_LIST)

    expect(cache.name).toBe('MonitorCache')
    expect(cache.meta).toEqual({ title: '缓存监控', menuGroup: 'config' })
    expect(cacheList.name).toBe('MonitorCacheList')
    expect(cacheList.meta).toEqual({ title: '缓存列表', menuGroup: 'config' })
  })

  it('keeps child paths relative to their parent', () => {
    const work = shell.children.find((route) => route.path === PATHS.WORK)
    expect(work.children[0].path).toBe('start')
    expect(work.children.map((child) => child.path)).toContain('pending/:id/:taskId')
    expect(work.redirect).toBe(PATHS.WORK_PENDING)
  })

  it('registers the workbench route as the default landing', () => {
    const workbench = shell.children.find((route) => route.path === PATHS.WORKBENCH)

    expect(workbench.name).toBe('Workbench')
    expect(workbench.meta.title).toBe('工作台')
    expect(workbench.meta.menuGroup).toBeUndefined()
    expect(shell.redirect).toBe(getDefaultPostLoginRoute())
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
        PATHS.MONITOR_ONLINE,
        PATHS.MONITOR_SERVER,
        PATHS.MONITOR_DATA,
        PATHS.MONITOR_CACHE,
        PATHS.MONITOR_CACHE_LIST,
        PATHS.SYSTEM_SYS_USER,
        PATHS.SYSTEM_SYS_ROLE,
        PATHS.SYSTEM_SYS_MENU,
        PATHS.SYSTEM_LOG_OPER,
        PATHS.SYSTEM_LOG_LOGIN,
        PATHS.SYSTEM_ASSIGN_AUTH,
        PATHS.SYSTEM_NOTICE,
        PATHS.SYSTEM_NOTICE_EDIT,
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

  // 全量并行时动态导入几十个组件会逼近默认线；#229 的工作台换成真实页面后又重了一档，
  // 单跑 ~7s、verify 并行时会超过 15s，所以留到 30s。
  it('resolves every lazily loaded route component', { timeout: 30_000 }, async () => {
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
