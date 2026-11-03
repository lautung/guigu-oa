import { test, expect } from './helpers/fixtures'
import { uiLogin } from './helpers/ui'

/**
 * P1-9（#229）：真实栈上登录落到工作台（ADR 0051），四张 KPI 取现有列表 total，
 * 点「待处理」进办事列表。数据量随轮次变化，只断言形状与导航，不猜具体数字。
 */
test('登录落到工作台：KPI 可用并进入待处理', async ({ page }) => {
  await uiLogin(page, 'bob')

  await expect(page).toHaveURL(/\/workbench$/)
  await expect(page.getByRole('heading', { name: '工作台', exact: true })).toBeVisible()
  await expect(page.locator('.shell-tab').first()).toContainText('工作台')

  const kpis = page.locator('.kpi-card')
  await expect(kpis).toHaveCount(4)
  await expect(kpis.nth(0).locator('.kpi-label')).toHaveText('待处理')
  await expect(kpis.nth(1).locator('.kpi-label')).toHaveText('已处理')
  await expect(kpis.nth(2).locator('.kpi-label')).toHaveText('我发起的')
  await expect(kpis.nth(3).locator('.kpi-label')).toHaveText('抄送我的')
  for (let index = 0; index < 4; index += 1) {
    await expect(kpis.nth(index).locator('.kpi-value')).toHaveText(/^\d+$/)
  }

  await kpis.nth(0).click()
  await expect(page).toHaveURL(/\/work\/pending$/)
  await expect(page.getByRole('heading', { name: '待处理', exact: true })).toBeVisible()
})
