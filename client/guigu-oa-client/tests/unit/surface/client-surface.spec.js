import { describe, expect, it } from 'vitest'
import { resolveClientSurface } from '@/surface/client-surface'

const SAMPLE_CONFIG_MENUS = [
  { path: '/system', title: '组织与权限', type: 0, sort: 1 },
  { path: '/system/sysUser', parentPath: '/system', title: '用户管理', type: 1, sort: 1 },
  { path: '/system/sysRole', parentPath: '/system', title: '角色管理', type: 1, sort: 2 },
  {
    path: '/system/assignAuth',
    parentPath: '/system/sysRole',
    title: '菜单授权',
    type: 1,
    hidden: true,
    sort: 3,
  },
  { path: '/system/processMgr', title: '审批管理', type: 0, sort: 2 },
  {
    path: '/system/processMgr/process',
    parentPath: '/system/processMgr',
    title: '审批实例查询',
    type: 1,
    sort: 1,
  },
]

const ONLY_USER_MENUS = [
  { path: '/system', title: '组织与权限', type: 0, sort: 1 },
  { path: '/system/sysUser', parentPath: '/system', title: '用户管理', type: 1, sort: 1 },
]

describe('外壳表面判定', () => {
  it('login page has no surface', () => {
    const surface = resolveClientSurface({
      path: '/login',
      hasToken: false,
      menus: [],
    })
    const trailingSlash = resolveClientSurface({
      path: '/login/',
      hasToken: false,
      menus: [],
    })

    expect(surface.showSurface).toBe(false)
    expect(surface.menuGroup).toBeNull()
    expect(surface.density).toBeNull()
    expect(surface.occupancy).toEqual({ action: 'allow' })
    expect(trailingSlash).toEqual(surface)
  })

  it('a tokenless config path keeps occupancy allow without showing the surface', () => {
    const surface = resolveClientSurface({
      path: '/system/sysUser',
      hasToken: false,
      menus: [],
    })

    expect(surface.showSurface).toBe(false)
    expect(surface.menuGroup).toBe('config')
    expect(surface.occupancy).toEqual({ action: 'allow' })
  })

  it('uses work density for 办事 and wide density for 配置', () => {
    const work = resolveClientSurface({
      path: '/work/pending',
      hasToken: true,
      menus: SAMPLE_CONFIG_MENUS,
    })
    const config = resolveClientSurface({
      path: '/system/sysUser',
      hasToken: true,
      menus: SAMPLE_CONFIG_MENUS,
    })

    expect(work).toMatchObject({ showSurface: true, menuGroup: 'work', density: 'constrained' })
    expect(work.occupancy).toEqual({ action: 'allow' })
    expect(config).toMatchObject({ showSurface: true, menuGroup: 'config', density: 'wide' })
    expect(config.occupancy).toEqual({ action: 'allow' })
  })

  it('hidden pages follow their visible parent', () => {
    const assignAuth = resolveClientSurface({
      path: '/system/assignAuth',
      hasToken: true,
      menus: SAMPLE_CONFIG_MENUS,
    })
    const absent = resolveClientSurface({
      path: '/system/assignAuth',
      hasToken: true,
      menus: ONLY_USER_MENUS,
    })

    expect(assignAuth.menuGroup).toBe('config')
    expect(assignAuth.density).toBe('wide')
    expect(assignAuth.occupancy).toEqual({ action: 'allow' })
    expect(absent.occupancy).toEqual({ action: 'redirect', to: '/work/pending' })
  })

  it('keeps a granted config page open regardless of viewport width', () => {
    const surface = resolveClientSurface({
      path: '/system/sysUser',
      hasToken: true,
      menus: ONLY_USER_MENUS,
    })

    expect(surface.occupancy).toEqual({ action: 'allow' })
  })

  it('the same 审批详情 takes its group and return path from the entry it was opened from', () => {
    const fromWork = resolveClientSurface({
      path: '/work/pending/9/task-1',
      hasToken: true,
      menus: SAMPLE_CONFIG_MENUS,
    })
    const fromConfig = resolveClientSurface({
      path: '/system/processMgr/process/9',
      hasToken: true,
      menus: SAMPLE_CONFIG_MENUS,
    })

    expect(fromWork.menuGroup).toBe('work')
    expect(fromWork.returnPath).toBe('/work/pending')
    expect(fromWork.hasWorkListPerspective).toBe(true)
    expect(fromConfig.menuGroup).toBe('config')
    expect(fromConfig.returnPath).toBe('/system/processMgr/process')
    expect(fromConfig.hasWorkListPerspective).toBe(false)
  })

  it('redirects an ungranted config page to the default landing', () => {
    const surface = resolveClientSurface({
      path: '/system/sysRole',
      hasToken: true,
      menus: ONLY_USER_MENUS,
    })

    expect(surface.occupancy).toEqual({ action: 'redirect', to: '/work/pending' })
  })
})
