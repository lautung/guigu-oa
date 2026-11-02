import { describe, expect, it } from 'vitest'
import { configPermission } from '@/config/permission'
import { MOBILE_BREAKPOINT } from '@/navigation/constants'

const DESKTOP_WIDTH = 1200
const MOBILE_WIDTH = 375

const USER_PAGE = [
  { path: '/system', title: '组织与权限', type: 0, sort: 1 },
  { path: '/system/sysUser', parentPath: '/system', title: '用户管理', type: 1, sort: 1 },
]

const ROLE_PAGE = [
  { path: '/system', title: '组织与权限', type: 0, sort: 1 },
  { path: '/system/sysRole', parentPath: '/system', title: '角色管理', type: 1, sort: 2 },
  {
    path: '/system/assignAuth',
    parentPath: '/system/sysRole',
    title: '菜单授权',
    type: 1,
    hidden: true,
    sort: 3,
  },
]

const PROCESS_TYPE_PAGE = [
  { path: '/system/processSet', title: '审批设置', type: 0, sort: 2 },
  {
    path: '/system/processSet/processType',
    parentPath: '/system/processSet',
    title: '审批类型',
    type: 1,
    sort: 1,
  },
]

const TEMPLATE_PAGE = [
  { path: '/system/processSet', title: '审批设置', type: 0, sort: 2 },
  {
    path: '/system/processSet/processTemplate',
    parentPath: '/system/processSet',
    title: '审批模板',
    type: 1,
    sort: 2,
  },
  {
    path: '/system/processSet/templateSet',
    parentPath: '/system/processSet/processTemplate',
    title: '审批模板设置',
    type: 1,
    hidden: true,
    sort: 3,
  },
]

const PROCESS_INSTANCE_PAGE = [
  { path: '/system/processMgr', title: '审批管理', type: 0, sort: 3 },
  {
    path: '/system/processMgr/process',
    parentPath: '/system/processMgr',
    title: '审批实例查询',
    type: 1,
    sort: 1,
  },
]

const CUSTOM_PAGE = [
  { path: '/office', title: '办公配置', type: 0, sort: 1 },
  { path: '/office/users', parentPath: '/office', title: '用户', type: 1, sort: 1 },
]

