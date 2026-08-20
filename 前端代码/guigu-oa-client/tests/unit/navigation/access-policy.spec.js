import {
  AUTH_WHITELIST,
  DESKTOP_CONFIG_HINT,
  MOBILE_BREAKPOINT,
  ROUTES,
  WORK_BUILTIN_ITEMS,
  buildSidebarTree,
  filterConfigMenus,
  getDefaultPostLoginRoute,
  hasConfigPermission,
  isMobileViewport,
  resolveAuthGuard,
  resolveNavigationGuard
} from '@/navigation/access-policy'

const SAMPLE_CONFIG_MENUS = [
  {
    path: '/system',
    component: 'Layout',
    meta: { title: '组织与权限' },
    children: [
      { path: 'sysUser', component: 'system/sysUser/list', meta: { title: '用户管理' } },
      { path: 'sysRole', component: 'system/sysRole/list', meta: { title: '角色管理' } },
      { path: 'sysMenu', component: 'system/sysMenu/list', meta: { title: '菜单管理' } },
      { path: 'assignAuth', component: 'system/sysRole/assignAuth', meta: { title: '菜单授权' }, hidden: true }
    ]
  },
  {
    path: '/processSet',
    component: 'Layout',
    meta: { title: '审批设置' },
    children: [
      { path: 'processType', component: 'processSet/processType/list', meta: { title: '审批类型' } },
      { path: 'processTemplate', component: 'processSet/processTemplate/list', meta: { title: '审批模板' } }
    ]
  },
  {
    path: '/wechat',
    component: 'Layout',
    meta: { title: '公众号菜单' },
    children: [
      { path: 'menu', component: 'wechat/menu/list', meta: { title: '菜单列表' } }
    ]
  },
  {
    path: '/dashboard',
    component: 'dashboard/index',
    meta: { title: 'Dashboard' }
  }
]

