import { expect, test, type Page, type Route } from '@playwright/test'
import { e2eCookieURL } from './helpers/base-url'
import { fulfillNoticeUnread } from './helpers/notice-mocks'

/**
 * i18n 框架（#257）：默认中文；顶栏切到 English 后外壳与登录页的文案跟随。
 * 业务菜单标题来自 /info，切语言后仍保持服务端下发的名称。
 */

const json = (route: Route, data: unknown) =>
  route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code: 200, data }) })

async function seedSession(page: Page): Promise<void> {
  await page.context().addCookies([
    { name: 'guigu_oa_client_token', value: 'locale-token', url: e2eCookieURL() },
    { name: 'guigu_oa_client_refresh_token', value: 'locale-refresh', url: e2eCookieURL() },
    { name: 'guigu_oa_client_entry', value: 'locale-entry', url: e2eCookieURL() },
  ])
}

async function mockApi(page: Page): Promise<void> {
  await page.route('**/dev-api/**', async (route) => {
    const path = new URL(route.request().url()).pathname.replace(/^\/dev-api/, '')
    if (path === '/admin/system/index/info') {
      return json(route, { name: 'Alice', roles: ['super_admin'], permissions: [], menus: [] })
    }
    if (path === '/admin/system/index/register/switch') return json(route, { enabled: false })
    if (/^\/admin\/process\/find(?:Pending|Processed|Started|CarbonCopied)\//.test(path)) {
      return json(route, { records: [], total: 0, pages: 0 })
    }
    if (await fulfillNoticeUnread(route, path)) return
    throw new Error(`Unexpected request ${path}`)
  })
}

test('顶栏切到 English 后外壳与登录页显示英文', async ({ page }) => {
  await seedSession(page)
  await mockApi(page)
  await page.goto('/work/pending')

  await expect(page.locator('.top-nav-item').first()).toHaveText('工作台')
  await expect(page.locator('.shell-breadcrumb')).toContainText('首页')

  await page.locator('.locale-btn').click()
  await page.locator('.el-dropdown-menu__item', { hasText: 'English' }).click()

  await expect(page.locator('.top-nav-item')).toHaveText([
    'Workbench',
    'Work',
    'System',
    'Monitor',
    'Tools',
  ])
  await expect(page.locator('.shell-breadcrumb')).toContainText('Home')
  await expect(page.locator('.shell-tab').first()).toHaveText('Workbench')
  await expect(page.locator('.user-trigger')).toHaveAttribute('aria-label', 'User menu')
  expect(await page.evaluate(() => localStorage.getItem('guigu_oa_client_locale'))).toBe('en')

  await page.context().clearCookies()
  await page.goto('/login')
  await expect(page.getByPlaceholder('Username')).toBeVisible()
  await expect(page.getByPlaceholder('Password')).toBeVisible()
  await expect(page.getByRole('button', { name: 'Sign in' })).toBeVisible()
})
