import { expect, test, type Page, type Route } from '@playwright/test'
import { e2eCookieURL } from './helpers/base-url'
import { isKnownBrowserNoise } from './helpers/known-noise'

/**
 * 公告展示面（#219）：顶栏铃铛、全部公告两个 tab、详情自动已读与不存在视图。
 * 接口形状按 ADR 0055（#218 的服务端合同）；图片走认证取回，正文里的同源
 * `/admin/notices/images/{id}` 最终以 `blob:` 地址出现在页面上。
 */

const json = (route: Route, data: unknown, code = 200, message?: string) =>
  route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code, data, message }) })

interface FeedRow {
  id: number
  title: string
  type: number
  publisherName: string
  publishTime: string
  read: boolean
}

interface DetailRow extends FeedRow {
  content: string
}

interface State {
  unreadCount: number
  unread: FeedRow[]
  feed: FeedRow[]
  details: Record<string, DetailRow>
  readIds: number[]
  feedQueries: URLSearchParams[]
  errors: string[]
}

const rows: FeedRow[] = [
  {
    id: 1,
    title: '系统维护通知',
    type: 1,
    publisherName: '管理员',
    publishTime: '2026-11-03 12:00:00',
    read: false,
  },
  {
    id: 2,
    title: '年会安排公告',
    type: 2,
    publisherName: '行政部',
    publishTime: '2026-11-02 09:00:00',
    read: true,
  },
]

const details: Record<string, DetailRow> = {
  '1': {
    ...rows[0],
    content:
      '<h3>维护窗口</h3><p>今晚 <strong>22:00</strong> 开始。</p>' +
      '<p><img src="/admin/notices/images/9" alt="维护截图"></p>',
  },
  '2': {
    ...rows[1],
    content: '<p>年会定于 12 月 20 日。</p>',
  },
}

async function setup(page: Page): Promise<State> {
  const state: State = {
    unreadCount: 1,
    unread: rows.filter((row) => !row.read),
    feed: rows.map((row) => ({ ...row })),
    details,
    readIds: [],
    feedQueries: [],
    errors: [],
  }
  page.on('pageerror', (error) => {
    if (!isKnownBrowserNoise(error.message)) state.errors.push(error.message)
  })
  page.on('console', (message) => {
    if (message.type() === 'error') state.errors.push(message.text())
  })
  await page.context().addCookies([
    { name: 'guigu_oa_client_token', value: 'notice-token', url: e2eCookieURL() },
    { name: 'guigu_oa_client_refresh_token', value: 'notice-refresh', url: e2eCookieURL() },
    { name: 'guigu_oa_client_entry', value: 'notice-entry', url: e2eCookieURL() },
  ])
  await page.route('**/dev-api/**', async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname.replace(/^\/dev-api/, '')
    const method = request.method()

    if (path.endsWith('/info')) return json(route, { name: 'Alice', menus: [] })
    if (path.endsWith('/unread-count')) return json(route, state.unreadCount)
    if (path.endsWith('/unread')) return json(route, state.unread)
    if (path === '/admin/notices/feed') {
      state.feedQueries.push(url.searchParams)
      const unreadOnly = url.searchParams.get('unreadOnly') === 'true'
      const records = unreadOnly ? state.feed.filter((row) => !row.read) : state.feed
      return json(route, { records, total: records.length, pages: 1 })
    }
    if (path === '/admin/notices/images/9') {
      return route.fulfill({ status: 200, contentType: 'image/png', body: Buffer.from([137, 80]) })
    }
    const readMatch = /^\/admin\/notices\/(\d+)\/read$/.exec(path)
    if (readMatch && method === 'POST') {
      const id = Number(readMatch[1])
      state.readIds.push(id)
      state.feed = state.feed.map((row) => (row.id === id ? { ...row, read: true } : row))
      state.unread = state.unread.filter((row) => row.id !== id)
      state.unreadCount = state.unread.length
      return json(route, null)
    }
    const detailMatch = /^\/admin\/notices\/(\d+)$/.exec(path)
    if (detailMatch) return json(route, state.details[detailMatch[1]] ?? null)

    return json(route, { records: [], total: 0, pages: 0 })
  })
  return state
}

test('铃铛显示未读与最近 5 条，点条目进详情并写回执后角标归零', async ({ page }) => {
  const state = await setup(page)
  await page.goto('/notice')
  await expect(page.getByRole('heading', { name: '全部公告' })).toBeVisible()

  const badge = page.locator('.top-bar .el-badge__content')
  await expect(badge).toHaveText('1')
  await expect(badge).toBeVisible()

  await page.getByRole('button', { name: '公告', exact: true }).click()
  const items = page.locator('.notice-item')
  await expect(items).toHaveCount(1)
  await expect(items.first()).toContainText('系统维护通知')
  await expect(items.first()).toContainText('通知')

  await items.first().click()
  await expect(page).toHaveURL(/\/notice\/1$/)
  await expect(page.getByRole('heading', { name: '系统维护通知' })).toBeVisible()
  await expect(page.locator('.notice-content')).toContainText('今晚 22:00 开始。')

  await expect.poll(() => state.readIds).toEqual([1])
  await expect(badge).toBeHidden()
  await expect(page.locator('.notice-content img')).toHaveAttribute('src', /^blob:/)
  expect(state.errors).toEqual([])
})

test('全部公告两个 tab 与行内容，未读 tab 只请求未读', async ({ page }) => {
  const state = await setup(page)
  await page.goto('/notice')
  await expect(page.getByRole('heading', { name: '全部公告' })).toBeVisible()

  const table = page.locator('.notice-table')
  await expect(table.locator('.el-table__row')).toHaveCount(2)
  await expect(table).toContainText('系统维护通知')
  await expect(table).toContainText('通知')
  await expect(table).toContainText('未读')

  await page.getByRole('tab', { name: '未读' }).click()
  await expect.poll(() => state.feedQueries.at(-1)?.get('unreadOnly')).toBe('true')
  await expect(table.locator('.el-table__row')).toHaveCount(1)
  await expect(table).toContainText('系统维护通知')
  await expect(table).not.toContainText('年会安排公告')

  await page.getByRole('tab', { name: '全部' }).click()
  await expect.poll(() => state.feedQueries.at(-1)?.get('unreadOnly')).toBeNull()
  await expect(table.locator('.el-table__row')).toHaveCount(2)

  await table.getByText('年会安排公告').click()
  await expect(page).toHaveURL(/\/notice\/2$/)
  await expect(page.locator('.notice-content')).toContainText('年会定于 12 月 20 日。')
  expect(state.errors).toEqual([])
})

test('撤回或删除后的详情按不存在视图处理', async ({ page }) => {
  await setup(page)
  await page.goto('/notice/404')

  await expect(page.getByText('公告不存在或已下线')).toBeVisible()
  await expect(page.locator('.notice-content')).toHaveCount(0)
  await expect(page.locator('.list-load-error')).toHaveCount(0)
})

test('角标超过 99 只显示 99+', async ({ page }) => {
  const state = await setup(page)
  state.unreadCount = 120
  await page.goto('/notice')

  await expect(page.locator('.top-bar .el-badge__content')).toHaveText('99+')
})
