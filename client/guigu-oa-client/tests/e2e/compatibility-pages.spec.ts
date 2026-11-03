import { expect, test, type Page, type Route, type TestInfo } from '@playwright/test'
import { mkdirSync, writeFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { e2eCookieURL } from './helpers/base-url'
import { isKnownBrowserNoise } from './helpers/known-noise'
import { fulfillNoticeUnread } from './helpers/notice-mocks'

/**
 * 兼容性关键页扫描（#114 §2/§3）：Chromium + WebKit、1280×900 + 390×844，
 * 1440×900 由 `desktop-1440-chromium` project 只跑本文件。
 *
 * 每格对六页（登录、发起审批列表/表单、待处理、审批详情、模板编辑、分配角色）截图，
 * 并判定 `#114 §5` 的前两条：页面级横向溢出、控制台 pageerror/error。
 * 办事主链路由 `workflow-smoke.spec.ts` 在各格复用；配置页在手机视口落回待处理是产品规则。
 */

const EVIDENCE_DIR = resolve('test-results/compatibility')

const json = (route: Route, data: unknown) =>
  route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code: 200, data }) })

const pendingRecord = {
  id: 'A',
  taskId: 'task-A',
  title: '请假申请',
  status: 1,
  createTime: '2026-10-29 09:00:00',
  formValues: JSON.stringify({ formShowData: { 请假原因: '家中有事' } }),
}

const template = {
  id: 101,
  processTypeId: 1,
  name: '请假申请',
  status: 1,
  availableForInitiation: true,
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
}

const menus = [
  { path: '/system/processSet', title: '审批设置', type: 0, sort: 2 },
  {
    path: '/system/processSet/processTemplate',
    parentPath: '/system/processSet',
    title: '审批模板',
    type: 1,
    sort: 2,
  },
  {
    path: '/system/processSet/templateSet',
    parentPath: '/system/processSet/processTemplate',
    title: '审批模板设置',
    type: 1,
    hidden: true,
    sort: 3,
  },
  { path: '/system', title: '系统管理', type: 0, sort: 1 },
  { path: '/system/sysUser', parentPath: '/system', title: '用户管理', type: 1, sort: 1 },
]

const users = [
  {
    id: 11,
    username: 'alice',
    name: 'Alice',
    phone: '13800000001',
    status: 1,
    createTime: '2026-01-01',
  },
  {
    id: 12,
    username: 'bob',
    name: 'Bob',
    phone: '13800000002',
    status: 1,
    createTime: '2026-01-02',
  },
]

interface ConsoleLog {
  errors: string[]
  warnings: string[]
  allowedExternalAssetErrors: string[]
}

const consoleLogs = new WeakMap<Page, ConsoleLog>()

/**
 * 已知噪声豁免（#114 §5）：模板编辑页的图标列表引用 `gw.alicdn.com` 的外部图片，
 * 受限网络下 CDN 返回 400，只影响图标缩略图，不影响页面交互。证据留在 console.json。
 */
