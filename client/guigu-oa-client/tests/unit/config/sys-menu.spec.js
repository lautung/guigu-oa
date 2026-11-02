import { describe, expect, it } from 'vitest'
import {
  assignMenuLabel,
  collectCheckedIds,
  doAssignRequest,
  emptyForm,
  findNodesRequest,
  formForCreate,
  mergeAssignMenuIds,
  presentAssignableConfigMenus,
  presentConfigMenuTree,
  removeRequest,
  toAssignRequest,
  writeRequest,
} from '@/config/sys-menu'

const CONFIG_TREE = [
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
        children: [],
      },
    ],
  },
]

describe('config/sys-menu', () => {
  describe('presentConfigMenuTree', () => {
    it('maps the server tree without re-classifying it', () => {
      const tree = presentConfigMenuTree(CONFIG_TREE)

      expect(tree.map((node) => node.name)).toEqual(['组织与权限'])
      expect(tree[0].children.map((node) => node.name)).toEqual(['用户管理'])
      expect(tree[0].children[0].select).toBe(true)
    })
  })

  describe('菜单授权', () => {
    it('maps the assignable tree as returned by the server', () => {
      const tree = presentAssignableConfigMenus(CONFIG_TREE)

      expect(tree.map((node) => node.name)).toEqual(['组织与权限'])
      expect(tree[0].children.map((node) => node.name)).toEqual(['用户管理'])
    })

    it('collects selected page ids', () => {
      expect(collectCheckedIds(presentAssignableConfigMenus(CONFIG_TREE))).toEqual([11])
    })

    it('keeps button nodes in the assignable tree and labels them with their perms', () => {
      const tree = presentAssignableConfigMenus([
        {
          id: 11,
          name: '用户管理',
          type: 1,
          select: true,
          children: [
            {
              id: 12,
              name: '新增用户',
              type: 2,
              perms: 'system:user:add',
              select: true,
              children: [],
            },
          ],
        },
      ])

      expect(tree[0].children[0]).toMatchObject({ type: 2, perms: 'system:user:add' })
      expect(assignMenuLabel(tree[0])).toBe('用户管理')
      expect(assignMenuLabel(tree[0].children[0])).toBe('新增用户（system:user:add）')
      expect(collectCheckedIds(tree)).toEqual([11, 12])
    })

    it('does not restore a selected directory as a standalone check', () => {
      const tree = presentAssignableConfigMenus([
        { id: 1, name: '系统管理', type: 0, select: true, children: [] },
      ])

      expect(collectCheckedIds(tree)).toEqual([])
    })

    it('dedupes checked ids', () => {
      expect(mergeAssignMenuIds([11, 11, 12])).toEqual([11, 12])
    })

    it('loads 菜单授权 with GET /admin/system/sysMenu/toAssign/{roleId}', () => {
      expect(toAssignRequest(3)).toEqual({
        method: 'get',
        url: '/admin/system/sysMenu/toAssign/3',
      })
    })

    it('saves 菜单授权 with POST /admin/system/sysMenu/doAssign', () => {
      expect(doAssignRequest(3, [1, 11])).toEqual({
        method: 'post',
        url: '/admin/system/sysMenu/doAssign',
        data: { roleId: 3, menuIdList: [1, 11] },
      })
    })
  })

  describe('菜单 CRUD API', () => {
    it('loads the menu tree with GET /admin/system/sysMenu/findNodes', () => {
      expect(findNodesRequest()).toEqual({
        method: 'get',
        url: '/admin/system/sysMenu/findNodes',
      })
    })

    it('creates a 配置类 menu with POST /admin/system/sysMenu/save', () => {
      expect(
        writeRequest({
          parentId: 1,
          name: '用户管理',
          type: 1,
          path: 'sysUser',
          component: 'system/sysUser/list',
          icon: 'el-icon-user-solid',
          sortValue: 1,
          status: 1,
        }),
      ).toEqual({
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
          status: 1,
        },
      })
    })

    it('writes Layout for a root directory and ParentView for a nested directory', () => {
      expect(
        writeRequest({
          parentId: 0,
          name: '组织与权限',
          type: 0,
          path: '/system',
          component: '',
          sortValue: 1,
          status: 1,
        }).data.component,
      ).toBe('Layout')

      expect(
        writeRequest({
          parentId: 1,
          name: '审批设置',
          type: 0,
          path: '/system/processSet',
          component: '',
          sortValue: 1,
          status: 1,
        }).data.component,
      ).toBe('ParentView')
    })

    it('updates a menu with PUT /admin/system/sysMenu/update', () => {
      expect(
        writeRequest({
          id: 11,
          parentId: 1,
          name: '用户管理',
          type: 1,
          path: 'sysUser',
          component: 'system/sysUser/list',
          perms: '',
          icon: '',
          sortValue: 1,
          status: 1,
        }).method,
      ).toBe('put')
    })

    it('deletes a menu with DELETE /admin/system/sysMenu/remove/{id}', () => {
      expect(removeRequest(11)).toEqual({
        method: 'delete',
        url: '/admin/system/sysMenu/remove/11',
      })
    })
  })

  describe('formForCreate', () => {
    it('starts 新建 with directory defaults', () => {
      expect(formForCreate()).toEqual(emptyForm())
    })

    it('creates a page under a directory', () => {
      expect(formForCreate(CONFIG_TREE[0])).toMatchObject({
        parentId: 1,
        parentName: '组织与权限',
        type: 1,
        component: '',
      })
    })

    it('creates a button under a page', () => {
      expect(formForCreate(CONFIG_TREE[0].children[0])).toMatchObject({
        parentId: 11,
        parentName: '用户管理',
        type: 2,
        path: '',
        component: '',
        perms: '',
      })
    })
  })

  it('sends a button with blank path/component and its permission identifier', () => {
    const request = writeRequest({
      parentId: 11,
      name: '新增用户',
      type: 2,
      path: '',
      component: '',
      perms: 'system:user:add',
      icon: '',
      sortValue: 1,
      status: 1,
    })

    expect(request.data).toMatchObject({
      parentId: 11,
      type: 2,
      path: '',
      component: '',
      perms: 'system:user:add',
    })
  })
})
