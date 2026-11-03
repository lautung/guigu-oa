import { expect, test, type Route, type BrowserContext, type Page } from '@playwright/test'
import { e2eCookieURL } from './helpers/base-url'
import { isKnownBrowserNoise } from './helpers/known-noise'
import { logoutFromShell } from './helpers/shell'

const pageErrors = new WeakMap<Page, string[]>()
test.beforeEach(({ page }) => {
  const errors: string[] = []
  pageErrors.set(page, errors)
  page.on('pageerror', (error) => {
    if (!isKnownBrowserNoise(error.message)) errors.push(error.message)
  })
})
test.afterEach(({ page }) => {
  expect(pageErrors.get(page)).toEqual([])
})

const json = (route: Route, data: unknown) =>
  route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code: 200, data }) })

async function seedEntry(context: BrowserContext) {
  await context.addCookies([
    { name: 'guigu_oa_client_token', value: 'access-alice', url: e2eCookieURL() },
    { name: 'guigu_oa_client_refresh_token', value: 'refresh-alice', url: e2eCookieURL() },
  ])
}

test('首次认出临时失败可重试，保留原目标和进入结果', async ({ page, context }) => {
  await seedEntry(context)
  let attempts = 0
  await page.route('**/dev-api/**', async (route) => {
    const path = new URL(route.request().url()).pathname.replace('/dev-api', '')
    if (path.endsWith('/info')) {
      if (++attempts === 1) return route.fulfill({ status: 503, body: 'temporary failure' })
      return json(route, { name: 'Alice', menus: [] })
    }
    return json(route, { records: [], total: 0, pages: 0 })
  })
  await page.goto('/work/pending?source=bookmark')
  await expect(page.getByRole('status')).toHaveText('暂时无法获取用户信息')
  await expect(page.getByRole('heading', { name: '待处理' })).toHaveCount(0)
  expect(
    (await context.cookies()).find((cookie) => cookie.name === 'guigu_oa_client_token')?.value,
  ).toBe('access-alice')
  await page.getByRole('button', { name: '重试' }).click()
  await expect(page.getByRole('heading', { name: '待处理' })).toBeVisible()
  await expect(page).toHaveURL(/\/work\/pending\?source=bookmark$/)
  expect(attempts).toBe(2)
})

test('已认出后的刷新网络故障保留原页面与进入结果', async ({ page, context }) => {
  await seedEntry(context)
  let refreshCalls = 0
  await page.route('**/dev-api/**', async (route) => {
    const path = new URL(route.request().url()).pathname.replace('/dev-api', '')
    if (path.endsWith('/info')) return json(route, { name: 'Alice', menus: [] })
    if (path.endsWith('/refresh')) {
      refreshCalls += 1
      return route.fulfill({ status: 503, body: 'temporary failure' })
    }
    return route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({ code: 208, message: '认证失败' }),
    })
  })
  await page.goto('/work/pending')
  await expect(page.getByRole('heading', { name: '待处理' })).toBeVisible()
  await expect(page.getByText('Alice', { exact: true })).toBeVisible()
  await expect.poll(() => refreshCalls).toBe(1)
  await expect(page).toHaveURL(/\/work\/pending$/)
  const cookies = await context.cookies()
  expect(cookies.find((cookie) => cookie.name === 'guigu_oa_client_token')?.value).toBe(
    'access-alice',
  )
  expect(cookies.find((cookie) => cookie.name === 'guigu_oa_client_refresh_token')?.value).toBe(
    'refresh-alice',
  )
})

test('旧业务请求迟到失败不会提示或清掉新用户', async ({ page, context }) => {
  await seedEntry(context)
  let releaseOld: (() => void) | undefined
  let currentUser = 'Alice'
  let pendingCalls = 0
  await page.route('**/dev-api/**', async (route) => {
    const path = new URL(route.request().url()).pathname.replace('/dev-api', '')
    if (path.endsWith('/info')) return json(route, { name: currentUser, menus: [] })
    if (path.endsWith('/login')) {
      currentUser = 'Bob'
      return json(route, { token: 'bob-only' })
    }
    if (path.includes('/findPending/') && ++pendingCalls === 1) {
      await new Promise<void>((resolve) => {
        releaseOld = resolve
      })
      return route.fulfill({ status: 503, body: 'OLD_REQUEST_FAILURE' })
    }
    return json(route, { records: [], total: 0, pages: 0 })
  })
  await page.goto('/work/pending')
  await expect(page.getByText('Alice', { exact: true })).toBeVisible()
  await expect.poll(() => !!releaseOld).toBe(true)
  await logoutFromShell(page)
  await page.getByPlaceholder('用户名').fill('bob')
  await page.getByPlaceholder('密码').fill('111111')
  await page.getByRole('button', { name: '登录' }).click()
  await expect(page.getByText('Bob', { exact: true })).toBeVisible()
  const oldResponse = page.waitForResponse(
    (response) => response.url().includes('/findPending/') && response.status() === 503,
  )
  releaseOld?.()
  await oldResponse
  await expect(page.getByText('Bob', { exact: true })).toBeVisible()
  await expect(page.locator('.el-message')).toHaveCount(0)
  await expect(page).toHaveURL(/\/workbench$/)
  expect(
    (await context.cookies()).find((cookie) => cookie.name === 'guigu_oa_client_token')?.value,
  ).toBe('bob-only')
})

test('另一标签的新进入结果不会被旧标签迟到刷新覆盖', async ({ page, context }) => {
  await seedEntry(context)
  let releaseRefresh: (() => void) | undefined
  let user = 'Alice'
  await context.route('**/dev-api/**', async (route) => {
    const path = new URL(route.request().url()).pathname.replace('/dev-api', '')
    if (path.endsWith('/info')) return json(route, { name: user, menus: [] })
    if (path.endsWith('/login')) {
      user = 'Bob'
      return json(route, { token: 'bob-only' })
    }
    if (route.request().frame().page() === page) {
      if (path.endsWith('/refresh')) {
        await new Promise<void>((resolve) => {
          releaseRefresh = resolve
        })
        return json(route, { token: 'alice-refreshed', refreshToken: 'refresh-alice' })
      }
      return route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code: 208 }) })
    }
    return json(route, { records: [], total: 0, pages: 0 })
  })
  await page.goto('/work/pending')
  await expect.poll(() => !!releaseRefresh).toBe(true)
  const bobPage = await context.newPage()
  await bobPage.goto('/work/pending')
  await logoutFromShell(bobPage)
  await bobPage.getByPlaceholder('用户名').fill('bob')
  await bobPage.getByPlaceholder('密码').fill('111111')
  await bobPage.getByRole('button', { name: '登录' }).click()
  await expect(bobPage.getByText('Bob', { exact: true })).toBeVisible()
  const response = page.waitForResponse((result) => result.url().endsWith('/refresh'))
  releaseRefresh?.()
  await response
  await expect(bobPage.getByText('Bob', { exact: true })).toBeVisible()
  await expect(page.locator('.el-message')).toHaveCount(0)
  expect(
    (await context.cookies()).find((cookie) => cookie.name === 'guigu_oa_client_token')?.value,
  ).toBe('bob-only')
  await bobPage.close()
})
