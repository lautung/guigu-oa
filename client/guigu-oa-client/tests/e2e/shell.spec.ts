import { expect, test, type Page, type Route } from '@playwright/test'
import { e2eCookieURL } from './helpers/base-url'
import { fulfillNoticeUnread } from './helpers/notice-mocks'

/**
 * 外壳框架（#228）：顶部一级 + 左侧二级 + 多标签 + 面包屑 + 亮/暗 + 响应式抽屉。
 * 只 mock `/dev-api`，验证浏览器里的真实外壳行为。
 */

const menus = [
  { path: '/system', title: '系统管理', type: 0, sort: 1 },
  { path: '/system/sysUser', parentPath: '/system', title: '用户管理', type: 1, sort: 1 },
  { path: '/system/log', parentPath: '/system', title: '日志管理', type: 0, sort: 2 },
  { path: '/system/log/operLog', parentPath: '/system/log', title: '操作日志', type: 1, sort: 1 },
]

const json = (route: Route, data: unknown) =>
  route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code: 200, data }) })

async function seedSession(page: Page): Promise<void> {
  await page.context().addCookies([
    { name: 'guigu_oa_client_token', value: 'shell-token', url: e2eCookieURL() },
    { name: 'guigu_oa_client_refresh_token', value: 'shell-refresh', url: e2eCookieURL() },
    { name: 'guigu_oa_client_entry', value: 'shell-entry', url: e2eCookieURL() },
  ])
}

async function mockApi(page: Page): Promise<void> {
  await page.route('**/dev-api/**', async (route) => {
    const path = new URL(route.request().url()).pathname.replace(/^\/dev-api/, '')
    if (path === '/admin/system/index/info') {
      return json(route, { name: 'Alice', roles: ['super_admin'], permissions: [], menus })
    }
    if (/^\/admin\/process\/find(?:Pending|Processed|Started|CarbonCopied)\//.test(path)) {
      return json(route, { records: [], total: 0, pages: 0 })
    }
    if (/^\/admin\/system\/sysUser\/\d+\/\d+$/.test(path)) {
      return json(route, { records: [], total: 0, pages: 0 })
    }
    if (/^\/admin\/system\/sysOperLog\/\d+\/\d+$/.test(path)) {
      return json(route, { records: [], total: 0, pages: 0 })
    }
    if (await fulfillNoticeUnread(route, path)) return
    throw new Error(`Unexpected request ${path}`)
  })
}

async function openShell(page: Page): Promise<void> {
  await seedSession(page)
  await mockApi(page)
  await page.goto('/work/pending')
  await expect(page.getByRole('heading', { name: '待处理', exact: true })).toBeVisible()
}

function topModule(page: Page, title: string) {
  return page.locator('.top-nav-item', { hasText: title })
}

function tab(page: Page, title: string) {
  return page.locator('.shell-tab', { hasText: title })
}

test('顶部一级切换、标签打开/关闭与面包屑', async ({ page }) => {
  test.skip((page.viewportSize()?.width || 0) < 900, '窄屏隐藏标签条')
  await openShell(page)

  const tabs = page.locator('.shell-tab')
  await expect(page.locator('.top-nav-item')).toHaveText([
    '工作台',
    '办事',
    '系统管理',
    '系统监控',
    '系统工具',
  ])
  await expect(tabs).toHaveText(['工作台', '待处理'])
  await expect(page.locator('.shell-breadcrumb')).toContainText('首页')
  await expect(page.locator('.shell-breadcrumb')).toContainText('办事')
  await expect(page.locator('.shell-breadcrumb')).toContainText('待处理')

  await topModule(page, '系统管理').click()
  await expect(page).toHaveURL(/\/system\/sysUser$/)
  await expect(tabs).toHaveText(['工作台', '待处理', '用户管理'])
  await expect(page.locator('.shell-breadcrumb')).toContainText('系统管理')

  await page.locator('.app-sidebar').getByRole('link', { name: '操作日志', exact: true }).click()
  await expect(page).toHaveURL(/\/system\/log\/operLog$/)
  await expect(tabs).toHaveText(['工作台', '待处理', '用户管理', '操作日志'])
  await expect(page.locator('.shell-breadcrumb')).toContainText('日志管理')
  await expect(page.locator('.shell-breadcrumb')).toContainText('操作日志')

  await tab(page, '待处理').click()
  await expect(page).toHaveURL(/\/work\/pending$/)
  await expect(tab(page, '待处理')).toHaveClass(/active/)

  await tab(page, '用户管理').locator('.tab-close').click()
  await expect(tabs).toHaveText(['工作台', '待处理', '操作日志'])

  await tab(page, '操作日志').click()
  await page.locator('.tab-more').click()
  await page.locator('.el-dropdown-menu__item', { hasText: '关闭当前' }).click()
  await expect(page).toHaveURL(/\/work\/pending$/)
  await expect(tabs).toHaveText(['工作台', '待处理'])

  await topModule(page, '系统管理').click()
  await expect(page).toHaveURL(/\/system\/sysUser$/)
  await page.locator('.tab-more').click()
  await page.locator('.el-dropdown-menu__item', { hasText: '关闭其他' }).click()
  await expect(tabs).toHaveText(['工作台', '用户管理'])

  await page.locator('.tab-more').click()
  await page.locator('.el-dropdown-menu__item', { hasText: '关闭全部' }).click()
  await expect(page).toHaveURL(/\/workbench$/)
  await expect(tabs).toHaveText(['工作台'])
  await expect(page.getByRole('heading', { name: '工作台', exact: true })).toBeVisible()
})

