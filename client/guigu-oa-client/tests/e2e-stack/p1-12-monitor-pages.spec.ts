import { test, expect } from './helpers/fixtures'
import { uiLoginExpectWork } from './helpers/ui'

/**
 * P1-12（#240，ADR 0060）：服务监控页与数据监控页的真实栈验收。
 *
 * admin（super_admin 全授予）登录后两页可打开且不 500：快照走自研
 * /admin/monitor/server 与 /admin/monitor/data（actuator 不外扩），Hikari 段在
 * 演示栈上必然可读（连接池随服务启动），各段读不到时页面降级为「不可用」卡片而非报错行。
 */

test('授予后服务监控页打开并渲染真实快照', async ({ page }) => {
  await uiLoginExpectWork(page, 'admin')

  await page.goto('/monitor/server')
  await expect(page).toHaveURL(/\/monitor\/server$/)
  await expect(page.getByRole('heading', { name: '服务监控', exact: true })).toBeVisible()
  await expect(page.locator('.shell-breadcrumb')).toContainText('系统监控')
  await expect(page.locator('.app-sidebar').getByText('服务监控', { exact: true })).toBeVisible()

  // 不 500：没有错误行；演示栈上 CPU/内存/Hikari 都可读，至少 Hikari 卡片可用。
  await expect(page.locator('.list-load-error')).toHaveCount(0)
  const hikariCard = page.locator('.metric-card', {
    has: page.getByRole('heading', { name: 'Hikari' }),
  })
  await expect(hikariCard).toBeVisible()
  await expect(hikariCard).toContainText('活跃')
  await expect(hikariCard).not.toContainText('不可用')
})

test('授予后数据监控页打开并渲染连接池与慢 SQL 环', async ({ page }) => {
  await uiLoginExpectWork(page, 'admin')

  await page.goto('/monitor/data')
  await expect(page).toHaveURL(/\/monitor\/data$/)
  await expect(page.getByRole('heading', { name: '数据监控', exact: true })).toBeVisible()
  await expect(page.locator('.shell-breadcrumb')).toContainText('系统监控')
  await expect(page.locator('.app-sidebar').getByText('数据监控', { exact: true })).toBeVisible()

  // 不 500：没有错误行；Hikari 段与 /server 同一读法，演示栈上可用。
  await expect(page.locator('.list-load-error')).toHaveCount(0)
  const hikariCard = page.locator('.metric-card', {
    has: page.getByRole('heading', { name: 'Hikari' }),
  })
  await expect(hikariCard).toBeVisible()
  await expect(hikariCard).toContainText('活跃')

  // 慢 SQL 段两态皆合法：demo profile 开采集时是表格，否则「采集未开启」空态。
  const slowSqlCard = page.locator('.slow-sql-card')
  await expect(slowSqlCard).toBeVisible()
  const emptyState = slowSqlCard.locator('.el-empty')
  if ((await emptyState.count()) > 0) {
    await expect(emptyState).toContainText('采集未开启')
  } else {
    await expect(slowSqlCard.locator('.el-table')).toBeVisible()
  }
})
