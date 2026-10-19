import { expect, test, type Page, type Route } from '@playwright/test'

function deferred() {
  let resolve!: () => void
  const promise = new Promise<void>((done) => {
    resolve = done
  })
  return { promise, resolve }
}
const original = {
  id: '97',
  name: '模板时序测试',
  processTypeId: 1,
  status: 0,
  description: '保留原说明',
  iconUrl: '',
  formProps: '[]',
  formOptions: '{}',
  processDefinitionPath: 'opaque/old.zip',
  processDefinitionKey: 'old',
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
  options: {
    record?: unknown
    read?: (route: Route) => Promise<void>
    upload?: (route: Route) => Promise<void>
    save?: (route: Route) => Promise<void>
    menus?: boolean
  } = {},
) {
  const errors: string[] = []
  const saved: Record<string, unknown>[] = []
  const uploads: string[] = []
  page.on('pageerror', (error) => errors.push(error.message))
  await page.context().addCookies([
    { name: 'guigu_oa_client_token', value: 'edit-token', domain: '127.0.0.1', path: '/' },
    {
      name: 'guigu_oa_client_refresh_token',
      value: 'edit-refresh',
      domain: '127.0.0.1',
      path: '/',
    },
    { name: 'guigu_oa_client_entry', value: 'edit-opening', domain: '127.0.0.1', path: '/' },
  ])
  await page.context().route('**/dev-api/**', async (route) => {
    const path = new URL(route.request().url()).pathname.replace('/dev-api', '')
    if (path === '/admin/system/index/info')
      return json(route, {
        name: 'Alice',
        routers:
          options.menus === false
            ? []
            : [{ path: '/processSet/processTemplate', meta: { title: '审批模板' } }],
      })
    if (path === '/admin/system/index/refresh')
      return json(route, { token: 'renewed-edit-token', refreshToken: 'renewed-refresh' })
    if (path === '/admin/process/processType/findAll') return json(route, [{ id: 1, name: '请假' }])
    if (path.startsWith('/admin/process/processTemplate/get/'))
      return options.read
        ? options.read(route)
        : json(route, options.record === undefined ? original : options.record)
    if (path === '/admin/process/processTemplate/uploadProcessDefinition') {
      uploads.push(route.request().headers()['token'] || '')
      return options.upload
        ? options.upload(route)
        : json(route, { processDefinitionPath: 'opaque/new.zip', processDefinitionKey: 'new' })
    }
    if (
      path === '/admin/process/processTemplate/save' ||
      path === '/admin/process/processTemplate/update'
    ) {
      saved.push(route.request().postDataJSON() as Record<string, unknown>)
      return options.save ? options.save(route) : json(route, null)
    }
    if (path === '/admin/process/processTemplate/1/10')
      return json(route, { records: [original], total: 1, pages: 1 })
    if (path.startsWith('/admin/process/findPending/'))
      return json(route, { records: [], total: 0, pages: 0 })
    throw new Error(`Unexpected request ${path}`)
  })
  return { errors, saved, uploads }
}
async function flowStep(page: Page) {
  await page.getByRole('button', { name: '下一步', exact: true }).click()
  await expect(page.getByText('基础组件', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '下一步', exact: true }).click()
  await expect(page.locator('input[type=file]')).toBeAttached()
}
async function file(page: Page, name: string) {
  await page
    .locator('input[type=file]')
    .setInputFiles({ name, mimeType: 'application/zip', buffer: Buffer.from(name) })
}

test('真实设计器往返三步后提交完整设计，保存仍为未发布', async ({ page }) => {
  test.skip((page.viewportSize()?.width || 0) <= 768, '配置在桌面使用')
  const h = await setup(page)
  await page.goto('/processSet/templateSet?id=97')
  await page
    .locator('.el-form-item')
    .filter({ hasText: '审批名称' })
    .locator('input')
    .fill('三步完整编辑')
  await page.getByRole('button', { name: '下一步', exact: true }).click()
  await page.getByText('输入框', { exact: true }).click()
  await page.getByRole('button', { name: '下一步', exact: true }).click()
  await page.getByRole('button', { name: '上一步', exact: true }).click()
  await expect(page.getByText('输入框', { exact: true })).toHaveCount(2)
  await page.getByRole('button', { name: '下一步', exact: true }).click()
  await page.getByRole('button', { name: '提交保存', exact: true }).click()
  await expect(page).toHaveURL(/\/processSet\/processTemplate$/)
  expect(h.saved).toHaveLength(1)
  expect(h.saved[0]).toMatchObject({
    id: '97',
    name: '三步完整编辑',
    status: 0,
    processDefinitionPath: 'opaque/old.zip',
    processDefinitionKey: 'old',
  })
  expect(JSON.parse(h.saved[0]!.formProps as string)).toEqual(
    expect.arrayContaining([expect.objectContaining({ type: 'input' })]),
  )
  expect(h.errors).toEqual([])
})

