import { expect, test, type Page, type Route } from '@playwright/test'
function deferred() {
  let resolve!: () => void
  const promise = new Promise<void>((yes) => {
    resolve = yes
  })
  return { promise, resolve }
}
const json = (route: Route, data: unknown, code = 200, message?: string) =>
  route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code, data, message }) })
interface State {
  starts: Record<string, unknown>[]
  pending: boolean
  created: boolean
  remove?: () => Promise<void>
  start?: (route: Route) => Promise<void>
  errors: string[]
  warnings: string[]
  expectedStartAbort?: boolean
}
async function setup(page: Page): Promise<State> {
  const state: State = { starts: [], pending: false, created: false, errors: [], warnings: [] }
  page.on('pageerror', (error) => state.errors.push(error.message))
  page.on('console', (message) => {
    const text = message.text()
    const existingDeprecation = text.includes(
      'ElementPlusError: [props] [API] type.text is about to be deprecated',
    )
    if (message.type() === 'warning' && !existingDeprecation) state.warnings.push(text)
    const expectedNetworkFailure =
      state.expectedStartAbort &&
      text === 'Failed to load resource: net::ERR_FAILED' &&
      message.location().url.endsWith('/admin/process/startUp')
    if (message.type() === 'error' && !expectedNetworkFailure) state.errors.push(text)
  })
  let attachment = { id: 42, fileName: 'proof.txt', sizeBytes: 5 }
  let nextAttachmentId = 42
  await page.route('**/dev-api/**', async (route) => {
    const path = new URL(route.request().url()).pathname.replace(/^\/dev-api/, '')
    if (path.endsWith('/login'))
      return json(route, { token: 'test-access', refreshToken: 'test-refresh' })
    if (path.endsWith('/info')) return json(route, { name: 'Alice', configMenus: [] })
    if (path.includes('/findPending/') || path.includes('/findProcessed/'))
      return json(route, { records: [], total: 0 })
    if (path.includes('/findStarted/'))
      return json(route, {
        records: state.created ? [{ id: 100, title: '请假申请', status: 1 }] : [],
        total: state.created ? 1 : 0,
      })
    if (path.endsWith('/findStartable'))
      return json(route, [
        {
          id: 1,
          name: '人事',
          processTemplateList: [
            { id: 101, name: '请假申请', status: 1, availableForInitiation: true },
          ],
        },
      ])
    if (path.endsWith('/carbonCopyRecipients'))
      return json(route, [{ id: 7, username: 'carol', name: 'Carol' }])
    if (path.includes('/admin/process/getProcessTemplate/'))
      return json(route, {
        id: 101,
        processTypeId: 1,
        name: '请假申请',
        status: 1,
        formProps: JSON.stringify([
          {
            type: 'input',
            field: 'reason',
            title: '请假原因',
            $required: true,
            props: { placeholder: '请输入请假原因' },
          },
        ]),
        formOptions: JSON.stringify({ submitBtn: false }),
      })
    if (path.includes('/attachments/drafts/')) return json(route, state.pending ? [attachment] : [])
    if (path.endsWith('/attachments/uploads')) {
      const data = route.request().postDataJSON()
      attachment = { id: nextAttachmentId++, fileName: data.fileName, sizeBytes: data.sizeBytes }
      return json(route, {
        attachmentId: attachment.id,
        uploadUrl: 'https://objects.test/proof',
        headers: {},
      })
    }
    if (path.endsWith(`/attachments/${attachment.id}/complete`)) {
      state.pending = true
      return json(route, attachment)
    }
    if (path.endsWith('/attachments/pending/42')) {
      await state.remove?.()
      state.pending = false
      return json(route, null)
    }
    if (path.endsWith('/startUp')) {
      state.starts.push(route.request().postDataJSON() as Record<string, unknown>)
      if (state.start) return state.start(route)
      state.created = true
      state.pending = false
      return json(route, null)
    }
    throw new Error(`Unexpected request ${path}`)
  })
  await page.route('https://objects.test/**', (route) => route.fulfill({ status: 200, body: '' }))
  await page.goto('/login')
  await page.getByPlaceholder('用户名').fill('alice')
  await page.getByPlaceholder('密码').fill('111111')
  await page.getByRole('button', { name: '登录' }).click()
  await expect(page.getByRole('heading', { name: '待处理', exact: true })).toBeVisible()
  await page.getByRole('link', { name: '发起审批', exact: true }).click()
  await page.getByRole('button', { name: '请假申请', exact: true }).click()
  await expect(page.getByRole('heading', { name: '请假申请', exact: true })).toBeVisible()
  await expect(page.getByText('暂无附件', { exact: true })).toBeVisible()
  return state
}
async function fill(page: Page) {
  await page.getByPlaceholder('请输入请假原因').fill('家中有事')
  await page.locator('.carbon-copy-select').click()
  await page.getByRole('option', { name: 'Carol', exact: true }).click()
}
async function upload(page: Page) {
  await page
    .getByLabel('添加附件', { exact: true })
    .setInputFiles({ name: 'proof.txt', mimeType: 'text/plain', buffer: Buffer.from('proof') })
  await expect(page.getByText('proof.txt', { exact: true })).toBeVisible()
  await expect(page.getByText('未确认上传完成', { exact: false })).toHaveCount(0)
}
function healthy(state: State) {
  expect(state.errors).toEqual([])
  expect(state.warnings).toEqual([])
}

