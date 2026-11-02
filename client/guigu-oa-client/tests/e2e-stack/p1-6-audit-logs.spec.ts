import { test, expect } from './helpers/fixtures'
import { marker } from './helpers/stack'
import { confirmMessageBox, rowAcrossPages, uiLoginExpectWork } from './helpers/ui'

/**
 * P1-6（#217）：日志管理两个页面的真实栈验收。
 *
 * 操作日志：后台写动作（新建角色）→ 操作日志页出现该行，详情里能看到该次请求的参数。
 * 登录日志：失败登录 → 登录日志页出现失败行；点解锁 → 出现解锁行，操作日志另记「谁点的」。
 * 不改种子数据；自造的角色用完即删，日志行按审计保留。
 */

test('后台写动作在操作日志页留下可查的行与参数', async ({ page }) => {
  const name = marker('日志角色')
  await uiLoginExpectWork(page, 'admin')

  await page.goto('/system/sysRole')
  await page.getByRole('button', { name: '新建', exact: true }).click()
  const dialog = page.locator('.el-dialog', { hasText: '新建' })
  await dialog.locator('.el-form-item', { hasText: '角色名称' }).locator('input').fill(name)
  await dialog.locator('.el-form-item', { hasText: '角色编码' }).locator('input').fill(name)
  await dialog.getByRole('button', { name: '确定', exact: true }).click()
  await expect(dialog).toBeHidden()

  await page.goto('/system/log/operLog')
  await expect(page.getByRole('heading', { name: '操作日志', exact: true })).toBeVisible()
  await page.getByPlaceholder('操作人').fill('admin')
  await page.getByPlaceholder('操作模块').fill('角色管理·新增角色')
  await page.getByRole('button', { name: '搜索', exact: true }).click()

  const row = await rowAcrossPages(page, '角色管理·新增角色')
  expect(row).toBeTruthy()
  await row!.getByRole('button', { name: '详情', exact: true }).click()
  const detail = page.locator('.el-dialog', { hasText: '操作日志详情' })
  await expect(detail).toBeVisible()
  await expect(detail.getByText(name)).toBeVisible()
  await expect(detail.getByText('角色管理·新增角色')).toBeVisible()
  await page.keyboard.press('Escape')
  await expect(page.locator('.el-dialog', { hasText: '操作日志详情' })).toHaveCount(0)

  // 清理自造角色（这次删除也会在操作日志里留一行，属于审计事实）。
  await page.goto('/system/sysRole')
  await page.getByPlaceholder('角色名称').fill(name)
  await page.getByRole('button', { name: '搜索', exact: true }).click()
  const roleRow = await rowAcrossPages(page, name)
  expect(roleRow).toBeTruthy()
  await roleRow!.getByRole('button', { name: '删除', exact: true }).click()
  await confirmMessageBox(page)
  await expect(page.locator('.el-table__row', { hasText: name })).toHaveCount(0)
})

test('登录失败写入失败行，解锁后出现解锁行并在操作日志留下解锁动作', async ({ page, oracle }) => {
  const failedUser = marker('登录失败')

  // 未知账号的一次错误密码：认证失败，登录审计按 FAILURE 落库（ADR 0054）。
  const failure = await oracle.raw('post', '/admin/system/index/login', {
    username: failedUser,
    password: 'definitely-wrong',
  })
  expect(failure.code).not.toBe(200)

  await uiLoginExpectWork(page, 'admin')
  await page.goto('/system/log/loginLog')
  await expect(page.getByRole('heading', { name: '登录日志', exact: true })).toBeVisible()
  await page.getByPlaceholder('用户名').fill(failedUser)
  await page.getByRole('button', { name: '搜索', exact: true }).click()

  const failedRow = await rowAcrossPages(page, failedUser)
  expect(failedRow).toBeTruthy()
  await expect(failedRow!.getByText('失败', { exact: true })).toBeVisible()

  await failedRow!.getByRole('button', { name: '解锁', exact: true }).click()
  await confirmMessageBox(page)

  const unlockRow = page.locator('.el-table__row', { hasText: '手动解锁' }).first()
  await expect(unlockRow).toBeVisible()
  await expect(unlockRow.getByText('解锁', { exact: true })).toBeVisible()
  await expect(unlockRow.getByText(failedUser, { exact: true })).toBeVisible()

  // 解锁是双写：操作日志另记一行「谁点的」（ADR 0054）。
  await page.goto('/system/log/operLog')
  await page.getByPlaceholder('操作模块').fill('登录日志·解锁')
  await page.getByRole('button', { name: '搜索', exact: true }).click()
  const unlockAction = await rowAcrossPages(page, '登录日志·解锁')
  expect(unlockAction).toBeTruthy()
  await expect(unlockAction!.getByText('解锁', { exact: true })).toBeVisible()
})