const EXTERNAL_ASSET_NOISE = [/^https:\/\/gw\.alicdn\.com\//]

function isAllowedExternalAssetError(text: string, url: string): boolean {
  return (
    text.includes('Failed to load resource: the server responded with a status of 400') &&
    EXTERNAL_ASSET_NOISE.some((pattern) => pattern.test(url))
  )
}

test.beforeEach(({ page }) => {
  const log: ConsoleLog = { errors: [], warnings: [], allowedExternalAssetErrors: [] }
  page.on('pageerror', (error) => {
    if (!isKnownBrowserNoise(error.message)) log.errors.push(`pageerror: ${error.message}`)
  })
  page.on('console', (message) => {
    if (message.type() === 'warning') log.warnings.push(message.text())
    if (message.type() !== 'error') return
    const text = message.text()
    const url = message.location().url
    if (isAllowedExternalAssetError(text, url)) {
      log.allowedExternalAssetErrors.push(`${text} (${url})`)
      return
    }
    log.errors.push(text)
  })
  consoleLogs.set(page, log)
  // 浏览器自动请求的 favicon 与产品无关，避免把 404 噪声算进兼容性结论。
  void page.route('**/favicon.ico', (route) => route.fulfill({ status: 204, body: '' }))
})

async function seedSession(page: Page) {
  await page.context().addCookies([
    { name: 'guigu_oa_client_token', value: 'compat-token', url: e2eCookieURL() },
    { name: 'guigu_oa_client_refresh_token', value: 'compat-refresh', url: e2eCookieURL() },
  ])
}

async function mockApi(page: Page) {
  await page.route('**/dev-api/**', async (route) => {
    const path = new URL(route.request().url()).pathname.replace(/^\/dev-api/, '')
    if (path === '/admin/system/index/info') return json(route, { name: 'Alice', menus })
    if (path === '/admin/process/findStartable')
      return json(route, [
        {
          id: 1,
          name: '人事',
          processTemplateList: [template],
        },
      ])
    if (path === '/admin/process/carbonCopyRecipients')
      return json(route, [{ id: 7, name: 'Carol' }])
    if (path === `/admin/process/getProcessTemplate/${template.id}`) return json(route, template)
    if (path.startsWith('/admin/process/findPending/'))
      return json(route, { records: [pendingRecord], total: 1, pages: 1 })
    if (path.startsWith('/admin/process/findProcessed/'))
      return json(route, { records: [], total: 0, pages: 0 })
    if (path.startsWith('/admin/process/findStarted/'))
      return json(route, { records: [], total: 0, pages: 0 })
    if (path === '/admin/process/show/A')
      return json(route, {
        process: { ...pendingRecord, title: '审批 A' },
        processRecordList: [],
        isApprove: true,
      })
    if (path.startsWith('/admin/attachments/processes/'))
      return json(route, { attachments: [], canUpload: true })
    if (path.startsWith('/admin/attachments/drafts/')) return json(route, [])
    if (path === '/admin/process/processType/findAll') return json(route, [{ id: 1, name: '请假' }])
    if (path === '/admin/process/processTemplate/get/97')
      return json(route, {
        id: '97',
        name: '请假申请',
        processTypeId: 1,
        status: 0,
        description: '演示模板',
        iconUrl: '',
        formProps: '[]',
        formOptions: '{}',
        processDefinitionPath: 'opaque/old.zip',
        processDefinitionKey: 'old',
      })
    if (/^\/admin\/system\/sysUser\/\d+\/\d+$/.test(path))
      return json(route, { records: users, total: users.length, pages: 1 })
    if (path.startsWith('/admin/system/sysRole/toAssign/'))
      return json(route, {
        allRolesList: [
          { id: 1, roleName: '管理员' },
          { id: 2, roleName: '审批人' },
        ],
        assginRoleList: [{ id: 1, roleName: '管理员' }],
      })
    if (await fulfillNoticeUnread(route, path)) return
    throw new Error(`Unexpected request ${path}`)
  })
}

function isMobileViewport(page: Page): boolean {
  return (page.viewportSize()?.width ?? 0) <= 768
}

function evidenceBase(page: Page, testInfo: TestInfo, name: string, state: string): string {
  const viewport = page.viewportSize() ?? { width: 0, height: 0 }
  const engine = testInfo.project.name.includes('webkit') ? 'webkit' : 'chromium'
  const dir = resolve(EVIDENCE_DIR, testInfo.project.name)
  mkdirSync(dir, { recursive: true })
  return resolve(dir, `${engine}-${viewport.width}x${viewport.height}-${name}-${state}`)
}

async function capture(page: Page, testInfo: TestInfo, name: string, state: string) {
  const base = evidenceBase(page, testInfo, name, state)
  await page.screenshot({ path: `${base}.png`, fullPage: true, animations: 'disabled' })

  const overflow = await page.evaluate(() => ({
    width: window.innerWidth,
    documentWidth: document.documentElement.scrollWidth,
  }))
  expect(
    overflow.documentWidth,
    `${name}/${state} 横向溢出：文档 ${overflow.documentWidth}px > 视口 ${overflow.width}px`,
  ).toBeLessThanOrEqual(overflow.width + 1)

  const log = consoleLogs.get(page) ?? {
    errors: [],
    warnings: [],
    allowedExternalAssetErrors: [],
  }
  writeFileSync(
    `${base}.console.json`,
    JSON.stringify({ project: testInfo.project.name, page: name, state, ...log }, null, 2),
  )
  expect(log.errors, `${name}/${state} 出现控制台错误`).toEqual([])
}

test('登录页在矩阵内可渲染', async ({ page }, testInfo) => {
  await mockApi(page)
  await page.goto('/login')
  await expect(page.getByPlaceholder('用户名')).toBeVisible()
  await expect(page.getByRole('button', { name: '登录', exact: true })).toBeVisible()
  await capture(page, testInfo, 'login', 'idle')
})

test('发起审批的模板列表与表单在矩阵内可渲染', async ({ page }, testInfo) => {
  await seedSession(page)
  await mockApi(page)

  await page.goto('/work/start')
  await expect(page.getByRole('button', { name: '请假申请', exact: true })).toBeVisible()
  await capture(page, testInfo, 'start', 'template-list')

  await page.getByRole('button', { name: '请假申请', exact: true }).click()
  await expect(page.getByRole('heading', { name: '请假申请', exact: true })).toBeVisible()
  await expect(page.getByPlaceholder('请输入请假原因')).toBeVisible()
  await capture(page, testInfo, 'start', 'form')
})

test('待处理列表与审批详情在矩阵内可渲染', async ({ page }, testInfo) => {
  await seedSession(page)
  await mockApi(page)

  await page.goto('/work/pending')
  await expect(page.getByRole('heading', { name: '待处理', exact: true })).toBeVisible()
  await expect(page.getByText('请假申请', { exact: true })).toBeVisible()
  await capture(page, testInfo, 'pending', 'list')

  await page.goto('/work/pending/A/task-A')
  await expect(page.getByRole('heading', { name: '审批 A', exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '通过', exact: true })).toBeVisible()
  await capture(page, testInfo, 'detail', 'pending')
})

test('配置-模板编辑：桌面可占用，手机落回待处理', async ({ page }, testInfo) => {
  await seedSession(page)
  await mockApi(page)
  await page.goto('/system/processSet/templateSet?id=97')

  if (isMobileViewport(page)) {
    await expect(page.getByRole('heading', { name: '待处理', exact: true })).toBeVisible()
    await capture(page, testInfo, 'config-template-edit', 'forced-pending')
    return
  }

  await expect(page.getByRole('button', { name: '下一步', exact: true })).toBeVisible()
  await expect(page.getByLabel('审批名称')).toHaveValue('请假申请')
  await capture(page, testInfo, 'config-template-edit', 'basic-info')
})

test('配置-分配角色：桌面可打开弹窗，手机落回待处理', async ({ page }, testInfo) => {
  await seedSession(page)
  await mockApi(page)
  await page.goto('/system/sysUser')

  if (isMobileViewport(page)) {
    await expect(page.getByRole('heading', { name: '待处理', exact: true })).toBeVisible()
    await capture(page, testInfo, 'config-assign-roles', 'forced-pending')
    return
  }

  await expect(page.getByRole('heading', { name: '用户管理', exact: true })).toBeVisible()
  await page
    .getByRole('row')
    .filter({ hasText: 'alice' })
    .getByRole('button', { name: '分配角色', exact: true })
    .click()
  const dialog = page.getByRole('dialog')
  await expect(dialog.getByText('用户：alice', { exact: true })).toBeVisible()
  await capture(page, testInfo, 'config-assign-roles', 'dialog')
})
