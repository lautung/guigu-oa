import { expect, type Locator, type Page } from '@playwright/test'

import { PASSWORD } from './oracle'

/**
 * UI 主路径操作（#121 §6）：真实浏览器操作统一客户端，断言交给各 spec 里的 oracle。
 * 选择器与 mock e2e（tests/e2e）保持一致——同一套 UI，同一套文案。
 */

export async function uiLogin(page: Page, username: string, password = PASSWORD): Promise<void> {
  await page.goto('/login')
  await page.getByPlaceholder('用户名').fill(username)
  await page.getByPlaceholder('密码').fill(password)
  await page.getByRole('button', { name: '登录' }).click()
}

export async function uiLoginExpectWork(page: Page, username: string): Promise<void> {
  await uiLogin(page, username)
  await expect(page.getByRole('heading', { name: '待处理' })).toBeVisible()
}

export function workItem(page: Page, text: string): Locator {
  return page.locator('.work-list-item', { hasText: text })
}

/**
 * 等当前办事列表初始加载完成（#169）：page-title 先于数据渲染，loading 中无行、
 * 无分页器、无空态；首轮扫描前必须等正信号（行或空态出现）。
 * 不能等 loading-mask 消失——注册时 mask 尚未挂载的话「不存在」会被 hidden
 * 立即满足（TOCTOU）；visible 语义会持续等到元素将来出现，无此空窗。
 */
export async function expectWorkListLoaded(page: Page): Promise<void> {
  await page
    .locator('.work-list-item, .el-empty')
    .first()
    .waitFor({ state: 'visible', timeout: 15_000 })
}

/** 点下一页并等列表返回；返回 false 表示没有下一页。翻页过早会跳过目标行（#169）。 */
export async function nextWorkListPage(page: Page): Promise<boolean> {
  const next = page.locator('.el-pagination .btn-next')
  if ((await next.count()) === 0 || (await next.isDisabled())) return false
  // 等列表请求真正返回再继续：固定 sleep 在慢 runner 上会提前翻下一页，跳过目标行。
  const response = page.waitForResponse(
    (res) => /\/admin\/process\/find(?:Pending|Processed|Started|CarbonCopied)\//.test(res.url()),
    { timeout: 15_000 },
  )
  await next.click()
  await response
  await page
    .locator('.list-container .el-loading-mask')
    .waitFor({ state: 'hidden', timeout: 5_000 })
    .catch(() => {})
  return true
}

async function walkWorkList(page: Page, text: string, maxPages: number): Promise<Locator | null> {
  await expectWorkListLoaded(page)
  for (let visited = 1; ; visited++) {
    const item = workItem(page, text)
    if ((await item.count()) > 0) return item.first()
    if (visited >= maxPages) return null
    if (!(await nextWorkListPage(page))) return null
  }
}

export async function expectWorkItemVisible(page: Page, text: string): Promise<Locator> {
  const item = await walkWorkList(page, text, 40)
  if (!item) throw new Error(`办事列表翻页后仍找不到：${text}`)
  await expect(item).toBeVisible()
  return item
}

export async function clickWorkItem(page: Page, text: string): Promise<void> {
  const item = await expectWorkItemVisible(page, text)
  await item.click()
}

export async function openWorkList(page: Page, title: string): Promise<void> {
  await page.getByRole('link', { name: title }).click()
  await expect(page.getByRole('heading', { name: title, level: 2 })).toBeVisible()
}

export function sidebarGroups(page: Page): Locator {
  return page.locator('.app-sidebar .sidebar-group')
}

/** 统一客户端发起页的演示表单：请假原因 + 请假天数（演示模板 formProps）。 */
export async function fillDemoForm(
  page: Page,
  fields: { reason: string; days?: string },
): Promise<void> {
  const reasonItem = page.locator('.el-form-item', { hasText: '请假原因' })
  await reasonItem.locator('input').first().fill(fields.reason)
  const daysItem = page.locator('.el-form-item', { hasText: '请假天数' })
  await daysItem
    .locator('input')
    .first()
    .fill(fields.days ?? '1')
}

// 提交成功即跳已发起；行存在性由调用方的 oracle 断言（该列表随轮次增长，不靠翻页找新行）。
export async function submitStart(page: Page): Promise<void> {
  await page.getByRole('button', { name: '提交', exact: true }).click()
  await expect(page.getByRole('heading', { name: '已发起', level: 2 })).toBeVisible()
}

/** 发起页上传附件并等它出现在附件列表。 */
export async function uploadStartAttachment(
  page: Page,
  file: { name: string; contentType: string; body: Buffer },
): Promise<void> {
  await page.locator('.attachment-section input[type="file"]').setInputFiles({
    name: file.name,
    mimeType: file.contentType,
    buffer: file.body,
  })
  await expect(page.locator('.attachment-section').getByText(file.name)).toBeVisible()
}

/** 在详情页点「通过 / 拒绝」，等待跳转到已处理列表并出现结果文案。 */
export async function decide(page: Page, action: '通过' | '拒绝'): Promise<void> {
  await page.getByRole('button', { name: action, exact: true }).click()
  await expect(page.getByRole('heading', { name: '已处理', level: 2 })).toBeVisible()
  await expect(page.getByText(action === '通过' ? '审批通过' : '审批拒绝').first()).toBeVisible()
}

/** Element Plus 确认框（删除等写操作的 confirm）。 */
export async function confirmMessageBox(page: Page): Promise<void> {
  const box = page.locator('.el-message-box')
  await expect(box).toBeVisible()
  await box.getByRole('button', { name: '确定' }).click()
  await expect(box).toBeHidden()
}

/** 配置页表格行：当前页找不到就翻页（el-pagination），最多 maxPages 页。 */
export async function rowAcrossPages(
  page: Page,
  text: string,
  maxPages = 10,
): Promise<Locator | null> {
  await page.locator('.el-table').first().waitFor({ state: 'visible' })
  await page
    .locator('.el-table__row')
    .first()
    .waitFor({ state: 'visible' })
    .catch(() => {})
  for (let visited = 1; ; visited++) {
    const row = page.locator('.el-table__row', { hasText: text })
    if ((await row.count()) > 0) return row.first()
    if (visited >= maxPages) return null
    const next = page.locator('.el-pagination .btn-next')
    if ((await next.count()) === 0 || (await next.isDisabled())) return null
    await next.click()
    await page.waitForTimeout(400)
  }
}
