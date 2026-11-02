import { expect, type Locator, type Page } from '@playwright/test'

/**
 * 办事列表（待处理 / 已处理 / 已发起 / 抄送）的定位、等待与翻页。
 *
 * 只依赖 @playwright/test：e2e-stack 与 security 两套配置共用同一份实现（#226），
 * 不要再在 spec 里复制一份扫描逻辑；`scripts/check-test-tooling.mjs` 会拒绝
 * `tests/**` 里出现办事列表行选择器（本文件除外）。选择器与 mock e2e（tests/e2e）
 * 保持一致——同一套 UI，同一套文案。
 */

export function workListItems(page: Page): Locator {
  return page.locator('.work-list-item')
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

/** 从第 1 页起逐页找目标行，最多 maxPages 页；找不到返回 null。 */
export async function walkWorkList(
  page: Page,
  text: string,
  maxPages: number,
): Promise<Locator | null> {
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