const CUSTOM_HIDDEN_PAGE = [
  { path: '/office', title: '办公配置', type: 0, sort: 1 },
  { path: '/office/users', parentPath: '/office', title: '用户', type: 1, sort: 1 },
  {
    path: '/office/userDetail',
    parentPath: '/office/users',
    title: '用户详情',
    type: 1,
    hidden: true,
    sort: 2,
  },
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
    it('has no 配置权限 when the granted entries are empty', () => {
      const permission = configPermission([])

      expect(permission.hasConfigPermission).toBe(false)
      expect(permission.grantedConfigMenus).toEqual([])
    })

    it('has no 配置权限 when only a directory is granted', () => {
      const permission = configPermission([{ path: '/system', title: '组织与权限', type: 0 }])

      expect(permission.hasConfigPermission).toBe(false)
      expect(permission.grantedConfigMenus).toEqual([])
    })

    it('has 配置权限 when at least one page is granted', () => {
      const permission = configPermission(USER_PAGE)

      expect(permission.hasConfigPermission).toBe(true)
    })

    it('returns the granted tree as 组织与权限 / 用户管理', () => {
      const permission = configPermission(USER_PAGE)

      expect(flattenTitles(permission.grantedConfigMenus)).toEqual(['组织与权限', '用户管理'])
    })

    it('does not list hidden pages in the sidebar', () => {
      const permission = configPermission(ROLE_PAGE)

      expect(flattenTitles(permission.grantedConfigMenus)).toEqual(['组织与权限', '角色管理'])
    })

    it('needs the visible parent page for a hidden page to count', () => {
      const permission = configPermission([
        {
          path: '/system/assignAuth',
          parentPath: '/system/sysRole',
          title: '菜单授权',
          type: 1,
          hidden: true,
        },
      ])

      expect(permission.hasConfigPermission).toBe(false)
      expect(canOccupy(permission, '/system/assignAuth')).toBe(false)
    })

    it('answers from the current grant, not a previous one', () => {
      const before = configPermission(USER_PAGE)
      const after = configPermission(ROLE_PAGE)

      expect(canOccupy(before, '/system/sysUser')).toBe(true)
      expect(canOccupy(before, '/system/sysRole')).toBe(false)
      expect(canOccupy(after, '/system/sysRole')).toBe(true)
      expect(canOccupy(after, '/system/sysUser')).toBe(false)
    })
  })

  describe('canOccupy', () => {
    it('can occupy a granted page and its parent directory on desktop', () => {
      const permission = configPermission(USER_PAGE)

      expect(canOccupy(permission, '/system/sysUser')).toBe(true)
      expect(canOccupy(permission, '/system/sysUser/9')).toBe(true)
      expect(canOccupy(permission, '/system')).toBe(true)
    })

    it('cannot occupy a page that was not granted', () => {
      const permission = configPermission(USER_PAGE)

      expect(canOccupy(permission, '/system/sysRole')).toBe(false)
      expect(canOccupy(permission, '/system/sysMenu')).toBe(false)
      expect(canOccupy(permission, '/system/assignAuth')).toBe(false)
      expect(canOccupy(permission, '/system/processSet/processType')).toBe(false)
    })

    it('can occupy a hidden page when its visible parent is granted', () => {
      const permission = configPermission(ROLE_PAGE)

      expect(canOccupy(permission, '/system/assignAuth')).toBe(true)
      expect(canOccupy(permission, '/system/assignAuth/9')).toBe(true)
    })

    it('cannot occupy a hidden page when the parent is granted but the entry is absent', () => {
      const permission = configPermission([
        { path: '/system', title: '组织与权限', type: 0, sort: 1 },
        { path: '/system/sysRole', parentPath: '/system', title: '角色管理', type: 1, sort: 2 },
      ])

      expect(canOccupy(permission, '/system/assignAuth')).toBe(false)
    })

    it('can occupy the 审批模板 wizard when 审批模板 is granted', () => {
      const permission = configPermission(TEMPLATE_PAGE)

      expect(canOccupy(permission, '/system/processSet/templateSet')).toBe(true)
    })

    it('cannot occupy the 审批模板 wizard when only 审批类型 is granted', () => {
      const permission = configPermission(PROCESS_TYPE_PAGE)

      expect(canOccupy(permission, '/system/processSet/templateSet')).toBe(false)
    })

    it('can occupy 审批实例详情 when 审批实例查询 is granted', () => {
      const permission = configPermission(PROCESS_INSTANCE_PAGE)

      expect(canOccupy(permission, '/system/processMgr/process')).toBe(true)
      expect(canOccupy(permission, '/system/processMgr/process/9')).toBe(true)
      expect(canOccupy(permission, '/system/processMgr')).toBe(true)
    })

    it('cannot occupy 配置 on mobile even when granted', () => {
      const permission = configPermission(USER_PAGE)

      expect(canOccupy(permission, '/system/sysUser', MOBILE_WIDTH)).toBe(false)
      expect(canOccupy(permission, '/system/sysUser', MOBILE_BREAKPOINT)).toBe(false)
      expect(canOccupy(permission, '/system/sysUser', MOBILE_BREAKPOINT + 1)).toBe(true)
    })

    it('does not treat 办事 paths as 配置 occupancy', () => {
      const permission = configPermission(USER_PAGE)

      expect(canOccupy(permission, '/work/pending')).toBe(false)
    })

    it('allows a custom granted page and its parent directory', () => {
      const permission = configPermission(CUSTOM_PAGE)

      expect(canOccupy(permission, '/office/users')).toBe(true)
      expect(canOccupy(permission, '/office/users/9')).toBe(true)
      expect(canOccupy(permission, '/office')).toBe(true)
      expect(canOccupy(permission, '/office/roles')).toBe(false)
    })

    it('can occupy a custom hidden page through its visible page parent', () => {
      const permission = configPermission(CUSTOM_HIDDEN_PAGE)

      expect(canOccupy(permission, '/office/userDetail')).toBe(true)
      expect(canOccupy(permission, '/office/userDetail/9')).toBe(true)
      expect(canOccupy(permission, '/office/userDetail', MOBILE_WIDTH)).toBe(false)
    })

    it('cannot occupy a custom hidden page whose visible parent has no grant', () => {
      const permission = configPermission([
        {
          path: '/office/userDetail',
          parentPath: '/office/users',
          title: '用户详情',
          type: 1,
          hidden: true,
        },
      ])

      expect(canOccupy(permission, '/office/userDetail')).toBe(false)
    })

    it('does not occupy unknown paths', () => {
      const permission = configPermission(USER_PAGE)

      expect(canOccupy(permission, '/unknown/path')).toBe(false)
      expect(canOccupy(permission, '/dashboard')).toBe(false)
    })
  })

  describe('entryOf', () => {
    it('returns the nearest 办事 entry', () => {
      const permission = configPermission([])

      expect(permission.entryOf('/work/pending')).toEqual({
        path: '/work/pending',
        group: 'work',
      })
      expect(permission.entryOf('/work/pending/9/task-1')).toEqual({
        path: '/work/pending',
        group: 'work',
      })
      expect(permission.entryOf('/work/profile')).toEqual({
        path: '/work/profile',
        group: 'work',
      })
    })

    it('prefers the longest 办事 entry', () => {
      const permission = configPermission([])

      expect(permission.entryOf('/work/start/9')).toEqual({
        path: '/work/start',
        group: 'work',
      })
      expect(permission.entryOf('/work/started/9')).toEqual({
        path: '/work/started',
        group: 'work',
      })
    })

    it('returns a declared 配置 route without needing a grant', () => {
      const permission = configPermission([])

      expect(permission.entryOf('/system/sysUser')).toEqual({
        path: '/system/sysUser',
        group: 'config',
      })
      expect(permission.entryOf('/system/sysUser/9')).toEqual({
        path: '/system/sysUser',
        group: 'config',
      })
      expect(permission.entryOf('/system/processMgr/process/9')).toEqual({
        path: '/system/processMgr/process',
        group: 'config',
      })
    })

    it('returns the visible parent for a declared hidden 配置 page', () => {
      const permission = configPermission(ROLE_PAGE)

      expect(permission.entryOf('/system/assignAuth')).toEqual({
        path: '/system/sysRole',
        group: 'config',
      })
      expect(permission.entryOf('/system/assignAuth/9')).toEqual({
        path: '/system/sysRole',
        group: 'config',
      })
      expect(configPermission([]).entryOf('/system/processSet/templateSet')).toEqual({
        path: '/system/processSet/processTemplate',
        group: 'config',
      })
    })

    it('returns a parent directory that only has children as a 配置 entry', () => {
      expect(configPermission(USER_PAGE).entryOf('/system')).toEqual({
        path: '/system',
        group: 'config',
      })
      expect(configPermission([]).entryOf('/system/processMgr')).toEqual({
        path: '/system/processMgr',
        group: 'config',
      })
    })

    it('returns a custom granted page as a 配置 entry', () => {
      const permission = configPermission(CUSTOM_PAGE)

      expect(permission.entryOf('/office/users')).toEqual({
        path: '/office/users',
        group: 'config',
      })
      expect(permission.entryOf('/office/users/9')).toEqual({
        path: '/office/users',
        group: 'config',
      })
    })

    it('returns the visible parent for a custom hidden entry', () => {
      const permission = configPermission(CUSTOM_HIDDEN_PAGE)

      expect(permission.entryOf('/office/userDetail')).toEqual({
        path: '/office/users',
        group: 'config',
      })
    })

    it('returns null for unknown paths', () => {
      const permission = configPermission(USER_PAGE)

      expect(permission.entryOf('/unknown/path')).toBeNull()
      expect(configPermission([]).entryOf('/unknown/path')).toBeNull()
    })
  })
})
