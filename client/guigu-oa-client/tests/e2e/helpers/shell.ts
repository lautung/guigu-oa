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

/** 登录落点（#229）：工作台是默认页，后续用例再显式切到待处理。 */
export async function expectWorkbench(page: Page): Promise<void> {
  await expect(page.getByRole('heading', { name: '工作台', exact: true })).toBeVisible()
  await expect(page).toHaveURL(/\/workbench$/)
}

export async function openPendingFromWorkbench(page: Page): Promise<void> {
  await page.getByRole('button', { name: '处理待办', exact: true }).click()
  await expect(page.getByRole('heading', { name: '待处理', exact: true })).toBeVisible()
}

/** 顶栏退出登录；窄屏与桌面走同一入口。 */
export async function logoutFromShell(page: Page): Promise<void> {
  await page.locator('.logout-btn').click()
  await expect(page.getByPlaceholder('用户名')).toBeVisible()
}
