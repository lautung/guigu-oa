import { expect, test, type Page, type Route } from '@playwright/test'
import { e2eCookieURL } from './helpers/base-url'
import { isKnownBrowserNoise } from './helpers/known-noise'

const json = (route: Route, data: unknown, code = 200) =>
  route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code, data }) })

const operRecord = {
  id: 1,
  title: '用户管理·新增用户',
  businessType: 1,
  method: 'SysUserController#save',
  requestMethod: 'POST',
  operName: 'admin',
  deptName: '研发部',
  operUrl: '/admin/system/sysUser/save',
  operIp: '127.0.0.1',
  operParam: '{"username":"alice","password":"***"}',
  resultCode: 200,
  resultMessage: '操作成功',
  status: 0,
  errorMsg: '',
  costTime: 12,
  createTime: '2026-01-01 10:00:00',
}

const lockedLoginRecord = {
  id: 7,
  username: 'alice',
  ip: '127.0.0.1',
  browser: 'Chrome',
  os: 'macOS',
  status: 2,
  msg: '账号锁定',
  createTime: '2026-01-02 10:00:00',
}

interface State {
  operRows: (typeof operRecord)[]
  loginRows: (typeof lockedLoginRecord)[]
  cleaned: string[]
  unlocked: string[]
  errors: string[]
}

async function setup(page: Page): Promise<State> {
  const state: State = {
    operRows: [{ ...operRecord }],
    loginRows: [{ ...lockedLoginRecord }],
    cleaned: [],
    unlocked: [],
    errors: [],
  }
  page.on('pageerror', (error) => {
    if (!isKnownBrowserNoise(error.message)) state.errors.push(error.message)
  })
  await page.context().addCookies([
    { name: 'guigu_oa_client_token', value: 'audit-token', url: e2eCookieURL() },
    { name: 'guigu_oa_client_refresh_token', value: 'audit-refresh', url: e2eCookieURL() },
    { name: 'guigu_oa_client_entry', value: 'audit-entry', url: e2eCookieURL() },
  ])
  await page.context().route('**/dev-api/**', async (route) => {
    const request = route.request()
    const path = new URL(request.url()).pathname.replace(/^\/dev-api/, '')
    if (path === '/admin/system/index/info') {
      return json(route, {
        name: 'Alice',
        roles: ['super_admin'],
        permissions: [
          'system:operLog:remove',
          'system:operLog:clean',
          'system:loginLog:remove',
          'system:loginLog:clean',
          'system:loginLog:unlock',
        ],
        menus: [
          { path: '/system', title: '系统管理', type: 0, sort: 1 },
          {
            path: '/system/log',
            parentPath: '/system',
            title: '日志管理',
            type: 0,
            sort: 11,
          },
          {
            path: '/system/log/operLog',
            parentPath: '/system/log',
            title: '操作日志',
            type: 1,
            sort: 1,
          },
          {
            path: '/system/log/loginLog',
            parentPath: '/system/log',
            title: '登录日志',
            type: 1,
            sort: 2,
          },
        ],
      })
    }
    if (path.startsWith('/admin/process/findPending/')) {
      return json(route, { records: [], total: 0, pages: 0 })
    }
    if (/^\/admin\/system\/sysOperLog\/\d+\/\d+$/.test(path)) {
      return json(route, { records: state.operRows, total: state.operRows.length, pages: 1 })
    }
    if (path === '/admin/system/sysOperLog/get/1') return json(route, operRecord)
    if (path === '/admin/system/sysOperLog/clean') {
      state.cleaned.push('oper')
      state.operRows = []
      return json(route, null)
    }
    if (/^\/admin\/system\/sysLoginLog\/\d+\/\d+$/.test(path)) {
      return json(route, { records: state.loginRows, total: state.loginRows.length, pages: 1 })
    }
    if (path.startsWith('/admin/system/sysLoginLog/unlock/')) {
      const username = decodeURIComponent(path.split('/').pop() || '')
      state.unlocked.push(username)
      state.loginRows = [
        { id: 8, username, status: 3, msg: '手动解锁', createTime: '2026-01-03 10:00:00' },
        ...state.loginRows,
      ]
      return json(route, null)
    }
    if (path === '/admin/system/sysLoginLog/clean') {
      state.cleaned.push('login')
      state.loginRows = []
      return json(route, null)
    }
    throw new Error(`Unexpected request ${request.method()} ${path}`)
  })
  await page.goto('/work/pending')
  await expect(page.getByRole('heading', { name: '待处理', exact: true })).toBeVisible()
  return state
}

