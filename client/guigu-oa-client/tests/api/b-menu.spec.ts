import { test, expect } from '@playwright/test'

import {
  ADMIN,
  Api,
  PLAIN_USER,
  assignMenus,
  createRole,
  deleteRole,
  expectCode,
  expectCodeAndMessage,
  flattenMenus,
  unique,
  type MenuRecord,
} from './helpers/api'

/**
 * B 系统配置 / 菜单（#120 §6 B16–B21，#152 B22，#154 B25）。
 *
 * `sysMenu` 页授予才能调用；`sysMenu/toAssign` 与 `sysMenu/doAssign` 归 `sysRole` 页。
 * 配置菜单入口（sysUser/sysRole/sysMenu 与三个根）不可重定义、不可禁用、不可删除。
 */

async function menuTree(api: Api): Promise<MenuRecord[]> {
  return api.call<MenuRecord[]>(ADMIN, 'get', '/admin/system/sysMenu/findNodes')
}

async function findMenu(api: Api, path: string): Promise<MenuRecord | undefined> {
  return flattenMenus(await menuTree(api)).find((menu) => menu.path === path)
}

async function createMenu(api: Api, payload: Record<string, unknown>): Promise<MenuRecord> {
  await api.call(ADMIN, 'post', '/admin/system/sysMenu/save', payload)
  const created = await findMenu(api, String(payload.path))
  if (!created) throw new Error(`创建后菜单树里找不到 ${String(payload.path)}`)
  return created
}

