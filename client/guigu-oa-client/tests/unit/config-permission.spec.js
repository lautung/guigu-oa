import { configPermission } from '@/config-permission'
import { MOBILE_BREAKPOINT } from '@/navigation/constants'

const DESKTOP_WIDTH = 1200
const MOBILE_WIDTH = 375

const ONLY_USER_MENUS = [
  {
    path: '/system',
    component: 'Layout',
    meta: { title: '组织与权限' },
    children: [
      { path: 'sysUser', component: 'system/sysUser/list', meta: { title: '用户管理' } }
    ]
  }
]

const ONLY_ROLE_MENUS = [
  {
    path: '/system',
    component: 'Layout',
    meta: { title: '组织与权限' },
    children: [
      { path: 'sysRole', component: 'system/sysRole/list', meta: { title: '角色管理' } }
    ]
  }
]

const ONLY_PROCESS_TYPE_MENUS = [
  {
    path: '/processSet',
    component: 'Layout',
    meta: { title: '审批设置' },
    children: [
      { path: 'processType', component: 'processSet/processType/list', meta: { title: '审批类型' } }
    ]
  }
]

const PROCESS_TEMPLATE_MENUS = [
  {
    path: '/processSet',
    component: 'Layout',
    meta: { title: '审批设置' },
    children: [
      { path: 'processTemplate', component: 'processSet/processTemplate/list', meta: { title: '审批模板' } }
    ]
  }
]

const PROCESS_INSTANCE_MENUS = [
  {
    path: '/processMgr',
    component: 'Layout',
    meta: { title: '审批查询' },
    children: [
      { path: 'process', component: 'processMgr/process/list', meta: { title: '审批实例查询' } }
    ]
  }
]

const CUSTOM_CONFIG_MENUS = [
  {
    path: '/office',
    component: 'Layout',
    meta: { title: '办公配置' },
    children: [
      { path: 'users', component: 'office/users/list', meta: { title: '用户' } }
    ]
  }
]

function canOccupy(permission, path, viewportWidth = DESKTOP_WIDTH) {
  return permission.canOccupy({ path, viewportWidth })
}

function flattenTitles(items = []) {
  return items.reduce((titles, item) => {
    titles.push(item.title)
    if (item.children) {
      titles.push(...flattenTitles(item.children))
    }
    return titles
  }, [])
}

