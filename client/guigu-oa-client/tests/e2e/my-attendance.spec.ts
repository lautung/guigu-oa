import { expect, test, type Page, type Route } from '@playwright/test'
import { e2eCookieURL } from './helpers/base-url'
import { fulfillCommonRequest } from './helpers/common-requests'
import { isKnownBrowserNoise } from './helpers/known-noise'
import { fulfillNoticeUnread } from './helpers/notice-mocks'

/**
 * 办事打卡页的 mock e2e（#279，ADR 0073）：登录即有、不套后台页面授予；
 * 手机宽度下今日班次、两张卡与两个按钮可用，点击上班打卡后卡面显示服务器时间。
 */

const json = (route: Route, data: unknown, code = 200) =>
  route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code, data }) })

const today = {
  date: '2026-11-10',
  weekday: 2,
  dayType: 1,
  workday: true,
  hasGroup: true,
  groupName: '研发组',
  shift: {
    id: 1,
    name: '标准班',
    startTime: '09:00:00',
    endTime: '18:00:00',
    lunchStart: '12:00:00',
    lunchEnd: '13:00:00',
    graceMinutes: 10,
  },
  clockInTime: null,
  clockOutTime: null,
}

interface State {
  punchIns: number
  errors: string[]
}

async function setup(page: Page): Promise<State> {
  const state: State = { punchIns: 0, errors: [] }
  page.on('pageerror', (error) => {
    if (!isKnownBrowserNoise(error.message)) state.errors.push(error.message)
  })
  await page.context().addCookies([
    { name: 'guigu_oa_client_token', value: 'attendance-token', url: e2eCookieURL() },
    { name: 'guigu_oa_client_refresh_token', value: 'attendance-refresh', url: e2eCookieURL() },
    { name: 'guigu_oa_client_entry', value: 'attendance-entry', url: e2eCookieURL() },
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
    if (path === '/admin/attendance/my/today') return json(route, today)
    if (path === '/admin/attendance/my/punch/in') {
      state.punchIns += 1
      return json(route, { ...today, clockInTime: '08:30:00' })
    }
    if (path === '/admin/attendance/my/punch/out') {
      return json(route, { ...today, clockInTime: '08:30:00', clockOutTime: '18:10:00' })
    }
    if (await fulfillNoticeUnread(route, path)) return
    throw new Error(`Unexpected request ${request.method()} ${path}`)
  })
  await page.goto('/work/my-attendance')
  await expect(page.getByRole('heading', { name: '今日打卡', exact: true })).toBeVisible()
  return state
}

test('手机宽度下打卡页展示今日班次与两卡状态，并可完成上班打卡', async ({ page }) => {
  const state = await setup(page)

  await expect(page.getByText('标准班')).toBeVisible()
  await expect(page.getByText('09:00 - 18:00')).toBeVisible()
  await expect(page.getByText('上班打卡', { exact: true })).toBeVisible()
  await expect(page.getByText('下班打卡', { exact: true })).toBeVisible()
  await expect(page.getByText('未打卡').first()).toBeVisible()

  await page.getByRole('button', { name: '上班打卡' }).click()

  await expect(page.getByText('已打卡 08:30')).toBeVisible()
  await expect(page.locator('.el-message')).toContainText('上班打卡成功')
  expect(state.punchIns).toBe(1)
  expect(state.errors).toEqual([])
})