test('已有旧包时，新上传在途与失败均不能保存，明确恢复后才采用旧包', async ({ page }) => {
  test.skip((page.viewportSize()?.width || 0) <= 768, '配置在桌面使用')
  const gate = deferred()
  const h = await setup(page, {
    upload: async (route) => {
      await gate.promise
      await route.abort('failed')
    },
  })
  await page.goto('/processSet/templateSet?id=97')
  await flowStep(page)
  const uploading = page.waitForRequest('**/uploadProcessDefinition')
  await file(page, 'replacement.zip')
  await uploading
  await expect(page.getByRole('button', { name: '提交保存', exact: true })).toBeDisabled()
  gate.resolve()
  await expect(page.getByRole('button', { name: /恢复.*zip/ })).toBeVisible()
  await expect(page.getByRole('button', { name: '提交保存', exact: true })).toBeDisabled()
  expect(h.saved).toHaveLength(0)
  await page.getByRole('button', { name: /恢复.*zip/ }).click()
  await expect(page.getByText('opaque/old.zip', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '提交保存', exact: true }).click()
  await expect(page).toHaveURL(/\/processSet\/processTemplate$/)
  expect(h.saved[0]).toMatchObject({
    processDefinitionPath: 'opaque/old.zip',
    processDefinitionKey: 'old',
  })
  expect(h.errors).toEqual([])
})

test('上传 A 后选择 B，A 的迟到结果不能覆盖最终采用的 B', async ({ page }) => {
  test.skip((page.viewportSize()?.width || 0) <= 768, '配置在桌面使用')
  const gate = deferred()
  let count = 0
  const h = await setup(page, {
    upload: async (route) => {
      const current = ++count
      if (current === 1) await gate.promise
      await json(route, {
        processDefinitionPath: current === 1 ? 'opaque/A.zip' : 'opaque/B.zip',
        processDefinitionKey: current === 1 ? 'A' : 'B',
      })
    },
  })
  await page.goto('/processSet/templateSet?id=97')
  await flowStep(page)
  await file(page, 'A.zip')
  await expect.poll(() => count).toBe(1)
  await file(page, 'B.zip')
  await expect(page.getByText('opaque/B.zip', { exact: true })).toBeVisible()
  const responseA = page.waitForResponse('**/uploadProcessDefinition')
  gate.resolve()
  await responseA
  await expect(page.getByText('opaque/B.zip', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '提交保存', exact: true }).click()
  await expect(page).toHaveURL(/\/processSet\/processTemplate$/)
  expect(h.saved[0]).toMatchObject({
    processDefinitionPath: 'opaque/B.zip',
    processDefinitionKey: 'B',
  })
  expect(h.errors).toEqual([])
})

test('保存断网后保留待确认快照，另开列表不释放限制，刷新不恢复未保存内容', async ({ page }) => {
  test.skip((page.viewportSize()?.width || 0) <= 768, '配置在桌面使用')
  const h = await setup(page, { save: (route) => route.abort('failed') })
  await page.goto('/processSet/templateSet?id=97')
  await page
    .locator('.el-form-item')
    .filter({ hasText: '审批名称' })
    .locator('input')
    .fill('本次待确认内容')
  await flowStep(page)
  await page.getByRole('button', { name: '提交保存', exact: true }).click()
  await expect(page.getByText('模板保存结果待确认', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '提交保存', exact: true })).toBeDisabled()
  await expect(page.locator('input[type=file]')).toBeDisabled()
  const popup = page.waitForEvent('popup')
  await page.getByRole('button', { name: '另开模板列表核实', exact: true }).click()
  const list = await popup
  await expect(list).toHaveURL(/\/processSet\/processTemplate$/)
  await expect(page.getByText('模板保存结果待确认', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '提交保存', exact: true })).toBeDisabled()
  expect(h.saved).toHaveLength(1)
  await page.reload()
  await expect(
    page.locator('.el-form-item').filter({ hasText: '审批名称' }).locator('input'),
  ).toHaveValue('模板时序测试')
  await expect(page.getByText('模板保存结果待确认', { exact: true })).toHaveCount(0)
  expect(h.saved).toHaveLength(1)
  expect(h.errors).toEqual([])
})

