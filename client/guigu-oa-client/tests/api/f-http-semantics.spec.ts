import { test, expect } from '@playwright/test'

import { ADMIN, Api, unique } from './helpers/api'

/**
 * F HTTP 语义（#120 §4.3「已知缺陷断言组」）。
 *
 * 当前实现把所有错误都包成 HTTP 200 + `body.code`，404/405 被 GlobalExceptionHandler
 * 吞成 `code=201`；无 token 的受保护请求却是 HTTP 403 + 空体。整组是缺陷 #145 的活证据：
 * #127 修成真实状态码后，本组应翻红并整体改写，其余用例不动。
 */

test.describe('F HTTP 语义（已知缺陷 #145）', () => {
  test('F1 业务失败返回 HTTP 200 + code 208', async ({ request }) => {
    const api = new Api(request)
    const result = await api.anonymous('post', '/admin/system/index/login', {
      username: unique('ghost'),
      password: 'whatever',
    })
    expect(result.status, '已知缺陷 #145：业务失败仍是 HTTP 200').toBe(200)
    expect(result.body.code).toBe(208)
  })

  test('F2 未知路径返回 HTTP 200 + code 201', async ({ request }) => {
    const api = new Api(request)
    const result = await api.envelope(ADMIN, 'get', '/admin/no-such-endpoint')
    expect(result.status, '已知缺陷 #145：404 被吞成 HTTP 200').toBe(200)
    expect(result.body.code).toBe(201)
    expect(result.body.message).toContain('全局异常处理')
  })

  test('F3 方法不匹配（GET publish）返回 HTTP 200 + code 201', async ({ request }) => {
    const api = new Api(request)
    const result = await api.envelope(ADMIN, 'get', '/admin/process/processTemplate/publish/1')
    expect(result.status, '已知缺陷 #145：405 被吞成 HTTP 200').toBe(200)
    expect(result.body.code).toBe(201)
  })

  test('F4 方法不匹配（GET updateStatus）返回 HTTP 200 + code 201', async ({ request }) => {
    const api = new Api(request)
    const result = await api.envelope(ADMIN, 'get', '/admin/system/sysUser/updateStatus/1/1')
    expect(result.status, '已知缺陷 #145：405 被吞成 HTTP 200').toBe(200)
    expect(result.body.code).toBe(201)
  })

  test('F5 无 token 受保护请求：HTTP 200 + code 208', async ({ request }) => {
    const api = new Api(request)
    const result = await api.anonymous('get', '/admin/system/sysUser/1/5')
    expect(result.status, '认证失败同样包成 HTTP 200').toBe(200)
    expect(result.body.code).toBe(208)
  })

  test('F6 匿名路径上的错误方法：HTTP 403 + 空体（另一套错误约定）', async ({ request }) => {
    const api = new Api(request)
    const result = await api.anonymous('get', '/admin/system/index/login')
    expect(result.status, '已知缺陷 #145：Spring Security 在 MVC 前拒绝，HTTP 403 且无 body').toBe(
      403,
    )
    expect(result.body.code).toBe(-1)
  })
})
