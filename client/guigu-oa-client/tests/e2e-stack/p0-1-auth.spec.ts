import { test, expect } from './helpers/fixtures'
import { uiLogin, uiLoginExpectWork, uiLogout } from './helpers/ui'

/**
 * P0-1（#121）：登录成功 / 失败（不区分用户不存在与密码错）+ 停用不能进入 + 退出后回到登录页、
 * 直达 URL 被拦。对应 GLOSSARY「登录」「停用」「退出」「统一客户端」。
 */

test('登录成功落到待处理', async ({ page }) => {
  await uiLoginExpectWork(page, 'alice')
  await expect(page).toHaveURL(/\/work\/pending$/)
})

test('密码错误与用户不存在都提示认证失败（不区分）', async ({ page }) => {
  await uiLogin(page, 'alice', 'wrong-password')
  await expect(page.locator('.el-message', { hasText: '认证失败' })).toBeVisible()
  await expect(page).toHaveURL(/\/login(\?.*)?$/)

  await page.getByPlaceholder('用户名').fill('no-such-user')
  await page.getByPlaceholder('密码').fill('whatever1')
  await page.getByRole('button', { name: '登录' }).click()
  await expect(page.locator('.el-message', { hasText: '认证失败' })).toBeVisible()
  await expect(page).toHaveURL(/\/login(\?.*)?$/)
})

test('停用的用户不能登录，恢复后可以', async ({ page, oracle }) => {
  const userId = await oracle.userId('admin', 'user')
  try {
    await oracle.setUserStatus('admin', userId, 0)
    await uiLogin(page, 'user')
    await expect(page.locator('.el-message', { hasText: '账号已停用' })).toBeVisible()
    await expect(page).toHaveURL(/\/login(\?.*)?$/)
  } finally {
    await oracle.setUserStatus('admin', userId, 1)
  }
  await uiLoginExpectWork(page, 'user')
})

test('退出后回到登录页', async ({ page }) => {
  await uiLoginExpectWork(page, 'bob')
  await uiLogout(page)
  await expect(page).toHaveURL(/\/login(\?.*)?$/)
})

test('未登录直达办事 URL 被拦回登录页', async ({ page }) => {
  await page.goto('/work/pending')
  await expect(page).toHaveURL(/\/login(\?.*)?$/)
  await expect(page.getByPlaceholder('用户名')).toBeVisible()
})

test('退出后直达办事 URL 同样被拦', async ({ page }) => {
  await uiLoginExpectWork(page, 'bob')
  await uiLogout(page)
  await expect(page).toHaveURL(/\/login(\?.*)?$/)
  await page.goto('/work/pending')
  await expect(page).toHaveURL(/\/login(\?.*)?$/)
})
