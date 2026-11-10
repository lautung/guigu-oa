import { expect, test, type Page, type Route } from '@playwright/test'
import { e2eCookieURL } from './helpers/base-url'
import { fulfillCommonRequest } from './helpers/common-requests'
import { isKnownBrowserNoise } from './helpers/known-noise'
import { fulfillNoticeUnread } from './helpers/notice-mocks'

/**
 * 补卡处理页的 mock e2e（#281，ADR 0073）：补卡页授予可达、成员候选按数据范围、
 * 查询当日回填两个槽位、只补下班时间落 POST 载荷并回显补卡后的有效时刻。
 */

const json = (route: Route, data: unknown, code = 200) =>
  route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code, data }) })

const users = [
  { id: 11, username: 't281-alice', name: '甲', deptId: 1, deptName: '研发部' },
  { id: 12, username: 't281-bob', name: '乙', deptId: 1, deptName: '研发部' },
]

const day = {
  date: '2026-03-09',
  weekday: 1,
  dayType: 1,
  workday: true,
  hasGroup: true,
  status: 5,
  clockInTime: '09:00:00',
  clockOutTime: null,
}

const punch = {
  id: 9,
  userId: 11,
  username: 't281-alice',
  name: '甲',
  deptId: 1,
  deptName: '研发部',
  punchDate: '2026-03-09',
  slot: 1,
  punchTime: '2026-03-09 09:00:00',
  source: 1,
  operatorName: '',
}

const attendanceMenus = [
  { path: '/business', title: '业务', type: 0, sort: 3 },
  { path: '/business/attendance', parentPath: '/business', title: '考勤管理', type: 0, sort: 1 },
  {
    path: '/business/attendance/repair',
    parentPath: '/business/attendance',
    title: '补卡处理',
    type: 1,
    sort: 3,
  },
]

interface State {
  repairPosts: Record<string, unknown>[]
  errors: string[]
}

async function setup(page: Page): Promise<State> {
  const state: State = { repairPosts: [], errors: [] }
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
      return json(route, {
        name: 'Alice',
        roles: ['attendance_admin'],
        permissions: ['attendance:record:repair'],
        menus: attendanceMenus,
      })
    }
    if (
      path.startsWith('/admin/process/findPending/') ||
      path.startsWith('/admin/process/findProcessed/') ||
      path.startsWith('/admin/process/findStarted/') ||
      path.startsWith('/admin/process/findCarbonCopied/')
    ) {
      return json(route, { records: [], total: 0, pages: 0 })
    }
    if (path === '/admin/attendance/repair/users') return json(route, users)
    if (/^\/admin\/attendance\/repair\/\d+\/\d+$/.test(path)) {
      return json(route, { records: [punch], total: 1, pages: 1 })
    }
    if (path === '/admin/attendance/repair/day') return json(route, day)
    if (path === '/admin/attendance/repair/save' && request.method() === 'POST') {
      state.repairPosts.push(request.postDataJSON() as Record<string, unknown>)
      return json(route, { ...day, status: 1, clockOutTime: '18:30:00' })
    }
    if (await fulfillNoticeUnread(route, path)) return
    throw new Error(`Unexpected request ${request.method()} ${path}`)
  })
  await page.goto('/business/attendance/repair')
  await expect(page.getByRole('heading', { name: '补卡处理', exact: true })).toBeVisible()
  return state
}

test('补卡页按成员+日期查询并只补一个槽位', async ({ page }) => {
  const state = await setup(page)

  await expect(page.getByText('近期打卡流水')).toBeVisible()
  await expect(page.getByText('t281-alice').first()).toBeVisible()

  const repairCard = page.locator('.repair-card')
  await repairCard.getByRole('combobox', { name: '成员' }).click()
  await page.getByText('研发部 / 甲（t281-alice）').click()
  const dateInput = repairCard.getByRole('combobox', { name: '日期' })
  await dateInput.fill('2026-03-09')
  await dateInput.press('Enter')
  await page.getByRole('button', { name: '查询当日' }).click()

  await expect(page.getByText('当前上班 09:00 / 下班 —')).toBeVisible()
  await expect(page.getByText('缺卡').first()).toBeVisible()

  // 下班槽位为空时只提交上班时间（服务端按「只补一槽」追加一行）。
  await page.getByRole('button', { name: '保存补卡' }).click()

  await expect(page.locator('.el-message')).toContainText('补卡成功')
  await expect(page.getByText('当前上班 09:00 / 下班 18:30')).toBeVisible()
  expect(state.repairPosts).toEqual([
    {
      userId: 11,
      repairDate: '2026-03-09',
      clockInTime: '09:00:00',
    },
  ])
  expect(state.errors).toEqual([])
})
