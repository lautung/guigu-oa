import { expect, test, type Page, type Route } from '@playwright/test'
import { e2eCookieURL } from './helpers/base-url'
import { isKnownBrowserNoise } from './helpers/known-noise'
import { fulfillNoticeUnread } from './helpers/notice-mocks'

/**
 * 公告发布管理页（#220/ADR 0055）：列表筛选/动作、编辑状态机、dirty 离开确认、
 * 图片上传与预览。接口形状按 #218 的管理侧合同，全部走 mock，不碰真实栈。
 */

const ALL_PERMISSIONS = [
  'system:notice:add',
  'system:notice:edit',
  'system:notice:remove',
  'system:notice:publish',
]

const json = (route: Route, data: unknown, code = 200, message?: string) =>
  route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code, data, message }) })

interface AdminRow {
  id: number
  title: string
  type: number
  status: number
  publisherName: string
  publishTime: string
  createTime: string
  content: string
}

interface AdminState {
  records: AdminRow[]
  nextId: number
  listQueries: URLSearchParams[]
  createBodies: Record<string, unknown>[]
  updateBodies: Record<string, unknown>[]
  uploads: number
  errors: string[]
}

const seedRows = (): AdminRow[] => [
  {
    id: 1,
    title: '系统维护通知',
    type: 1,
    status: 0,
    publisherName: '',
    publishTime: '',
    createTime: '2026-11-01 09:00:00',
    content: '<p>维护草稿</p>',
  },
  {
    id: 2,
    title: '年会安排公告',
    type: 2,
    status: 1,
    publisherName: '管理员',
    publishTime: '2026-11-02 09:00:00',
    createTime: '2026-11-01 10:00:00',
    content: '<p>年会定于 12 月 20 日。</p>',
  },
  {
    id: 3,
    title: '旧公告',
    type: 2,
    status: 2,
    publisherName: '管理员',
    publishTime: '2026-10-01 09:00:00',
    createTime: '2026-09-30 09:00:00',
    content: '<p>已撤回内容</p>',
  },
]

async function setup(
  page: Page,
  { permissions = ALL_PERMISSIONS }: { permissions?: string[] } = {},
): Promise<AdminState> {
  const state: AdminState = {
    records: seedRows(),
    nextId: 4,
    listQueries: [],
    createBodies: [],
    updateBodies: [],
    uploads: 0,
    errors: [],
  }
  page.on('pageerror', (error) => {
    if (!isKnownBrowserNoise(error.message)) state.errors.push(error.message)
  })
  page.on('console', (message) => {
    if (message.type() === 'error') state.errors.push(message.text())
  })
  await page.context().addCookies([
    { name: 'guigu_oa_client_token', value: 'notice-admin-token', url: e2eCookieURL() },
    { name: 'guigu_oa_client_refresh_token', value: 'notice-admin-refresh', url: e2eCookieURL() },
    { name: 'guigu_oa_client_entry', value: 'notice-admin-entry', url: e2eCookieURL() },
  ])
  await page.route('**/dev-api/**', async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname.replace(/^\/dev-api/, '')
    const method = request.method()

    if (path.endsWith('/info')) {
      return json(route, {
        name: 'Alice',
        roles: [],
        permissions,
        menus: [
          { path: '/system', title: '组织与权限', type: 0, sort: 1 },
          {
            path: '/system/notice',
            parentPath: '/system',
            title: '通知公告',
            type: 1,
            sort: 1,
          },
        ],
      })
    }
    if (await fulfillNoticeUnread(route, path)) return

    if (path === '/admin/system/notices' && method === 'GET') {
      state.listQueries.push(url.searchParams)
      const title = url.searchParams.get('title') || ''
      const type = url.searchParams.get('type')
      const status = url.searchParams.get('status')
      const records = state.records
        .filter((row) => !title || row.title.includes(title))
        .filter((row) => type == null || String(row.type) === type)
        .filter((row) => status == null || String(row.status) === status)
        .map((row) => ({
          id: row.id,
          title: row.title,
          type: row.type,
          status: row.status,
          publisherName: row.publisherName,
          publishTime: row.publishTime,
          createTime: row.createTime,
        }))
      return json(route, { records, total: records.length, pages: 1 })
    }
    if (path === '/admin/system/notices' && method === 'POST') {
      const body = (request.postDataJSON() || {}) as Record<string, unknown>
      state.createBodies.push(body)
      const record: AdminRow = {
        id: state.nextId++,
        title: String(body.title || ''),
        type: Number(body.type || 1),
        status: 0,
        publisherName: '',
        publishTime: '',
        createTime: '2026-11-03 12:00:00',
        content: String(body.content || ''),
      }
      state.records.unshift(record)
      return json(route, record)
    }
    if (path === '/admin/system/notices/images' && method === 'POST') {
      state.uploads += 1
      return json(route, { id: 9, url: '/admin/notices/images/9' })
    }
    const publishMatch = /^\/admin\/system\/notices\/(\d+)\/publish$/.exec(path)
    if (publishMatch && method === 'POST') {
      const record = state.records.find((row) => row.id === Number(publishMatch[1]))
      if (!record) return json(route, null, 201, '公告不存在')
      record.status = 1
      record.publisherName = '管理员'
      record.publishTime = '2026-11-03 12:30:00'
      return json(route, record)
    }
    const withdrawMatch = /^\/admin\/system\/notices\/(\d+)\/withdraw$/.exec(path)
    if (withdrawMatch && method === 'POST') {
      const record = state.records.find((row) => row.id === Number(withdrawMatch[1]))
      if (!record) return json(route, null, 201, '公告不存在')
      record.status = 2
      return json(route, record)
    }
    const detailMatch = /^\/admin\/system\/notices\/(\d+)$/.exec(path)
    if (detailMatch) {
      const record = state.records.find((row) => row.id === Number(detailMatch[1]))
      if (!record) return json(route, null, 201, '公告不存在')
      if (method === 'GET') return json(route, record)
      if (method === 'PUT') {
        const body = (request.postDataJSON() || {}) as Record<string, unknown>
        state.updateBodies.push(body)
        record.title = String(body.title || '')
        record.type = Number(body.type || 1)
        record.content = String(body.content || '')
        return json(route, record)
      }
      if (method === 'DELETE') {
        state.records = state.records.filter((row) => row.id !== record.id)
        return json(route, null)
      }
    }
    if (path === '/admin/notices/images/9') {
      return route.fulfill({
        status: 200,
        contentType: 'image/png',
        body: Buffer.from([137, 80, 78, 71]),
      })
    }

    return json(route, { records: [], total: 0, pages: 0 })
  })
  return state
}

