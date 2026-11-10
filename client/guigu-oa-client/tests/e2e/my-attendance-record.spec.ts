import { expect, test, type Page, type Route } from '@playwright/test'
import { e2eCookieURL } from './helpers/base-url'
import { fulfillCommonRequest } from './helpers/common-requests'
import { isKnownBrowserNoise } from './helpers/known-noise'
import { fulfillNoticeUnread } from './helpers/notice-mocks'

/**
 * 办事「考勤记录」页的 mock e2e（#281，ADR 0073）：登录即有、不套后台页面授予；
 * 手机宽度下当月逐日状态可读，九枚举、未打卡与未来日期同屏呈现。
 */

const json = (route: Route, data: unknown, code = 200) =>
  route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code, data }) })

const month = {
  month: '2026-03',
  days: [
    {
      date: '2026-03-02',
      weekday: 1,
      dayType: 1,
      workday: true,
      status: 1,
      clockInTime: '09:00:00',
      clockOutTime: '18:00:00',
    },
    {
      date: '2026-03-03',
      weekday: 2,
      dayType: 1,
      workday: true,
      status: 2,
      clockInTime: '09:15:00',
      clockOutTime: '18:00:00',
    },
    {
      date: '2026-03-04',
      weekday: 3,
      dayType: 1,
      workday: true,
      status: 3,
      clockInTime: '09:00:00',
      clockOutTime: '17:40:00',
    },
    {
      date: '2026-03-05',
      weekday: 4,
      dayType: 1,
      workday: true,
      status: 4,
      clockInTime: '09:15:00',
      clockOutTime: '17:40:00',
    },
    {
      date: '2026-03-06',
      weekday: 5,
      dayType: 1,
      workday: true,
      status: 5,
      clockInTime: '09:15:00',
      clockOutTime: null,
    },
    { date: '2026-03-07', weekday: 6, dayType: 1, workday: true, status: 6, leaveTypeName: '年假' },
    { date: '2026-03-08', weekday: 7, dayType: 2, workday: false, status: 7 },
    { date: '2026-03-09', weekday: 1, dayType: 3, workday: false, status: 8 },
    { date: '2026-03-10', weekday: 2, dayType: 4, workday: true, status: 9 },
    {
      date: '2026-03-11',
      weekday: 3,
      dayType: 1,
      workday: true,
      pending: true,
      status: null,
      clockInTime: '09:00:00',
    },
    { date: '2026-03-12', weekday: 4, dayType: 1, workday: true, pending: false, status: null },
  ],
}

const balances = [
  { leaveTypeId: 1, leaveTypeName: '年假', status: 1, availableHours: 5.5, pendingHours: 2 },
  { leaveTypeId: 3, leaveTypeName: '调休', status: 1, availableHours: -1, pendingHours: 0 },
]

interface State {
  errors: string[]
}

async function setup(page: Page): Promise<State> {
  const state: State = { errors: [] }
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
    if (path === '/admin/attendance/my/records') return json(route, month)
    if (path === '/admin/attendance/my/balances') return json(route, balances)
    if (await fulfillNoticeUnread(route, path)) return
    throw new Error(`Unexpected request ${request.method()} ${path}`)
  })
  await page.goto('/work/my-attendance-record')
  await expect(page.getByRole('heading', { name: '考勤记录', exact: true })).toBeVisible()
  return state
}

test('手机宽度下考勤记录展示当月逐日状态与未打卡', async ({ page }) => {
  const state = await setup(page)

  const statusTags = page.locator('.day-status')
  await expect(page.getByText('2026-03').first()).toBeVisible()
  await expect(statusTags.filter({ hasText: '正常' }).first()).toBeVisible()
  await expect(statusTags.filter({ hasText: '缺卡' }).first()).toBeVisible()
  await expect(statusTags.filter({ hasText: '请假·年假' }).first()).toBeVisible()
  await expect(statusTags.filter({ hasText: '补班日' }).first()).toBeVisible()
  await expect(statusTags.filter({ hasText: '未打卡' }).first()).toBeVisible()

  // 余额区：可用小时、在途与负余额在手机宽度下也可读。
  await expect(page.getByRole('heading', { name: '假期余额', exact: true })).toBeVisible()
  await expect(page.locator('.balance-item', { hasText: '年假' })).toContainText('5.5 小时')
  await expect(page.locator('.balance-item', { hasText: '年假' })).toContainText('在途 2')
  await expect(page.locator('.balance-item.is-negative', { hasText: '调休' })).toContainText(
    '-1 小时',
  )

  expect(state.errors).toEqual([])
})