test('删除在途阻止发起，完成后需要再次点击', async ({ page }) => {
  const state = await setup(page)
  await fill(page)
  await upload(page)
  const removing = deferred()
  state.remove = () => removing.promise
  await page.getByRole('button', { name: '删除', exact: true }).click()
  await expect(page.getByRole('button', { name: '删除中…', exact: true })).toBeVisible()
  await page.getByRole('button', { name: '提交', exact: true }).click()
  await expect(page.getByText('请等待附件读取、上传或删除完成', { exact: true })).toBeVisible()
  expect(state.starts).toHaveLength(0)
  removing.resolve()
  await expect(page.getByText('暂无附件', { exact: true })).toBeVisible()
  expect(state.starts).toHaveLength(0)
  await page.getByRole('button', { name: '提交', exact: true }).click()
  await expect(page).toHaveURL(/\/work\/started$/)
  expect(state.starts[0].attachmentIds).toEqual([])
  healthy(state)
})
test('提交在途锁定全部内容，离开后的成功不抢导航', async ({ page }) => {
  const state = await setup(page)
  await fill(page)
  await upload(page)
  const sending = deferred()
  state.start = async (route) => {
    await sending.promise
    await json(route, null).catch(() => undefined)
  }
  await page.getByRole('button', { name: '提交', exact: true }).click()
  await expect.poll(() => state.starts.length).toBe(1)
  await expect(page.getByPlaceholder('请输入请假原因')).toBeDisabled()
  await expect(page.locator('input[type="file"]')).toBeDisabled()
  await expect(page.getByRole('combobox', { name: '抄送给' })).toBeDisabled()
  await expect(page.getByRole('button', { name: '删除', exact: true })).toBeDisabled()
  await page.getByRole('button', { name: '返回', exact: true }).click()
  await expect(page).toHaveURL(/\/work\/start$/)
  sending.resolve()
  await expect.poll(() => page.url()).toMatch(/\/work\/start$/)
  await expect(page.getByText('发起成功', { exact: true })).toHaveCount(0)
  expect(JSON.parse(state.starts[0].formValues as string).formData.reason).toBe('家中有事')
  healthy(state)
})
test('明确拒绝保留内容解锁，修改后可再次发起', async ({ page }) => {
  const state = await setup(page)
  await fill(page)
  await upload(page)
  state.start = async (route) => {
    await json(route, null, 201, '本次无法发起')
  }
  await page.getByRole('button', { name: '提交', exact: true }).click()
  await expect(page.getByText('本次无法发起', { exact: true })).toBeVisible()
  await expect(page.getByPlaceholder('请输入请假原因')).toBeEnabled()
  await expect(page.getByPlaceholder('请输入请假原因')).toHaveValue('家中有事')
  await expect(page.getByText('proof.txt', { exact: true })).toBeVisible()
  state.start = undefined
  await page.getByPlaceholder('请输入请假原因').fill('修改后')
  await page.getByRole('button', { name: '提交', exact: true }).click()
  await expect(page).toHaveURL(/\/work\/started$/)
  expect(state.starts).toHaveLength(2)
  expect(JSON.parse(state.starts[1].formValues as string).formData.reason).toBe('修改后')
  healthy(state)
})
test('结果不明跨刷新保留内容，核实后结束本次', async ({ page }, testInfo) => {
  const state = await setup(page)
  await fill(page)
  await upload(page)
  state.start = async (route) => {
    state.created = true
    state.pending = false
    state.expectedStartAbort = true
    await route.abort('failed')
  }
  await page.getByRole('button', { name: '提交', exact: true }).click()
  await expect(page.getByRole('heading', { name: '发起结果待确认', exact: true })).toBeVisible()
  await page.reload()
  await expect(page.getByRole('heading', { name: '发起结果待确认', exact: true })).toBeVisible()
  await expect(page.getByPlaceholder('请输入请假原因')).toHaveValue('家中有事')
  await expect(page.locator('.carbon-copy-select')).toContainText('Carol')
  await expect(page.getByText('proof.txt', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '提交', exact: true })).toBeDisabled()
  await expect(page.locator('vite-error-overlay')).toHaveCount(0)
  await expect(page).toHaveTitle('发起审批 - 云尚办公')
  await page.screenshot({ path: testInfo.outputPath('unconfirmed.png'), fullPage: true })
  const size = await page.evaluate(() => ({
    width: innerWidth,
    document: document.documentElement.scrollWidth,
  }))
  expect(size.document).toBeLessThanOrEqual(size.width)
  expect(state.starts).toHaveLength(1)
  await page.getByRole('link', { name: '查看已发起', exact: true }).click()
  await expect(page).toHaveURL(/\/work\/started$/)
  await expect(page.getByRole('heading', { name: '已发起', exact: true })).toBeVisible()
  await expect(page.getByText('请假申请', { exact: true })).toBeVisible()
  await page.goBack()
  await expect(page).toHaveURL(/\/work\/start\/101$/)
  await expect(page.getByRole('heading', { name: '发起结果待确认', exact: true })).toBeVisible()
  await page.getByRole('button', { name: '已找到，结束本次', exact: true }).click()
  await expect(page).toHaveURL(/\/work\/started$/)
  expect(state.starts).toHaveLength(1)
  healthy(state)
})