test('标签跨模块保留，切回一级回到该模块最近的标签', async ({ page }) => {
  test.skip((page.viewportSize()?.width || 0) < 900, '窄屏隐藏标签条')
  await openShell(page)

  await topModule(page, '工作台').click()
  await expect(page).toHaveURL(/\/workbench$/)
  await expect(tab(page, '工作台')).toHaveClass(/active/)

  await topModule(page, '办事').click()
  await expect(page).toHaveURL(/\/work\/pending$/)

  await topModule(page, '系统管理').click()
  await expect(page).toHaveURL(/\/system\/sysUser$/)

  await topModule(page, '办事').click()
  await expect(page).toHaveURL(/\/work\/pending$/)
  await expect(page.locator('.shell-tab')).toHaveText(['工作台', '待处理', '用户管理'])
})

test('窄屏隐藏标签条，侧栏改抽屉，配置页提示建议桌面', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await openShell(page)

  await expect(page.locator('.shell-tabs')).toHaveCount(0)
  await page.locator('.menu-toggle').click()
  await expect(page.locator('.app-sidebar')).toHaveClass(/open/)
  await page.locator('.app-sidebar').getByRole('link', { name: '待处理', exact: true }).click()
  await expect(page.locator('.app-sidebar')).not.toHaveClass(/open/)

  await topModule(page, '系统管理').click()
  await expect(page).toHaveURL(/\/system\/sysUser$/)
  await expect(page.locator('.desktop-hint')).toHaveText('建议桌面')

  await page.locator('.menu-toggle').click()
  await page.locator('.app-sidebar').getByRole('link', { name: '操作日志', exact: true }).click()
  await expect(page).toHaveURL(/\/system\/log\/operLog$/)
  await expect(page.locator('.app-sidebar')).not.toHaveClass(/open/)
  await expect(page.getByRole('heading', { name: '操作日志', exact: true })).toBeVisible()
})

test('系统监控与系统工具是固定一级空态，不打开死链', async ({ page }) => {
  test.skip((page.viewportSize()?.width || 0) < 900, '窄屏隐藏标签条')
  await openShell(page)

  await topModule(page, '系统监控').click()
  await expect(page).toHaveURL(/\/monitor$/)
  await expect(page.locator('.shell-tab', { hasText: '系统监控' })).toHaveClass(/active/)
  await expect(page.locator('.module-placeholder')).toContainText('系统监控暂无可用页面')
  await expect(page.locator('.app-sidebar')).toContainText('页面将在后续版本提供')
  await expect(page.locator('.shell-breadcrumb')).toContainText('系统监控')

  await topModule(page, '系统工具').click()
  await expect(page).toHaveURL(/\/tool$/)
  await expect(page.locator('.module-placeholder')).toContainText('系统工具暂无可用页面')
  await expect(page.locator('.shell-tab')).toHaveText(['工作台', '待处理', '系统监控', '系统工具'])
})

test('没有配置授予时系统管理置灰，仍是五项之一', async ({ page }) => {
  await page.context().addCookies([
    { name: 'guigu_oa_client_token', value: 'shell-token', url: e2eCookieURL() },
    { name: 'guigu_oa_client_refresh_token', value: 'shell-refresh', url: e2eCookieURL() },
    { name: 'guigu_oa_client_entry', value: 'shell-entry', url: e2eCookieURL() },
  ])
  await page.route('**/dev-api/**', async (route) => {
    const path = new URL(route.request().url()).pathname.replace(/^\/dev-api/, '')
    if (path === '/admin/system/index/info') {
      return json(route, { name: 'Alice', roles: ['user'], permissions: [], menus: [] })
    }
    if (/^\/admin\/process\/find(?:Pending|Processed|Started|CarbonCopied)\//.test(path)) {
      return json(route, { records: [], total: 0, pages: 0 })
    }
    if (await fulfillNoticeUnread(route, path)) return
    throw new Error(`Unexpected request ${path}`)
  })

  await page.goto('/work/pending')
  await expect(page.getByRole('heading', { name: '待处理', exact: true })).toBeVisible()

  await expect(page.locator('.top-nav-item')).toHaveText([
    '工作台',
    '办事',
    '系统管理',
    '系统监控',
    '系统工具',
  ])
  const system = topModule(page, '系统管理')
  await expect(system).toHaveClass(/disabled/)
  await expect(system).toBeDisabled()
  await expect(page.locator('.top-nav-item', { hasText: '系统监控' })).toBeEnabled()
})
