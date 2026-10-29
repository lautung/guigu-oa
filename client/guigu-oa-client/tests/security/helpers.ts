import { expect, type Page } from '@playwright/test'

/** 安全用例共用的入口清单与登录动作（#113 / #139）。 */
export const PASSWORD = '111111'

/** HTML 入口：根、登录、办事列表、SPA 回退；安全响应头在这些响应上都必须成立。 */
export const CLIENT_PAGES = ['/', '/login', '/work/pending', '/nonexistent-sec-hdr']

export async function uiLogin(page: Page, username: string): Promise<void> {
  await page.goto('/login')
  await page.getByPlaceholder('用户名').fill(username)
  await page.getByPlaceholder('密码').fill(PASSWORD)
  await page.getByRole('button', { name: '登录' }).click()
  await expect(page.getByRole('heading', { name: '待处理' })).toBeVisible()
}
