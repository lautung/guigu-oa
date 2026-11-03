import { expect, test, type Page, type Route } from '@playwright/test'
import { e2eCookieURL } from './helpers/base-url'
import { isKnownBrowserNoise } from './helpers/known-noise'
import { fulfillNoticeUnread } from './helpers/notice-mocks'

function deferred() {
  let resolve!: () => void
  const promise = new Promise<void>((done) => {
    resolve = done
  })
  return { promise, resolve }
}
const record = {
  id: 'A',
  taskId: 'task-A',
  title: '审批 A',
  status: 1,
  formValues: JSON.stringify({ formShowData: { 原因: '出差' } }),
}
async function json(route: Route, data: unknown, code = 200) {
  await route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ code, data }),
  })
}
async function setup(
  page: Page,
  approve: (route: Route) => Promise<void>,
  uploadGate?: Promise<void>,
  readGate?: Promise<void>,
  completionGate?: Promise<void>,
) {
  const errors: string[] = []
  page.on('pageerror', (error) => {
    if (!isKnownBrowserNoise(error.message)) errors.push(error.message)
  })
  await page.context().addCookies([
    { name: 'guigu_oa_client_token', value: 'token', url: e2eCookieURL() },
    { name: 'guigu_oa_client_entry', value: 'login-detail-test', url: e2eCookieURL() },
  ])
  let approvals = 0,
    completions = 0
  await page.route('**/dev-api/**', async (route) => {
    const path = new URL(route.request().url()).pathname.replace('/dev-api', '')
    if (path === '/admin/system/index/info') return json(route, { name: 'Alice', menus: [] })
    if (path.startsWith('/admin/process/show/')) {
      const id = path.split('/').pop()
      if (id === 'A' && readGate) await readGate
      return json(route, {
        process: { ...record, id, title: `审批 ${id}` },
        processRecordList: [],
        isApprove: true,
      })
    }
    if (path === '/admin/process/approve') {
      approvals++
      return approve(route)
    }
    if (path.startsWith('/admin/process/find'))
      return json(route, {
        records: [record, { ...record, id: 'B', taskId: 'task-B', title: '审批 B' }],
        total: 2,
        pages: 1,
      })
    if (path.startsWith('/admin/attachments/processes/') && path.endsWith('/download-url'))
      return json(route, { downloadUrl: 'http://localhost:9000/download' })
    if (path.startsWith('/admin/attachments/processes/'))
      return json(route, {
        attachments: [{ id: 9, fileName: '原附件.txt', sizeBytes: 2 }],
        canUpload: true,
      })
    if (path === '/admin/attachments/uploads') {
      const data = route.request().postDataJSON() as { fileName: string }
      return json(route, {
        attachmentId: data.fileName,
        uploadUrl: `http://localhost:9000/${data.fileName}`,
        headers: {},
      })
    }
    if (path.endsWith('/complete')) {
      completions++
      if (completionGate) await completionGate
      return json(route, { id: completions, fileName: `补传-${completions}.txt`, sizeBytes: 1 })
    }
    if (await fulfillNoticeUnread(route, path)) return
    throw new Error(`Unexpected request ${path}`)
  })
  // 生产构建的 CSP 只放行 connect-src 里的对象存储源（#139），mock 必须使用同一来源。
  await page.route('http://localhost:9000/**', async (route) => {
    if (uploadGate) await uploadGate
    await route.fulfill({ status: 200, body: '' })
  })
  return { errors, approvals: () => approvals, completions: () => completions }
}

test('通信故障后待确认跨浏览器刷新恢复，人工结束后从最新待处理重新进入', async ({ page }) => {
  const h = await setup(page, (route) => route.abort('failed'))
  await page.goto('/work/pending/A/task-A')
  await expect(page.getByRole('heading', { name: '审批 A', exact: true })).toBeVisible()
  await page.getByRole('button', { name: '通过', exact: true }).click()
  await expect(page.getByRole('heading', { name: '处理结果待确认' })).toBeVisible()
  await expect(page.locator('input[type=file]')).toBeDisabled()
  await page.reload()
  await expect(page.getByRole('heading', { name: '处理结果待确认' })).toBeVisible()
  expect(h.approvals()).toBe(1)
  await expect(page.getByRole('button', { name: '通过', exact: true })).toBeDisabled()
  await page.getByRole('button', { name: '刷新详情', exact: true }).click()
  await expect(page.getByRole('heading', { name: '处理结果待确认' })).toBeVisible()
  await page.getByRole('button', { name: '我已核实，结束本次记录' }).click()
  await expect(page.getByText('本次记录已结束', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '通过', exact: true })).toBeDisabled()
  await page.getByRole('button', { name: '返回最新待处理', exact: true }).click()
  await expect(page).toHaveURL(/\/work\/pending$/)
  await page.getByText('审批 A', { exact: true }).click()
  await expect(page.getByRole('button', { name: '通过', exact: true })).toBeEnabled()
  expect(h.approvals()).toBe(1)
  expect(h.errors).toEqual([])
  const widths = await page.evaluate(() => [innerWidth, document.documentElement.scrollWidth])
  expect(widths[1]).toBeLessThanOrEqual(widths[0]!)
})

