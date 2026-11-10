import { expect, test, type Page, type Route } from '@playwright/test'
import { e2eCookieURL } from './helpers/base-url'
import { fulfillCommonRequest } from './helpers/common-requests'
import { isKnownBrowserNoise } from './helpers/known-noise'
import { fulfillNoticeUnread } from './helpers/notice-mocks'

/**
 * 办事「我的申请」的 mock e2e（#282，ADR 0073/0067）：请假/加班两页签渲染、
 * 被拒单据可重提且请求带客户端幂等键、新建请假对话框可用；手机宽度下页面照常可读。
 */

const json = (route: Route, data: unknown, code = 200) =>
  route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code, data }) })

const leaveTypes = [
  { id: 1, name: '年假', balanceRequired: 1, availableHours: 8 },
  { id: 2, name: '事假', balanceRequired: 0, availableHours: null },
]

const submittedLeave = {
  id: 501,
  leaveTypeId: 1,
  leaveTypeName: '年假',
  startDate: '2026-12-07',
  endDate: '2026-12-08',
  days: 2,
  hours: 16,
  reason: '回家',
  status: 1,
  submitTime: '2026-11-10 09:00:00',
  approval: {
    processId: 9001,
    status: 1,
    currentApprover: '张三',
    description: '等待张三审批',
  },
}

const rejectedLeave = {
  id: 502,
  leaveTypeId: 2,
  leaveTypeName: '事假',
  startDate: '2026-12-09',
  endDate: '2026-12-09',
  days: 1,
  hours: 8,
  reason: '办事',
  status: -1,
  finishTime: '2026-11-10 10:00:00',
}

const draftOvertime = {
  id: 601,
  overtimeDate: '2026-12-12',
  startTime: '18:00:00',
  endTime: '20:00:00',
  hours: 0,
  reason: '上线',
  status: 0,
}

interface State {
  submitBodies: Record<string, unknown>[]
  errors: string[]
}

async function setup(page: Page): Promise<State> {
  const state: State = { submitBodies: [], errors: [] }
  page.on('pageerror', (error) => {
    if (!isKnownBrowserNoise(error.message)) state.errors.push(error.message)
  })
  await page.context().addCookies([
    { name: 'guigu_oa_client_token', value: 'applications-token', url: e2eCookieURL() },
    { name: 'guigu_oa_client_refresh_token', value: 'applications-refresh', url: e2eCookieURL() },
    { name: 'guigu_oa_client_entry', value: 'applications-entry', url: e2eCookieURL() },
  ])
  await page.context().route('**/dev-api/**', async (route) => {
    const request = route.request()
    const path = new URL(request.url()).pathname.replace(/^\/dev-api/, '')
    if (await fulfillCommonRequest(route, path)) return
    if (path === '/admin/system/index/info') {
      return json(route, { name: 'Alice', roles: [], permissions: [], menus: [] })
    }
    if (
      path.startsWith('/admin/process/findPending/') ||
      path.startsWith('/admin/process/findProcessed/') ||
      path.startsWith('/admin/process/findStarted/') ||
      path.startsWith('/admin/process/findCarbonCopied/')
    ) {
      return json(route, { records: [], total: 0, pages: 0 })
    }
    if (/^\/admin\/attendance\/my\/leave\/\d+\/\d+$/.test(path)) {
      return json(route, { records: [submittedLeave, rejectedLeave], total: 2, pages: 1 })
    }
    if (/^\/admin\/attendance\/my\/overtime\/\d+\/\d+$/.test(path)) {
      return json(route, { records: [draftOvertime], total: 1, pages: 1 })
    }
    if (path === '/admin/attendance/my/leave/types') return json(route, leaveTypes)
    if (path === '/admin/attendance/my/leave/502/submit') {
      state.submitBodies.push(request.postDataJSON() as Record<string, unknown>)
      return json(route, {
        ...rejectedLeave,
        status: 1,
        submitTime: '2026-11-10 11:00:00',
        approval: { processId: 9002, status: 1, currentApprover: '李四' },
      })
    }
    if (await fulfillNoticeUnread(route, path)) return
    throw new Error(`Unexpected request ${request.method()} ${path}`)
  })
  await page.goto('/work/my-applications')
  await expect(page.getByRole('heading', { name: '我的申请', exact: true })).toBeVisible()
  return state
}

test('桌面：两页签渲染请假与加班状态', async ({ page }) => {
  test.skip((page.viewportSize()?.width || 0) <= 768, '表单交互在桌面覆盖')
  const state = await setup(page)

  await expect(page.getByText('年假')).toBeVisible()
  await expect(page.getByText('审批中')).toBeVisible()
  await expect(page.getByText('已拒绝')).toBeVisible()
  await expect(page.getByText('回家')).toBeVisible()
  await expect(page.getByRole('button', { name: '新建请假' })).toBeVisible()

  await page.getByRole('tab', { name: '加班' }).click()
  await expect(page.getByText('提交后计算')).toBeVisible()
  await expect(page.getByText('2026-12-12 18:00-20:00')).toBeVisible()
  await expect(page.getByRole('button', { name: '新建加班' })).toBeVisible()
  expect(state.errors).toEqual([])
})

test('桌面：被拒请假可重提，请求带客户端幂等键', async ({ page }) => {
  test.skip((page.viewportSize()?.width || 0) <= 768, '表单交互在桌面覆盖')
  const state = await setup(page)

  const rejectedCard = page.locator('.apply-card', { hasText: '办事' })
  await expect(rejectedCard.getByRole('button', { name: '编辑' })).toBeVisible()
  await rejectedCard.getByRole('button', { name: '提交' }).click()

  await expect(page.locator('.el-message')).toContainText('已提交审批')
  await expect(rejectedCard.getByText('审批中')).toBeVisible()
  expect(state.submitBodies).toHaveLength(1)
  const key = state.submitBodies[0].idempotencyKey
  expect(typeof key).toBe('string')
  expect(String(key).length).toBeGreaterThan(8)
  expect(String(key).length).toBeLessThanOrEqual(64)
  expect(state.errors).toEqual([])
})

test('桌面：新建请假对话框可打开并取消', async ({ page }) => {
  test.skip((page.viewportSize()?.width || 0) <= 768, '表单交互在桌面覆盖')
  const state = await setup(page)

  await page.getByRole('button', { name: '新建请假' }).click()
  const dialog = page.getByRole('dialog')
  await expect(dialog).toBeVisible()
  await expect(dialog.getByText('天数与小时数在提交时按当时班次与节假日日历定稿')).toBeVisible()
  await dialog.getByRole('button', { name: '取消' }).click()
  await expect(dialog).toBeHidden()
  expect(state.errors).toEqual([])
})

test('手机宽度：我的申请照常可读', async ({ page }) => {
  test.skip((page.viewportSize()?.width || 0) > 768, '只在手机宽度跑')
  const state = await setup(page)

  await expect(page.getByRole('heading', { name: '我的申请', exact: true })).toBeVisible()
  await expect(page.getByRole('tab', { name: '请假' })).toBeVisible()
  await expect(page.getByRole('tab', { name: '加班' })).toBeVisible()
  await expect(page.getByText('审批中')).toBeVisible()
  await expect(page.getByText('已拒绝')).toBeVisible()
  expect(state.errors).toEqual([])
})
