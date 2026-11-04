import type { Route } from '@playwright/test'

/**
 * 外壳/登录页在 2026-11 波新增的公共读请求（#251 字典、#253 注册开关）。
 * 各 e2e spec 的 dev-api mock 先放行这两类，再进入各自的业务 allowlist。
 */
const DICTS: Record<string, { dictLabel: string; dictValue: string }[]> = {
  sys_notice_type: [
    { dictLabel: '通知', dictValue: '1' },
    { dictLabel: '公告', dictValue: '2' },
  ],
  sys_notice_status: [
    { dictLabel: '草稿', dictValue: '0' },
    { dictLabel: '已发布', dictValue: '1' },
    { dictLabel: '已撤回', dictValue: '2' },
  ],
  sys_job_status: [
    { dictLabel: '成功', dictValue: '1' },
    { dictLabel: '失败', dictValue: '2' },
    { dictLabel: '跳过', dictValue: '3' },
  ],
  sys_login_status: [
    { dictLabel: '成功', dictValue: '0' },
    { dictLabel: '失败', dictValue: '1' },
    { dictLabel: '锁定', dictValue: '2' },
    { dictLabel: '解锁', dictValue: '3' },
  ],
}

const envelope = (data: unknown) => JSON.stringify({ code: 200, message: '', data })

export async function fulfillCommonRequest(route: Route, path: string): Promise<boolean> {
  if (path === '/admin/system/index/register/switch') {
    await route.fulfill({ contentType: 'application/json', body: envelope({ enabled: false }) })
    return true
  }
  if (path === '/admin/system/dict/data/batch') {
    const types = (new URL(route.request().url()).searchParams.get('types') || '')
      .split(',')
      .filter(Boolean)
    const data: Record<string, unknown> = {}
    for (const type of types) data[type] = DICTS[type] || []
    await route.fulfill({ contentType: 'application/json', body: envelope(data) })
    return true
  }
  return false
}
