import { test, expect } from '@playwright/test'

import {
  ADMIN,
  Api,
  PASSWORD,
  PLAIN_USER,
  createUser,
  deleteUser,
  expectCode,
  expectCodeAndMessage,
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

  test('B5 update：空密码保留原 hash / 改密生效且旧会话立即失效（#143）', async ({ request }) => {
    const api = new Api(request)
    const temp = await createUser(api, unique('api-update'))
    try {
      const old = await api.login(temp.username)
      expectCode(old, 200, 'B5 改密前登录')

      await api.call(ADMIN, 'put', '/admin/system/sysUser/update', {
        id: temp.id,
        username: temp.username,
        name: `${temp.username}改名`,
        status: 1,
      })
      expectCode(await api.login(temp.username), 200, 'B5 空密码保留原口令')
      expectCode(
        (await api.raw('get', '/admin/system/index/info', { token: old.data.token })).body,
        200,
        'B5 未改密不吊销会话',
      )

      await api.call(ADMIN, 'put', '/admin/system/sysUser/update', {
        id: temp.id,
        username: temp.username,
        name: `${temp.username}改名`,
        status: 1,
        password: '222222',
      })
      expectCode(await api.login(temp.username, '222222'), 200, 'B5 新口令可登录')
      expectCode(await api.login(temp.username), 208, 'B5 旧口令被拒')

      const stale = await api.raw('get', '/admin/system/index/info', { token: old.data.token })
      expectCode(stale.body, 208, 'B5 改密后旧 access token 失效')

      const staleRefresh = await api.anonymous('post', '/admin/system/index/refresh', {
        refreshToken: old.data.refreshToken,
      })
      expectCode(staleRefresh.body, 208, 'B5 改密后旧 refresh token 失效')
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

  test('B23 save：用户名/姓名越界 201 字段文案（不落兜底，ADR 0036）', async ({ request }) => {
    const api = new Api(request)

    // 越过 sys_user.username/name 的 VARCHAR(20)：超长曾落到「执行全局异常处理...」。
    const overLength = await api.envelope(ADMIN, 'post', '/admin/system/sysUser/save', {
      username: 'a'.repeat(21),
      name: '名'.repeat(21),
      password: '111111',
    })
    expectCodeAndMessage(overLength.body, 201, '姓名过长；用户名过长', 'B23 越界')

    // 恰好 20 字符仍可创建，证明上限与列宽一致；建了就删。
    const username = unique('api-len').padEnd(20, '0')
    await api.call(ADMIN, 'post', '/admin/system/sysUser/save', {
      username,
      name: '名'.repeat(20),
      password: '111111',
    })
    const page = await api.call<PageResult<UserRecord>>(
      ADMIN,
      'get',
      `/admin/system/sysUser/1/50?keyword=${encodeURIComponent(username)}`,
    )
    const created = page.records.find((record) => record.username === username)
    if (!created) throw new Error('B23 20 字符用户名创建后列表查不到')
    try {
      expect(created.name).toBe('名'.repeat(20))
    } finally {
      await deleteUser(api, created.id)
    }
  })

  test('B24 save：重名 201 命名拒绝 / 无授予 205（ADR 0042）', async ({ request }) => {
    const api = new Api(request)
    const temp = await createUser(api, unique('api-dup'))
    try {
      const dup = await api.envelope(ADMIN, 'post', '/admin/system/sysUser/save', {
        username: temp.username,
        name: temp.username,
        password: PASSWORD,
        status: 1,
      })
      expectCodeAndMessage(dup.body, 201, '用户名已存在', 'B24 重名新增')

      // 顺序契约：查重判定在配置 gate 之后，未授予 + 重名 = 205，不是 201。
      expect(
        await api.code(PLAIN_USER, 'post', '/admin/system/sysUser/save', {
          username: temp.username,
          name: 'denied',
          password: PASSWORD,
          status: 1,
        }),
      ).toBe(205)
    } finally {
      await deleteUser(api, temp.id)
    }
  })

  test('B25 update：改名撞他人 201 命名拒绝 / 改回自身 200（ADR 0042）', async ({ request }) => {
    const api = new Api(request)
    const a = await createUser(api, unique('api-ua'))
    const b = await createUser(api, unique('api-ub'))
    try {
      const collide = await api.envelope(ADMIN, 'put', '/admin/system/sysUser/update', {
        id: b.id,
        username: a.username,
      })
      expectCodeAndMessage(collide.body, 201, '用户名已存在', 'B25 改名撞人')

      await api.call(ADMIN, 'put', '/admin/system/sysUser/update', {
        id: b.id,
        username: b.username,
        name: `${b.username}自名`,
      })
    } finally {
      await deleteUser(api, a.id)
      await deleteUser(api, b.id)
    }
  })

  test('B26 remove：删除让位，同名可重建（ADR 0042）', async ({ request }) => {
    const api = new Api(request)
    const username = unique('api-reuse')
    const first = await createUser(api, username)
    await deleteUser(api, first.id)

    // 软删行不再占名：同名重建放行，重建行是列表里唯一可见的那条。
    await api.call(ADMIN, 'post', '/admin/system/sysUser/save', {
      username,
      name: username,
      password: PASSWORD,
      status: 1,
    })
    const page = await api.call<PageResult<UserRecord>>(
      ADMIN,
      'get',
      `/admin/system/sysUser/1/50?keyword=${encodeURIComponent(username)}`,
    )
    const recreated = page.records.find((record) => record.username === username)
    expect(recreated, 'B26 删除后同名重建可见').toBeTruthy()
    await deleteUser(api, (recreated as UserRecord).id)
  })
})