async function confirmBox(page: Page) {
  const box = page.locator('.el-message-box')
  await expect(box).toBeVisible()
  await box.getByRole('button', { name: '确定', exact: true }).click()
  await expect(box).toBeHidden()
}

async function typeContent(page: Page, text: string) {
  await page.locator('.ql-editor').click()
  await page.keyboard.type(text)
}

test('草稿 → 编辑 → 发布 → 撤回 → 删除的完整流程', async ({ page }) => {
  test.skip((page.viewportSize()?.width ?? 0) <= 768, '配置在桌面使用（ADR 0051）')
  const state = await setup(page)
  const title = 'e2e 维护通知'
  await page.goto('/system/notice')
  await expect(page.getByRole('heading', { name: '通知公告' })).toBeVisible()

  await page.getByRole('button', { name: '新建', exact: true }).click()
  await expect(page).toHaveURL(/\/system\/notice\/edit$/)
  await expect(page.getByRole('heading', { name: '新建公告' })).toBeVisible()

  await page.getByPlaceholder('公告标题').fill(title)
  await typeContent(page, '第一版正文')
  await page.getByRole('button', { name: '保存草稿', exact: true }).click()

  await expect(page).toHaveURL(/\/system\/notice\/edit\/4$/)
  await expect(page.locator('.notice-meta-row')).toContainText('草稿')
  expect(state.createBodies).toEqual([{ title, type: 1, content: '<p>第一版正文</p>' }])

  await page.getByRole('button', { name: '返回', exact: true }).click()
  await expect(page.getByRole('heading', { name: '通知公告' })).toBeVisible()
  const row = page.locator('.el-table__row', { hasText: title })
  await expect(row).toBeVisible()
  await expect(row).toContainText('草稿')

  await row.getByRole('button', { name: '编辑', exact: true }).click()
  await expect(page).toHaveURL(/\/system\/notice\/edit\/4$/)
  await page.getByPlaceholder('公告标题').fill(`${title}（已编辑）`)
  await page.getByRole('button', { name: '保存草稿', exact: true }).click()
  await expect(page.locator('.notice-dirty-hint')).toHaveCount(0)
  expect(state.updateBodies[0]).toMatchObject({
    title: `${title}（已编辑）`,
    content: '<p>第一版正文</p>',
  })

  await page.getByRole('button', { name: '发布', exact: true }).click()
  await confirmBox(page)
  await expect(page.locator('.notice-meta-row')).toContainText('已发布')
  await expect(page.getByRole('button', { name: '撤回', exact: true })).toBeVisible()

  await page.getByRole('button', { name: '撤回', exact: true }).click()
  await confirmBox(page)
  await expect(page.locator('.notice-meta-row')).toContainText('已撤回')
  await expect(page.locator('.notice-readonly-hint')).toBeVisible()
  await expect(page.getByPlaceholder('公告标题')).toBeDisabled()

  await page.getByRole('button', { name: '删除', exact: true }).click()
  await confirmBox(page)
  await expect(page.getByRole('heading', { name: '通知公告' })).toBeVisible()
  await expect(page.locator('.el-table__row', { hasText: title })).toHaveCount(0)
  expect(state.errors).toEqual([])
})

