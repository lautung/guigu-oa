import { test, expect } from './helpers/fixtures'
import { poll } from './helpers/oracle'
import { sidebarGroups, uiLoginExpectWork } from './helpers/ui'

/**
 * P1-2（#121）：分配角色后菜单权限生效（ADR 0006/0015）。admin 在用户管理给 user
 * 授「配置管理员」，user 重新进入后侧栏出现配置组并能进配置页；撤销后回到只办事。
 * GLOSSARY「配置权限」「分配角色」。
 *
 * doAssign 允许 201（提交成功、权限缓存失效待确认，ADR 0015），落库与缓存失效都有
 * 毫秒到秒级延迟：落库用 oracle 轮询确认，侧栏变化用重新登录重试。
 * 测试结束前撤销授权，不污染后续运行。
 */

test('分配配置角色后 user 的配置菜单生效，撤销后消失', async ({ page, oracle }) => {
  const userId = await oracle.userId('admin', 'user')
  try {
    await uiLoginExpectWork(page, 'admin')
    await page.goto('/system/sysUser')
    await page.getByPlaceholder('用户名/姓名/手机号码').fill('user')
    await page.getByRole('button', { name: '搜索' }).click()
    const row = page.locator('.el-table__row', { hasText: 'user' }).first()
    await expect(row).toBeVisible()
    await row.getByRole('button', { name: '分配角色' }).click()

    const dialog = page.locator('.el-dialog', { hasText: '分配角色' })
    await expect(dialog).toBeVisible()
    await dialog.locator('.el-checkbox', { hasText: '配置管理员' }).click()
    await dialog.getByRole('button', { name: '保存' }).click()
    await expect(dialog).toBeHidden()

    // 先确认分配已落库（缓存失效待确认不改变这次提交已保留的事实）。
    const assigned = await poll(15_000, 500, async () => {
      const data = await oracle.call<{ hasRoleList?: { roleName: string }[] }>(
        'admin',
        'get',
        `/admin/system/sysRole/toAssign/${userId}`,
      )
      return data.assginRoleList?.some((item) => item.roleName === '配置管理员') ? true : undefined
    })
    expect(assigned, '分配应最终落库').toBe(true)

    // 重新进入：配置组出现，配置页可以占用。
    await page.locator('.logout-btn').click()
    await loginUntilSidebarGroups(page, 'user', 2)
    await page.goto('/system/processSet/processType')
    await expect(page.getByRole('heading', { name: '审批类型', level: 2 })).toBeVisible()
  } finally {
    await oracle.assignRoles('admin', userId, [])
  }

  // 撤销后回到只办事（重新进入才重新读取菜单，同样等缓存失效）。
  await page.locator('.logout-btn').click()
  await loginUntilSidebarGroups(page, 'user', 1)
})

/** 登录后核对侧栏分组数；权限缓存失效有延迟，重试几次重新登录。 */
async function loginUntilSidebarGroups(
  page: import('@playwright/test').Page,
  username: string,
  groups: number,
): Promise<void> {
  for (let attempt = 0; ; attempt++) {
    await uiLoginExpectWork(page, username)
    const matched = (await sidebarGroups(page).count()) === groups
    if (matched || attempt >= 5) break
    await page.locator('.logout-btn').click()
    await page.waitForTimeout(1_000)
  }
  await expect(sidebarGroups(page)).toHaveCount(groups)
}
