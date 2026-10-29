import { test, expect } from '@playwright/test'

import { ADMIN, Api, unique } from './helpers/api'

/**
 * F HTTP 语义（#145 修复后的契约）。
 *
 * 修复前所有错误都包成 HTTP 200 + `body.code`，404/405 被 GlobalExceptionHandler
 * 吞成 `code=201`「执行全局异常处理...」，无 token 的受保护请求也是 HTTP 200 + 208，
 * 而匿名路径上的错误方法却是 HTTP 403 + 空体——两套约定并存。
 *
 * 修复后的契约（原 6 例已从「已知缺陷断言」改写为契约断言）：
 * - 认证失败（无 token / 坏 token / 登录失败 / 账号停用）→ HTTP 401 + `body.code` 208/209；
 *   匿名路径上的错误方法不再出现 403 + 空体，同走 401 + 208；
 * - 容器级错误 → 真实状态码（404/405）+ `body.code` 201（业务失败码保留）；
 * - 业务失败仍是 HTTP 200 + `body.code`（201/205 等），其余用例不受影响。
 */

test.describe('F HTTP 语义（#145 修复后的契约）', () => {
  test('F1 登录失败返回 HTTP 401 + code 208', async ({ request }) => {
    const api = new Api(request)
    const result = await api.anonymous('post', '/admin/system/index/login', {
      username: unique('ghost'),
      password: 'whatever',
    })
    expect(result.status, '认证失败统一 401').toBe(401)
    expect(result.body.code).toBe(208)
  })

  test('F2 未知路径返回 HTTP 404 + code 201', async ({ request }) => {
    const api = new Api(request)
    const result = await api.envelope(ADMIN, 'get', '/admin/no-such-endpoint')
    expect(result.status, '404 不再被吞成 HTTP 200').toBe(404)
    expect(result.body.code).toBe(201)
    expect(result.body.message).toContain('不存在')
  })

  test('F3 方法不匹配（GET doAssign）返回 HTTP 405 + code 201', async ({ request }) => {
    const api = new Api(request)
    // doAssign 是 POST-only 且没有同段数的 GET 路由模式，是真正的 405；
    // 旧用例的 GET publish/1 会先撞上 GET {page}/{limit} 成为参数类型不匹配（见 F7）。
    const result = await api.envelope(ADMIN, 'get', '/admin/system/sysRole/doAssign')
    expect(result.status, '405 不再被吞成 HTTP 200').toBe(405)
    expect(result.body.code).toBe(201)
  })

  test('F4 方法不匹配（GET updateStatus）返回 HTTP 405 + code 201', async ({ request }) => {
    const api = new Api(request)
    const result = await api.envelope(ADMIN, 'get', '/admin/system/sysUser/updateStatus/1/1')
    expect(result.status).toBe(405)
    expect(result.body.code).toBe(201)
  })

  test('F7 参数类型不匹配仍是业务信封（HTTP 200 + code 201，非容器级错误）', async ({
    request,
  }) => {
    const api = new Api(request)
    // GET publish/1 命中 GET {page}/{limit} 的分页路由，"publish" 转 Long 失败属于
    // 业务参数错误，不在 #145 的容器级范围，契约保持 HTTP 200 + 业务码。
    const result = await api.envelope(ADMIN, 'get', '/admin/process/processTemplate/publish/1')
    expect(result.status).toBe(200)
    expect(result.body.code).toBe(201)
  })

  test('F5 无 token 受保护请求：HTTP 401 + code 208', async ({ request }) => {
    const api = new Api(request)
    const result = await api.anonymous('get', '/admin/system/sysUser/1/5')
    expect(result.status, '认证失败统一 401，与登录失败同一套约定').toBe(401)
    expect(result.body.code).toBe(208)
  })

  test('F6 匿名路径上的错误方法：HTTP 401 + code 208（不再 403 空体）', async ({ request }) => {
    const api = new Api(request)
    const result = await api.anonymous('get', '/admin/system/index/login')
    expect(result.status, '未认证请求统一 401 + 统一响应体').toBe(401)
    expect(result.body.code).toBe(208)
  })
})
