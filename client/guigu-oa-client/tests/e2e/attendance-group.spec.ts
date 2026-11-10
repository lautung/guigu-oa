import { expect, test, type Page, type Route } from '@playwright/test'
import { fulfillCommonRequest } from './helpers/common-requests'
import { e2eCookieURL } from './helpers/base-url'
import { isKnownBrowserNoise } from './helpers/known-noise'
import { fulfillNoticeUnread } from './helpers/notice-mocks'

/**
 * 考勤组与班次页的 mock e2e（#278，ADR 0073）：侧栏可达、三区块渲染、
 * 新建考勤组（选班次 + 选成员）落 POST 载荷、四个占位壳页空态、
 * 未授予用户被守卫回落工作台。
 */

const json = (route: Route, data: unknown, code = 200) =>
  route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code, data }) })

const shift = {
  id: 1,
  name: '标准班',
  startTime: '09:00:00',
  endTime: '18:00:00',
  lunchStart: '12:00:00',
  lunchEnd: '13:00:00',
  graceMinutes: 10,
  remark: '研发',
  createTime: '2026-11-10 09:00:00',
}

const group = {
  id: 5,
  name: '研发组',
  shiftId: 1,
  shiftName: '标准班',
  workdays: '1,2,3,4,5',
  memberCount: 2,
  remark: '',
  createTime: '2026-11-10 09:00:00',
}

const holiday = { id: 2, holidayDate: '2026-10-01', name: '国庆', dayType: 1 }

const punch = {
  id: 9,
  userId: 11,
  username: 't278-alice',
  name: '甲',
  deptId: 2,
  deptName: '研发部',
  punchDate: '2026-11-10',
  slot: 2,
  punchTime: '2026-11-10 18:30:00',
  source: 2,
  operatorName: '乙',
}

const members = [
  { id: 1, userId: 11, username: 't278-alice', name: '甲' },
  { id: 2, userId: 12, username: 't278-bob', name: '乙' },
]

const statRows = [
  {
    userId: 11,
    username: 't283-normal',
    name: '甲',
    deptId: 2,
    deptName: '研发部',
    requiredDays: 20,
    actualDays: 19,
    lateCount: 3,
    earlyCount: 2,
    missingDays: 1,
    leaveDays: [
      { leaveTypeName: '年假', days: 2 },
      { leaveTypeName: '事假', days: 1 },
    ],
    overtimeHours: 4.5,
  },
  {
    userId: 12,
    username: 't283-leave',
    name: '乙',
    deptId: 2,
    deptName: '研发部',
    requiredDays: 20,
    actualDays: 17,
    lateCount: 0,
    earlyCount: 0,
    missingDays: 0,
    leaveDays: [{ leaveTypeName: '年假', days: 3 }],
    overtimeHours: 0,
  },
]

const statMonth = {
  month: '2026-02',
  days: [
    {
      date: '2026-02-05',
      weekday: 4,
      dayType: 1,
      workday: true,
      hasGroup: true,
      status: 4,
      pending: false,
      clockInTime: '09:15:00',
      clockOutTime: '17:40:00',
    },
    {
      date: '2026-02-12',
      weekday: 4,
      dayType: 3,
      workday: false,
      hasGroup: true,
      status: 8,
      pending: false,
    },
  ],
}

const users = [
  { id: 11, username: 't278-alice', name: '甲' },
  { id: 12, username: 't278-bob', name: '乙' },
]

const attendanceMenus = [
  { path: '/system', title: '系统管理', type: 0, sort: 1 },
  { path: '/system/sysUser', parentPath: '/system', title: '用户管理', type: 1, sort: 1 },
  { path: '/business', title: '业务', type: 0, sort: 3 },
  { path: '/business/attendance', parentPath: '/business', title: '考勤管理', type: 0, sort: 1 },
  ...['group', 'record', 'repair', 'leave', 'stat'].map((key, index) => ({
    path: `/business/attendance/${key}`,
    parentPath: '/business/attendance',
    title: ['考勤组与班次', '打卡记录', '补卡处理', '假期类型与发放', '月度统计'][index],
    type: 1,
    sort: index + 1,
  })),
]

