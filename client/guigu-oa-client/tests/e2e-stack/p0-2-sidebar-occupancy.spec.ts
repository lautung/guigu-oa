import { test, expect } from './helpers/fixtures'
import { sidebarGroups, uiLoginExpectWork } from './helpers/ui'

/**
 * P0-2（#121）：admin 与 user 的侧栏分组差异；user 直达配置 URL 落到待处理；
 * admin 可进配置页。对应 GLOSSARY「配置权限」「菜单」「菜单组」「占用」。
 */

test('admin 侧栏有办事与配置两组', async ({ page }) => {
  await uiLoginExpectWork(page, 'admin')
  const groups = sidebarGroups(page)
  await expect(groups).toHaveCount(2)
  await expect(groups.filter({ hasText: '办事' })).toBeVisible()
  await expect(groups.filter({ hasText: '配置' })).toBeVisible()
  await expect(page.locator('.app-sidebar').getByText('组织与权限')).toBeVisible()
  await expect(page.locator('.app-sidebar').getByText('审批设置')).toBeVisible()
  await expect(page.locator('.app-sidebar').getByText('审批管理')).toBeVisible()
})

test('user 侧栏只有办事组', async ({ page }) => {
  await uiLoginExpectWork(page, 'user')
  const groups = sidebarGroups(page)
  await expect(groups).toHaveCount(1)
  await expect(groups.filter({ hasText: '办事' })).toBeVisible()
  await expect(page.locator('.app-sidebar').getByText('组织与权限')).toHaveCount(0)
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
