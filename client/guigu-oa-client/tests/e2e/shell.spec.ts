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

async function mockApi(page: Page, info: { menus?: unknown[] } = {}): Promise<void> {
  await page.route('**/dev-api/**', async (route) => {
    const path = new URL(route.request().url()).pathname.replace(/^\/dev-api/, '')
    if (path === '/admin/system/index/info') {
      return json(route, {
        name: 'Alice',
        roles: ['super_admin'],
        permissions: [],
        menus,
        ...info,
      })
    }
    if (/^\/admin\/process\/find(?:Pending|Processed|Started|CarbonCopied)\//.test(path)) {
      return json(route, { records: [], total: 0, pages: 0 })
    }
    if (path === '/admin/system/sysUser/depts') return json(route, [])
    if (path === '/admin/system/sysUser/posts') return json(route, [])
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

test('系统监控与系统工具无授予时都置灰（#239/#246）', async ({ page }) => {
  test.skip((page.viewportSize()?.width || 0) < 900, '窄屏隐藏标签条')
  await openShell(page)

  // 与系统管理同款语义：无授予的一级置灰，不落到占位页。
  for (const title of ['系统监控', '系统工具']) {
    const module = topModule(page, title)
    await expect(module).toHaveClass(/disabled/)
    await expect(module).toBeDisabled()
  }
  await expect(page.locator('.shell-tab')).toHaveText(['工作台', '待处理'])
})

test('授予接口文档后系统工具落在 /tool/api-doc（#246）', async ({ page }) => {
  test.skip((page.viewportSize()?.width || 0) < 900, '窄屏隐藏标签条')
  const toolMenus = [
    { path: '/tool', title: '系统工具', type: 0, sort: 5 },
    { path: '/tool/api-doc', parentPath: '/tool', title: '接口文档', type: 1, sort: 1 },
  ]
  await seedSession(page)
  await mockApi(page, { menus: toolMenus })
  // 演示环境形状：springdoc 关闭，探测拿到 404 → 未启用态，不出现死链按钮。
  await page.route('**/v3/api-docs', (route) => route.fulfill({ status: 404, body: '' }))

  await page.goto('/tool')
  await expect(page).toHaveURL(/\/tool\/api-doc$/)
  await expect(page.getByRole('heading', { name: '接口文档', exact: true })).toBeVisible()
  await expect(page.getByText('当前环境未启用接口文档，仅开发环境提供')).toBeVisible()
  await expect(page.getByRole('button', { name: '打开 Swagger UI' })).toHaveCount(0)
  await expect(page.locator('.shell-tab')).toHaveText(['工作台', '接口文档'])
})

test('授予在线用户后系统监控落在 /monitor/online（#239）', async ({ page }) => {
  test.skip((page.viewportSize()?.width || 0) < 900, '窄屏隐藏标签条')
  const monitorMenus = [
    { path: '/monitor', title: '系统监控', type: 0, sort: 4 },
    { path: '/monitor/online', parentPath: '/monitor', title: '在线用户', type: 1, sort: 1 },
  ]
  await seedSession(page)
  await page.route('**/dev-api/**', async (route) => {
    const path = new URL(route.request().url()).pathname.replace(/^\/dev-api/, '')
    if (path === '/admin/system/index/info') {
      return json(route, {
        name: 'Alice',
        roles: ['super_admin'],
        permissions: ['monitor:online:forceLogout'],
        menus: monitorMenus,
      })
    }
    if (/^\/admin\/monitor\/online\/\d+\/\d+$/.test(path)) {
      return json(route, {
        records: [
          {
            sessionId: 'sess-current',
            username: 'Alice',
            name: '爱丽丝',
            loginTime: '2026-01-01 08:00:00',
            ip: '127.0.0.1',
            browser: 'Chromium',
            os: 'macOS',
            lastActive: '2026-01-01 09:00:00',
            current: true,
          },
        ],
        total: 1,
        pages: 1,
      })
    }
    if (/^\/admin\/process\/find(?:Pending|Processed|Started|CarbonCopied)\//.test(path)) {
      return json(route, { records: [], total: 0, pages: 0 })
    }
    if (await fulfillNoticeUnread(route, path)) return
    throw new Error(`Unexpected request ${path}`)
  })
  await page.goto('/work/pending')
  await expect(page.getByRole('heading', { name: '待处理', exact: true })).toBeVisible()

  await topModule(page, '系统监控').click()
  await expect(page).toHaveURL(/\/monitor\/online$/)
  await expect(page.getByRole('heading', { name: '在线用户', exact: true })).toBeVisible()
  await expect(page.locator('.app-sidebar').getByText('在线用户')).toBeVisible()
  await expect(page.locator('.shell-breadcrumb')).toContainText('系统监控')
  const row = page.locator('.el-table__row', { hasText: 'Alice' })
  await expect(row).toContainText('当前会话')
  await expect(row.getByRole('button', { name: '强退' })).toBeDisabled()
  await expect(page.locator('.page-header')).toContainText('共 1 条')
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
  // 系统监控翻真（#239）后与系统管理同款：无监控授予时同样置灰。
  const monitor = topModule(page, '系统监控')
  await expect(monitor).toHaveClass(/disabled/)
  await expect(monitor).toBeDisabled()
})

test('菜单搜索按标题打开页面，无结果给空态', async ({ page }) => {
  test.skip((page.viewportSize()?.width || 0) < 900, '窄屏不展开搜索弹层')
  await openShell(page)

  await page.locator('.menu-search-trigger').click()
  const input = page.locator('.menu-search-input')
  await expect(input).toBeVisible()

  await input.fill('操作')
  const results = page.locator('.menu-search-item')
  await expect(results).toHaveCount(1)
  await expect(results.first()).toContainText('操作日志')
  await expect(results.first()).toContainText('系统管理 / 日志管理')

  await results.first().click()
  await expect(page).toHaveURL(/\/system\/log\/operLog$/)
  await expect(page.getByRole('heading', { name: '操作日志', exact: true })).toBeVisible()
  await expect(page.locator('.shell-breadcrumb')).toContainText('日志管理')

  await page.locator('.menu-search-trigger').click()
  await page.locator('.menu-search-input').fill('不存在的页面')
  await expect(page.locator('.menu-search-empty')).toHaveText('没有匹配的页面')
})

test('侧栏折叠为 62px 图标栏并可再展开', async ({ page }) => {
  test.skip((page.viewportSize()?.width || 0) < 900, '窄屏用抽屉而不是折叠栏')
  await openShell(page)

  const sidebar = page.locator('.app-sidebar')
  await expect(sidebar).not.toHaveClass(/collapsed/)
  await page.locator('.collapse-btn').click()
  await expect(sidebar).toHaveClass(/collapsed/)
  await expect(sidebar).toHaveCSS('width', '62px')
  await expect(sidebar.locator('.side-item').first()).toHaveAttribute('title', '发起审批')

  await page.locator('.collapse-btn').click()
  await expect(sidebar).not.toHaveClass(/collapsed/)
})

test('用户菜单切换亮暗与密度并写入 localStorage', async ({ page }) => {
  await openShell(page)

  await page.locator('.user-trigger').click()
  await page.locator('[data-theme="dark"]').click()
  await expect(page.locator('html')).toHaveClass(/dark/)
  expect(await page.evaluate(() => localStorage.getItem('guigu_oa_shell_theme'))).toBe('dark')

  await page.locator('[data-density="large"]').click()
  expect(await page.evaluate(() => localStorage.getItem('guigu_oa_shell_density'))).toBe('large')

  await page.locator('[data-theme="light"]').click()
  await expect(page.locator('html')).not.toHaveClass(/dark/)
})
