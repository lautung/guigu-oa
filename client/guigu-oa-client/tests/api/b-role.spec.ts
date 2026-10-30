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
  expectCodeAndMessage,
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

  test('B8 doAssign：全量覆写生效；不存在/已删除的 roleId 返回 201 且不写行（#144）', async ({
    request,
  }) => {
    const api = new Api(request)
    const temp = await createUser(api, unique('api-dorole'))
    const role = await createRole(api, unique('api-dorole-role'))
    const removed = await createRole(api, unique('api-dorole-gone'))
    await deleteRole(api, removed.id)
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
      expectCode(ghost.body, 201, '不存在的 roleId 拒绝整个分配（#144）')

      const deleted = await api.envelope(ADMIN, 'post', '/admin/system/sysRole/doAssign', {
        userId: temp.id,
        roleIdList: [removed.id],
      })
      expectCode(deleted.body, 201, '已删除的 roleId 同样拒绝（#144）')

      const after = await api.call<{ assginRoleList: RoleRecord[] }>(
        ADMIN,
        'get',
        `/admin/system/sysRole/toAssign/${temp.id}`,
      )
      expect(after.assginRoleList.map((item) => item.id)).toEqual(
        [role.id],
        '被拒绝的分配不得改动既有角色（#144 不写任何行）',
      )
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

  test('B24 save：角色名称/编码越界 201 字段文案（不落兜底，ADR 0036）', async ({ request }) => {
    const api = new Api(request)

    // 越过 sys_role.role_name/role_code 的 VARCHAR(20)：超长曾落到「执行全局异常处理...」。
    const overLength = await api.envelope(ADMIN, 'post', '/admin/system/sysRole/save', {
      roleName: '角'.repeat(21),
      roleCode: 'c'.repeat(21),
    })
    expectCodeAndMessage(overLength.body, 201, '角色编码过长；角色名称过长', 'B24 越界')

    // 恰好 20 字符仍可创建，证明上限与列宽一致；建了就删。
    const roleName = unique('api-role').padEnd(20, '0')
    await api.call(ADMIN, 'post', '/admin/system/sysRole/save', {
      roleName,
      roleCode: unique('api-code'),
      description: '',
    })
    const roles = await api.call<RoleRecord[]>(ADMIN, 'get', '/admin/system/sysRole/findAll')
    const created = roles.find((role) => role.roleName === roleName)
    if (!created) throw new Error('B24 20 字符角色名创建后 findAll 查不到')
    try {
      expect(created.roleCode).toBeTruthy()
    } finally {
      await deleteRole(api, created.id)
    }
  })
})
