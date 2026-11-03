import { test, expect } from './helpers/fixtures'
import { uiLoginExpectWork } from './helpers/ui'

/**
 * P1-14（#242，ADR 0060）：定时任务页的真实栈验收。
 *
 * admin（super_admin 全授予）登录后：任务表由启动登记产生（demo 任务
 * demoLogMonitorJob 种成 disabled 行），页面无新增/删除入口；disabled 任务允许
 * 手动执行一次（#238 明确语义），异步触发后执行记录出现在抽屉里（触发方式
 * 「手动执行」）；cron 编辑非法值吃服务端 201 文案，合法值保存后行上刷新。
 * user（无监控授予）直达回落工作台。
 */

const DEMO_JOB = '演示：示例日志任务'

test('授予后定时任务页打开并渲染启动登记的任务行', async ({ page }) => {
  await uiLoginExpectWork(page, 'admin')

  await page.goto('/monitor/job')
  await expect(page).toHaveURL(/\/monitor\/job$/)
  await expect(page.getByRole('heading', { name: '定时任务', exact: true })).toBeVisible()
  await expect(page.locator('.shell-breadcrumb')).toContainText('系统监控')
  await expect(page.locator('.app-sidebar').getByText('定时任务', { exact: true })).toBeVisible()

  // demo 任务行可见：bean 存活（无「异常」标签），默认停用（开关关着但可操作）。
  const demoRow = page.locator('.el-table__row', { hasText: DEMO_JOB })
  await expect(demoRow).toBeVisible()
  await expect(demoRow.locator('.broken-tag')).toHaveCount(0)
  await expect(demoRow.locator('.el-switch:not(.is-disabled)')).toBeVisible()
  await expect(demoRow).toContainText('demoLogMonitorJob')
  await expect(demoRow).toContainText('禁止')

  // 任务行由启动登记产生：页面不提供新增与删除（ADR 0060）。
  await expect(page.getByRole('button', { name: '新增' })).toHaveCount(0)
  await expect(page.getByRole('button', { name: '删除' })).toHaveCount(0)
})

test('disabled 的 demo 任务手动执行一次后记录出现在抽屉', async ({ page }) => {
  await uiLoginExpectWork(page, 'admin')

  await page.goto('/monitor/job')
  const demoRow = page.locator('.el-table__row', { hasText: DEMO_JOB })
  await expect(demoRow).toBeVisible()

  // 二次确认 → 异步触发 → 「已触发」提示。
  await demoRow.getByRole('button', { name: '执行一次' }).click()
  await expect(page.locator('.el-message-box')).toContainText(DEMO_JOB)
  await page.locator('.el-message-box').getByRole('button', { name: '确定' }).click()
  await expect(page.locator('.el-message')).toContainText('已触发')

  // 执行是异步落库的：抽屉每次打开重读最近 20 条，Esc 关掉重开等记录出现。
  const drawerRecord = page
    .locator('.el-drawer .el-table__row', { hasText: '手动执行' })
    .filter({ hasText: '成功' })
  for (let attempt = 0; attempt < 5; attempt += 1) {
    await page.waitForTimeout(1200)
    // 抽屉开着时行按钮在遮罩下：先 Esc 关（动画后隐藏），再重开重读。
    const drawer = page.locator('.el-drawer')
    if ((await drawer.count()) > 0 && !(await drawer.isHidden())) {
      await page.keyboard.press('Escape')
      await expect(drawer).toBeHidden()
    }
    await demoRow.getByRole('button', { name: '执行记录' }).click()
    if (await drawerRecord.first().isVisible()) break
  }
  await expect(drawerRecord.first()).toBeVisible()
  await expect(page.locator('.el-drawer')).toContainText(DEMO_JOB)

  // 抽屉列齐：触发方式 / 状态 / 开始时间 / 耗时。
  await expect(page.locator('.el-drawer')).toContainText('触发方式')
  await expect(page.locator('.el-drawer')).toContainText('开始时间')
  await expect(page.locator('.el-drawer')).toContainText('耗时')
})

test('编辑弹窗：非法 cron 吃服务端 201 文案，合法保存后行上刷新', async ({ page }) => {
  await uiLoginExpectWork(page, 'admin')

  await page.goto('/monitor/job')
  const demoRow = page.locator('.el-table__row', { hasText: DEMO_JOB })
  await expect(demoRow).toBeVisible()

  await demoRow.getByRole('button', { name: '编辑' }).click()
  const dialog = page.locator('.el-dialog')
  await expect(dialog).toContainText(DEMO_JOB)
  await expect(dialog.locator('.cron-hints')).toBeVisible()

  const cronInput = dialog.locator('.cron-input input')
  // 初始值即登记的默认 cron（表单从行回读）；收尾按这个原值还原。
  const originalCron = await cronInput.inputValue()

  // 收尾（finally）：直接走 API 还原 demo 任务的 cron（服务端「有行不覆盖用户改过的
  // cron」，不还原会永久漂移）。认证走 token 头（服务端只读头），值从浏览器会话 cookie 取。
  const cookie = await page.evaluate(() => document.cookie)
  const token = /guigu_oa_client_token=([^;]+)/.exec(cookie)?.[1] ?? ''
  const jobsResponse = await page.request.get('/dev-api/admin/monitor/jobs', {
    headers: { token },
  })
  const jobsBody = (await jobsResponse.json()) as {
    data?: { id: number; beanName: string }[]
  }
  const demoId = (jobsBody.data ?? []).find((job) => job.beanName === 'demoLogMonitorJob')?.id
  expect(demoId).toBeTruthy()

  try {
    // 非法 cron：服务端 201 文案回显在弹窗内，弹窗不关。
    await cronInput.fill('not a cron')
    await dialog.getByRole('button', { name: '保存' }).click()
    await expect(dialog).toContainText('cron 表达式不合法')
    await expect(dialog).toBeVisible()

    // 合法值保存成功：提示已保存（失败分支的请求层 toast 可能还在屏上，按文案过滤），
    // 行上 cron 刷新为输入值。
    await cronInput.fill('0 0 * * * ?')
    await dialog.getByRole('button', { name: '保存' }).click()
    await expect(page.locator('.el-message', { hasText: '已保存' })).toBeVisible()
    await expect(demoRow).toContainText('0 0 * * * ?')
  } finally {
    const restore = await page.request.put(`/dev-api/admin/monitor/jobs/${demoId}`, {
      headers: { token },
      data: { cron: originalCron },
    })
    expect(restore.ok()).toBeTruthy()
  }
})

test('没有监控授予的 user 直达定时任务页回落工作台', async ({ page }) => {
  await uiLoginExpectWork(page, 'user')

  await page.goto('/monitor/job')
  await expect(page).toHaveURL(/\/workbench$/)
  await expect(page.getByRole('heading', { name: '工作台', exact: true })).toBeVisible()
})
