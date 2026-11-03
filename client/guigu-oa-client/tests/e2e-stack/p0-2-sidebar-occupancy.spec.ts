import { test, expect } from './helpers/fixtures'
import { topModules, uiLoginExpectWork, uiSwitchModule } from './helpers/ui'

/**
 * P0-2（#121）：admin 与 user 的一级模块差异；user 直达配置 URL 落到待处理；
 * admin 可进配置页。对应 GLOSSARY「后台」「后台页」「页面授予」。
 */

test('admin 顶部有工作台/办事/系统管理，系统管理侧栏可见', async ({ page }) => {
  await uiLoginExpectWork(page, 'admin')
  await expect(topModules(page)).toHaveText(['工作台', '办事', '系统管理'])
  await uiSwitchModule(page, '系统管理')
  await expect(page).toHaveURL(/\/system\/sysUser$/)
  const sidebar = page.locator('.app-sidebar')
  await expect(sidebar.getByText('用户管理')).toBeVisible()
  await expect(sidebar.getByText('角色管理')).toBeVisible()
  await expect(sidebar.getByText('菜单管理')).toBeVisible()
})

test('user 顶部只有工作台与办事', async ({ page }) => {
  await uiLoginExpectWork(page, 'user')
  await expect(topModules(page)).toHaveText(['工作台', '办事'])
  await expect(page.locator('.top-nav-item', { hasText: '系统管理' })).toHaveCount(0)
})

test('user 直达配置 URL 落到待处理', async ({ page }) => {
  await uiLoginExpectWork(page, 'user')
  await page.goto('/system/sysUser')
  await expect(page).toHaveURL(/\/work\/pending$/)
  await expect(page.getByRole('heading', { name: '待处理', level: 2 })).toBeVisible()
})

test('admin 可以进入用户管理', async ({ page }) => {
  await uiLoginExpectWork(page, 'admin')
  await page.goto('/system/sysUser')
  await expect(page).toHaveURL(/\/system\/sysUser$/)
  await expect(page.getByRole('heading', { name: '用户管理', level: 2 })).toBeVisible()
})