test('重新选择替换不可用附件，无需再忽略旧引用', async ({ page }) => {
  const state = await setup(page)
  await fill(page)
  await upload(page)
  state.start = async (route) => {
    state.pending = false
    state.expectedStartAbort = true
    await route.abort('failed')
  }
  await page.getByRole('button', { name: '提交', exact: true }).click()
  await expect(page.getByRole('heading', { name: '发起结果待确认', exact: true })).toBeVisible()
  await page.getByRole('button', { name: '仍未找到，重新发起', exact: true }).click()
  await page.getByRole('button', { name: '接受可能重复，继续', exact: true }).click()
  await page.getByLabel('重新选择 proof.txt', { exact: true }).setInputFiles({
    name: 'replacement.txt',
    mimeType: 'text/plain',
    buffer: Buffer.from('new proof'),
  })
  await expect(page.getByText('replacement.txt', { exact: true })).toBeVisible()
  await expect(page.getByText('proof.txt', { exact: true })).toHaveCount(0)
  await expect(page.getByRole('button', { name: '不再附加', exact: true })).toHaveCount(0)
  state.start = undefined
  await page.getByRole('button', { name: '提交', exact: true }).click()
  await expect(page).toHaveURL(/\/work\/started$/)
  expect(state.starts).toHaveLength(2)
  expect(state.starts[1].attachmentIds).toEqual([43])
  healthy(state)
})
test('空列表不解锁，明确重新发起先核对不可用附件', async ({ page }) => {
  const state = await setup(page)
  await fill(page)
  await upload(page)
  state.start = async (route) => {
    state.pending = false
    state.expectedStartAbort = true
    await route.abort('failed')
  }
  await page.getByRole('button', { name: '提交', exact: true }).click()
  await expect(page.getByRole('heading', { name: '发起结果待确认', exact: true })).toBeVisible()
  await page.getByRole('link', { name: '查看已发起', exact: true }).click()
  await expect(page.getByText('暂无已发起审批', { exact: true })).toBeVisible()
  await page.goBack()
  await expect(page.getByRole('button', { name: '提交', exact: true })).toBeDisabled()
  await page.getByRole('button', { name: '仍未找到，重新发起', exact: true }).click()
  await expect(page.getByRole('dialog')).toContainText('可能产生重复审批')
  await page.getByRole('button', { name: '接受可能重复，继续', exact: true }).click()
  await expect(
    page.getByText('已不可用于本次发起，请重新选择或不再附加', { exact: true }),
  ).toBeVisible()
  expect(state.starts).toHaveLength(1)
  await page.getByRole('button', { name: '提交', exact: true }).click()
  expect(state.starts).toHaveLength(1)
  await page.getByRole('button', { name: '不再附加', exact: true }).click()
  state.start = undefined
  await page.getByRole('button', { name: '提交', exact: true }).click()
  await expect(page).toHaveURL(/\/work\/started$/)
  expect(state.starts).toHaveLength(2)
  expect(state.starts[1].attachmentIds).toEqual([])
  healthy(state)
})
