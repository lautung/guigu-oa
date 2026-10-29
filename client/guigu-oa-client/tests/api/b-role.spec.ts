import { test, expect } from '@playwright/test'

import {
  ADMIN,
  Api,
  PLAIN_USER,
  assignRoles,
  createRole,
  createUser,
  deleteRole,
  deleteUser,
  expectCode,
  unique,
  type PageResult,
  type RoleRecord,
} from './helpers/api'

/**
 * B 系统配置 / 角色（#120 §6 B7–B15）。
 *
 * `sysRole` 页授予才能调用；`sysRole/toAssign` 与 `sysRole/doAssign` 例外——它们归 `sysUser` 页。
 */

test.describe('B 角色管理', () => {
  test('B7 toAssign：正例（全量 + 已授）/ 无授予 205', async ({ request }) => {
    const api = new Api(request)
    const temp = await createUser(api, unique('api-toassign'))
    try {
      const data = await api.call<{
        allRolesList: RoleRecord[]
        assginRoleList: RoleRecord[]
      }>(ADMIN, 'get', `/admin/system/sysRole/toAssign/${temp.id}`)
      expect(Array.isArray(data.allRolesList)).toBe(true)
      expect(Array.isArray(data.assginRoleList)).toBe(true)

      expect(await api.code(PLAIN_USER, 'get', `/admin/system/sysRole/toAssign/${temp.id}`)).toBe(
        205,
      )
    } finally {
      await deleteUser(api, temp.id)
    }
  })

  test('B8 doAssign：全量覆写生效；已知缺陷 #144 不存在的 roleId 也被接受', async ({ request }) => {
    const api = new Api(request)
    const temp = await createUser(api, unique('api-dorole'))
    const role = await createRole(api, unique('api-dorole-role'))
    try {
      await assignRoles(api, temp.id, [role.id])
      const assigned = await api.call<{ assginRoleList: RoleRecord[] }>(
        ADMIN,
        'get',
        `/admin/system/sysRole/toAssign/${temp.id}`,
      )
      expect(assigned.assginRoleList.map((item) => item.id)).toEqual([role.id])

      const ghost = await api.envelope(ADMIN, 'post', '/admin/system/sysRole/doAssign', {
        userId: temp.id,
        roleIdList: [999999],
      })
      expectCode(ghost.body, 200, '已知缺陷 #144：不存在的 roleId 被接受（修复后应变 201）')
    } finally {
      await assignRoles(api, temp.id, [])
      await deleteRole(api, role.id)
      await deleteUser(api, temp.id)
    }
  })

  test('B9 findAll：有授予 200 / 无授予 205', async ({ request }) => {
    const api = new Api(request)
    const roles = await api.call<RoleRecord[]>(ADMIN, 'get', '/admin/system/sysRole/findAll')
    expect(roles.length).toBeGreaterThan(0)
    expect(await api.code(PLAIN_USER, 'get', '/admin/system/sysRole/findAll')).toBe(205)
  })

  test('B10 分页：正例 / 无授予 205', async ({ request }) => {
    const api = new Api(request)
    const page = await api.call<PageResult<RoleRecord>>(ADMIN, 'get', '/admin/system/sysRole/1/5')
    expect(page.total).toBeGreaterThan(0)
    expect(await api.code(PLAIN_USER, 'get', '/admin/system/sysRole/1/5')).toBe(205)
  })

  test('B11 save：正例 / 无授予 205', async ({ request }) => {
    const api = new Api(request)
    const name = unique('api-role-save')
    const role = await createRole(api, name)
    try {
      expect(role.roleName).toBe(name)
      expect(
        await api.code(PLAIN_USER, 'post', '/admin/system/sysRole/save', {
          roleName: unique('api-role-denied'),
          roleCode: unique('code'),
        }),
      ).toBe(205)
    } finally {
      await deleteRole(api, role.id)
    }
  })

  test('B12 get：正例 / 无授予 205 / 不存在的 id 返回 200 null', async ({ request }) => {
    const api = new Api(request)
    const role = await createRole(api, unique('api-role-get'))
    try {
      const detail = await api.call<RoleRecord>(
        ADMIN,
        'get',
        `/admin/system/sysRole/get/${role.id}`,
      )
      expect(detail.id).toBe(role.id)

      expect(await api.code(PLAIN_USER, 'get', `/admin/system/sysRole/get/${role.id}`)).toBe(205)

      const missing = await api.call<RoleRecord | null>(
        ADMIN,
        'get',
        '/admin/system/sysRole/get/999999',
      )
      expect(missing).toBeNull()
    } finally {
      await deleteRole(api, role.id)
    }
  })

  test('B13 update：正例 / 无授予 205', async ({ request }) => {
    const api = new Api(request)
    const role = await createRole(api, unique('api-role-update'))
    try {
      const renamed = `${role.roleName}改`
      await api.call(ADMIN, 'put', '/admin/system/sysRole/update', {
        id: role.id,
        roleName: renamed,
        roleCode: role.roleCode,
      })
      const detail = await api.call<RoleRecord>(
        ADMIN,
        'get',
        `/admin/system/sysRole/get/${role.id}`,
      )
      expect(detail.roleName).toBe(renamed)

      expect(
        await api.code(PLAIN_USER, 'put', '/admin/system/sysRole/update', {
          id: role.id,
          roleName: 'denied',
        }),
      ).toBe(205)
    } finally {
      await deleteRole(api, role.id)
    }
  })

  test('B14 remove：正例（get 返回 null）/ 无授予 205', async ({ request }) => {
    const api = new Api(request)
    const role = await createRole(api, unique('api-role-remove'))
    await api.call(ADMIN, 'delete', `/admin/system/sysRole/remove/${role.id}`)
    const detail = await api.call<RoleRecord | null>(
      ADMIN,
      'get',
      `/admin/system/sysRole/get/${role.id}`,
    )
    expect(detail).toBeNull()

    expect(await api.code(PLAIN_USER, 'delete', `/admin/system/sysRole/remove/${role.id}`)).toBe(
      205,
    )
  })

  test('B15 batchRemove：正例（List<Long> 体）/ 无授予 205', async ({ request }) => {
    const api = new Api(request)
    const first = await createRole(api, unique('api-role-batch-a'))
    const second = await createRole(api, unique('api-role-batch-b'))
    try {
      await api.call(ADMIN, 'delete', '/admin/system/sysRole/batchRemove', [first.id, second.id])
      const firstDetail = await api.call<RoleRecord | null>(
        ADMIN,
        'get',
        `/admin/system/sysRole/get/${first.id}`,
      )
      const secondDetail = await api.call<RoleRecord | null>(
        ADMIN,
        'get',
        `/admin/system/sysRole/get/${second.id}`,
      )
      expect(firstDetail).toBeNull()
      expect(secondDetail).toBeNull()

      expect(
        await api.code(PLAIN_USER, 'delete', '/admin/system/sysRole/batchRemove', [999999]),
      ).toBe(205)
    } finally {
      await deleteRole(api, first.id)
      await deleteRole(api, second.id)
    }
  })
})