test.describe('B 菜单管理', () => {
  test('B16 toAssign：可授树 + 已勾选 / 无授予 205', async ({ request }) => {
    const api = new Api(request)
    const role = await createRole(api, unique('api-menu-toassign'))
    try {
      const tree = await api.call<MenuRecord[]>(
        ADMIN,
        'get',
        `/admin/system/sysMenu/toAssign/${role.id}`,
      )
      const menus = flattenMenus(tree)
      expect(menus.length).toBeGreaterThan(0)
      expect(menus.some((menu) => menu.path === 'sysUser')).toBe(true)
      expect(menus.every((menu) => menu.select === false)).toBe(true)

      expect(await api.code(PLAIN_USER, 'get', `/admin/system/sysMenu/toAssign/${role.id}`)).toBe(
        205,
      )
    } finally {
      await deleteRole(api, role.id)
    }
  })

  test('B17 doAssign：合法授予生效 / 新非法 id 201', async ({ request }) => {
    const api = new Api(request)
    const role = await createRole(api, unique('api-menu-doassign'))
    try {
      const tree = await api.call<MenuRecord[]>(
        ADMIN,
        'get',
        `/admin/system/sysMenu/toAssign/${role.id}`,
      )
      const sysUserMenu = flattenMenus(tree).find((menu) => menu.path === 'sysUser')
      expect(sysUserMenu, 'B17 菜单树里应有 sysUser 页').toBeTruthy()

      await assignMenus(api, role.id, [(sysUserMenu as MenuRecord).id])
      const after = await api.call<MenuRecord[]>(
        ADMIN,
        'get',
        `/admin/system/sysMenu/toAssign/${role.id}`,
      )
      const selected = flattenMenus(after).filter((menu) => menu.select === true)
      expect(selected.map((menu) => menu.path)).toEqual(['sysUser'])

      const invalid = await api.envelope(ADMIN, 'post', '/admin/system/sysMenu/doAssign', {
        roleId: role.id,
        menuIdList: [999999],
      })
      expectCode(invalid.body, 201, 'B17 新非法 id')
    } finally {
      await deleteRole(api, role.id)
    }
  })

  test('B18 findNodes：配置树 / 无授予 205', async ({ request }) => {
    const api = new Api(request)
    const tree = await menuTree(api)
    expect(tree.some((menu) => menu.path === '/system')).toBe(true)
    expect(await api.code(PLAIN_USER, 'get', '/admin/system/sysMenu/findNodes')).toBe(205)
  })

  test('B19 save：正例 / 受保护 path 201 / 非配置菜单 201', async ({ request }) => {
    const api = new Api(request)
    const system = await findMenu(api, '/system')
    expect(system, 'B19 应有 /system 根').toBeTruthy()
    const path = unique('apiMenu')
    const page = await createMenu(api, {
      parentId: (system as MenuRecord).id,
      name: `接口页${path}`,
      type: 1,
      path,
      component: 'system/sysUser/list',
      perms: '',
      icon: '',
      sortValue: 99,
      status: 1,
    })
    try {
      expect(page.type).toBe(1)

      const protectedPath = await api.envelope(ADMIN, 'post', '/admin/system/sysMenu/save', {
        parentId: (system as MenuRecord).id,
        name: '重定义入口',
        type: 1,
        path: 'sysUser',
        component: 'system/sysUser/list',
        status: 1,
      })
      expectCode(protectedPath.body, 201, 'B19 受保护 path')

      const nonConfig = await api.envelope(ADMIN, 'post', '/admin/system/sysMenu/save', {
        parentId: page.id,
        name: '按钮',
        type: 2,
        path: unique('apiBtn'),
        perms: 'api:test',
        status: 1,
      })
      // type 只收 0/1（ADR 0036）：坏类型在校验边界就被拦下，不再借道「只能维护配置菜单」。
      expectCodeAndMessage(nonConfig.body, 201, '菜单类型只能为 0 或 1', 'B19 type 非法（边界）')
    } finally {
      await api.call(ADMIN, 'delete', `/admin/system/sysMenu/remove/${page.id}`)
    }
  })

  test('B20 update：正例 / 受保护 type 与 status 变更 201', async ({ request }) => {
    const api = new Api(request)
    const system = await findMenu(api, '/system')
    const path = unique('apiMenuUpd')
    const page = await createMenu(api, {
      parentId: (system as MenuRecord).id,
      name: `接口页${path}`,
      type: 1,
      path,
      component: 'system/sysUser/list',
      perms: '',
      icon: '',
      sortValue: 98,
      status: 1,
    })
    try {
      const renamed = `${page.name}改`
      await api.call(ADMIN, 'put', '/admin/system/sysMenu/update', { ...page, name: renamed })
      expect((await findMenu(api, path))?.name).toBe(renamed)

      const protectedMenu = await findMenu(api, 'sysUser')
      expect(protectedMenu, 'B20 应有 sysUser 入口').toBeTruthy()

      const typeChange = await api.envelope(ADMIN, 'put', '/admin/system/sysMenu/update', {
        ...(protectedMenu as MenuRecord),
        type: 0,
      })
      expectCode(typeChange.body, 201, 'B20 受保护入口改 type')

      const disable = await api.envelope(ADMIN, 'put', '/admin/system/sysMenu/update', {
        ...(protectedMenu as MenuRecord),
        status: 0,
      })
      expectCode(disable.body, 201, 'B20 受保护入口禁用')
    } finally {
      await api.call(ADMIN, 'delete', `/admin/system/sysMenu/remove/${page.id}`)
    }
  })

  test('B21 remove：正例 / 有子节点 201 / 受保护入口 201', async ({ request }) => {
    const api = new Api(request)
    const system = await findMenu(api, '/system')
    const dirPath = unique('apiMenuDir')
    const dir = await createMenu(api, {
      parentId: (system as MenuRecord).id,
      name: `接口目录${dirPath}`,
      type: 0,
      path: dirPath,
      component: '',
      perms: '',
      icon: '',
      sortValue: 97,
      status: 1,
    })
    const childPath = unique('apiMenuChild')
    const child = await createMenu(api, {
      parentId: dir.id,
      name: `接口子页${childPath}`,
      type: 1,
      path: childPath,
      component: 'system/sysUser/list',
      perms: '',
      icon: '',
      sortValue: 96,
      status: 1,
    })
    try {
      const withChildren = await api.envelope(
        ADMIN,
        'delete',
        `/admin/system/sysMenu/remove/${dir.id}`,
      )
      expectCode(withChildren.body, 201, 'B21 有子节点')

      await api.call(ADMIN, 'delete', `/admin/system/sysMenu/remove/${child.id}`)
      await api.call(ADMIN, 'delete', `/admin/system/sysMenu/remove/${dir.id}`)
      expect(await findMenu(api, dirPath)).toBeUndefined()

      const protectedMenu = await findMenu(api, 'sysUser')
      const protectedRemove = await api.envelope(
        ADMIN,
        'delete',
        `/admin/system/sysMenu/remove/${(protectedMenu as MenuRecord).id}`,
      )
      expectCode(protectedRemove.body, 201, 'B21 受保护入口')
    } finally {
      await api.call(ADMIN, 'delete', `/admin/system/sysMenu/remove/${child.id}`).catch(() => {})
      await api.call(ADMIN, 'delete', `/admin/system/sysMenu/remove/${dir.id}`).catch(() => {})
    }
  })

  test('B22 顺序契约：未授予 + 空体是字段文案 201（校验先于配置 gate）', async ({ request }) => {
    const api = new Api(request)

    const empty = await api.envelope(PLAIN_USER, 'post', '/admin/system/sysMenu/save', {})
    expectCode(empty.body, 201, 'B22 未授予 + 空体')
    expect(empty.body.message).toBe(
      '菜单名称不能为空；上级菜单不能为空；路由地址不能为空；菜单类型不能为空',
    )

    // 良构体才轮到配置 gate：205 仍是「没有配置权限」的唯一答案。
    const wellFormed = await api.envelope(PLAIN_USER, 'post', '/admin/system/sysMenu/save', {
      parentId: 1,
      name: '越权新增',
      type: 1,
      path: unique('apiDenied'),
      component: 'system/sysUser/list',
    })
    expectCode(wellFormed.body, 205, 'B22 未授予 + 良构体')
  })

  test('B25 越界与缺组件：字段文案 201（不落兜底，ADR 0036）', async ({ request }) => {
    const api = new Api(request)
    const system = await findMenu(api, '/system')
    expect(system, 'B25 应有 /system 根').toBeTruthy()

    // 名称越过 sys_menu.name 的 VARCHAR(50)：超长曾落到「执行全局异常处理...」。
    const tooLong = await api.envelope(ADMIN, 'post', '/admin/system/sysMenu/save', {
      parentId: (system as MenuRecord).id,
      name: '菜'.repeat(51),
      type: 0,
      path: unique('apiLen'),
      component: '',
      status: 1,
    })
    expectCodeAndMessage(tooLong.body, 201, '菜单名称过长', 'B25 名称越界')

    // 类型为菜单必须自带组件路径，目录不受限。
    const barePage = await api.envelope(ADMIN, 'post', '/admin/system/sysMenu/save', {
      parentId: (system as MenuRecord).id,
      name: '无组件页',
      type: 1,
      path: unique('apiBare'),
      status: 1,
    })
    expectCodeAndMessage(barePage.body, 201, '类型为菜单时组件路径不能为空', 'B25 页面缺组件')

    // 恰好 50 字符仍可创建，证明上限与列宽一致；建为停用并删除。
    const boundaryName = '界'.repeat(50)
    const boundary = await createMenu(api, {
      parentId: (system as MenuRecord).id,
      name: boundaryName,
      type: 0,
      path: unique('apiEdge'),
      component: '',
      status: 0,
    })
    try {
      expect(boundary.name).toBe(boundaryName)
    } finally {
      await api.call(ADMIN, 'delete', `/admin/system/sysMenu/remove/${boundary.id}`)
    }

    // 目录转菜单页而不发组件：沿用旧行（空），service 按落库形状拒绝（ADR 0036）。
    const dir = await createMenu(api, {
      parentId: (system as MenuRecord).id,
      name: `接口目录转页${unique('apiDir')}`,
      type: 0,
      path: unique('apiDirPg'),
      component: '',
      status: 0,
    })
    try {
      const converted = await api.envelope(ADMIN, 'put', '/admin/system/sysMenu/update', {
        id: dir.id,
        parentId: (system as MenuRecord).id,
        type: 1,
        name: '转成菜单页',
        path: dir.path,
      })
      expectCodeAndMessage(
        converted.body,
        201,
        '类型为菜单时组件路径不能为空',
        'B25 目录转页缺组件',
      )
    } finally {
      await api.call(ADMIN, 'delete', `/admin/system/sysMenu/remove/${dir.id}`)
    }
  })
})
