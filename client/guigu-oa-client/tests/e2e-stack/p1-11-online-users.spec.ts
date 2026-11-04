import { test, expect } from './helpers/fixtures'
import { uiLoginExpectWork } from './helpers/ui'

/**
 * P1-11（#239，ADR 0060）：在线用户页的真实栈验收。
 *
 * admin 登录后，在线列表第一行就是当前会话（服务端按 loginTime 倒序，本次登录最新），
 * 带「当前会话」标记且强退按钮禁用；没有监控授予的 user 直达页面回落工作台。
 */

test('登录后在线列表出现当前会话并标 current', async ({ page }) => {
  await uiLoginExpectWork(page, 'admin')

  await page.goto('/monitor/online')
  await expect(page).toHaveURL(/\/monitor\/online$/)
  await expect(page.getByRole('heading', { name: '在线用户', exact: true })).toBeVisible()
  await expect(page.locator('.shell-breadcrumb')).toContainText('系统监控')
  await expect(page.locator('.app-sidebar').getByText('在线用户', { exact: true })).toBeVisible()

  const currentRow = page.locator('.el-table__row', { hasText: '当前会话' })
  await expect(currentRow).toBeVisible()
  await expect(currentRow).toContainText('admin')
  await expect(currentRow.getByRole('button', { name: '强退' })).toBeDisabled()
})

test('没有监控授予的 user 直达 /monitor/online 回落工作台', async ({ page }) => {
  await uiLoginExpectWork(page, 'user')

  await page.goto('/monitor/online')
  await expect(page).toHaveURL(/\/workbench$/)
  await expect(page.getByRole('heading', { name: '工作台', exact: true })).toBeVisible()
})