interface State {
  groupPosts: Record<string, unknown>[]
  punchQueries: Record<string, string>[]
  errors: string[]
}

async function setup(page: Page, granted = true): Promise<State> {
  const state: State = { groupPosts: [], punchQueries: [], errors: [] }
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
        name: granted ? 'Alice' : 'Bob',
        roles: granted ? ['super_admin'] : [],
        permissions: granted
          ? ['attendance:group:add', 'attendance:group:edit', 'attendance:group:remove']
          : [],
        menus: granted
          ? attendanceMenus
          : [
              { path: '/system', title: '系统管理', type: 0, sort: 1 },
              { path: '/business', title: '业务', type: 0, sort: 3 },
            ],
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
    // 点击「系统管理」一级药丸会落到该模块第一个已授页（用户管理），顺带 mock 其读面。
    if (/^\/admin\/system\/sysUser\/\d+\/\d+$/.test(path)) {
      return json(route, { records: [], total: 0, pages: 1 })
    }
    if (path === '/admin/system/sysUser/depts') return json(route, [])
    if (path === '/admin/system/sysUser/posts') return json(route, [])
    if (/^\/admin\/attendance\/shift\/\d+\/\d+$/.test(path)) {
      return json(route, { records: [shift], total: 1, pages: 1 })
    }
    if (path === '/admin/attendance/shift/options') return json(route, [shift])
    if (/^\/admin\/attendance\/group\/\d+\/\d+$/.test(path)) {
      return json(route, { records: [group], total: 1, pages: 1 })
    }
    if (path === '/admin/attendance/group/userOptions') return json(route, users)
    if (/^\/admin\/attendance\/group\/\d+\/members$/.test(path)) return json(route, members)
    if (/^\/admin\/attendance\/holiday\/\d+\/\d+$/.test(path)) {
      return json(route, { records: [holiday], total: 1, pages: 1 })
    }
    if (/^\/admin\/attendance\/punch\/\d+\/\d+$/.test(path)) {
      state.punchQueries.push(Object.fromEntries(new URL(request.url()).searchParams))
      return json(route, { records: [punch], total: 1, pages: 1 })
    }
    if (/^\/admin\/attendance\/stat\/\d+\/\d+$/.test(path)) {
      return json(route, { records: statRows, total: statRows.length, pages: 1 })
    }
    if (path === '/admin/attendance/stat/daily') {
      return json(route, statMonth)
    }
    if (path === '/admin/attendance/group/save') {
      state.groupPosts.push(request.postDataJSON() as Record<string, unknown>)
      return json(route, null)
    }
    if (await fulfillNoticeUnread(route, path)) return
    throw new Error(`Unexpected request ${request.method()} ${path}`)
  })
  await page.goto('/work/pending')
  await expect(page.getByRole('heading', { name: '待处理', exact: true })).toBeVisible()
  return state
}

async function openAttendancePage(page: Page): Promise<void> {
  const link = page.locator('.app-sidebar').getByRole('link', { name: '考勤组与班次', exact: true })
  if ((await link.count()) === 0) {
    await page.locator('.top-nav-item', { hasText: '业务' }).click()
  }
  await link.click()
  await expect(page.getByRole('heading', { name: '考勤组与班次', exact: true })).toBeVisible()
}

test('侧栏进入考勤组与班次：班次/考勤组/节假日三区块渲染', async ({ page }) => {
  test.skip((page.viewportSize()?.width || 0) <= 768, '配置在桌面使用')
  const state = await setup(page)
  await openAttendancePage(page)

  await expect(page.getByRole('cell', { name: '标准班' }).first()).toBeVisible()
  await expect(page.getByRole('cell', { name: '研发组' })).toBeVisible()
  await expect(page.getByRole('cell', { name: '周一、周二、周三、周四、周五' })).toBeVisible()
  await expect(page.getByRole('cell', { name: '国庆' })).toBeVisible()

  // 成员候选端点已经取过：选项来自考勤自有 mapper，不依赖用户管理页权限。
  expect(state.errors).toEqual([])
})

