import {
  collectCheckedIds,
  doAssignRequest,
  emptyForm,
  findNodesRequest,
  menuWriteValidationError,
  mergeAssignMenuIds,
  presentConfigMenuTree,
  removeRequest,
  toAssignRequest,
  writeRequest
} from '@/config/sys-menu'

const FULL_TREE = [
  {
    id: 1,
    name: '组织与权限',
    path: '/system',
    component: 'Layout',
    type: 0,
    icon: 'el-icon-user-solid',
    perms: '',
    sortValue: 1,
    status: 1,
    parentId: 0,
    select: true,
    children: [
      {
        id: 11,
        name: '用户管理',
        path: 'sysUser',
        component: 'system/sysUser/list',
        type: 1,
        icon: '',
        perms: '',
        sortValue: 1,
        status: 1,
        parentId: 1,
        select: true,
        children: []
      }
    ]
  },
  {
    id: 9,
    name: '公众号菜单',
    path: '/wechat',
    component: 'Layout',
    type: 0,
    icon: '',
    perms: '',
    sortValue: 9,
    status: 1,
    parentId: 0,
    select: true,
    children: [
      {
        id: 91,
        name: '菜单列表',
        path: 'menu',
        component: 'wechat/menu/list',
        type: 1,
        icon: '',
        perms: '',
        sortValue: 1,
        status: 1,
        parentId: 9,
        select: true,
        children: []
      }
    ]
  },
  {
    id: 8,
    name: '部门管理',
    path: 'sysDept',
    component: 'system/sysDept/list',
    type: 1,
    icon: '',
    perms: '',
    sortValue: 8,
    status: 1,
    parentId: 0,
    select: false,
    children: []
  }
]

describe('config/sys-menu', () => {
  describe('presentConfigMenuTree', () => {
    it('keeps 配置类 menus and drops 公众号 / Dashboard / 部门岗位日志', () => {
      const tree = presentConfigMenuTree(FULL_TREE)
      expect(tree.map(node => node.name)).toEqual(['组织与权限'])
      expect(tree[0].children.map(node => node.name)).toEqual(['用户管理'])
    })
  })

  describe('菜单授权', () => {
    it('collects selected leaf ids for the tree checkbox', () => {
      expect(collectCheckedIds(presentConfigMenuTree(FULL_TREE))).toEqual([11])
    })

    it('keeps previously granted out-of-spec menus when saving 配置类 checks', () => {
      expect(mergeAssignMenuIds([11], FULL_TREE)).toEqual([11, 9, 91])
    })

    it('loads 菜单授权 with GET /admin/system/sysMenu/toAssign/{roleId}', () => {
      expect(toAssignRequest(3)).toEqual({
        method: 'get',
        url: '/admin/system/sysMenu/toAssign/3'
      })
    })

    it('saves 菜单授权 with POST /admin/system/sysMenu/doAssign', () => {
      expect(doAssignRequest(3, [1, 11])).toEqual({
        method: 'post',
        url: '/admin/system/sysMenu/doAssign',
        data: { roleId: 3, menuIdList: [1, 11] }
      })
    })
  })

  describe('menuWriteValidationError', () => {
    it('rejects out-of-spec menus such as 公众号 or 部门', () => {
      expect(menuWriteValidationError({
        path: '/wechat',
        component: 'Layout'
      })).toBe('只能维护配置类菜单，不能添加公众号、Dashboard、部门/岗位/日志或在线流程设计器')

      expect(menuWriteValidationError({
        path: 'sysDept',
        component: 'system/sysDept/list'
      })).toBe('只能维护配置类菜单，不能添加公众号、Dashboard、部门/岗位/日志或在线流程设计器')
    })

    it('rejects 办事内置 routes', () => {
      expect(menuWriteValidationError({
        path: '/work/pending',
        component: 'work/pending/index'
      })).toBe('办事内置入口不在菜单管理中维护')
    })

    it('accepts a 配置类 menu', () => {
      expect(menuWriteValidationError({
        path: 'sysUser',
        component: 'system/sysUser/list'
      })).toBeNull()
    })
  })

  describe('菜单 CRUD API', () => {
    it('loads the menu tree with GET /admin/system/sysMenu/findNodes', () => {
      expect(findNodesRequest()).toEqual({
        method: 'get',
        url: '/admin/system/sysMenu/findNodes'
      })
    })

    it('creates a 配置类 menu with POST /admin/system/sysMenu/save', () => {
      expect(writeRequest({
        parentId: 1,
        name: '用户管理',
        type: 1,
        path: 'sysUser',
        component: 'system/sysUser/list',
        icon: 'el-icon-user-solid',
        sortValue: 1,
        status: 1
      })).toEqual({
        method: 'post',
        url: '/admin/system/sysMenu/save',
        data: {
          parentId: 1,
          name: '用户管理',
          type: 1,
          path: 'sysUser',
          component: 'system/sysUser/list',
          perms: '',
          icon: 'el-icon-user-solid',
          sortValue: 1,
          status: 1
        }
      })
    })

    it('writes Layout for a root directory and ParentView for a nested directory', () => {
      expect(writeRequest({
        parentId: 0,
        name: '组织与权限',
        type: 0,
        path: '/system',
        component: '',
        sortValue: 1,
        status: 1
      }).data.component).toBe('Layout')

      expect(writeRequest({
        parentId: 1,
        name: '审批设置',
        type: 0,
        path: '/processSet',
        component: '',
        sortValue: 1,
        status: 1
      }).data.component).toBe('ParentView')
    })

    it('updates a menu with PUT /admin/system/sysMenu/update', () => {
      expect(writeRequest({
        id: 11,
        parentId: 1,
        name: '用户管理',
        type: 1,
        path: 'sysUser',
        component: 'system/sysUser/list',
        perms: '',
        icon: '',
        sortValue: 1,
        status: 1
      }).method).toBe('put')
    })

    it('deletes a menu with DELETE /admin/system/sysMenu/remove/{id}', () => {
      expect(removeRequest(11)).toEqual({
        method: 'delete',
        url: '/admin/system/sysMenu/remove/11'
      })
    })
  })

  describe('presentForm', () => {
    it('starts 新建 with directory defaults', () => {
      expect(emptyForm()).toEqual({
        id: '',
        parentId: 0,
        parentName: '',
        name: '',
        type: 0,
        path: '',
        component: 'Layout',
        perms: '',
        icon: '',
        sortValue: 1,
        status: 1
      })
    })
  })
})