async function openFromSidebar(page: Page, title: string): Promise<void> {
  await page.getByRole('link', { name: title, exact: true }).click()
  await expect(page.getByRole('heading', { name: title, exact: true })).toBeVisible()
}

test('侧栏进入操作日志：列表、详情弹窗与清空确认', async ({ page }) => {
  test.skip((page.viewportSize()?.width || 0) <= 768, '配置在桌面使用')
  const state = await setup(page)
  await openFromSidebar(page, '操作日志')

  const row = page.getByRole('row').filter({ hasText: '用户管理·新增用户' })
  await expect(row).toBeVisible()
  await expect(row.getByText('新增', { exact: true })).toBeVisible()
  await expect(row.getByText('成功', { exact: true })).toBeVisible()

  await row.getByRole('button', { name: '详情', exact: true }).click()
  const dialog = page.getByRole('dialog')
  await expect(dialog.getByText('操作日志详情')).toBeVisible()
  await expect(dialog.getByText('"username": "alice"')).toBeVisible()
  await expect(dialog.getByText('200', { exact: true })).toBeVisible()
  await expect(dialog.getByText('12 ms', { exact: true })).toBeVisible()
  await page.keyboard.press('Escape')
  await expect(page.getByRole('dialog')).toHaveCount(0)

  await page.getByRole('button', { name: '清空', exact: true }).click()
  const box = page.locator('.el-message-box')
  await expect(box).toContainText('清空将删除全部操作日志且不可恢复')
  await box.getByRole('button', { name: '确定' }).click()
  await expect(box).toBeHidden()
  await expect(page.getByText('暂无数据')).toBeVisible()
  expect(state.cleaned).toEqual(['oper'])
  expect(state.errors).toEqual([])
})

test('侧栏进入登录日志：解锁确认后出现解锁行，清空需确认', async ({ page }) => {
  test.skip((page.viewportSize()?.width || 0) <= 768, '配置在桌面使用')
  const state = await setup(page)
  await openFromSidebar(page, '登录日志')

  const lockedRow = page.getByRole('row').filter({ hasText: 'alice' })
  await expect(lockedRow).toBeVisible()
  await expect(lockedRow.getByText('锁定', { exact: true })).toBeVisible()

  await lockedRow.getByRole('button', { name: '解锁', exact: true }).click()
  const box = page.locator('.el-message-box')
  await expect(box).toContainText('确定解锁账号「alice」')
  await box.getByRole('button', { name: '确定' }).click()
  await expect(box).toBeHidden()

  await expect(page.getByRole('row').filter({ hasText: '手动解锁' })).toBeVisible()
  await expect(
    page.locator('.el-table__row', { hasText: '手动解锁' }).locator('.el-tag', { hasText: '解锁' }),
  ).toBeVisible()
  expect(state.unlocked).toEqual(['alice'])

  await page.getByRole('button', { name: '清空', exact: true }).click()
  const cleanBox = page.locator('.el-message-box')
  await expect(cleanBox).toContainText('清空将删除全部登录日志且不可恢复')
  await cleanBox.getByRole('button', { name: '确定' }).click()
  await expect(cleanBox).toBeHidden()
  await expect(page.getByText('暂无数据')).toBeVisible()
  expect(state.cleaned).toEqual(['login'])
  expect(state.errors).toEqual([])
})
