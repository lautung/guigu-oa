import { test, expect } from '@playwright/test'

import {
  ADMIN,
  Api,
  PLAIN_USER,
  createUser,
  deleteUser,
  expectCode,
  unique,
  type PageResult,
  type UserRecord,
} from './helpers/api'

/**
 * B 系统配置 / 用户（#120 §6 B1–B6）。
 *
 * 权限契约：`sysUser` 页授予才能调用；`user` 无授予，全部 205。
 * #133 修复后用户列表与详情不再返回 `password`（JsonProperty WRITE_ONLY）。
 */

test.describe('B 用户管理', () => {
  test('B1 updateStatus：停用后下一次请求 209 / 无授予 205', async ({ request }) => {
    const api = new Api(request)
    const temp = await createUser(api, unique('api-status'))
    try {
      const token = await api.token(temp.username)
      await api.call(ADMIN, 'put', `/admin/system/sysUser/updateStatus/${temp.id}/0`)

      const next = await api.raw('get', '/admin/system/index/info', { token })
      expectCode(next.body, 209, 'B1 停用后下一次请求')

      expect(
        await api.code(PLAIN_USER, 'put', `/admin/system/sysUser/updateStatus/${temp.id}/1`),
      ).toBe(205)
    } finally {
      await deleteUser(api, temp.id)
    }
  })

  test('B2 分页：正例 / 无授予 205 / limit 超上限 201 / 不返回密码', async ({ request }) => {
    const api = new Api(request)

    const page = await api.call<PageResult<UserRecord>>(ADMIN, 'get', '/admin/system/sysUser/1/5')
    expect(page.total).toBeGreaterThan(0)
    expect(page.records.length).toBeGreaterThan(0)
    expect(page.records[0]).not.toHaveProperty('password')

    expect(await api.code(PLAIN_USER, 'get', '/admin/system/sysUser/1/5')).toBe(205)

    const oversized = await api.envelope(ADMIN, 'get', '/admin/system/sysUser/1/1000')
    expectCode(oversized.body, 201, 'B2 limit=1000（#137 上限 500）')
    expect(oversized.body.message).toContain('500')
  })

  test('B3 get：正例 / 无授予 205 / 不存在的 id 返回 200 null', async ({ request }) => {
    const api = new Api(request)
    const temp = await createUser(api, unique('api-get'))
    try {
      const detail = await api.call<UserRecord>(
        ADMIN,
        'get',
        `/admin/system/sysUser/get/${temp.id}`,
      )
      expect(detail.username).toBe(temp.username)
      expect(detail).not.toHaveProperty('password')

      expect(await api.code(PLAIN_USER, 'get', `/admin/system/sysUser/get/${temp.id}`)).toBe(205)

      const missing = await api.call<UserRecord | null>(
        ADMIN,
        'get',
        '/admin/system/sysUser/get/999999',
      )
      expect(missing).toBeNull()
    } finally {
      await deleteUser(api, temp.id)
    }
  })

  test('B4 save：正例（BCrypt 落库）/ 无授予 205', async ({ request }) => {
    const api = new Api(request)
    const username = unique('api-save')
    await api.call(ADMIN, 'post', '/admin/system/sysUser/save', {
      username,
      name: username,
      password: '111111',
      phone: '',
      status: 1,
    })
    const page = await api.call<PageResult<UserRecord>>(
      ADMIN,
      'get',
      `/admin/system/sysUser/1/50?keyword=${encodeURIComponent(username)}`,
    )
    const created = page.records.find((record) => record.username === username)
    expect(created, 'B4 创建后列表可见').toBeTruthy()
    try {
      expectCode(await api.login(username), 200, 'B4 BCrypt 口令可登录')

      expect(
        await api.code(PLAIN_USER, 'post', '/admin/system/sysUser/save', {
          username: unique('api-denied'),
          name: 'denied',
          password: '111111',
          status: 1,
        }),
      ).toBe(205)
    } finally {
      await deleteUser(api, (created as UserRecord).id)
    }
  })

  test('B5 update：空密码保留原 hash / 改密生效；已知缺陷 #143 旧 token 仍可用', async ({
    request,
  }) => {
    const api = new Api(request)
    const temp = await createUser(api, unique('api-update'))
    try {
      const oldToken = await api.token(temp.username)

      await api.call(ADMIN, 'put', '/admin/system/sysUser/update', {
        id: temp.id,
        username: temp.username,
        name: `${temp.username}改名`,
        status: 1,
      })
      expectCode(await api.login(temp.username), 200, 'B5 空密码保留原口令')

      await api.call(ADMIN, 'put', '/admin/system/sysUser/update', {
        id: temp.id,
        username: temp.username,
        name: `${temp.username}改名`,
        status: 1,
        password: '222222',
      })
      expectCode(await api.login(temp.username, '222222'), 200, 'B5 新口令可登录')
      expectCode(await api.login(temp.username), 208, 'B5 旧口令被拒')

      const stale = await api.raw('get', '/admin/system/index/info', { token: oldToken })
      expectCode(stale.body, 200, '已知缺陷 #143：改密后旧 access token 仍可用（修复后应变 208）')
    } finally {
      await deleteUser(api, temp.id)
    }
  })

  test('B6 remove：逻辑删除后列表不含 / 无授予 205', async ({ request }) => {
    const api = new Api(request)
    const temp = await createUser(api, unique('api-remove'))
    try {
      await api.call(ADMIN, 'delete', `/admin/system/sysUser/remove/${temp.id}`)
      const page = await api.call<PageResult<UserRecord>>(
        ADMIN,
        'get',
        `/admin/system/sysUser/1/50?keyword=${encodeURIComponent(temp.username)}`,
      )
      expect(page.records.find((record) => record.username === temp.username)).toBeUndefined()

      expect(await api.code(PLAIN_USER, 'delete', `/admin/system/sysUser/remove/${temp.id}`)).toBe(
        205,
      )
    } finally {
      await deleteUser(api, temp.id)
    }
  })
})
