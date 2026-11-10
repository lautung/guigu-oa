import { expect, test, type Page, type Route } from '@playwright/test'
import { e2eCookieURL } from './helpers/base-url'
import { fulfillCommonRequest } from './helpers/common-requests'
import { isKnownBrowserNoise } from './helpers/known-noise'
import { fulfillNoticeUnread } from './helpers/notice-mocks'

/**
 * 假期类型与发放页的 mock e2e（#280，ADR 0073）：类型表与发放流水渲染、
 * 新建类型落 POST 载荷、多人负数调整发放落 POST 载荷（每人一行由服务端展开）。
 */

const json = (route: Route, data: unknown, code = 200) =>
  route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code, data }) })

const leaveTypes = [
  { id: 1, name: '年假', balanceRequired: 1, overtimeCredit: 0, status: 1, sortNo: 1, remark: '' },
  { id: 2, name: '事假', balanceRequired: 0, overtimeCredit: 0, status: 1, sortNo: 2, remark: '' },
  { id: 3, name: '调休', balanceRequired: 1, overtimeCredit: 1, status: 0, sortNo: 3, remark: '' },
]

const ledger = {
  id: 21,
  userId: 11,
  username: 't280-alice',
  name: '甲',
  deptId: 2,
  deptName: '研发部',
  leaveTypeId: 1,
  leaveTypeName: '年假',
  entryType: 1,
  changeHours: -2.25,
  operatorName: '乙',
  remark: '冲抵错发',
  createTime: '2026-11-10 09:00:00',
}

const users = [
  { id: 11, username: 't280-alice', name: '甲', deptName: '研发部' },
  { id: 12, username: 't280-bob', name: '乙', deptName: '研发部' },
]

const attendanceMenus = [
  { path: '/business', title: '业务', type: 0, sort: 3 },
  { path: '/business/attendance', parentPath: '/business', title: '考勤管理', type: 0, sort: 1 },
  {
    path: '/business/attendance/leave',
    parentPath: '/business/attendance',
    title: '假期类型与发放',
    type: 1,
    sort: 4,
  },
  { path: '/system', title: '系统管理', type: 0, sort: 1 },
  { path: '/system/sysUser', parentPath: '/system', title: '用户管理', type: 1, sort: 1 },
]

interface State {
  typePosts: Record<string, unknown>[]
  grantPosts: Record<string, unknown>[]
  errors: string[]
}

async function setup(page: Page): Promise<State> {
  const state: State = { typePosts: [], grantPosts: [], errors: [] }
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
        roles: ['super_admin'],
        permissions: [
          'attendance:type:add',
          'attendance:type:edit',
          'attendance:type:remove',
          'attendance:ledger:grant',
        ],
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
    if (/^\/admin\/attendance\/leave-type\/\d+\/\d+$/.test(path)) {
      return json(route, { records: leaveTypes, total: leaveTypes.length, pages: 1 })
    }
    if (path === '/admin/attendance/leave-type/options') return json(route, leaveTypes)
    if (/^\/admin\/attendance\/ledger\/\d+\/\d+$/.test(path)) {
      return json(route, { records: [ledger], total: 1, pages: 1 })
    }
    if (path === '/admin/attendance/ledger/users') return json(route, users)
    if (path === '/admin/attendance/leave-type/save') {
      state.typePosts.push(request.postDataJSON() as Record<string, unknown>)
      return json(route, null)
    }
    if (path === '/admin/attendance/ledger/grant') {
      state.grantPosts.push(request.postDataJSON() as Record<string, unknown>)
      return json(route, 2)
    }
    if (await fulfillNoticeUnread(route, path)) return
    throw new Error(`Unexpected request ${request.method()} ${path}`)
  })
  await page.goto('/business/attendance/leave')
  await expect(page.getByRole('heading', { name: '假期类型与发放', exact: true })).toBeVisible()
  return state
}

test('假期类型与发放：类型表与流水表渲染 join 字段', async ({ page }) => {
  test.skip((page.viewportSize()?.width || 0) <= 768, '配置在桌面使用')
  const state = await setup(page)

  await expect(page.getByRole('cell', { name: '年假' }).first()).toBeVisible()
  await expect(page.getByRole('cell', { name: '需要余额' }).first()).toBeVisible()
  await expect(page.getByRole('cell', { name: '加班入账' })).toBeVisible()
  await expect(page.getByRole('cell', { name: '甲' })).toBeVisible()
  await expect(page.getByRole('cell', { name: '研发部' })).toBeVisible()
  await expect(page.getByRole('cell', { name: '发放', exact: true })).toBeVisible()
  await expect(page.getByRole('cell', { name: '-2.25' })).toBeVisible()
  await expect(page.getByText('冲抵错发')).toBeVisible()
  expect(state.errors).toEqual([])
})

test('新建类型：名称必填，保存落 POST 载荷', async ({ page }) => {
  test.skip((page.viewportSize()?.width || 0) <= 768, '配置在桌面使用')
  const state = await setup(page)

  await page.getByRole('button', { name: '新建类型' }).click()
  const dialog = page.getByRole('dialog')
  await expect(dialog).toBeVisible()
  await dialog.getByLabel('类型名称').fill('育儿假')
  await dialog.getByRole('button', { name: '确定' }).click()
  await expect(page.locator('.el-message')).toContainText('操作成功')

  expect(state.typePosts).toHaveLength(1)
  expect(state.typePosts[0]).toMatchObject({
    name: '育儿假',
    balanceRequired: 1,
    overtimeCredit: 0,
    status: 1,
    sortNo: 0,
    remark: '',
  })
  expect(state.errors).toEqual([])
})

test('发放/调整：选多人、负数小时，保存落 POST 载荷', async ({ page }) => {
  test.skip((page.viewportSize()?.width || 0) <= 768, '配置在桌面使用')
  const state = await setup(page)

  await page.getByRole('button', { name: '发放/调整' }).click()
  const dialog = page.getByRole('dialog')
  await expect(dialog).toBeVisible()

  await dialog.locator('.el-select').first().click()
  // 发放下拉只列启用且需要余额的类型（事假/调休不出现在可见列表）。
  await page.getByRole('option', { name: '年假' }).click()

  await dialog.locator('.el-select').nth(1).click()
  const userOptions = page.locator('.el-select-dropdown:visible')
  await userOptions.locator('.el-select-dropdown__item', { hasText: '甲（t280-alice）' }).click()
  await userOptions.locator('.el-select-dropdown__item', { hasText: '乙（t280-bob）' }).click()
  await dialog.locator('.el-input-number input').fill('-2.5')
  await dialog.getByLabel('备注').fill('冲抵错发')
  await dialog.getByRole('button', { name: '确定' }).click()
  await expect(page.locator('.el-message')).toContainText('已写入 2 条发放流水')

  expect(state.grantPosts).toHaveLength(1)
  expect(state.grantPosts[0]).toMatchObject({
    leaveTypeId: 1,
    userIds: [11, 12],
    changeHours: -2.5,
    remark: '冲抵错发',
  })
  expect(state.errors).toEqual([])
})