test('认证恢复重发保存后断网，仍进入待确认且不再次自动重发', async ({ page }) => {
  test.skip((page.viewportSize()?.width || 0) <= 768, '配置在桌面使用')
  let requests = 0
  const h = await setup(page, {
    save: (route) => (++requests === 1 ? json(route, null, 208) : route.abort('failed')),
  })
  await page.goto('/processSet/templateSet?id=97')
  await flowStep(page)
  await page.getByRole('button', { name: '提交保存', exact: true }).click()
  await expect(page.getByText('模板保存结果待确认', { exact: true })).toBeVisible()
  expect(h.saved).toHaveLength(2)
  await expect(page.getByRole('button', { name: '提交保存', exact: true })).toBeDisabled()
  expect(h.errors).toEqual([])
})

test('上传逐请求采用刷新后的凭据，再上传新包使用当前身份', async ({ page }) => {
  test.skip((page.viewportSize()?.width || 0) <= 768, '配置在桌面使用')
  let requests = 0
  const h = await setup(page, {
    upload: (route) =>
      ++requests === 1
        ? json(route, null, 208)
        : json(route, {
            processDefinitionPath: 'opaque/renewed.zip',
            processDefinitionKey: 'renewed',
          }),
  })
  await page.goto('/processSet/templateSet?id=97')
  await flowStep(page)
  await file(page, 'renewed.zip')
  await expect(page.getByText('opaque/renewed.zip', { exact: true })).toBeVisible()
  expect(h.uploads).toEqual(['edit-token', 'renewed-edit-token'])
  await file(page, 'next.zip')
  await expect.poll(() => h.uploads.length).toBe(3)
  expect(h.uploads[2]).toBe('renewed-edit-token')
  expect(h.saved).toHaveLength(0)
  expect(h.errors).toEqual([])
})

for (const [name, record] of [
  ['缺失记录', null],
  ['损坏设计', { ...original, formProps: '{broken' }],
  ['顶层不兼容', { ...original, formOptions: '[]' }],
] as const) {
  test(`${name}不会降级为空白新建或覆盖`, async ({ page }) => {
    test.skip((page.viewportSize()?.width || 0) <= 768, '配置在桌面使用')
    const h = await setup(page, { record })
    await page.goto('/processSet/templateSet?id=97')
    await expect(page.getByRole('button', { name: /重试|重新读取/ })).toBeVisible()
    await expect(page.getByRole('button', { name: '下一步', exact: true })).toBeDisabled()
    expect(h.saved).toHaveLength(0)
    expect(h.errors).toEqual([])
  })
}

test('手机尺寸与缺少配置菜单时不能占用模板编辑页', async ({ page }) => {
  const h = await setup(page, { menus: false })
  await page.goto('/processSet/templateSet?id=97')
  await expect(page).toHaveURL(/\/work\/pending$/)
  await expect(page.getByRole('heading', { name: '待处理', exact: true })).toBeVisible()
  expect(h.saved).toHaveLength(0)
  expect(h.errors).toEqual([])
})

test('有配置菜单的手机也不能占用模板编辑页', async ({ page }) => {
  test.skip((page.viewportSize()?.width || 0) > 768, '验证手机配置进入规则')
  const h = await setup(page)
  await page.goto('/processSet/templateSet?id=97')
  await expect(page).toHaveURL(/\/work\/pending$/)
  await expect(page.getByRole('heading', { name: '待处理', exact: true })).toBeVisible()
  expect(h.saved).toHaveLength(0)
})
