import { test, expect } from '@playwright/test'

import {
  ADMIN,
  ALICE,
  Api,
  PLAIN_USER,
  createUser,
  deleteUser,
  expectCode,
  unique,
  type InfoPayload,
  type TokenPair,
} from './helpers/api'

/**
 * A 认证 / 认出（#120 §6 A1–A4）。
 *
 * 错误码契约：200 成功；208 认证失败；209 账号停用。
 * A4 的「登出作废 token」是 #134 修复后的行为：登出后同一 access token 必须 208。
 *
 * 登录限流（#135）按账号累计失败，所以错密码用例只用临时账号，并在同一次用例里
 * 用一次成功登录清零；种子账号不承担失败计数。
 */

test.describe('A 认证与认出', () => {
  test('A1 登录：正例 / 错密码 / 不存在用户 / 停用用户', async ({ request }) => {
    const api = new Api(request)

    const ok = await api.login(ADMIN)
    expectCode(ok, 200, 'A1 正例')
    expect(ok.data?.token).toBeTruthy()
    expect(ok.data?.refreshToken).toBeTruthy()

    expectCode(await api.login(unique('ghost')), 208, 'A1 不存在的用户')

    const temp = await createUser(api, unique('api-login'))
    try {
      expectCode(await api.login(temp.username, 'wrong-password'), 208, 'A1 错密码')
      expectCode(await api.login(temp.username), 200, 'A1 错密码后正确口令（并清零计数）')

      await api.call(ADMIN, 'put', `/admin/system/sysUser/updateStatus/${temp.id}/0`)
      expectCode(await api.login(temp.username), 209, 'A1 停用用户')
    } finally {
      await deleteUser(api, temp.id)
    }
  })

  test('A2 刷新：正例 / access 当 refresh / 篡改 refresh / 停用用户', async ({ request }) => {
    const api = new Api(request)
    const first = await api.login(ALICE)
    expectCode(first, 200, 'A2 首次登录')
    const pair = first.data as TokenPair

    const refreshed = await api.anonymous<TokenPair>('post', '/admin/system/index/refresh', {
      refreshToken: pair.refreshToken,
    })
    expectCode(refreshed.body, 200, 'A2 正例')
    expect(refreshed.body.data.token).toBeTruthy()
    // 同一秒内刷新会得到字符串完全相同的 JWT（iat/exp 精度到秒），所以只验证可用性。
    const withNewToken = await api.raw('get', '/admin/system/index/info', {
      token: refreshed.body.data.token,
    })
    expectCode(withNewToken.body, 200, 'A2 新 access token 可用')
    expect(refreshed.body.data.refreshToken).toBe(pair.refreshToken)

    const accessAsRefresh = await api.anonymous('post', '/admin/system/index/refresh', {
      refreshToken: pair.token,
    })
    expectCode(accessAsRefresh.body, 208, 'A2 access 当 refresh')

    const tampered = `${pair.refreshToken.slice(0, -2)}${pair.refreshToken.endsWith('a') ? 'b' : 'a'}`
    const tamperedResult = await api.anonymous('post', '/admin/system/index/refresh', {
      refreshToken: tampered,
    })
    expectCode(tamperedResult.body, 208, 'A2 篡改 refresh')

    const temp = await createUser(api, unique('api-refresh'))
    try {
      const disabledPair = await api.login(temp.username)
      expectCode(disabledPair, 200, 'A2 停用前登录')
      await api.call(ADMIN, 'put', `/admin/system/sysUser/updateStatus/${temp.id}/0`)
      const disabledRefresh = await api.anonymous('post', '/admin/system/index/refresh', {
        refreshToken: (disabledPair.data as TokenPair).refreshToken,
      })
      expectCode(disabledRefresh.body, 209, 'A2 停用用户刷新')
    } finally {
      await deleteUser(api, temp.id)
    }
  })

  test('A3 info：admin 有配置菜单 / user 为空 / 无 token 208', async ({ request }) => {
    const api = new Api(request)

    const adminInfo = await api.call<InfoPayload>(ADMIN, 'get', '/admin/system/index/info')
    expect(adminInfo.configMenus.length).toBeGreaterThan(0)
    expect(adminInfo.configMenus.some((menu) => menu.path === '/system/sysUser')).toBe(true)

    const plainInfo = await api.call<InfoPayload>(PLAIN_USER, 'get', '/admin/system/index/info')
    expect(plainInfo.configMenus).toHaveLength(0)

    const anonymous = await api.anonymous('get', '/admin/system/index/info')
    expectCode(anonymous.body, 208, 'A3 无 token')
  })

  test('A4 登出：有 token 作废会话 / 无 token 幂等 200', async ({ request }) => {
    const api = new Api(request)
    const temp = await createUser(api, unique('api-logout'))
    try {
      const token = await api.token(temp.username)
      const out = await api.raw('post', '/admin/system/index/logout', { token, data: {} })
      expectCode(out.body, 200, 'A4 登出')

      const replay = await api.raw('get', '/admin/system/index/info', { token })
      expectCode(replay.body, 208, 'A4 登出后原 token 必须失效（#134）')
      api.forget(temp.username)
    } finally {
      await deleteUser(api, temp.id)
    }

    const anonymous = await api.anonymous('post', '/admin/system/index/logout', {})
    expectCode(anonymous.body, 200, 'A4 无 token 登出')
  })
})