test('新建考勤组：选班次、勾工作日、选成员，保存落 POST 载荷', async ({ page }) => {
  test.skip((page.viewportSize()?.width || 0) <= 768, '配置在桌面使用')
  const state = await setup(page)
  await openAttendancePage(page)

  await page.getByRole('button', { name: '新建考勤组' }).click()
  const dialog = page.getByRole('dialog')
  await expect(dialog).toBeVisible()

  await dialog.getByLabel('考勤组名称').fill('客服组')
  await dialog.locator('.el-select').first().click()
  await page.locator('.el-select-dropdown__item', { hasText: '标准班' }).first().click()
  // 默认勾选周一至五，补勾周六。
  await dialog.getByText('周六', { exact: true }).click()
  await dialog.locator('.el-select').nth(1).click()
  await page.locator('.el-select-dropdown__item', { hasText: '甲（t278-alice）' }).click()

  await dialog.getByRole('button', { name: '确定' }).click()
  await expect(page.locator('.el-message')).toContainText('操作成功')

  expect(state.groupPosts).toHaveLength(1)
  expect(state.groupPosts[0]).toMatchObject({
    name: '客服组',
    shiftId: 1,
    workdays: [1, 2, 3, 4, 5, 6],
    memberUserIds: [11],
    remark: '',
  })
  expect(state.errors).toEqual([])
})

test('月度统计页按人汇总并可下钻逐日明细', async ({ page }) => {
  test.skip((page.viewportSize()?.width || 0) <= 768, '配置在桌面使用')
  const state = await setup(page)

  await page.goto('/business/attendance/stat')
  await expect(page.getByRole('heading', { name: '月度统计', exact: true })).toBeVisible()
  await expect(page.getByRole('cell', { name: '甲', exact: true })).toBeVisible()
  await expect(page.getByRole('cell', { name: '研发部' }).first()).toBeVisible()
  await expect(page.getByRole('columnheader', { name: '年假（天）' })).toBeVisible()
  await expect(page.getByRole('cell', { name: '4.5' })).toBeVisible()

  await page.getByRole('button', { name: '明细' }).first().click()
  await expect(page.locator('.daily-title')).toContainText('逐日明细')
  await expect(page.getByText('迟到且早退', { exact: true })).toBeVisible()
  expect(state.errors).toEqual([])
})

test('打卡记录页展示 join 姓名/部门并按人筛选重查', async ({ page }) => {
  test.skip((page.viewportSize()?.width || 0) <= 768, '配置在桌面使用')
  const state = await setup(page)

  await page.goto('/business/attendance/record')
  await expect(page.getByRole('heading', { name: '打卡记录', exact: true })).toBeVisible()
  await expect(page.getByRole('cell', { name: '甲' })).toBeVisible()
  await expect(page.getByRole('cell', { name: '研发部' })).toBeVisible()
  await expect(page.getByRole('cell', { name: '下班卡' })).toBeVisible()
  await expect(page.getByRole('cell', { name: '补卡' })).toBeVisible()
  await expect(page.getByRole('cell', { name: '乙' })).toBeVisible()

  await page.getByPlaceholder('姓名或用户名').fill('甲')
  await page.locator('.filter-form').getByRole('button', { name: '搜索' }).click()
  await expect.poll(() => state.punchQueries.at(-1)?.name).toBe('甲')
  expect(state.errors).toEqual([])
})

test('未授予考勤页面的用户被守卫回落工作台', async ({ page }) => {
  test.skip((page.viewportSize()?.width || 0) <= 768, '配置在桌面使用')
  await setup(page, false)

  await page.goto('/business/attendance/group')
  await expect(page).not.toHaveURL('/business/attendance/group')
  await expect(
    page
      .getByRole('heading', { name: '待处理', exact: true })
      .or(page.getByRole('heading', { name: '工作台', exact: true })),
  ).toBeVisible()
})