test('图片上传插入服务端 URL，预览按认证取回显示 blob', async ({ page }) => {
  test.skip((page.viewportSize()?.width ?? 0) <= 768, '配置在桌面使用（ADR 0051）')
  const state = await setup(page)
  await page.goto('/system/notice/edit')
  await expect(page.getByRole('heading', { name: '新建公告' })).toBeVisible()

  await page.getByPlaceholder('公告标题').fill('带图公告')
  await typeContent(page, '看图')
  const chooser = page.waitForEvent('filechooser')
  await page.locator('.ql-toolbar .ql-image').click()
  const fileChooser = await chooser
  await fileChooser.setFiles({
    name: 'shot.png',
    mimeType: 'image/png',
    buffer: Buffer.from([137, 80, 78, 71]),
  })
  await expect.poll(() => state.uploads).toBe(1)
  await expect(page.locator('.ql-editor img')).toHaveAttribute('src', '/admin/notices/images/9')

  await page.getByRole('tab', { name: '预览' }).click()
  await expect(page.locator('.notice-content img')).toHaveAttribute('src', /^blob:/)

  await page.getByRole('button', { name: '保存草稿', exact: true }).click()
  await expect.poll(() => state.createBodies.length).toBe(1)
  expect(state.createBodies[0].content).toContain('/admin/notices/images/9')
  expect(state.createBodies[0].content).not.toContain('blob:')
  expect(state.errors).toEqual([])
})

test('dirty 离开确认：取消留在编辑页，确认后回列表', async ({ page }) => {
  test.skip((page.viewportSize()?.width ?? 0) <= 768, '配置在桌面使用（ADR 0051）')
  await setup(page)
  await page.goto('/system/notice/edit/2')
  await expect(page.getByRole('heading', { name: '编辑公告' })).toBeVisible()

  await page.getByPlaceholder('公告标题').fill('未保存的修改')
  await page.getByRole('button', { name: '返回', exact: true }).click()
  const box = page.locator('.el-message-box')
  await expect(box).toBeVisible()
  await expect(box).toContainText('未保存修改')
  await box.getByRole('button', { name: '取消', exact: true }).click()
  await expect(page).toHaveURL(/\/system\/notice\/edit\/2$/)

  await page.getByRole('button', { name: '返回', exact: true }).click()
  await confirmBox(page)
  await expect(page.getByRole('heading', { name: '通知公告' })).toBeVisible()
})

test('按钮点权限决定列表动作与图片按钮可见性', async ({ page }) => {
  test.skip((page.viewportSize()?.width ?? 0) <= 768, '配置在桌面使用（ADR 0051）')
  await setup(page, { permissions: ['system:notice:edit'] })
  await page.goto('/system/notice')
  await expect(page.getByRole('heading', { name: '通知公告' })).toBeVisible()

  await expect(page.getByRole('button', { name: '新建', exact: true })).toHaveCount(0)
  const draftRow = page.locator('.el-table__row', { hasText: '系统维护通知' })
  await expect(draftRow.getByRole('button', { name: '编辑', exact: true })).toBeVisible()
  await expect(draftRow.getByRole('button', { name: '发布', exact: true })).toHaveCount(0)
  await expect(draftRow.getByRole('button', { name: '删除', exact: true })).toHaveCount(0)

  await draftRow.getByRole('button', { name: '编辑', exact: true }).click()
  await expect(page.getByRole('heading', { name: '编辑公告' })).toBeVisible()
  // 上传端点要 system:notice:add；只有 edit 时隐藏图片按钮，而不是让上传撞 205。
  await expect(page.locator('.ql-toolbar .ql-image')).toHaveCount(0)
})

test('有 publish 权限时列表显示发布/撤回按钮', async ({ page }) => {
  test.skip((page.viewportSize()?.width ?? 0) <= 768, '配置在桌面使用（ADR 0051）')
  await setup(page)
  await page.goto('/system/notice')
  await expect(page.getByRole('heading', { name: '通知公告' })).toBeVisible()

  const draftRow = page.locator('.el-table__row', { hasText: '系统维护通知' })
  const publishedRow = page.locator('.el-table__row', { hasText: '年会安排公告' })
  const withdrawnRow = page.locator('.el-table__row', { hasText: '旧公告' })

  await expect(draftRow.getByRole('button', { name: '发布', exact: true })).toBeVisible()
  await expect(publishedRow.getByRole('button', { name: '撤回', exact: true })).toBeVisible()
  await expect(withdrawnRow.getByRole('button', { name: '再次发布', exact: true })).toBeVisible()
  await expect(publishedRow.getByRole('button', { name: '编辑', exact: true })).toBeVisible()
})
