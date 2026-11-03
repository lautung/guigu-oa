import { test, expect } from './helpers/fixtures'
import { uiLogin, uiLoginExpectWork, uiLogout } from './helpers/ui'

/**
 * P1-13（#241，ADR 0060）：缓存监控页与缓存列表页的真实栈验收。
 *
 * admin（super_admin 全授予）登录后：缓存监控页读真实 Redis INFO（栈上 Redis
 * 常驻，各卡必有内容）；缓存列表页在 perm: 前缀下能看到已登录账号的权限缓存键，
 * 删除 user 的键生效（admin 自己的键不能删——删掉后当前会话的下一次请求要等
 * /info 才重建权限，用例会因此看到 205 错误行）；非 perm 前缀只读无删除入口。
 */

test('授予后缓存监控页打开并渲染真实 INFO 卡片', async ({ page }) => {
  await uiLoginExpectWork(page, 'admin')

  await page.goto('/monitor/cache')
  await expect(page).toHaveURL(/\/monitor\/cache$/)
  await expect(page.getByRole('heading', { name: '缓存监控', exact: true })).toBeVisible()
  await expect(page.locator('.shell-breadcrumb')).toContainText('系统监控')
  await expect(page.locator('.app-sidebar').getByText('缓存监控', { exact: true })).toBeVisible()

  // 不 500：没有错误行；栈上 Redis 常驻，信息与内存卡必有内容。
  await expect(page.locator('.list-load-error')).toHaveCount(0)
  const serverCard = page.locator('.metric-card', {
    has: page.getByRole('heading', { name: 'Redis 信息' }),
  })
  await expect(serverCard).toBeVisible()
  await expect(serverCard).toContainText('版本')
  await expect(serverCard).not.toContainText('不可用')

  const memoryCard = page.locator('.metric-card', {
    has: page.getByRole('heading', { name: '内存' }),
  })
  await expect(memoryCard).toContainText('已用内存')
  await expect(memoryCard).not.toContainText('不可用')

  const clientsCard = page.locator('.metric-card', {
    has: page.getByRole('heading', { name: '客户端与命中率' }),
  })
  await expect(clientsCard).toContainText('连接客户端')
})

test('缓存列表 perm: 键可见且删除 user 的键生效', async ({ page }) => {
  // 三个账号先后登录，各写下一个 perm:cache:<username> 键（无 TTL，直到失效才消失）；
  // 最后以 admin 收尾，保证会话不受删除动作影响。
  await uiLoginExpectWork(page, 'admin')
  await uiLogout(page)
  await uiLogin(page, 'user')
  await expect(page.getByRole('heading', { name: '工作台', exact: true })).toBeVisible()
  await uiLogout(page)
  await uiLoginExpectWork(page, 'admin')

  await page.goto('/monitor/cacheList')
  await expect(page).toHaveURL(/\/monitor\/cacheList$/)
  await expect(page.getByRole('heading', { name: '缓存列表', exact: true })).toBeVisible()
  await expect(page.locator('.shell-breadcrumb')).toContainText('系统监控')
  await expect(page.locator('.app-sidebar').getByText('缓存列表', { exact: true })).toBeVisible()

  // 默认前缀 perm:：两个账号的权限缓存键都可见。
  const adminRow = page.locator('.el-table__row', { hasText: 'perm:cache:admin' })
  const userRow = page.locator('.el-table__row', { hasText: 'perm:cache:user' })
  await expect(adminRow).toBeVisible()
  await expect(userRow).toBeVisible()
  await expect(userRow.getByRole('button', { name: '删除' })).toBeVisible()

  // 删除 user 的键（不是当前会话的键）：二次确认后成功并刷新当前窗口。
  await userRow.getByRole('button', { name: '删除' }).click()
  await expect(page.locator('.el-message-box')).toContainText('perm:cache:user')
  await page.locator('.el-message-box').getByRole('button', { name: '确定' }).click()
  await expect(page.locator('.el-message')).toContainText('已删除缓存键')

  await expect(userRow).toHaveCount(0)
  await expect(adminRow).toBeVisible()
  // 页面保持可读：没有错误行（删的是别人的键，当前会话的权限缓存仍在）。
  await expect(page.locator('.list-load-error')).toHaveCount(0)
})

test('缓存列表非 perm 前缀只读：键可见但无删除入口', async ({ page }) => {
  await uiLoginExpectWork(page, 'admin')

  await page.goto('/monitor/cacheList')
  await expect(page.locator('.el-table__row', { hasText: 'perm:cache:admin' })).toBeVisible()

  // 切到 login:session:：会话登记键必然存在（本次登录），且没有行内删除按钮。
  await page.locator('.prefix-select').click()
  await page.getByRole('option', { name: 'login:session:' }).click()
  const sessionRow = page.locator('.el-table__row', { hasText: 'login:session:' })
  await expect(sessionRow.first()).toBeVisible()
  await expect(page.locator('.el-table__row button')).toHaveCount(0)
  // 顶部清理按钮仍在，但它清的是 perm:cache:*，与当前前缀无关。
  await expect(page.locator('.clear-cache-button')).toBeVisible()
})

test('没有监控授予的 user 直达缓存两页回落工作台', async ({ page }) => {
  await uiLoginExpectWork(page, 'user')

  await page.goto('/monitor/cache')
  await expect(page).toHaveURL(/\/workbench$/)
  await expect(page.getByRole('heading', { name: '工作台', exact: true })).toBeVisible()

  await page.goto('/monitor/cacheList')
  await expect(page).toHaveURL(/\/workbench$/)
  await expect(page.getByRole('heading', { name: '工作台', exact: true })).toBeVisible()
})
