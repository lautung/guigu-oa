import { request } from '@playwright/test'

import { apiBaseURL } from './helpers/env'

/**
 * 干净栈前置检查（2026-10-30 复盘）：
 *
 * 若干用例依赖干净演示栈的数据态（D1 断言 alice 的已发起列表只有本轮记录、
 * E5 按标记翻页查找）。在压测造数卷（`perf/seed.mjs` 的 1 万实例）上跑会以数据态
 * 失败并指向错误方向——先在入口拒绝，给出恢复命令。
 *
 * 信号：alice 的已发起总数。干净栈是 1–2 条（演示播种），造数卷上万；
 * 阈值取 2000，相当于上千轮接口套件残留才会误报。
 */
export default async function globalSetup(): Promise<void> {
  // 与 helpers/api.ts 的 Api.url 同一约定：完整 URL 拼接。baseURL + 以 '/' 开头的相对路径
  // 会按 URL 解析丢掉入口前缀（如 /dev-api），请求会打到错误路径。
  const context = await request.newContext({ timeout: 15_000 })
  try {
    const login = await context.post(`${apiBaseURL}/admin/system/index/login`, {
      data: { username: 'alice', password: '111111' },
    })
    if (!login.ok()) {
      throw new Error(
        `接口套件前置检查失败：登录 alice 返回 HTTP ${login.status()}。` +
          `确认 Compose 栈已起且演示种子已播种（当前入口 ${apiBaseURL}）。`,
      )
    }
    const envelope = (await login.json()) as { data?: { token?: string } }
    const token = envelope?.data?.token
    if (!token) {
      throw new Error('接口套件前置检查失败：登录响应没有 token，确认演示种子已播种。')
    }

    const started = await context.get(`${apiBaseURL}/admin/process/findStarted/1/1`, {
      headers: { token },
    })
    const startedEnvelope = (await started.json()) as { data?: { total?: number } }
    const total = Number(startedEnvelope?.data?.total ?? 0)
    if (total > 2000) {
      throw new Error(
        `接口套件前置检查失败：alice 的已发起记录 ${total} 条，疑似压测造数卷。` +
          '接口套件需要干净演示栈：docker compose down --volumes --remove-orphans && ' +
          'docker compose up -d --wait。',
      )
    }
  } finally {
    await context.dispose()
  }
}
