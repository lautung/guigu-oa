import { constantRoutes } from '@/router'
import { createViewportOccupancyHandler, resolveClientSurface } from '@/surface/client-surface'

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
      menus: []
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
      menus: SAMPLE_CONFIG_MENUS
    })

    expect(surface.showSurface).toBe(true)
    expect(surface.menuGroup).toBe('work')
    expect(surface.density).toBe('constrained')
    expect(surface.occupancy).toEqual({ action: 'allow' })
  })

  it('a 配置 entry belongs to the 配置 菜单组, uses 配置 density, and allows occupancy on desktop', () => {
    const surface = resolveClientSurface({
      path: '/system/sysUser',
      viewportWidth: 1200,
      hasToken: true,
      menus: SAMPLE_CONFIG_MENUS
    })

    expect(surface.showSurface).toBe(true)
    expect(surface.menuGroup).toBe('config')
    expect(surface.density).toBe('wide')
    expect(surface.occupancy).toEqual({ action: 'allow' })
  })

  it('occupancy-lands on 待处理 when 角色管理 was not granted', () => {
    expect(resolveClientSurface({
      path: '/system/sysRole',
      viewportWidth: 1200,
      hasToken: true,
      menus: [{
        path: '/system',
        meta: { title: '组织与权限' },
        children: [
          { path: 'sysUser', meta: { title: '用户管理' } }
        ]
      }]
    }).occupancy).toEqual({
      action: 'redirect',
      to: '/work/pending'
    })
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
      menus
    }).menuGroup).toBe('config')
  })

  it('hidden 菜单授权 and 审批模板设置 belong to the same 菜单组 as their parent entries', () => {
    expect(resolveClientSurface({
      path: '/system/assignAuth',
      viewportWidth: 1200,
      hasToken: true,
      menus: SAMPLE_CONFIG_MENUS
    }).menuGroup).toBe('config')

    expect(resolveClientSurface({
      path: '/processSet/templateSet',
      viewportWidth: 1200,
      hasToken: true,
      menus: SAMPLE_CONFIG_MENUS
    }).menuGroup).toBe('config')
  })

  it('详情 opened from 审批实例查询 belongs to 配置, has no 办事列表视角, and returns to 审批实例查询', () => {
    const surface = resolveClientSurface({
      path: '/processMgr/process/9',
      viewportWidth: 1200,
      hasToken: true,
      menus: SAMPLE_CONFIG_MENUS
    })

    expect(surface.menuGroup).toBe('config')
    expect(surface.density).toBe('wide')
    expect(surface.hasWorkListPerspective).toBe(false)
    expect(surface.returnPath).toBe('/processMgr/process')
  })

  it('the same 审批详情 uses 办事 density from 待处理 and 配置 density from 审批实例查询', () => {
    const fromWork = resolveClientSurface({
      path: '/work/pending/9/task-1',
      viewportWidth: 1200,
      hasToken: true,
      menus: SAMPLE_CONFIG_MENUS
    })
    const fromConfig = resolveClientSurface({
      path: '/processMgr/process/9',
      viewportWidth: 1200,
      hasToken: true,
      menus: SAMPLE_CONFIG_MENUS
    })

    expect(fromWork.menuGroup).toBe('work')
    expect(fromWork.density).toBe('constrained')
    expect(fromConfig.menuGroup).toBe('config')
    expect(fromConfig.density).toBe('wide')
  })

  it('a logged-in user entering a 配置 entry on mobile occupancy-lands on 待处理', () => {
    const surface = resolveClientSurface({
      path: '/system/sysUser',
      viewportWidth: 375,
      hasToken: true,
      menus: SAMPLE_CONFIG_MENUS
    })

    expect(surface.occupancy).toEqual({
      action: 'redirect',
      to: '/work/pending'
    })
  })

  it('a logged-in user entering an ungranted 配置 entry occupancy-lands on 待处理', () => {
    const surface = resolveClientSurface({
      path: '/system/sysUser',
      viewportWidth: 1200,
      hasToken: true,
      menus: []
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
      menus: SAMPLE_CONFIG_MENUS
    })

    expect(surface.occupancy).toEqual({
      action: 'redirect',
      to: '/work/pending'
    })
  })

  it('shrinking onto 配置 occupancy-lands on 待处理 via the same occupancy as entering, not the 配置 URL', () => {
    const surfaceInput = {
      path: '/system/sysUser',
      hasToken: true,
      menus: SAMPLE_CONFIG_MENUS
    }
    const entering = resolveClientSurface({
      ...surfaceInput,
      viewportWidth: 375
    }).occupancy
    const goTo = vi.fn()
    const handleViewportWidth = createViewportOccupancyHandler({
      getSurfaceInput: () => surfaceInput,
      goTo
    })

    handleViewportWidth(1200)
    expect(goTo).not.toHaveBeenCalled()

    handleViewportWidth(375)
    expect(goTo).toHaveBeenCalledTimes(1)
    expect(goTo).toHaveBeenCalledWith(entering.to)
    expect(goTo).toHaveBeenCalledWith('/work/pending')
    expect(goTo.mock.calls[0][0]).not.toBe('/system/sysUser')
  })

  it('shrinking onto a hidden 配置 page or 审批实例查询详情 occupancy-lands on 待处理', () => {
    const goTo = vi.fn()

    createViewportOccupancyHandler({
      getSurfaceInput: () => ({
        path: '/system/assignAuth',
        hasToken: true,
        menus: SAMPLE_CONFIG_MENUS
      }),
      goTo
    })(375)

    createViewportOccupancyHandler({
      getSurfaceInput: () => ({
        path: '/processMgr/process/9',
        hasToken: true,
        menus: SAMPLE_CONFIG_MENUS
      }),
      goTo
    })(375)

    expect(goTo).toHaveBeenCalledTimes(2)
    expect(goTo).toHaveBeenNthCalledWith(1, '/work/pending')
    expect(goTo).toHaveBeenNthCalledWith(2, '/work/pending')
  })

  it('shrinking onto 办事 does not leave the current page', () => {
    const goTo = vi.fn()
    createViewportOccupancyHandler({
      getSurfaceInput: () => ({
        path: '/work/pending',
        hasToken: true,
        menus: SAMPLE_CONFIG_MENUS
      }),
      goTo
    })(375)

    expect(goTo).not.toHaveBeenCalled()
  })

  it('办事入口 and 配置入口 hang on the same surface host', () => {
    const hosted = constantRoutes.filter(route => route.children && route.children.length)
    expect(hosted.length).toBeGreaterThan(1)
    hosted.forEach(route => {
      expect(route.component).toBe(hosted[0].component)
    })
  })
})
