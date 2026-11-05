import { expect, test, type APIRequestContext } from '@playwright/test'

import { PASSWORD } from './helpers'

/**
 * SEC-REGISTER（#253）与 SEC-CLIENT-ADDRESS（#262）：匿名注册是新的匿名端点，安全套件钉
 * 四件事——探测与注册端点不需要认证（不是 401）、开关关闭时 201「注册未开放」、打开后
 * 同一来源第 6 次被 201 限流。用例只在自身开关窗口内改动 `sys.account.registerUser`，
 * 结束（含失败）恢复为关闭。
 *
 * #262 的来源信任口径（ADR 0063）：默认配置不信任任何转发头，直连 8800 轮换
 * `X-Real-IP` / `X-Forwarded-For` 不能制造新的注册限流桶；经 nginx 的请求仍按真实来源
 * 记录与计数，伪造的 XFF 跳不生效。
 */

const SWITCH_KEY = 'sys.account.registerUser'
const REGISTER_PATH = '/dev-api/admin/system/index/register'
const SWITCH_PATH = `${REGISTER_PATH}/switch`
const LOGIN_LOG_PATH = '/dev-api/admin/system/sysLoginLog/1/10'
const TOO_FREQUENT = '注册尝试过于频繁，请稍后再试'
const FIELD_ERROR = '密码长度必须在 6 到 100 位之间'
// 限流用例直连后端：没有受信代理配置时，来源只能是对端地址，伪造头必须完全无效。
const API_ORIGIN = (process.env.API_ORIGIN || 'http://localhost:8800').replace(/\/$/, '')
const BACKEND_REGISTER_PATH = `${API_ORIGIN}/admin/system/index/register`

type LoginLogRow = { username?: string; ip?: string }

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

async function loginLogRows(
  request: APIRequestContext,
  token: string,
  username: string,
): Promise<LoginLogRow[]> {
  const response = await request.get(LOGIN_LOG_PATH, {
    headers: { token },
    params: { username },
  })
  const body = (await response.json()) as { code: number; data?: { records?: LoginLogRow[] } }
  expect(body.code, '查询登录日志').toBe(200)
  return body.data?.records ?? []
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

/** 直连后端：每次换一组伪造头，用来证明它们不会改变来源。 */
function registerFromBackend(
  request: APIRequestContext,
  attempt: number,
  body: ReturnType<typeof payload>,
) {
  return request.post(BACKEND_REGISTER_PATH, {
    headers: {
      'X-Real-IP': `203.0.113.${attempt}`,
      'X-Forwarded-For': `198.51.100.${attempt}`,
    },
    data: body,
  })
}

test.describe.serial('SEC-REGISTER 匿名注册', () => {
  // 每个用例用独立来源；关闭态那一次也计数，共用 IP 会让限流用例提前量到窗口。
  const randomHost = () => Math.floor(Math.random() * 200) + 20
  const closedIp = `198.51.100.${randomHost()}`

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

  test('直连后端换伪造头也不能新建注册限流桶', async ({ request }) => {
    const { token, id } = await closeSwitch(request)
    await setSwitch(request, token, id, true)
    const observed: string[] = []
    try {
      let throttled = false
      for (let attempt = 1; attempt <= 6 && !throttled; attempt += 1) {
        const response = await registerFromBackend(
          request,
          attempt,
          payload(`sec-ca-rate-${attempt}`, '12345'),
        )
        const body = (await response.json()) as { code: number; message?: string }
        observed.push(`#${attempt} ${body.code} ${body.message ?? ''}`)
        if (body.message === TOO_FREQUENT) {
          throttled = true
        } else {
          // 短密码让前几次都失败，同时证明失败尝试也计入同一来源。
          expect(body.code, observed.join(' | ')).toBe(201)
          expect(body.message, observed.join(' | ')).toBe(FIELD_ERROR)
        }
      }
      expect(
        throttled,
        `六次不同的伪造头必须累计到同一个桶，否则说明头被信任了：${observed.join(' | ')}`,
      ).toBe(true)
    } finally {
      await setSwitch(request, token, id, false)
    }
  })

  test('经 nginx 的登录仍记录真实来源，伪造 XFF 不生效', async ({ request }) => {
    const token = await adminLogin(request)
    const adminRow = (await loginLogRows(request, token, 'admin')).find(
      (row) => row.username === 'admin',
    )
    expect(adminRow?.ip, '管理员登录必须落一行带来源的登录日志').toBeTruthy()
    const realIp = adminRow?.ip as string

    // 同一账号两次失败登录、每次换一个伪造来源；经 nginx 时 X-Real-IP 会被覆盖、
    // XFF 只追加，解析器从右向左取真实对端，两条审计行必须是同一个真实来源。
    const username = `sec-ca-${Date.now() % 1_000_000}`
    for (const forged of ['203.0.113.81', '203.0.113.82']) {
      const response = await request.post('/dev-api/admin/system/index/login', {
        headers: { 'X-Real-IP': forged, 'X-Forwarded-For': forged },
        data: { username, password: 'wrong-password' },
      })
      expect(response.status(), '错误口令是认证失败').toBe(401)
    }

    const rows = await loginLogRows(request, token, username)
    expect(rows.length, `两次失败登录应各有一行：${JSON.stringify(rows)}`).toBe(2)
    expect(new Set(rows.map((row) => row.ip)).size, JSON.stringify(rows)).toBe(1)
    expect(rows[0].ip, JSON.stringify(rows)).toBe(realIp)
    expect(rows[0].ip, JSON.stringify(rows)).not.toContain('203.0.113.8')
  })
})
