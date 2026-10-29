import { test, expect } from '@playwright/test'

import {
  ADMIN,
  Api,
  assignMenus,
  assignRoles,
  createRole,
  createUser,
  deleteRole,
  deleteUser,
  flattenMenus,
  unique,
  type InfoPayload,
  type MenuRecord,
} from './helpers/api'

/**
 * 权限分支：单页授予只开该页（#120 §3）。
 *
 * 用 API 现场构造「只授予 sysUser 页」的角色与账号，不用 admin 冒充。
 * 删除角色后的授予回收是已知缺陷 #146 的活证据（修复后第二例翻红）。
 */

test.describe('B 配置授予分支', () => {
  test('单页授予只开 sysUser，其余配置页仍 205', async ({ request }) => {
    const api = new Api(request)
    const role = await createRole(api, unique('api-grant-role'))
    const temp = await createUser(api, unique('api-grant-user'))
    try {
      const tree = await api.call<MenuRecord[]>(
        ADMIN,
        'get',
        `/admin/system/sysMenu/toAssign/${role.id}`,
      )
      const sysUserMenu = flattenMenus(tree).find((menu) => menu.path === 'sysUser')
      expect(sysUserMenu, '菜单树里应有 sysUser 页').toBeTruthy()

      await assignMenus(api, role.id, [(sysUserMenu as MenuRecord).id])
      await assignRoles(api, temp.id, [role.id])

      const info = await api.call<InfoPayload>(temp.username, 'get', '/admin/system/index/info')
      // 读取时补祖先：目录 /system 随页面出现，但其它配置页不出现。
      const pagePaths = info.configMenus
        .map((menu) => menu.path)
        .filter((path) => path !== '/system')
      expect(pagePaths).toEqual(['/system/sysUser'])

      expect(await api.code(temp.username, 'get', '/admin/system/sysUser/1/5')).toBe(200)
      expect(await api.code(temp.username, 'get', '/admin/system/sysRole/1/5')).toBe(205)
      expect(await api.code(temp.username, 'get', '/admin/system/sysMenu/findNodes')).toBe(205)
      expect(await api.code(temp.username, 'get', '/admin/process/processTemplate/1/5')).toBe(205)
      expect(await api.code(temp.username, 'get', '/admin/process/processType/findAll')).toBe(205)
    } finally {
      await assignRoles(api, temp.id, []).catch(() => {})
      await deleteRole(api, role.id).catch(() => {})
      await deleteUser(api, temp.id).catch(() => {})
    }
  })

  test('已知缺陷 #146：删除角色后成员授予不回收（修复后应变 205）', async ({ request }) => {
    const api = new Api(request)
    const role = await createRole(api, unique('api-revoke-role'))
    const temp = await createUser(api, unique('api-revoke-user'))
    try {
      const tree = await api.call<MenuRecord[]>(
        ADMIN,
        'get',
        `/admin/system/sysMenu/toAssign/${role.id}`,
      )
      const sysUserMenu = flattenMenus(tree).find((menu) => menu.path === 'sysUser')
      expect(sysUserMenu, '菜单树里应有 sysUser 页').toBeTruthy()

      await assignMenus(api, role.id, [(sysUserMenu as MenuRecord).id])
      await assignRoles(api, temp.id, [role.id])
      expect(await api.code(temp.username, 'get', '/admin/system/sysUser/1/5')).toBe(200)

      await deleteRole(api, role.id)

      expect(
        await api.code(temp.username, 'get', '/admin/system/sysUser/1/5'),
        '已知缺陷 #146：角色删除后授予仍在（修复后应 205）',
      ).toBe(200)
    } finally {
      await assignRoles(api, temp.id, []).catch(() => {})
      await deleteRole(api, role.id).catch(() => {})
      await deleteUser(api, temp.id).catch(() => {})
    }
  })
})
