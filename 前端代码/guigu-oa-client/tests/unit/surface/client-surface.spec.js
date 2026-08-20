import { resolveClientSurface } from '@/surface/client-surface'

const SAMPLE_CONFIG_MENUS = [
  {
    path: '/system',
    meta: { title: '组织与权限' },
    children: [
      { path: 'sysUser', meta: { title: '用户管理' } },
      { path: 'sysRole', meta: { title: '角色管理' } },
      { path: 'assignAuth', meta: { title: '菜单授权' }, hidden: true }
    ]
  },
  {
    path: '/processSet',
    meta: { title: '审批设置' },
    children: [
      { path: 'processType', meta: { title: '审批类型' } },
      { path: 'processTemplate', meta: { title: '审批模板' } },
      { path: 'templateSet', meta: { title: '审批模板设置' }, hidden: true }
    ]
  },
  {
    path: '/processMgr',
    meta: { title: '审批查询' },
    children: [
      { path: 'process', meta: { title: '审批实例查询' } }
    ]
  }
]

describe('统一客户端表面', () => {
  it('login page has no 菜单组 and does not show the surface', () => {
    const surface = resolveClientSurface({
      path: '/login',
      viewportWidth: 1200,
      hasToken: false,
      menus: [],
      configEntryAuthorized: false
    })

    expect(surface.showSurface).toBe(false)
    expect(surface.menuGroup).toBeNull()
    expect(surface.density).toBeNull()
    expect(surface.occupancy).toEqual({ action: 'allow' })
  })

  it('a 办事 entry belongs to the 办事 菜单组, uses 办事 density, and allows occupancy', () => {
    const surface = resolveClientSurface({
      path: '/work/pending',
      viewportWidth: 375,
      hasToken: true,
      menus: SAMPLE_CONFIG_MENUS,
      configEntryAuthorized: false
    })

    expect(surface.showSurface).toBe(true)
    expect(surface.menuGroup).toBe('work')
    expect(surface.density).toBe('work')
    expect(surface.occupancy).toEqual({ action: 'allow' })
  })

  it('a 配置 entry belongs to the 配置 菜单组, uses 配置 density, and allows occupancy when authorized on desktop', () => {
    const surface = resolveClientSurface({
      path: '/system/sysUser',
      viewportWidth: 1200,
      hasToken: true,
      menus: SAMPLE_CONFIG_MENUS,
      configEntryAuthorized: true
    })

    expect(surface.showSurface).toBe(true)
    expect(surface.menuGroup).toBe('config')
    expect(surface.density).toBe('config')
    expect(surface.occupancy).toEqual({ action: 'allow' })
  })

  it('assigns 菜单组 from the 菜单 tree entry rather than an old path prefix', () => {
    const menus = [{
      path: '/office',
      meta: { title: '办公配置' },
      children: [
        { path: 'users', meta: { title: '用户' } }
      ]
    }]

    expect(resolveClientSurface({
      path: '/office/users',
      viewportWidth: 1200,
      hasToken: true,
      menus,
      configEntryAuthorized: true
    }).menuGroup).toBe('config')
  })

  it('hidden 菜单授权 and 审批模板设置 belong to the same 菜单组 as their parent entries', () => {
    expect(resolveClientSurface({
      path: '/system/assignAuth',
      viewportWidth: 1200,
      hasToken: true,
      menus: SAMPLE_CONFIG_MENUS,
      configEntryAuthorized: true
    }).menuGroup).toBe('config')

    expect(resolveClientSurface({
      path: '/processSet/templateSet',
      viewportWidth: 1200,
      hasToken: true,
      menus: SAMPLE_CONFIG_MENUS,
      configEntryAuthorized: true
    }).menuGroup).toBe('config')
  })

  it('详情 opened from 审批实例查询 belongs to 配置, has no 办事列表视角, and returns to 审批实例查询', () => {
    const surface = resolveClientSurface({
      path: '/processMgr/process/9',
      viewportWidth: 1200,
      hasToken: true,
      menus: SAMPLE_CONFIG_MENUS,
      configEntryAuthorized: true
    })

    expect(surface.menuGroup).toBe('config')
    expect(surface.hasWorkListPerspective).toBe(false)
    expect(surface.returnPath).toBe('/processMgr/process')
  })

  it('a logged-in user entering a 配置 entry on mobile occupancy-lands on 待处理, not an intercept page', () => {
    const surface = resolveClientSurface({
      path: '/system/sysUser',
      viewportWidth: 375,
      hasToken: true,
      menus: SAMPLE_CONFIG_MENUS,
      configEntryAuthorized: true
    })

    expect(surface.occupancy).toEqual({
      action: 'redirect',
      to: '/work/pending'
    })
    expect(surface.occupancy.to).not.toBe('/mobile-config-block')
  })

  it('a logged-in user entering an unauthorized 配置 entry occupancy-lands on 待处理', () => {
    const surface = resolveClientSurface({
      path: '/system/sysUser',
      viewportWidth: 1200,
      hasToken: true,
      menus: [],
      configEntryAuthorized: false
    })

    expect(surface.occupancy).toEqual({
      action: 'redirect',
      to: '/work/pending'
    })
  })

  it('occupancy also lands on 待处理 when already on a 配置 page at mobile viewport', () => {
    const surface = resolveClientSurface({
      path: '/processSet/processType',
      viewportWidth: 768,
      hasToken: true,
      menus: SAMPLE_CONFIG_MENUS,
      configEntryAuthorized: true
    })

    expect(surface.occupancy).toEqual({
      action: 'redirect',
      to: '/work/pending'
    })
  })
})
