import { expect, test, type Page, type Route } from '@playwright/test'
import { e2eCookieURL } from './helpers/base-url'
import { isKnownBrowserNoise } from './helpers/known-noise'

/**
 * 工作台（#229）：登录落点、四张 KPI、近期审批、公告、快捷入口，
 * 以及加载骨架与空态。只 mock `/dev-api`，浏览器里跑真实外壳与页面。
 */

const pendingRows = [1, 2, 3, 4, 5].map((index) => ({
  id: `p-${index}`,
  taskId: `t-${index}`,
  title: `审批 ${index}`,
  name: '张三',
  processTypeName: '请假审批',
  createTime: '2026-11-02 09:00:00',
  status: 1,
  formValues: JSON.stringify({ formShowData: { 请假原因: '家中有事' } }),
}))

const unreadNotices = [
  {
    id: 1,
    title: '系统维护通知',
    type: 1,
    publisherName: '管理员',
    publishTime: '2026-11-02 10:00:00',
    read: false,
  },
]

const json = (route: Route, data: unknown) =>
  route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code: 200, data }) })

interface State {
  pendingTotal: number
  processedTotal: number
  startedTotal: number
  copiedTotal: number
  pendingRows: Record<string, unknown>[]
  unread: Record<string, unknown>[]
  unreadCount: number
  gate?: Promise<void>
  errors: string[]
}

function makeState(overrides: Partial<State> = {}): State {
  return {
    pendingTotal: 2,
    processedTotal: 4,
    startedTotal: 1,
    copiedTotal: 3,
    pendingRows,
    unread: unreadNotices,
    unreadCount: 1,
    errors: [],
    ...overrides,
  }
}

async function mockApi(page: Page, state: State): Promise<void> {
  await page.route('**/dev-api/**', async (route) => {
    const request = route.request()
    const path = new URL(request.url()).pathname.replace(/^\/dev-api/, '')
    const method = request.method()

    if (path === '/admin/system/index/login' && method === 'POST') {
      return json(route, { token: 'workbench-token', refreshToken: 'workbench-refresh' })
    }
    if (path === '/admin/system/index/info' && method === 'GET') {
      return json(route, { name: 'Alice', avatar: '', menus: [] })
    }
    if (path === '/admin/process/findStartable' && method === 'GET') return json(route, [])
    if (path === '/admin/process/findPending/1/1' && method === 'GET') {
      if (state.gate) await state.gate
      return json(route, { records: [], total: state.pendingTotal, pages: 1 })
    }
    if (path === '/admin/process/findPending/1/5' && method === 'GET') {
      return json(route, { records: state.pendingRows, total: state.pendingTotal, pages: 1 })
    }
    if (path.startsWith('/admin/process/findPending/') && method === 'GET') {
      return json(route, { records: state.pendingRows, total: state.pendingTotal, pages: 1 })
    }
    if (path.startsWith('/admin/process/findProcessed/') && method === 'GET') {
      return json(route, { records: [], total: state.processedTotal, pages: 1 })
    }
    if (path.startsWith('/admin/process/findStarted/') && method === 'GET') {
      return json(route, { records: [], total: state.startedTotal, pages: 1 })
    }
    if (path.startsWith('/admin/process/findCarbonCopied/') && method === 'GET') {
      return json(route, { records: [], total: state.copiedTotal, pages: 1 })
    }
    if (path.endsWith('/admin/notices/unread-count')) return json(route, state.unreadCount)
    if (path.endsWith('/admin/notices/unread')) return json(route, state.unread)

    throw new Error(`Unexpected request ${method} ${path}`)
  })
}

async function setup(
  page: Page,
  overrides: Partial<State> = {},
  authenticated = true,
): Promise<State> {
  const state = makeState(overrides)
  page.on('pageerror', (error) => {
    if (!isKnownBrowserNoise(error.message)) state.errors.push(error.message)
  })
  if (authenticated) {
    await page.context().addCookies([
      { name: 'guigu_oa_client_token', value: 'workbench-token', url: e2eCookieURL() },
      { name: 'guigu_oa_client_refresh_token', value: 'workbench-refresh', url: e2eCookieURL() },
      { name: 'guigu_oa_client_entry', value: 'workbench-entry', url: e2eCookieURL() },
    ])
  }
  await mockApi(page, state)
  return state
}

test('登录落到工作台，KPI 与近期审批可用，点 KPI 进待处理', async ({ page }) => {
  const state = await setup(page, {}, false)

  await page.goto('/login')
  await page.getByPlaceholder('用户名').fill('alice')
  await page.getByPlaceholder('密码').fill('111111')
  await page.getByRole('button', { name: '登录' }).click()

  await expect(page).toHaveURL(/\/workbench$/)
  await expect(page.getByRole('heading', { name: '工作台', exact: true })).toBeVisible()
  await expect(page.locator('.workbench-date')).toContainText(/\d+ 月 \d+ 日 周/)
  await expect(page.getByText('当前有 2 项待处理', { exact: true })).toBeVisible()

  const kpis = page.locator('.kpi-card')
  await expect(kpis).toHaveCount(4)
  await expect(kpis.nth(0).locator('.kpi-label')).toHaveText('待处理')
  await expect(kpis.nth(0).locator('.kpi-value')).toHaveText('2')
  await expect(kpis.nth(1).locator('.kpi-value')).toHaveText('4')
  await expect(kpis.nth(2).locator('.kpi-value')).toHaveText('1')
  await expect(kpis.nth(3).locator('.kpi-value')).toHaveText('3')

  await expect(page.locator('.el-table__row')).toHaveCount(5)
  await expect(page.getByText('审批 1', { exact: true })).toBeVisible()
  await expect(page.locator('.notice-card .notice-item')).toHaveCount(1)
  await expect(page.locator('.notice-card .notice-item')).toContainText('系统维护通知')
  await expect(page.locator('.notice-card .notice-item')).toContainText('通知')
  await expect(page.locator('.quick-link')).toHaveCount(4)

  await kpis.nth(0).click()
  await expect(page).toHaveURL(/\/work\/pending$/)
  await expect(page.getByRole('heading', { name: '待处理', exact: true })).toBeVisible()
  expect(state.errors).toEqual([])
})

test('没有待处理时显示空态与发起审批 CTA', async ({ page }) => {
  const state = await setup(page, { pendingTotal: 0, pendingRows: [] })

  await page.goto('/workbench')
  await expect(page.getByRole('heading', { name: '工作台', exact: true })).toBeVisible()
  await expect(page.getByText('当前没有待处理事项', { exact: true }).first()).toBeVisible()

  const empty = page.locator('.workbench-empty')
  await expect(empty).toBeVisible()
  await expect(empty).toContainText('当前没有待处理事项')
  await empty.getByRole('button', { name: '发起审批', exact: true }).click()
  await expect(page).toHaveURL(/\/work\/start$/)
  expect(state.errors).toEqual([])
})

test('首屏加载显示 8 行骨架', async ({ page }) => {
  let release!: () => void
  const gate = new Promise<void>((resolve) => {
    release = resolve
  })
  const state = await setup(page, { gate })

  await page.goto('/workbench')
  const skeleton = page.locator('.workbench-skeleton')
  await expect(skeleton).toBeVisible()
  await expect(skeleton).toHaveAttribute('data-rows', '8')
  await expect(page.locator('.kpi-card')).toHaveCount(0)

  release()
  await expect(page.locator('.kpi-card')).toHaveCount(4)
  await expect(skeleton).toHaveCount(0)
  expect(state.errors).toEqual([])
})
