import { expect, test, type Page, type Route } from '@playwright/test'
import { e2eCookieURL } from './helpers/base-url'
import { isKnownBrowserNoise } from './helpers/known-noise'

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
  page.on('pageerror', (error) => {
    if (!isKnownBrowserNoise(error.message)) errors.push(error.message)
  })
  await page.context().addCookies([
    { name: 'guigu_oa_client_token', value: 'edit-token', url: e2eCookieURL() },
    {
      name: 'guigu_oa_client_refresh_token',
      value: 'edit-refresh',
      url: e2eCookieURL(),
    },
    { name: 'guigu_oa_client_entry', value: 'edit-opening', url: e2eCookieURL() },
  ])
  await page.context().route('**/dev-api/**', async (route) => {
    const path = new URL(route.request().url()).pathname.replace('/dev-api', '')
    if (path === '/admin/system/index/info')
      return json(route, {
        name: 'Alice',
        configMenus:
          options.menus === false
            ? []
            : [
                { path: '/processSet', title: '审批设置', type: 0, sort: 2 },
                {
                  path: '/processSet/processTemplate',
                  parentPath: '/processSet',
                  title: '审批模板',
                  type: 1,
                  sort: 2,
                },
                {
                  path: '/processSet/templateSet',
                  parentPath: '/processSet/processTemplate',
                  title: '审批模板设置',
                  type: 1,
                  hidden: true,
                  sort: 3,
                },
              ],
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
  await expect(page.locator('._fc-m').getByRole('textbox')).toBeVisible()
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
  await expect(page.getByText('opaque/old.zip')).toBeVisible()
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
  await expect(page.getByText('opaque/B.zip')).toBeVisible()
  const responseA = page.waitForResponse('**/uploadProcessDefinition')
  gate.resolve()
  await responseA
  await expect(page.getByText('opaque/B.zip')).toBeVisible()
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
  await page.getByLabel('描述', { exact: true }).fill('本次待确认说明')
  await page.getByRole('button', { name: '下一步', exact: true }).click()
  await page.getByText('输入框', { exact: true }).click()
  await page.getByRole('button', { name: '下一步', exact: true }).click()
  await page.getByRole('button', { name: '提交保存', exact: true }).click()
  await expect(page.getByText('模板保存结果待确认', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '提交保存', exact: true })).toBeDisabled()
  await expect(page.locator('input[type=file]')).toBeDisabled()
  await page.getByRole('button', { name: '上一步', exact: true }).click()
  const submittedForm = page.locator('.submitted-form-preview')
  await expect(submittedForm.getByRole('heading', { name: '本次提交的表单设计' })).toBeVisible()
  await expect(submittedForm.getByRole('textbox')).toBeVisible()
  await expect(submittedForm.getByRole('textbox')).toBeDisabled()
  expect(JSON.parse(h.saved[0]!.formProps as string)).toEqual(
    expect.arrayContaining([expect.objectContaining({ type: 'input' })]),
  )
  await page.getByRole('button', { name: '上一步', exact: true }).click()
  await expect(page.getByLabel('审批名称', { exact: true })).toHaveValue('本次待确认内容')
  await expect(page.getByLabel('审批名称', { exact: true })).toBeDisabled()
  await expect(
    page
      .locator('.el-form-item')
      .filter({ hasText: '审批类型' })
      .getByText('请假', { exact: true }),
  ).toBeVisible()
  await expect(page.getByLabel('审批类型', { exact: true })).toBeDisabled()
  await expect(page.getByLabel('描述', { exact: true })).toHaveValue('本次待确认说明')
  await expect(page.getByLabel('描述', { exact: true })).toBeDisabled()
  await page.getByRole('button', { name: '下一步', exact: true }).click()
  await expect(submittedForm.getByRole('textbox')).toBeDisabled()
  await page.getByRole('button', { name: '下一步', exact: true }).click()
  await expect(page.getByRole('button', { name: '提交保存', exact: true })).toBeDisabled()
  const popup = page.context().waitForEvent('page')
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
  await expect(page.getByText('opaque/renewed.zip')).toBeVisible()
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

test('新建的合法空设计在有效上传后保存为未发布', async ({ page }) => {
  test.skip((page.viewportSize()?.width || 0) <= 768, '配置在桌面使用')
  const h = await setup(page)
  await page.goto('/processSet/templateSet')
  await page.getByLabel('审批名称', { exact: true }).fill('空设计新建')
  await page.getByText('请选择审批类型', { exact: true }).click()
  await page.getByRole('option', { name: '请假', exact: true }).click()
  await flowStep(page)
  await expect(page.getByRole('button', { name: '提交保存', exact: true })).toBeDisabled()
  await file(page, 'empty.zip')
  await expect(page.getByRole('button', { name: '提交保存', exact: true })).toBeEnabled()
  await page.getByRole('button', { name: '提交保存', exact: true }).click()
  await expect(page).toHaveURL(/\/processSet\/processTemplate$/)
  expect(h.saved).toHaveLength(1)
  expect(h.saved[0]).not.toHaveProperty('id')
  expect(h.saved[0]).toMatchObject({
    name: '空设计新建',
    status: 0,
    formProps: '[]',
    processDefinitionPath: 'opaque/new.zip',
  })
  expect(h.errors).toEqual([])
})

test('B 成功后 C 返回不完整结果，明确恢复时采用最近成功的 B', async ({ page }) => {
  test.skip((page.viewportSize()?.width || 0) <= 768, '配置在桌面使用')
  let uploads = 0
  const h = await setup(page, {
    upload: (route) =>
      ++uploads === 1
        ? json(route, { processDefinitionPath: 'opaque/B.zip', processDefinitionKey: 'B' })
        : json(route, { processDefinitionPath: 'opaque/C.zip' }),
  })
  await page.goto('/processSet/templateSet?id=97')
  await flowStep(page)
  await file(page, 'B.zip')
  await expect(page.getByText('opaque/B.zip')).toBeVisible()
  await file(page, 'C.zip')
  await expect(page.getByText('流程 zip 上传失败')).toBeVisible()
  await expect(page.getByRole('button', { name: '提交保存', exact: true })).toBeDisabled()
  await page.getByRole('button', { name: '恢复旧 zip', exact: true }).click()
  await expect(page.getByText('opaque/B.zip')).toBeVisible()
  await page.getByRole('button', { name: '提交保存', exact: true }).click()
  await expect(page).toHaveURL(/\/processSet\/processTemplate$/)
  expect(h.saved[0]).toMatchObject({
    processDefinitionPath: 'opaque/B.zip',
    processDefinitionKey: 'B',
  })
  expect(h.errors).toEqual([])
})

test('保存中冻结内容，确认离开后重新进入同地址不接受旧保存成功', async ({ page }) => {
  test.skip((page.viewportSize()?.width || 0) <= 768, '配置在桌面使用')
  const gate = deferred()
  const h = await setup(page, {
    save: async (route) => {
      await gate.promise
      await json(route, null)
    },
  })
  await page.goto('/processSet/templateSet?id=97')
  await page.getByLabel('审批名称', { exact: true }).fill('在途提交快照')
  await flowStep(page)
  const sending = page.waitForRequest('**/processTemplate/update')
  await page.getByRole('button', { name: '提交保存', exact: true }).click()
  await sending
  await expect(page.getByRole('button', { name: '提交保存', exact: true })).toBeDisabled()
  await expect(page.locator('input[type=file]')).toBeDisabled()
  await page.getByRole('button', { name: '返回', exact: true }).click()
  await expect(page.getByRole('dialog')).toContainText('不撤销')
  await page.getByRole('button', { name: '确认离开', exact: true }).click()
  await expect(page).toHaveURL(/\/processSet\/processTemplate$/)
  await page
    .locator('.el-table__row')
    .filter({ hasText: '模板时序测试' })
    .getByRole('button', { name: '编辑', exact: true })
    .click()
  await expect(page.getByLabel('审批名称', { exact: true })).toHaveValue('模板时序测试')
  const response = page.waitForResponse('**/processTemplate/update')
  gate.resolve()
  await response
  await expect(page).toHaveURL(/templateSet\?id=97$/)
  await expect(page.getByLabel('审批名称', { exact: true })).toHaveValue('模板时序测试')
  await expect(page.getByText('保存成功', { exact: true })).toHaveCount(0)
  expect(h.saved).toHaveLength(1)
  expect(h.saved[0]!.name).toBe('在途提交快照')
  expect(h.errors).toEqual([])
})

test('普通离开可取消，缩到手机时优先强制离开并关闭丢弃确认', async ({ page }) => {
  test.skip((page.viewportSize()?.width || 0) <= 768, '从桌面缩为手机')
  const h = await setup(page)
  await page.goto('/processSet/templateSet?id=97')
  await page.getByLabel('审批名称', { exact: true }).fill('尚未保存的编辑')
  await page.getByRole('link', { name: '待处理', exact: true }).click()
  await expect(page.getByRole('dialog')).toBeVisible()
  await page.getByRole('button', { name: '继续编辑', exact: true }).click()
  await expect(page.getByLabel('审批名称', { exact: true })).toHaveValue('尚未保存的编辑')
  await page.getByRole('link', { name: '待处理', exact: true }).click()
  await expect(page.getByRole('dialog')).toBeVisible()
  await page.setViewportSize({ width: 390, height: 844 })
  await expect(page).toHaveURL(/\/work\/pending$/)
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await expect(page.getByRole('heading', { name: '待处理', exact: true })).toBeVisible()
  expect(h.saved).toHaveLength(0)
  expect(h.errors).toEqual([])
})

test('结束待确认编辑前说明重复写入风险，取消结束仍保留快照', async ({ page }) => {
  test.skip((page.viewportSize()?.width || 0) <= 768, '配置在桌面使用')
  const h = await setup(page, { save: (route) => json(route, null, 599) })
  await page.goto('/processSet/templateSet?id=97')
  await flowStep(page)
  await page.getByRole('button', { name: '提交保存', exact: true }).click()
  await expect(page.getByText('模板保存结果待确认', { exact: true })).toBeVisible()
  await page.getByRole('alert').getByRole('button', { name: '结束本次编辑', exact: true }).click()
  await expect(page.getByRole('dialog')).toContainText('重复创建或覆盖')
  await page.getByRole('button', { name: '保留本次编辑', exact: true }).click()
  await expect(page.getByRole('dialog')).toBeHidden()
  await expect(page.getByRole('button', { name: '提交保存', exact: true })).toBeDisabled()
  await page.getByRole('alert').getByRole('button', { name: '结束本次编辑', exact: true }).click()
  await page.getByRole('dialog').getByRole('button', { name: '结束本次编辑', exact: true }).click()
  await expect(page).toHaveURL(/\/processSet\/processTemplate$/)
  expect(h.saved).toHaveLength(1)
  await expect(page.getByText('保存失败', { exact: true })).toHaveCount(0)
  expect(h.errors).toEqual([])
})

test('读取在途离开后重开同地址，旧读取不覆盖本次内容', async ({ page }) => {
  test.skip((page.viewportSize()?.width || 0) <= 768, '配置在桌面使用')
  const gate = deferred()
  let reads = 0
  const h = await setup(page, {
    read: async (route) => {
      const first = ++reads === 1
      if (first) await gate.promise
      await json(route, { ...original, name: first ? '不应出现的旧读取' : '本次重新读取' })
    },
  })
  const reading = page.waitForRequest('**/processTemplate/get/97')
  await page.goto('/processSet/templateSet?id=97')
  await reading
  await page.getByRole('link', { name: '审批模板', exact: true }).click()
  await expect(page).toHaveURL(/\/processSet\/processTemplate$/)
  await page.locator('.el-table__row').getByRole('button', { name: '编辑', exact: true }).click()
  await expect(page.getByLabel('审批名称', { exact: true })).toHaveValue('本次重新读取')
  const oldResponse = page.waitForResponse('**/processTemplate/get/97')
  gate.resolve()
  await oldResponse
  await expect(page.getByLabel('审批名称', { exact: true })).toHaveValue('本次重新读取')
  expect(h.saved).toHaveLength(0)
  expect(h.errors).toEqual([])
})

test('进入结果被替换时清理编辑，旧上传结果不影响重新进入', async ({ page }) => {
  test.skip((page.viewportSize()?.width || 0) <= 768, '配置在桌面使用')
  const gate = deferred()
  const h = await setup(page, {
    upload: async (route) => {
      await gate.promise
      await json(route, {
        processDefinitionPath: 'old-identity.zip',
        processDefinitionKey: 'old-identity',
      })
    },
  })
  await page.goto('/processSet/templateSet?id=97')
  await page.getByLabel('审批名称', { exact: true }).fill('旧身份未保存内容')
  await flowStep(page)
  const uploading = page.waitForRequest('**/uploadProcessDefinition')
  await file(page, 'old-identity.zip')
  await uploading
  await page
    .context()
    .addCookies([
      { name: 'guigu_oa_client_entry', value: 'replacement-entry', domain: '127.0.0.1', path: '/' },
    ])
  await page.evaluate(() => window.dispatchEvent(new Event('focus')))
  await expect(page).toHaveURL(/\/work\/pending$/)
  await expect(page.getByRole('dialog')).toHaveCount(0)
  const oldResponse = page.waitForResponse('**/uploadProcessDefinition')
  gate.resolve()
  await oldResponse
  await page.getByRole('link', { name: '审批模板', exact: true }).click()
  await page.locator('.el-table__row').getByRole('button', { name: '编辑', exact: true }).click()
  await expect(page.getByLabel('审批名称', { exact: true })).toHaveValue('模板时序测试')
  await flowStep(page)
  await expect(page.getByText('opaque/old.zip')).toBeVisible()
  await expect(page.getByText('old-identity.zip')).toHaveCount(0)
  expect(h.saved).toHaveLength(0)
  expect(h.errors).toEqual([])
})
