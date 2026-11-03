import { expect, type Page } from '@playwright/test'

/** 窄屏下列表在抽屉里：先打开菜单再点侧栏链接。 */
export async function openShellLink(page: Page, title: string): Promise<void> {
  const sidebar = page.locator('.app-sidebar')
  if ((page.viewportSize()?.width ?? 1280) < 900) {
    const isOpen = await sidebar.evaluate((element) => element.classList.contains('open'))
    if (!isOpen) await page.locator('.menu-toggle').click()
  }
  await page.locator('.app-sidebar').getByRole('link', { name: title, exact: true }).click()
}

/** 用户菜单里的退出登录；窄屏与桌面走同一入口。 */
export async function logoutFromShell(page: Page): Promise<void> {
  await page.locator('.user-trigger').click()
  await page.locator('.el-dropdown-menu__item', { hasText: '退出登录' }).click()
  await expect(page.getByPlaceholder('用户名')).toBeVisible()
}
