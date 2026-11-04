import { expect, test, type APIRequestContext } from '@playwright/test'

import { PASSWORD } from './helpers'

/**
 * SEC-REGISTER（#253）：匿名注册是新的匿名端点，安全套件钉三件事——
 * 探测与注册端点不需要认证（不是 401）、开关关闭时 201「注册未开放」、
 * 打开后同一 IP 每分钟第 6 次被 201 限流。用例只在自身开关窗口内改动
 * `sys.account.registerUser`，结束（含失败）恢复为关闭。
 */

const SWITCH_KEY = 'sys.account.registerUser'
const REGISTER_PATH = '/dev-api/admin/system/index/register'
const SWITCH_PATH = `${REGISTER_PATH}/switch`

async function adminLogin(request: APIRequestContext): Promise<string> {
  const response = await request.post('/dev-api/admin/system/index/login', {
    data: { username: 'admin', password: PASSWORD },
  })
  const body = (await response.json()) as { code: number; data?: { token?: string } }
  expect(body.code, '管理员登录').toBe(200)
  expect(body.data?.token, '管理员登录返回 token').toBeTruthy()
  return body.data?.token as string
}

async function findSwitchId(request: APIRequestContext, token: string): Promise<number> {
  const response = await request.get('/dev-api/admin/system/config/1/100', {
    headers: { token },
    params: { configKey: SWITCH_KEY },
  })
  const body = (await response.json()) as {
    code: number
    data?: { records?: { id?: number }[] }
  }
  expect(body.code).toBe(200)
  const id = body.data?.records?.[0]?.id
  expect(id, `参数 ${SWITCH_KEY} 必须存在（V127 种子）`).toBeTruthy()
  return id as number
}

async function setSwitch(
  request: APIRequestContext,
  token: string,
  id: number,
  enabled: boolean,
): Promise<void> {
  const response = await request.put('/dev-api/admin/system/config/update', {
    headers: { token },
    data: { id, configValue: String(enabled) },
  })
  const body = (await response.json()) as { code: number; message?: string }
  expect(body.code, `切换注册开关为 ${enabled}`).toBe(200)
}

/** 每个用例自己登录管理员并确保开关关闭；不把 test-scoped 的 request 留在 beforeAll。 */
async function closeSwitch(request: APIRequestContext): Promise<{ token: string; id: number }> {
  const token = await adminLogin(request)
  const id = await findSwitchId(request, token)
  await setSwitch(request, token, id, false)
  return { token, id }
}

function payload(username: string, password: string) {
  return {
    username,
    password,
    confirm: password,
    name: '安全套件注册',
    phone: '',
  }
}

function register(request: APIRequestContext, sourceIp: string, body: ReturnType<typeof payload>) {
  return request.post(REGISTER_PATH, {
    headers: { 'X-Forwarded-For': sourceIp },
    data: body,
  })
}

test.describe.serial('SEC-REGISTER 匿名注册', () => {
  // 每个用例用独立来源；关闭态那一次也计数，共用 IP 会让限流用例提前量到窗口。
  const randomHost = () => Math.floor(Math.random() * 200) + 20
  const closedIp = `198.51.100.${randomHost()}`
  const rateIp = `198.51.100.${randomHost()}`

  test('探测端点匿名可读且只回布尔', async ({ request }) => {
    const response = await request.get(SWITCH_PATH)
    expect(response.status()).toBe(200)
    const body = (await response.json()) as { code: number; data?: { enabled?: unknown } }
    expect(body.code).toBe(200)
    expect(typeof body.data?.enabled).toBe('boolean')
  })

  test('关闭时注册端点仍是匿名（201 注册未开放，不是 401）', async ({ request }) => {
    await closeSwitch(request)

    const response = await register(request, closedIp, payload('sec-register-closed', 'secret1'))
    expect(response.status()).toBe(200)
    const body = (await response.json()) as { code: number; message?: string }
    expect(body.code).toBe(201)
    expect(body.message).toBe('注册未开放')
  })

  test('注册只接受 POST', async ({ request }) => {
    const response = await request.get(REGISTER_PATH)
    expect(response.status()).toBe(405)
  })

  test('开启后同一来源第 6 次被限流', async ({ request }) => {
    const { token, id } = await closeSwitch(request)
    await setSwitch(request, token, id, true)
    try {
      // 短密码让前五次都失败，同时证明失败尝试也计入 IP 次数。
      for (let attempt = 1; attempt <= 5; attempt += 1) {
        const response = await register(
          request,
          rateIp,
          payload(`sec-register-rate-${attempt}`, '12345'),
        )
        const body = (await response.json()) as { code: number; message?: string }
        expect(body.code).toBe(201)
        expect(body.message).toBe('密码长度必须在 6 到 100 位之间')
      }

      const limited = await register(request, rateIp, payload('sec-register-rate-sixth', 'secret1'))
      expect(limited.status()).toBe(200)
      const body = (await limited.json()) as { code: number; message?: string }
      expect(body.code).toBe(201)
      expect(body.message).toBe('注册尝试过于频繁，请稍后再试')
    } finally {
      await setSwitch(request, token, id, false)
    }
  })
})
