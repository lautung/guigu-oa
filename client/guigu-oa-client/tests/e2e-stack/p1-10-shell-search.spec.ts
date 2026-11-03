import { test, expect } from './helpers/fixtures'
import { uiLoginExpectWork } from './helpers/ui'

/**
 * P1-10（#230）：真实栈上的顶栏菜单搜索。
 * 索引来自 /info.menus 与内置页面，只搜已授权页面；这里用所有登录用户都有的
 * 「全部公告」验证「输入 → 结果 → 打开页面 → 面包屑」整条链路。
 */
test('菜单搜索打开全部公告并落到真实页面', async ({ page }) => {
  await uiLoginExpectWork(page, 'user')

  await page.locator('.menu-search-trigger').click()
  const input = page.locator('.menu-search-input')
  await expect(input).toBeVisible()

  await input.fill('公告')
  const result = page.locator('.menu-search-item', { hasText: '全部公告' }).first()
  await expect(result).toBeVisible()
  await result.click()

  await expect(page).toHaveURL(/\/notice$/)
  await expect(page.getByRole('heading', { name: '全部公告', exact: true })).toBeVisible()
  await expect(page.locator('.shell-breadcrumb')).toContainText('工作台')
  await expect(page.locator('.shell-breadcrumb')).toContainText('全部公告')
})
