import { expect, test, type Page, type Route } from '@playwright/test'

function deferred() {
  let resolve!: () => void
  const promise = new Promise<void>((done) => {
    resolve = done
  })
  return { promise, resolve }
}

const json = (route: Route, data: unknown, code = 200) =>
  route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code, data }) })

const users = [
  {
    id: 11,
    username: 'alice',
    name: 'Alice',
    phone: '13800000001',
    status: 1,
    createTime: '2026-01-01',
  },
  {
    id: 12,
    username: 'bob',
    name: 'Bob',
    phone: '13800000002',
    status: 1,
    createTime: '2026-01-02',
  },
]

const roleLists = {
  11: {
    allRolesList: [
      { id: 1, roleName: '管理员' },
      { id: 2, roleName: '审批人' },
    ],
    assginRoleList: [{ id: 1, roleName: '管理员' }],
  },
  12: {
    allRolesList: [
      { id: 1, roleName: '管理员' },
      { id: 2, roleName: '审批人' },
    ],
    assginRoleList: [{ id: 2, roleName: '审批人' }],
  },
}

interface State {
  reads: number
  assignments: Record<string, unknown>[]
  infoCalls: number
  errors: string[]
  warnings: string[]
}

interface Options {
  assign?: (route: Route) => Promise<void>
  read?: (route: Route, userId: string) => Promise<void>
}

async function setup(page: Page, options: Options = {}): Promise<State> {
  const state: State = { reads: 0, assignments: [], infoCalls: 0, errors: [], warnings: [] }
  page.on('pageerror', (error) => state.errors.push(error.message))
  page.on('console', (message) => {
    const text = message.text()
    const deprecation = text.includes('is about to be deprecated')
    if (message.type() === 'warning' && !deprecation) state.warnings.push(text)
    const expectedServerError = text.includes(
      'Failed to load resource: the server responded with a status of 500',
    )
    if (message.type() === 'error' && !expectedServerError && !deprecation) state.errors.push(text)
  })
  await page.context().addCookies([
    { name: 'guigu_oa_client_token', value: 'assign-token', domain: '127.0.0.1', path: '/' },
    {
      name: 'guigu_oa_client_refresh_token',
      value: 'assign-refresh',
      domain: '127.0.0.1',
      path: '/',
    },
    { name: 'guigu_oa_client_entry', value: 'assign-entry', domain: '127.0.0.1', path: '/' },
  ])
  await page.context().route('**/dev-api/**', async (route) => {
    const path = new URL(route.request().url()).pathname.replace(/^\/dev-api/, '')
    if (path === '/admin/system/index/info') {
      state.infoCalls += 1
      return json(route, {
        name: 'Alice',
        configMenus: [
          { path: '/system', title: '系统管理', type: 0, sort: 1 },
          {
            path: '/system/sysUser',
            parentPath: '/system',
            title: '用户管理',
            type: 1,
            sort: 1,
          },
        ],
      })
    }
    if (path.startsWith('/admin/process/findPending/'))
      return json(route, { records: [], total: 0, pages: 0 })
    if (/^\/admin\/system\/sysUser\/\d+\/\d+$/.test(path))
      return json(route, { records: users, total: users.length, pages: 1 })
    if (path.startsWith('/admin/system/sysRole/toAssign/')) {
      state.reads += 1
      const userId = path.split('/').pop() || ''
      return options.read ? options.read(route, userId) : json(route, roleLists[userId])
    }
    if (path === '/admin/system/sysRole/doAssign') {
      state.assignments.push(route.request().postDataJSON() as Record<string, unknown>)
      return options.assign ? options.assign(route) : json(route, null)
    }
    throw new Error(`Unexpected request ${path}`)
  })
  await page.goto('/system/sysUser')
  await expect(page.getByRole('heading', { name: '用户管理', exact: true })).toBeVisible()
  return state
}

function row(page: Page, username: string) {
  return page.getByRole('row').filter({ hasText: username })
}

async function openAssignment(page: Page, username: string) {
  await row(page, username).getByRole('button', { name: '分配角色', exact: true }).click()
  const dialog = page.getByRole('dialog')
  await expect(dialog.getByText(`用户：${username}`, { exact: true })).toBeVisible()
  return dialog
}

test('勾选并保存角色后关闭弹窗并刷新当前操作者', async ({ page }) => {
  test.skip((page.viewportSize()?.width || 0) <= 768, '配置在桌面使用')
  const state = await setup(page)
  const dialog = await openAssignment(page, 'alice')
  await expect(dialog.getByRole('checkbox', { name: '管理员' })).toBeChecked()
  await dialog.getByText('管理员', { exact: true }).click()
  await dialog.getByText('审批人', { exact: true }).click()
  await expect(dialog.getByRole('checkbox', { name: '管理员' })).not.toBeChecked()
  await expect(dialog.getByRole('checkbox', { name: '审批人' })).toBeChecked()
  await dialog.getByRole('button', { name: '保存', exact: true }).click()

  await expect(page.getByText('分配角色成功', { exact: true })).toBeVisible()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  expect(state.assignments).toEqual([{ userId: 11, roleIdList: [2] }])
  await expect.poll(() => state.infoCalls).toBe(2)
  expect(state.errors).toEqual([])
})

