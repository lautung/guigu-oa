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

// 办事列表的定位/等待/翻页收在共享 helper（#226）：这里只 re-export，
// 避免 security 套件再出现一份没跟上 #169 的副本。
export {
  clickWorkItem,
  expectWorkItemVisible,
  expectWorkListLoaded,
  nextWorkListPage,
  walkWorkList,
  workItem,
  workListItems,
} from '../../helpers/work-list'

export async function openWorkList(page: Page, title: string): Promise<void> {
  await page.getByRole('link', { name: title }).click()
  await expect(page.getByRole('heading', { name: title, level: 2 })).toBeVisible()
}

export function topModules(page: Page): Locator {
  return page.locator('.top-nav-item')
}

export async function uiSwitchModule(page: Page, title: string): Promise<void> {
  await page.locator('.top-nav-item', { hasText: title }).click()
}

export async function uiLogout(page: Page): Promise<void> {
  await page.locator('.logout-btn').click()
  await expect(page.getByPlaceholder('用户名')).toBeVisible()
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

/** 发起页上传附件并等它确认进入附件列表。 */
export async function uploadStartAttachment(
  page: Page,
  file: { name: string; contentType: string; body: Buffer },
): Promise<void> {
  await page.locator('.attachment-section input[type="file"]').setInputFiles({
    name: file.name,
    mimeType: file.contentType,
    buffer: file.body,
  })
  // 文件名在上传开始即乐观渲染（未确认行也会出现，#192）；提交会撞 busy 守卫被静默拒绝，
  // 必须等确认态正信号——行内出现「删除」按钮（未确认/中断行只有重新选择或继续未完成文件）。
  const row = page.locator('.attachment-section li', { hasText: file.name })
  await expect(row).toBeVisible()
  await expect(row.getByRole('button', { name: '删除', exact: true })).toBeVisible()
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