describe('navigation/access-policy', () => {
  describe('getDefaultPostLoginRoute', () => {
    it('returns pending as the default post-login landing', () => {
      expect(getDefaultPostLoginRoute()).toBe(ROUTES.PENDING)
    })
  })

  describe('hasConfigPermission', () => {
    it('returns false when menus are empty', () => {
      expect(hasConfigPermission([])).toBe(false)
    })

    it('returns false when only excluded menus are present', () => {
      expect(hasConfigPermission([
        { path: '/wechat', children: [{ path: 'menu', component: 'wechat/menu/list', meta: { title: '菜单' } }] },
        { path: '/dashboard', component: 'dashboard/index', meta: { title: 'Dashboard' } }
      ])).toBe(false)
    })

    it('returns true when at least one config menu remains after filtering', () => {
      expect(hasConfigPermission(SAMPLE_CONFIG_MENUS)).toBe(true)
    })
  })

  describe('filterConfigMenus', () => {
    it('removes wechat and dashboard entries', () => {
      const filtered = filterConfigMenus(SAMPLE_CONFIG_MENUS)
      const paths = filtered.map(item => item.path)

      expect(paths).toContain('/system')
      expect(paths).toContain('/processSet')
      expect(paths).not.toContain('/wechat')
      expect(paths).not.toContain('/dashboard')
    })

    it('removes Activiti online iframe designer entries', () => {
      const filtered = filterConfigMenus([
        {
          path: '/processSet',
          meta: { title: '审批设置' },
          children: [
            { path: 'processTemplate', meta: { title: '审批模板' } },
            { path: 'onlineProcessSet', component: 'processSet/processTemplate/onlineProcessSet', meta: { title: '在线流程设置' } }
          ]
        }
      ])

      const childPaths = (filtered[0].children || []).map(item => item.path)
      expect(childPaths).toContain('/processSet/processTemplate')
      expect(childPaths).not.toContain('/processSet/onlineProcessSet')
    })

  })

  describe('buildSidebarTree', () => {
    it('always includes work items in correct order', () => {
      const tree = buildSidebarTree({ menus: [], viewportWidth: 1200 })
      const workGroup = tree[0]

      expect(tree).toHaveLength(1)
      expect(workGroup.key).toBe('work')
      expect(workGroup.items.map(item => item.title)).toEqual(WORK_BUILTIN_ITEMS.map(item => item.title))
      expect(workGroup.items.map(item => item.path)).toEqual(WORK_BUILTIN_ITEMS.map(item => item.path))
    })

    it('hides config group without config permission', () => {
      const tree = buildSidebarTree({ menus: [], viewportWidth: 1200 })
      expect(tree.some(group => group.key === 'config')).toBe(false)
    })

    it('merges config group below work when user has config permission', () => {
      const tree = buildSidebarTree({ menus: SAMPLE_CONFIG_MENUS, viewportWidth: 1200 })

      expect(tree).toHaveLength(2)
      expect(tree[0].key).toBe('work')
      expect(tree[1].key).toBe('config')
      expect(tree[1].items.length).toBeGreaterThan(0)
    })

    it('collapses config group on mobile with desktop hint', () => {
      const tree = buildSidebarTree({ menus: SAMPLE_CONFIG_MENUS, viewportWidth: 375 })
      const configGroup = tree.find(group => group.key === 'config')

      expect(configGroup.collapsed).toBe(true)
      expect(configGroup.hint).toBe(DESKTOP_CONFIG_HINT)
    })

    it('keeps config group expanded on desktop', () => {
      const tree = buildSidebarTree({ menus: SAMPLE_CONFIG_MENUS, viewportWidth: 1200 })
      const configGroup = tree.find(group => group.key === 'config')

      expect(configGroup.collapsed).toBe(false)
      expect(configGroup.hint).toBeNull()
    })
  })

  describe('resolveAuthGuard', () => {
    it('redirects unauthenticated users to login with return path', () => {
      expect(resolveAuthGuard({ path: ROUTES.PENDING, hasToken: false })).toEqual({
        action: 'redirect',
        to: `${ROUTES.LOGIN}?redirect=${encodeURIComponent(ROUTES.PENDING)}`
      })
    })

    it('allows login page without token', () => {
      expect(resolveAuthGuard({ path: ROUTES.LOGIN, hasToken: false })).toEqual({
        action: 'allow'
      })
    })

    it('redirects authenticated users away from login to pending', () => {
      expect(resolveAuthGuard({ path: ROUTES.LOGIN, hasToken: true })).toEqual({
        action: 'redirect',
        to: ROUTES.PENDING
      })
    })

    it('only whitelists the login page', () => {
      expect(AUTH_WHITELIST).toEqual([ROUTES.LOGIN])
    })
  })

  describe('resolveNavigationGuard', () => {
    it('redirects mobile 配置 navigation to 待处理', () => {
      expect(resolveNavigationGuard({
        path: '/system/sysUser',
        hasToken: true,
        viewportWidth: 375,
        menus: SAMPLE_CONFIG_MENUS
      })).toEqual({
        action: 'redirect',
        to: ROUTES.PENDING
      })
    })

    it('redirects config routes when user lacks config permission', () => {
      expect(resolveNavigationGuard({
        path: '/system/sysUser',
        hasToken: true,
        viewportWidth: 1200,
        menus: []
      })).toEqual({
        action: 'redirect',
        to: ROUTES.PENDING
      })
    })

    it('allows desktop config routes for authorized users', () => {
      expect(resolveNavigationGuard({
        path: '/system/sysUser',
        hasToken: true,
        viewportWidth: 1200,
        menus: SAMPLE_CONFIG_MENUS
      })).toEqual({
        action: 'allow'
      })
    })
  })

  describe('isMobileViewport', () => {
    it('uses the mobile breakpoint', () => {
      expect(isMobileViewport(MOBILE_BREAKPOINT)).toBe(true)
      expect(isMobileViewport(MOBILE_BREAKPOINT + 1)).toBe(false)
    })
  })
})