test('保存结果待确认时冻结内容，刷新后从行内待核实恢复并只读取最新角色', async ({ page }) => {
  test.skip((page.viewportSize()?.width || 0) <= 768, '配置在桌面使用')
  const state = await setup(page, {
    assign: (route) => route.fulfill({ status: 500, body: 'boom' }),
    read: async (route, userId) =>
      userId === '11' && state.reads > 1
        ? json(route, {
            allRolesList: roleLists[11].allRolesList,
            assginRoleList: [
              { id: 1, roleName: '管理员' },
              { id: 2, roleName: '审批人' },
            ],
          })
        : json(route, roleLists[userId]),
  })
  const dialog = await openAssignment(page, 'alice')
  await dialog.getByText('管理员', { exact: true }).click()
  await dialog.getByText('审批人', { exact: true }).click()
  await expect(dialog.getByRole('checkbox', { name: '管理员' })).not.toBeChecked()
  await expect(dialog.getByRole('checkbox', { name: '审批人' })).toBeChecked()
  await dialog.getByRole('button', { name: '保存', exact: true }).click()

  await expect(dialog.getByRole('heading', { name: '分配结果待确认' })).toBeVisible()
  await expect(dialog.getByText('本次提交', { exact: true })).toBeVisible()
  await expect(dialog.getByRole('listitem')).toContainText(['审批人'])
  await expect(dialog.getByRole('button', { name: '保存', exact: true })).toHaveCount(0)
  expect(state.assignments).toHaveLength(1)

  await dialog.getByRole('button', { name: '关闭', exact: true }).click()
  await page.locator('.el-message-box').getByRole('button', { name: '确认关闭' }).click()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await expect(row(page, 'alice').getByText('待核实', { exact: true })).toBeVisible()

  await page.reload()
  await expect(page.getByRole('heading', { name: '用户管理', exact: true })).toBeVisible()
  await expect(row(page, 'alice').getByText('待核实', { exact: true })).toBeVisible()
  expect(state.reads).toBe(1)

  const restored = await openAssignment(page, 'alice')
  await expect(restored.getByRole('heading', { name: '分配结果待确认' })).toBeVisible()
  await expect(restored.getByRole('listitem')).toContainText(['审批人'])
  expect(state.reads).toBe(1)
  await expect(restored.getByText('尚未重新读取。', { exact: true })).toBeVisible()
  await expect(restored.getByText('管理员', { exact: true })).toHaveCount(0)
  await restored.getByRole('button', { name: '重新读取当前角色', exact: true }).click()
  await expect(restored.getByText('管理员', { exact: true })).toBeVisible()
  await expect(restored.getByText('尚未重新读取。', { exact: true })).toHaveCount(0)
  expect(state.reads).toBe(2)
  await expect(restored.getByRole('checkbox')).toHaveCount(0)

  await restored.getByRole('button', { name: '结束本次分配', exact: true }).click()
  await page
    .locator('.el-message-box')
    .getByRole('button', { name: '结束本次分配', exact: true })
    .click()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await expect(row(page, 'alice').getByText('待核实', { exact: true })).toHaveCount(0)

  const reopened = await openAssignment(page, 'alice')
  await expect(reopened.getByRole('checkbox', { name: '管理员' })).toBeChecked()
  await expect(reopened.getByRole('checkbox', { name: '审批人' })).toBeChecked()
  expect(state.reads).toBe(3)
  expect(state.assignments).toHaveLength(1)
  expect(state.errors).toEqual([])
})

test('关闭保存中的弹窗后迟到成功不关闭新弹窗，但仍刷新资料与菜单', async ({ page }) => {
  test.skip((page.viewportSize()?.width || 0) <= 768, '配置在桌面使用')
  const gate = deferred()
  const state = await setup(page, {
    assign: async (route) => {
      await gate.promise
      await json(route, null)
    },
  })
  const dialog = await openAssignment(page, 'alice')
  await dialog.getByRole('button', { name: '保存', exact: true }).click()
  await expect(dialog.getByText(/正在保存角色分配/)).toBeVisible()
  await dialog.getByRole('button', { name: '取消', exact: true }).click()
  await page.locator('.el-message-box').getByRole('button', { name: '确认关闭' }).click()
  await expect(page.getByRole('dialog')).toHaveCount(0)

  const bob = await openAssignment(page, 'bob')
  await expect(bob.getByRole('checkbox', { name: '审批人' })).toBeChecked()
  gate.resolve()

  await expect.poll(() => state.infoCalls).toBe(2)
  await expect(page.getByText('分配角色成功', { exact: true })).toHaveCount(0)
  await expect(bob.getByText('用户：bob', { exact: true })).toBeVisible()
  await expect(bob.getByRole('checkbox', { name: '审批人' })).toBeChecked()
  expect(state.assignments).toEqual([{ userId: 11, roleIdList: [1] }])
  expect(state.errors).toEqual([])
})

test('变成手机尺寸时强制离开配置并丢弃未保存勾选', async ({ page }) => {
  test.skip((page.viewportSize()?.width || 0) <= 768, '配置在桌面使用')
  const state = await setup(page)
  const dialog = await openAssignment(page, 'alice')
  await dialog.getByText('审批人', { exact: true }).click()

  await page.setViewportSize({ width: 390, height: 844 })
  await expect(page).toHaveURL(/\/work\/pending$/)
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await expect(page.locator('.el-message-box')).toHaveCount(0)

  await page.setViewportSize({ width: 1280, height: 900 })
  await page.goto('/system/sysUser')
  await expect(page.getByRole('heading', { name: '用户管理', exact: true })).toBeVisible()
  const reopened = await openAssignment(page, 'alice')
  await expect(reopened.getByRole('checkbox', { name: '管理员' })).toBeChecked()
  await expect(reopened.getByRole('checkbox', { name: '审批人' })).not.toBeChecked()
  expect(state.reads).toBe(2)
  expect(state.errors).toEqual([])
})