describe('配置权限', () => {
  describe('hasConfigPermission 与 grantedConfigMenus', () => {
    it('has no 配置权限 when the menu tree is empty', () => {
      const permission = configPermission([])

      expect(permission.hasConfigPermission).toBe(false)
      expect(permission.grantedConfigMenus).toEqual([])
    })

    it('has no 配置权限 when only 办事 menus are granted', () => {
      const permission = configPermission([
        { path: '/work/pending', meta: { title: '待处理' } }
      ])

      expect(permission.hasConfigPermission).toBe(false)
      expect(permission.grantedConfigMenus).toEqual([])
    })

    it('has 配置权限 when at least one 配置类菜单 is granted', () => {
      const permission = configPermission(ONLY_USER_MENUS)

      expect(permission.hasConfigPermission).toBe(true)
    })

    it('returns granted 配置类菜单 as 组织与权限 / 用户管理 when only 用户管理 is granted', () => {
      const permission = configPermission(ONLY_USER_MENUS)

      expect(flattenTitles(permission.grantedConfigMenus)).toEqual(['组织与权限', '用户管理'])
    })

    it('drops 部门 / 岗位 / 日志 / Dashboard / 公众号 / 在线流程设计器 from granted 配置类菜单', () => {
      const permission = configPermission([
        {
          path: '/system',
          component: 'Layout',
          meta: { title: '组织与权限' },
          children: [
            { path: 'sysUser', component: 'system/sysUser/list', meta: { title: '用户管理' } },
            { path: 'sysDept', component: 'system/sysDept/list', meta: { title: '部门管理' } },
            { path: 'sysPost', component: 'system/sysPost/list', meta: { title: '岗位管理' } },
            { path: 'sysOperLog', component: 'system/sysOperLog/list', meta: { title: '操作日志' } }
          ]
        },
        {
          path: '/processSet',
          component: 'Layout',
          meta: { title: '审批设置' },
          children: [
            { path: 'processTemplate', component: 'processSet/processTemplate/list', meta: { title: '审批模板' } },
            { path: 'onlineProcessSet', component: 'processSet/processTemplate/onlineProcessSet', meta: { title: '在线流程设计器' } }
          ]
        },
        { path: '/dashboard', component: 'dashboard/index', meta: { title: 'Dashboard' } },
        {
          path: '/wechat',
          component: 'Layout',
          meta: { title: '公众号菜单' },
          children: [
            { path: 'menu', component: 'wechat/menu/list', meta: { title: '菜单列表' } }
          ]
        }
      ])

      expect(flattenTitles(permission.grantedConfigMenus)).toEqual([
        '组织与权限',
        '用户管理',
        '审批设置',
        '审批模板'
      ])
    })

    it('does not list hidden 配置 pages among granted 配置类菜单', () => {
      const permission = configPermission([
        {
          path: '/system',
          component: 'Layout',
          meta: { title: '组织与权限' },
          children: [
            { path: 'sysRole', component: 'system/sysRole/list', meta: { title: '角色管理' } },
            { path: 'assignAuth', component: 'system/sysRole/assignAuth', meta: { title: '菜单授权' }, hidden: true }
          ]
        }
      ])

      expect(flattenTitles(permission.grantedConfigMenus)).toEqual(['组织与权限', '角色管理'])
    })

    it('omits a parent directory that has no grantable children', () => {
      const permission = configPermission([
        {
          path: '/system',
          component: 'Layout',
          meta: { title: '组织与权限' },
          children: [
            { path: 'sysDept', component: 'system/sysDept/list', meta: { title: '部门管理' } }
          ]
        }
      ])

      expect(permission.hasConfigPermission).toBe(false)
      expect(permission.grantedConfigMenus).toEqual([])
    })

    it('ignores button nodes when answering 配置权限', () => {
      const permission = configPermission([
        {
          path: '/system',
          meta: { title: '组织与权限' },
          children: [
            {
              path: 'sysUser',
              component: 'system/sysUser/list',
              meta: { title: '用户管理' },
              children: [
                { path: 'btn', perms: 'bnt.sysUser.add', type: 2, meta: { title: '添加' } }
              ]
            }
          ]
        }
      ])

      expect(permission.hasConfigPermission).toBe(true)
      expect(flattenTitles(permission.grantedConfigMenus)).toEqual(['组织与权限', '用户管理'])
    })

    it('does not treat 办事 menus as 配置权限', () => {
      const permission = configPermission([
        { path: '/work/pending', meta: { title: '待处理' } }
      ])

      expect(permission.hasConfigPermission).toBe(false)
    })
  })

  describe('canOccupy', () => {
    it('can occupy a granted 配置 page and its parent directory on desktop', () => {
      const permission = configPermission(ONLY_USER_MENUS)

      expect(canOccupy(permission, '/system/sysUser')).toBe(true)
      expect(canOccupy(permission, '/system')).toBe(true)
    })

    it('cannot occupy a 配置 page that was not granted', () => {
      const permission = configPermission(ONLY_USER_MENUS)

      expect(canOccupy(permission, '/system/sysRole')).toBe(false)
      expect(canOccupy(permission, '/system/sysMenu')).toBe(false)
      expect(canOccupy(permission, '/system/assignAuth')).toBe(false)
      expect(canOccupy(permission, '/processSet/processType')).toBe(false)
    })

    it('cannot occupy 菜单授权 when only 用户管理 is granted', () => {
      const permission = configPermission(ONLY_USER_MENUS)

      expect(canOccupy(permission, '/system/assignAuth')).toBe(false)
    })

    it('can occupy hidden 菜单授权 when 角色管理 is granted', () => {
      const permission = configPermission(ONLY_ROLE_MENUS)

      expect(canOccupy(permission, '/system/assignAuth')).toBe(true)
      expect(canOccupy(permission, '/system/assignAuth/9')).toBe(true)
    })

    it('can occupy the 审批模板 wizard when 审批模板 is granted', () => {
      const permission = configPermission(PROCESS_TEMPLATE_MENUS)

      expect(canOccupy(permission, '/processSet/templateSet')).toBe(true)
    })

    it('cannot occupy the 审批模板 wizard when only 审批类型 is granted', () => {
      const permission = configPermission(ONLY_PROCESS_TYPE_MENUS)

      expect(canOccupy(permission, '/processSet/templateSet')).toBe(false)
    })

    it('can occupy 审批实例详情 when 审批实例查询 is granted', () => {
      const permission = configPermission(PROCESS_INSTANCE_MENUS)

      expect(canOccupy(permission, '/processMgr/process')).toBe(true)
      expect(canOccupy(permission, '/processMgr/process/9')).toBe(true)
      expect(canOccupy(permission, '/processMgr')).toBe(true)
    })

    it('cannot occupy a known 配置 page that is not in the current grant', () => {
      const permission = configPermission([])

      expect(canOccupy(permission, '/system/sysUser')).toBe(false)
      expect(canOccupy(permission, '/system/sysUser/9')).toBe(false)
      expect(canOccupy(permission, '/processMgr/process/9')).toBe(false)
    })

    it('cannot occupy 配置 on mobile even when granted', () => {
      const permission = configPermission(ONLY_USER_MENUS)

      expect(canOccupy(permission, '/system/sysUser', MOBILE_WIDTH)).toBe(false)
      expect(canOccupy(permission, '/system/sysUser', MOBILE_BREAKPOINT)).toBe(false)
      expect(canOccupy(permission, '/system/sysUser', MOBILE_BREAKPOINT + 1)).toBe(true)
    })

    it('does not treat 办事 menus as 配置 occupancy', () => {
      const permission = configPermission(ONLY_USER_MENUS)

      expect(canOccupy(permission, '/work/pending')).toBe(false)
    })

    it('does not treat a hidden-only node as granting sibling pages', () => {
      const permission = configPermission([
        {
          path: '/system',
          meta: { title: '组织与权限' },
          children: [
            { path: 'assignAuth', component: 'system/sysRole/assignAuth', meta: { title: '菜单授权' }, hidden: true }
          ]
        }
      ])

      expect(permission.hasConfigPermission).toBe(false)
      expect(canOccupy(permission, '/system/sysUser')).toBe(false)
      expect(canOccupy(permission, '/system/assignAuth')).toBe(false)
    })

    it('answers from the current 配置类菜单 tree, not a previous grant', () => {
      const before = configPermission(ONLY_USER_MENUS)
      const after = configPermission(ONLY_ROLE_MENUS)

      expect(canOccupy(before, '/system/sysUser')).toBe(true)
      expect(canOccupy(before, '/system/sysRole')).toBe(false)
      expect(canOccupy(after, '/system/sysRole')).toBe(true)
      expect(canOccupy(after, '/system/sysUser')).toBe(false)
    })

    it('replaces later grants with the latest menu tree', () => {
      let permission = configPermission(ONLY_USER_MENUS)
      expect(canOccupy(permission, '/system/sysUser')).toBe(true)

      permission = configPermission(ONLY_ROLE_MENUS)
      expect(canOccupy(permission, '/system/sysUser')).toBe(false)
      expect(canOccupy(permission, '/system/sysRole')).toBe(true)
      expect(canOccupy(permission, '/system/assignAuth')).toBe(true)
    })

    it('allows a custom menu path from the menu tree and its parent directory', () => {
      const permission = configPermission(CUSTOM_CONFIG_MENUS)

      expect(canOccupy(permission, '/office/users')).toBe(true)
      expect(canOccupy(permission, '/office/users/9')).toBe(true)
      expect(canOccupy(permission, '/office')).toBe(true)
      expect(canOccupy(permission, '/office/roles')).toBe(false)
    })

    it('does not occupy unknown paths', () => {
      const permission = configPermission(ONLY_USER_MENUS)

      expect(canOccupy(permission, '/unknown/path')).toBe(false)
      expect(canOccupy(permission, '/dashboard')).toBe(false)
    })
  })

  describe('entryOf', () => {
    it('returns the nearest 办事 builtin entry', () => {
      const permission = configPermission([])

      expect(permission.entryOf('/work/pending')).toEqual({
        path: '/work/pending',
        group: 'work'
      })
      expect(permission.entryOf('/work/pending/9/task-1')).toEqual({
        path: '/work/pending',
        group: 'work'
      })
      expect(permission.entryOf('/work/profile')).toEqual({
        path: '/work/profile',
        group: 'work'
      })
    })

    it('prefers the longest 办事 builtin entry', () => {
      const permission = configPermission([])

      expect(permission.entryOf('/work/start/9')).toEqual({
        path: '/work/start',
        group: 'work'
      })
      expect(permission.entryOf('/work/started/9')).toEqual({
        path: '/work/started',
        group: 'work'
      })
    })

    it('classifies 办事 pages as 办事 even when they appear in the menu tree', () => {
      const permission = configPermission([
        { path: '/work/pending', meta: { title: '待处理' } }
      ])

      expect(permission.entryOf('/work/pending/9/task-1')).toEqual({
        path: '/work/pending',
        group: 'work'
      })
    })

    it('returns a 配置 entry from the menu tree', () => {
      const permission = configPermission(ONLY_USER_MENUS)

      expect(permission.entryOf('/system/sysUser')).toEqual({
        path: '/system/sysUser',
        group: 'config'
      })
      expect(permission.entryOf('/system/sysUser/9')).toEqual({
        path: '/system/sysUser',
        group: 'config'
      })
    })

    it('returns a custom menu path as a 配置 entry', () => {
      const permission = configPermission(CUSTOM_CONFIG_MENUS)

      expect(permission.entryOf('/office/users')).toEqual({
        path: '/office/users',
        group: 'config'
      })
      expect(permission.entryOf('/office/users/9')).toEqual({
        path: '/office/users',
        group: 'config'
      })
    })

    it('returns a parent directory that only has children as a 配置 entry', () => {
      const permission = configPermission(ONLY_USER_MENUS)

      expect(permission.entryOf('/system')).toEqual({
        path: '/system',
        group: 'config'
      })
      expect(configPermission([]).entryOf('/processMgr')).toEqual({
        path: '/processMgr',
        group: 'config'
      })
    })

    it('returns the nearest visible parent for a hidden 配置 page', () => {
      expect(configPermission(ONLY_ROLE_MENUS).entryOf('/system/assignAuth')).toEqual({
        path: '/system/sysRole',
        group: 'config'
      })
      expect(configPermission(PROCESS_TEMPLATE_MENUS).entryOf('/processSet/templateSet')).toEqual({
        path: '/processSet/processTemplate',
        group: 'config'
      })
    })

    it('returns the nearest visible ancestor when the hidden page is a sibling in the menu tree', () => {
      const permission = configPermission([
        {
          path: '/system',
          meta: { title: '组织与权限' },
          children: [
            { path: 'sysRole', component: 'system/sysRole/list', meta: { title: '角色管理' } },
            { path: 'assignAuth', component: 'system/sysRole/assignAuth', meta: { title: '菜单授权' }, hidden: true }
          ]
        }
      ])

      expect(permission.entryOf('/system/assignAuth')).toEqual({
        path: '/system',
        group: 'config'
      })
      expect(canOccupy(permission, '/system/assignAuth')).toBe(true)
    })

    it('returns the catalog parent when the hidden 配置 page is absent from the menu tree', () => {
      expect(configPermission([]).entryOf('/system/assignAuth')).toEqual({
        path: '/system/sysRole',
        group: 'config'
      })
      expect(configPermission([]).entryOf('/processSet/templateSet')).toEqual({
        path: '/processSet/processTemplate',
        group: 'config'
      })
    })

    it('falls back to the product catalog for known 配置 pages absent from the menu tree', () => {
      const permission = configPermission([])

      expect(permission.entryOf('/system/sysUser')).toEqual({
        path: '/system/sysUser',
        group: 'config'
      })
      expect(permission.entryOf('/processMgr/process/9')).toEqual({
        path: '/processMgr/process',
        group: 'config'
      })
    })

    it('returns the 审批实例查询 entry for an approval-instance detail path', () => {
      const permission = configPermission(PROCESS_INSTANCE_MENUS)

      expect(permission.entryOf('/processMgr/process/9')).toEqual({
        path: '/processMgr/process',
        group: 'config'
      })
    })

    it('lets the menu tree win over the product catalog', () => {
      const permission = configPermission([
        {
          path: '/system',
          meta: { title: '组织与权限' },
          children: [
            { path: 'sysUser', meta: { title: '用户管理' }, hidden: true }
          ]
        }
      ])

      expect(permission.hasConfigPermission).toBe(false)
      expect(permission.entryOf('/system/sysUser')).toEqual({
        path: '/system',
        group: 'config'
      })
    })

    it('returns null for unknown paths', () => {
      const permission = configPermission(ONLY_USER_MENUS)

      expect(permission.entryOf('/unknown/path')).toBeNull()
      expect(configPermission([]).entryOf('/unknown/path')).toBeNull()
    })
  })
})
