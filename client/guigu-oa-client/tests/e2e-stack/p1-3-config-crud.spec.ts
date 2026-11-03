import { test, expect } from './helpers/fixtures'
import { marker } from './helpers/stack'
import { confirmMessageBox, rowAcrossPages, uiLoginExpectWork } from './helpers/ui'

/**
 * P1-3（#121）：用户 / 角色 / 菜单 / 审批类型 CRUD 各一条 happy path（admin 配置页真实栈）。
 * 对应 GLOSSARY「配置」「用户」「角色」「菜单」「审批类型」。
 * 每条建了就删；菜单建为停用状态，避免影响任何人的侧栏。
 */

test('审批类型：建、改、删', async ({ page }) => {
  const name = marker('类型')
  await uiLoginExpectWork(page, 'admin')
  await page.goto('/system/processSet/processType')
  await page.getByRole('button', { name: '新建' }).click()
  const dialog = page.locator('.el-dialog', { hasText: '新建' })
  await dialog.locator('.el-form-item', { hasText: '类型名称' }).locator('input').fill(name)
  await dialog.locator('.el-form-item', { hasText: '描述' }).locator('input').fill('栈测试类型')
  await dialog.getByRole('button', { name: '确定' }).click()
  await expect(dialog).toBeHidden()

  let row = await rowAcrossPages(page, name)
  expect(row).toBeTruthy()
  await row!.getByRole('button', { name: '编辑' }).click()
  const editDialog = page.locator('.el-dialog', { hasText: '编辑' })
  await editDialog
    .locator('.el-form-item', { hasText: '类型名称' })
    .locator('input')
    .fill(`${name}改`)
  await editDialog.getByRole('button', { name: '确定' }).click()
  await expect(editDialog).toBeHidden()

  row = await rowAcrossPages(page, `${name}改`)
  expect(row).toBeTruthy()
  await row!.getByRole('button', { name: '删除' }).click()
  await confirmMessageBox(page)
  await expect(page.locator('.el-table__row', { hasText: `${name}改` })).toHaveCount(0)
})

test('角色：建、删', async ({ page }) => {
  const name = marker('角色')
  await uiLoginExpectWork(page, 'admin')
  await page.goto('/system/sysRole')
  await page.getByRole('button', { name: '新建' }).click()
  const dialog = page.locator('.el-dialog', { hasText: '新建' })
  await dialog.locator('.el-form-item', { hasText: '角色名称' }).locator('input').fill(name)
  await dialog.locator('.el-form-item', { hasText: '角色编码' }).locator('input').fill(name)
  await dialog.getByRole('button', { name: '确定' }).click()
  await expect(dialog).toBeHidden()

  const row = await rowAcrossPages(page, name)
  expect(row).toBeTruthy()
  await row!.getByRole('button', { name: '删除' }).click()
  await confirmMessageBox(page)
  await expect(page.locator('.el-table__row', { hasText: name })).toHaveCount(0)
})

test('用户：建、删', async ({ page }) => {
  const name = marker('用户')
  await uiLoginExpectWork(page, 'admin')
  await page.goto('/system/sysUser')
  await page.getByRole('button', { name: '新建' }).click()
  const dialog = page.locator('.el-dialog', { hasText: '新建用户' })
  await dialog.locator('.el-form-item', { hasText: '用户名' }).locator('input').fill(name)
  await dialog.locator('.el-form-item', { hasText: '密码' }).locator('input').fill('111111')
  await dialog.locator('.el-form-item', { hasText: '姓名' }).locator('input').fill(name)
  await dialog.getByRole('button', { name: '确定' }).click()
  await expect(dialog).toBeHidden()

  await page.getByPlaceholder('用户名/姓名/手机号码').fill(name)
  await page.getByRole('button', { name: '搜索', exact: true }).click()
  const row = await rowAcrossPages(page, name)
  expect(row).toBeTruthy()
  await row!.getByRole('button', { name: '删除' }).click()
  await confirmMessageBox(page)
  await expect(page.locator('.el-table__row', { hasText: name })).toHaveCount(0)
})

test('菜单：建（停用）、删', async ({ page }) => {
  const name = marker('菜单')
  await uiLoginExpectWork(page, 'admin')
  await page.goto('/system/sysMenu')

  // 服务端只允许维护配置菜单（子节点须挂在配置根下，ADR 0011 的授予闭包），
  // 顶层任意路径菜单会被 201「只能维护配置菜单」拒绝，所以这里在「审批管理」下加停用子节点。
  // 菜单页必须自带组件路径（ADR 0036），默认类型是「菜单」，组件路径也要填。
  const parentRow = page.locator('.el-table__row', { hasText: '审批管理' }).first()
  await expect(parentRow).toBeVisible()
  await parentRow.getByRole('button', { name: '添加下级' }).click()
  const dialog = page.locator('.el-dialog', { hasText: '添加下级节点' })
  await dialog.locator('.el-form-item', { hasText: '菜单名称' }).locator('input').fill(name)
  await dialog
    .locator('.el-form-item', { hasText: '路由地址' })
    .locator('input')
    .fill(`stk-${name}`)
  await dialog
    .locator('.el-form-item', { hasText: '组件路径' })
    .locator('input')
    .fill('system/sysUser/list')
  await dialog.locator('.el-form-item', { hasText: '状态' }).getByText('停用').click()
  await dialog.getByRole('button', { name: '确定' }).click()
  await expect(dialog).toBeHidden()

  const row = page.locator('.el-table__row', { hasText: name }).first()
  await expect(row).toBeVisible()
  await row.getByRole('button', { name: '删除' }).click()
  await confirmMessageBox(page)
  await expect(page.locator('.el-table__row', { hasText: name })).toHaveCount(0)
})