test('处理中可直接返回，迟到成功不抢导航，重新打开相同地址没有旧提示', async ({ page }) => {
  const gate = deferred()
  const h = await setup(page, async (route) => {
    await gate.promise
    await json(route, null)
  })
  await page.goto('/work/pending/A/task-A')
  await page.getByRole('button', { name: '通过', exact: true }).click()
  await expect(page.getByText('正在提交处理', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '返回', exact: true }).click()
  await expect(page).toHaveURL(/\/work\/pending$/)
  gate.resolve()
  await expect.poll(h.approvals).toBe(1)
  await expect(page.getByRole('heading', { name: '待处理', exact: true })).toBeVisible()
  await page.getByText('审批 A', { exact: true }).click()
  await expect(page.getByRole('heading', { name: '审批 A', exact: true })).toBeVisible()
  await expect(page.getByText('正在提交处理', { exact: true })).toHaveCount(0)
  expect(h.errors).toEqual([])
})

test('整批附件补传时按钮禁用且不排队，补传结束后主动拒绝进入已处理', async ({ page }) => {
  const gate = deferred()
  const h = await setup(page, (route) => json(route, null), gate.promise)
  await page.goto('/work/pending/A/task-A')
  await expect(page.locator('input[type=file]')).toBeEnabled()
  await page.locator('input[type=file]').setInputFiles([
    { name: '1.txt', mimeType: 'text/plain', buffer: Buffer.from('1') },
    { name: '2.txt', mimeType: 'text/plain', buffer: Buffer.from('2') },
  ])
  await expect(page.getByRole('button', { name: '通过', exact: true })).toBeDisabled()
  await expect(page.getByRole('button', { name: '拒绝', exact: true })).toBeDisabled()
  expect(h.approvals()).toBe(0)
  gate.resolve()
  await expect(page.getByText('补传-2.txt', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '拒绝', exact: true })).toBeEnabled()
  expect(h.completions()).toBe(2)
  expect(h.approvals()).toBe(0)
  await page.getByRole('button', { name: '拒绝', exact: true }).click()
  await expect(page).toHaveURL(/\/work\/processed$/)
  expect(h.approvals()).toBe(1)
  expect(h.errors).toEqual([])
})

test('已处理入口保持只读并返回原列表', async ({ page }) => {
  const h = await setup(page, (route) => json(route, null))
  await page.goto('/work/processed/A')
  await expect(page.getByRole('heading', { name: '审批 A', exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '通过', exact: true })).toHaveCount(0)
  await page.getByRole('button', { name: '返回', exact: true }).click()
  await expect(page).toHaveURL(/\/work\/processed$/)
  expect(h.approvals()).toBe(0)
  expect(h.errors).toEqual([])
})

test('读取 A 时返回并打开 B，A 迟到结果不覆盖 B', async ({ page }) => {
  const gate = deferred()
  const h = await setup(page, (route) => json(route, null), undefined, gate.promise)
  const requestA = page.waitForRequest('**/admin/process/show/A')
  await page.goto('/work/pending/A/task-A')
  await requestA
  await page.getByRole('button', { name: '返回', exact: true }).click()
  await page.getByText('审批 B', { exact: true }).click()
  await expect(page.getByRole('heading', { name: '审批 B', exact: true })).toBeVisible()
  const responseA = page.waitForResponse('**/admin/process/show/A')
  gate.resolve()
  await responseA
  await expect(page.getByRole('heading', { name: '审批 B', exact: true })).toBeVisible()
  await expect(page.getByRole('heading', { name: '审批 A', exact: true })).toHaveCount(0)
  await expect(page.getByRole('button', { name: '通过', exact: true })).toBeEnabled()
  expect(h.errors).toEqual([])
})

test('附件登记在途离开列表后重开同审批，保持互斥直至原登记结束且不排队', async ({ page }) => {
  const gate = deferred()
  const h = await setup(page, (route) => json(route, null), undefined, undefined, gate.promise)
  await page.goto('/work/pending/A/task-A')
  await expect(page.locator('input[type=file]')).toBeEnabled()
  const registering = page.waitForRequest('**/admin/attachments/*/complete')
  await page.locator('input[type=file]').setInputFiles([
    { name: '1.txt', mimeType: 'text/plain', buffer: Buffer.from('1') },
    { name: '2.txt', mimeType: 'text/plain', buffer: Buffer.from('2') },
  ])
  await registering
  await page.getByRole('button', { name: '返回', exact: true }).click()
  await expect(page).toHaveURL(/\/work\/pending$/)
  await page.getByText('审批 A', { exact: true }).click()
  await expect(page.getByRole('heading', { name: '审批 A', exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '通过', exact: true })).toBeDisabled()
  await expect(page.getByRole('button', { name: '拒绝', exact: true })).toBeDisabled()
  await expect(page.locator('input[type=file]')).toBeDisabled()
  await expect(page.getByText('原附件.txt', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '下载', exact: true })).toBeEnabled()
  await page.getByRole('button', { name: '重新读取附件', exact: true }).click()
  await expect(page.getByText('原附件.txt', { exact: true })).toBeVisible()
  expect(h.approvals()).toBe(0)
  gate.resolve()
  await expect(page.getByRole('button', { name: '通过', exact: true })).toBeEnabled()
  await expect(page.getByText('补传-1.txt', { exact: true })).toHaveCount(0)
  expect(h.completions()).toBe(1)
  expect(h.approvals()).toBe(0)
  await page.getByRole('button', { name: '通过', exact: true }).click()
  await expect(page).toHaveURL(/\/work\/processed$/)
  expect(h.approvals()).toBe(1)
  expect(h.errors).toEqual([])
})
