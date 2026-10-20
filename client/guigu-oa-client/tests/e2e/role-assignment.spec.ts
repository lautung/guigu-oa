import { expect, test, type Locator, type Page, type Route } from '@playwright/test'

// These controlled responses prove browser interaction and request ownership, not backend writes.
const browserErrors = new WeakMap<Page, string[]>()
test.afterEach(async ({ page }, testInfo) => {
  if (testInfo.status === 'skipped') return
  expect(browserErrors.get(page) || []).toEqual([])
  await expect(page).toHaveTitle(/ - 云尚办公$/)
  await expect(page.locator('body > #app')).not.toBeEmpty()
})

const roles = [
  { id: 11, roleName: '财务角色' },
  { id: 12, roleName: '人事角色' },
]
const users = [
  { id: '101', username: 'alice', name: 'Alice', status: 1 },
  { id: '102', username: 'bob', name: 'Bob', status: 1 },
]
function deferred() {
  let resolve!: () => void
  const promise = new Promise<void>((done) => {
    resolve = done
  })
  return { promise, resolve }
}
async function json(route: Route, data: unknown, code = 200) {
  await route.fulfill({
    contentType: 'application/json',
    body: JSON.stringify({ code, data }),
  })
}
function assigned(ids: number[]) {
  return { allRolesList: roles, assginRoleList: ids.map((id) => ({ id })) }
}
async function setup(
  page: Page,
  options: {
    read?: (route: Route, target: string) => Promise<void>
    save?: (route: Route) => Promise<void>
    info?: (route: Route, attempt: number) => Promise<void>
  } = {},
) {
  const saved: { userId: string; roleIdList: number[] }[] = []
  const saveTokens: string[] = []
  const errors: string[] = []
  let infoCalls = 0
  let refreshCalls = 0
  browserErrors.set(page, errors)
  page.on('pageerror', (error) => errors.push(error.message))
  page.on('console', (message) => {
    // The fixtures deliberately abort requests or return 503; Chromium reports those as resource errors.
    if (message.type() === 'error' && !message.text().startsWith('Failed to load resource:'))
      errors.push(message.text())
  })
  await page.context().addCookies([
    { name: 'guigu_oa_client_token', value: 'role-token', domain: '127.0.0.1', path: '/' },
    {
      name: 'guigu_oa_client_refresh_token',
      value: 'role-refresh',
      domain: '127.0.0.1',
      path: '/',
    },
    { name: 'guigu_oa_client_entry', value: 'role-entry', domain: '127.0.0.1', path: '/' },
  ])
  await page.context().route('**/dev-api/**', async (route) => {
    const path = new URL(route.request().url()).pathname.replace('/dev-api', '')
    if (path === '/admin/system/index/info') {
      infoCalls++
      return options.info
        ? options.info(route, infoCalls)
        : json(route, {
            name: 'Alice',
            routers: [{ path: '/system/sysUser', meta: { title: '用户管理' } }],
          })
    }
    if (path === '/admin/system/index/refresh') {
      refreshCalls++
      return json(route, { token: 'renewed-role-token', refreshToken: 'renewed-role-refresh' })
    }
    if (path === '/admin/system/sysUser/1/10')
      return json(route, { records: users, total: 2, pages: 1 })
    if (path.startsWith('/admin/system/sysRole/toAssign/')) {
      const target = path.split('/').at(-1)!
      return options.read
        ? options.read(route, target)
        : json(route, assigned(target === '101' ? [11] : [12]))
    }
    if (path === '/admin/system/sysRole/doAssign') {
      saved.push(route.request().postDataJSON() as (typeof saved)[number])
      saveTokens.push(route.request().headers()['token'] || '')
      return options.save ? options.save(route) : json(route, null)
    }
    if (path === '/admin/system/sysRole/1/10')
      return json(route, { records: roles, total: 2, pages: 1 })
    if (path.startsWith('/admin/process/findPending/'))
      return json(route, { records: [], total: 0, pages: 0 })
    throw new Error(`Unexpected request ${path}`)
  })
  return {
    saved,
    saveTokens,
    errors,
    get infoCalls() {
      return infoCalls
    },
    get refreshCalls() {
      return refreshCalls
    },
  }
}
function assignment(page: Page) {
  return page.getByRole('dialog', { name: '分配角色', exact: true })
}
async function open(page: Page, username: string) {
  await page
    .locator('.el-table__row')
    .filter({ hasText: username })
    .getByRole('button', { name: '分配角色', exact: true })
    .click()
  await expect(assignment(page)).toContainText(`用户：${username}`)
}
async function confirmLeave(page: Page) {
  const confirmation = page.getByRole('dialog', { name: '离开分配角色', exact: true })
  await expect(confirmation).toContainText('不撤销')
  await confirmation.getByRole('button', { name: '确认离开', exact: true }).click()
}
function saveButton(dialog: Locator) {
  return dialog.getByRole('button', { name: '保存', exact: true })
}

test.describe('分配角色真实弹窗的受控响应合同', () => {
  test.beforeEach(({ page }) => {
    test.skip((page.viewportSize()?.width || 0) <= 768, '配置在桌面使用')
  })

  test('关闭 A 后打开 B，A 的迟到读取不能改变 B 的勾选和精确提交', async ({ page }) => {
    const gate = deferred()
    const h = await setup(page, {
      read: async (route, target) => {
        if (target === '101') await gate.promise
        await json(route, assigned(target === '101' ? [11] : [12]))
      },
    })
    await page.goto('/system/sysUser')
    const readingA = page.waitForRequest('**/sysRole/toAssign/101')
    await open(page, 'alice')
    await readingA
    await expect(saveButton(assignment(page))).toBeDisabled()
    await assignment(page).getByRole('button', { name: '关闭', exact: true }).click()
    await open(page, 'bob')
    await expect(assignment(page).getByRole('checkbox', { name: '人事角色' })).toBeChecked()
    const responseA = page.waitForResponse('**/sysRole/toAssign/101')
    gate.resolve()
    await responseA
    await expect(assignment(page)).toContainText('用户：bob（102）')
    await expect(assignment(page).getByRole('checkbox', { name: '财务角色' })).not.toBeChecked()
    await expect(assignment(page).getByRole('checkbox', { name: '人事角色' })).toBeChecked()
    await saveButton(assignment(page)).click()
    await expect(assignment(page)).toBeHidden()
    expect(h.saved).toEqual([{ userId: '102', roleIdList: [12] }])
    expect(h.errors).toEqual([])
  })

  test('保存冻结并阻止连续点击，取消关闭保留内容，重开与离开再返回均不释放在途限制', async ({
    page,
  }) => {
    const gate = deferred()
    const h = await setup(page, {
      save: async (route) => {
        await gate.promise
        await json(route, null)
      },
    })
    await page.goto('/work/pending')
    await page.getByRole('link', { name: '用户管理', exact: true }).click()
    await open(page, 'alice')
    await assignment(page).getByText('人事角色', { exact: true }).click()
    await saveButton(assignment(page)).dblclick()
    await expect.poll(() => h.saved.length).toBe(1)
    await expect(saveButton(assignment(page))).toBeDisabled()
    await expect(assignment(page).getByRole('checkbox', { name: '财务角色' })).toBeDisabled()
    await expect(assignment(page).getByRole('checkbox', { name: '人事角色' })).toBeDisabled()
    await expect(assignment(page)).toContainText('本次提交角色：财务角色、人事角色')
    await expect(
      assignment(page).getByRole('button', { name: '结束本次分配', exact: true }),
    ).toHaveCount(0)
    await assignment(page).getByRole('button', { name: '关闭', exact: true }).click()
    await page.getByRole('button', { name: '继续分配', exact: true }).click()
    await expect(assignment(page)).toContainText('正在保存分配角色')
    await assignment(page).getByRole('button', { name: '关闭', exact: true }).click()
    await confirmLeave(page)
    await open(page, 'alice')
    await expect(saveButton(assignment(page))).toBeDisabled()
    await expect(assignment(page)).toContainText('本次提交角色：财务角色、人事角色')
    const back = page.goBack()
    await confirmLeave(page)
    await back
    await expect(page).toHaveURL(/\/work\/pending$/)
    await page.goForward()
    await open(page, 'alice')
    await expect(assignment(page)).toContainText('正在保存分配角色')
    await expect(saveButton(assignment(page))).toBeDisabled()
    const response = page.waitForResponse('**/sysRole/doAssign')
    gate.resolve()
    await response
    await expect(assignment(page)).toContainText('分配已保存')
    expect(h.saved).toEqual([{ userId: '101', roleIdList: [11, 12] }])
  })

  test('刷新丢失原响应的在途分配恢复为待确认，保留原提交且不自动重发', async ({ page }) => {
    const gate = deferred()
    const h = await setup(page, {
      save: async (route) => {
        await gate.promise
        await json(route, null)
      },
    })
    await page.goto('/system/sysUser')
    await open(page, 'alice')
    await assignment(page).getByText('人事角色', { exact: true }).click()
    await saveButton(assignment(page)).click()
    await expect.poll(() => h.saved.length).toBe(1)
    await page.reload()
    await open(page, 'alice')
    await expect(assignment(page).getByRole('heading', { name: '分配结果待确认' })).toBeVisible()
    await expect(assignment(page)).toContainText('本次提交角色：财务角色、人事角色')
    await expect(saveButton(assignment(page))).toBeDisabled()
    expect(h.saved).toHaveLength(1)
    gate.resolve()
  })

  test('待确认跨刷新保留，核实相同集合或读取失败均不释放，人工结束后必须重新读取', async ({
    page,
  }) => {
    let reads = 0
    const finalRead = deferred()
    const h = await setup(page, {
      read: async (route) => {
        reads++
        if (reads === 3) return route.abort('failed')
        if (reads === 4) await finalRead.promise
        await json(route, assigned(reads === 1 ? [11] : reads === 4 ? [11] : [12]))
      },
      save: (route) => json(route, null, 201),
    })
    await page.goto('/system/sysUser')
    await open(page, 'alice')
    await assignment(page).getByText('财务角色', { exact: true }).click()
    await assignment(page).getByText('人事角色', { exact: true }).click()
    await saveButton(assignment(page)).click()
    await expect(assignment(page).getByRole('heading', { name: '分配结果待确认' })).toBeVisible()
    await page.reload()
    await open(page, 'alice')
    await expect(assignment(page)).toContainText('本次提交角色：人事角色')
    expect(reads).toBe(1)
    await assignment(page).getByRole('button', { name: '重新读取当前角色', exact: true }).click()
    await expect(assignment(page)).toContainText('当前已保存角色：人事角色')
    await expect(assignment(page).getByRole('heading', { name: '分配结果待确认' })).toBeVisible()
    await expect(saveButton(assignment(page))).toBeDisabled()
    await assignment(page).getByRole('button', { name: '重新读取当前角色', exact: true }).click()
    await expect(
      assignment(page).getByRole('alert').filter({ hasText: 'Network Error' }),
    ).toBeVisible()
    await expect(assignment(page)).toContainText('本次提交角色：人事角色')
    await expect(saveButton(assignment(page))).toBeDisabled()
    await page.screenshot({ path: '/private/tmp/issue-98-role-assignment.png', fullPage: true })
    await assignment(page).getByRole('button', { name: '结束本次分配', exact: true }).click()
    const end = page.getByRole('dialog', { name: '结束本次分配', exact: true })
    await expect(end).toContainText('原保存仍可能生效并影响后续分配')
    await end.getByRole('button', { name: '保留本次分配', exact: true }).click()
    await expect(saveButton(assignment(page))).toBeDisabled()
    await assignment(page).getByRole('button', { name: '结束本次分配', exact: true }).click()
    await end.getByRole('button', { name: '结束本次分配', exact: true }).click()
    await expect.poll(() => reads).toBe(4)
    await expect(saveButton(assignment(page))).toBeDisabled()
    await expect(assignment(page).getByRole('checkbox')).toHaveCount(0)
    finalRead.resolve()
    await expect(assignment(page).getByRole('checkbox', { name: '财务角色' })).toBeChecked()
    await expect(assignment(page).getByRole('checkbox', { name: '人事角色' })).not.toBeChecked()
    await expect(saveButton(assignment(page))).toBeEnabled()
    expect(h.saved).toEqual([{ userId: '101', roleIdList: [12] }])
  })

  test('离开用户页后的迟到成功刷新菜单，撤回当前角色页菜单后必须离开配置', async ({ page }) => {
    const gate = deferred()
    const h = await setup(page, {
      info: (route, attempt) =>
        json(route, {
          name: 'Alice',
          routers:
            attempt === 1
              ? [
                  { path: '/system/sysUser', meta: { title: '用户管理' } },
                  { path: '/system/sysRole', meta: { title: '角色管理' } },
                ]
              : [{ path: '/system/sysUser', meta: { title: '用户管理' } }],
        }),
      save: async (route) => {
        await gate.promise
        await json(route, null)
      },
    })
    await page.goto('/system/sysRole')
    await page.getByRole('link', { name: '用户管理', exact: true }).click()
    await open(page, 'alice')
    await saveButton(assignment(page)).click()
    await expect.poll(() => h.saved.length).toBe(1)
    const back = page.goBack()
    await confirmLeave(page)
    await back
    await expect(page).toHaveURL(/\/system\/sysRole$/)
    const response = page.waitForResponse('**/sysRole/doAssign')
    gate.resolve()
    await response
    await expect.poll(() => h.infoCalls).toBe(2)
    await expect(page).toHaveURL(/\/work\/pending$/)
    await expect(page.getByRole('heading', { name: '待处理', exact: true })).toBeVisible()
    await expect(page.getByText('分配角色成功', { exact: true })).toHaveCount(0)
    expect(h.saved).toHaveLength(1)
  })

  for (const result of ['成功', '失败'] as const) {
    test(`A 的迟到${result}不关闭或提示后来打开的 B，其他目标仍可正常保存`, async ({ page }) => {
      const gate = deferred()
      let requests = 0
      const h = await setup(page, {
        save: async (route) => {
          if (++requests === 1) {
            await gate.promise
            return json(route, null, result === '成功' ? 200 : 201)
          }
          await json(route, null)
        },
      })
      await page.goto('/system/sysUser')
      await open(page, 'alice')
      await saveButton(assignment(page)).click()
      await expect.poll(() => h.saved.length).toBe(1)
      await assignment(page).getByRole('button', { name: '关闭', exact: true }).click()
      await confirmLeave(page)
      await open(page, 'bob')
      await expect(assignment(page).getByRole('checkbox', { name: '人事角色' })).toBeChecked()
      const response = page.waitForResponse('**/sysRole/doAssign')
      gate.resolve()
      await response
      if (result === '成功') await expect.poll(() => h.infoCalls).toBe(2)
      await expect(assignment(page)).toContainText('用户：bob（102）')
      await expect(assignment(page).getByRole('checkbox', { name: '人事角色' })).toBeChecked()
      await expect(saveButton(assignment(page))).toBeEnabled()
      await expect(page.getByText('分配角色成功', { exact: true })).toHaveCount(0)
      await expect(assignment(page).getByRole('heading', { name: '分配结果待确认' })).toHaveCount(0)
      await assignment(page).getByText('财务角色', { exact: true }).click()
      await saveButton(assignment(page)).click()
      await expect(assignment(page)).toBeHidden()
      expect(h.saved).toEqual([
        { userId: '101', roleIdList: [11] },
        { userId: '102', roleIdList: [11, 12] },
      ])
    })
  }

  test('明确保存成功后认出收回自身配置菜单，离开用户页且不再次提交', async ({ page }) => {
    const h = await setup(page, {
      info: (route, attempt) =>
        json(route, {
          name: 'Alice',
          routers: attempt === 1 ? [{ path: '/system/sysUser', meta: { title: '用户管理' } }] : [],
        }),
    })
    await page.goto('/system/sysUser')
    await open(page, 'alice')
    await assignment(page).getByText('财务角色', { exact: true }).click()
    await saveButton(assignment(page)).click()
    await expect(page).toHaveURL(/\/work\/pending$/)
    await expect(page.getByRole('heading', { name: '待处理', exact: true })).toBeVisible()
    await expect(page.getByRole('link', { name: '用户管理', exact: true })).toHaveCount(0)
    await expect(assignment(page)).toHaveCount(0)
    expect(h.saved).toEqual([{ userId: '101', roleIdList: [] }])
    expect(h.infoCalls).toBe(2)
  })

  test('认证明确过期只恢复重放一次，重放沿用冻结提交，之后断网保留待确认', async ({ page }) => {
    let requests = 0
    const replay = deferred()
    const h = await setup(page, {
      save: async (route) => {
        if (++requests === 1) return json(route, null, 208)
        await replay.promise
        await route.abort('failed')
      },
    })
    await page.goto('/system/sysUser')
    await open(page, 'alice')
    await assignment(page).getByText('人事角色', { exact: true }).click()
    await saveButton(assignment(page)).click()
    await expect.poll(() => h.saved.length).toBe(2)
    await expect(saveButton(assignment(page))).toBeDisabled()
    await expect(assignment(page).getByRole('checkbox', { name: '人事角色' })).toBeDisabled()
    expect(h.saved).toEqual([
      { userId: '101', roleIdList: [11, 12] },
      { userId: '101', roleIdList: [11, 12] },
    ])
    expect(h.saveTokens).toEqual(['role-token', 'renewed-role-token'])
    replay.resolve()
    await expect(assignment(page).getByRole('heading', { name: '分配结果待确认' })).toBeVisible()
    await expect(assignment(page)).toContainText('本次提交角色：财务角色、人事角色')
    await assignment(page).getByRole('button', { name: '关闭', exact: true }).click()
    await confirmLeave(page)
    await open(page, 'alice')
    await expect(saveButton(assignment(page))).toBeDisabled()
    await expect(assignment(page).getByRole('heading', { name: '分配结果待确认' })).toBeVisible()
    expect(h.saved).toHaveLength(2)
    expect(h.refreshCalls).toBe(1)
  })

  test('保存成功与权限刷新故障分别显示，重试权限刷新不产生第二次保存', async ({ page }) => {
    const h = await setup(page, {
      info: (route, attempt) =>
        attempt === 2
          ? route.fulfill({ status: 503, body: 'temporary recognition failure' })
          : json(route, {
              name: 'Alice',
              routers: [{ path: '/system/sysUser', meta: { title: '用户管理' } }],
            }),
    })
    await page.goto('/system/sysUser')
    await open(page, 'alice')
    await saveButton(assignment(page)).click()
    await expect(assignment(page)).toBeHidden()
    await expect(page.getByRole('alert').filter({ hasText: '权限刷新暂未完成' })).toContainText(
      '分配已保存，权限刷新暂未完成',
    )
    await page.getByRole('button', { name: '重试权限刷新', exact: true }).click()
    await expect(page.getByRole('button', { name: '重试权限刷新', exact: true })).toHaveCount(0)
    await expect(page).toHaveURL(/\/system\/sysUser$/)
    expect(h.saved).toEqual([{ userId: '101', roleIdList: [11] }])
    expect(h.infoCalls).toBe(3)
  })

  test('身份替换时强制离开并清理保存中的离开确认，旧成功不替新身份刷新', async ({ page }) => {
    const gate = deferred()
    const h = await setup(page, {
      save: async (route) => {
        await gate.promise
        await json(route, null)
      },
    })
    await page.goto('/system/sysUser')
    await open(page, 'alice')
    await saveButton(assignment(page)).click()
    await expect.poll(() => h.saved.length).toBe(1)
    await assignment(page).getByRole('button', { name: '关闭', exact: true }).click()
    await expect(page.getByRole('dialog', { name: '离开分配角色', exact: true })).toBeVisible()
    await page.context().addCookies([
      {
        name: 'guigu_oa_client_entry',
        value: 'replacement-entry',
        domain: '127.0.0.1',
        path: '/',
      },
    ])
    await page.evaluate(() => window.dispatchEvent(new Event('focus')))
    await expect(page).toHaveURL(/\/work\/pending$/)
    await expect(page.getByRole('dialog')).toHaveCount(0)
    const infoBeforeOldResult = h.infoCalls
    const response = page.waitForResponse('**/sysRole/doAssign')
    gate.resolve()
    await response
    await expect(page.getByRole('heading', { name: '待处理', exact: true })).toBeVisible()
    await expect(page.getByText('分配角色成功', { exact: true })).toHaveCount(0)
    expect(h.infoCalls).toBe(infoBeforeOldResult)
    await page.getByRole('link', { name: '用户管理', exact: true }).click()
    await open(page, 'alice')
    await expect(saveButton(assignment(page))).toBeEnabled()
    await expect(assignment(page).getByRole('heading', { name: '分配结果待确认' })).toHaveCount(0)
    expect(h.saved).toHaveLength(1)
  })

  test('普通关闭可取消并保留勾选，缩到手机时强制离开并清理丢弃确认', async ({ page }) => {
    const h = await setup(page)
    await page.goto('/system/sysUser')
    await open(page, 'alice')
    await assignment(page).getByText('人事角色', { exact: true }).click()
    await assignment(page).getByRole('button', { name: '关闭', exact: true }).click()
    await expect(page.getByRole('dialog', { name: '离开分配角色', exact: true })).toContainText(
      '丢弃本次勾选',
    )
    await page.getByRole('button', { name: '继续分配', exact: true }).click()
    await expect(assignment(page).getByRole('checkbox', { name: '人事角色' })).toBeChecked()
    await assignment(page).getByRole('button', { name: '关闭', exact: true }).click()
    await expect(page.getByRole('dialog', { name: '离开分配角色', exact: true })).toBeVisible()
    await page.setViewportSize({ width: 390, height: 844 })
    await expect(page).toHaveURL(/\/work\/pending$/)
    await expect(page.getByRole('dialog')).toHaveCount(0)
    await page.setViewportSize({ width: 1280, height: 900 })
    await page.getByRole('link', { name: '用户管理', exact: true }).click()
    await open(page, 'alice')
    await expect(assignment(page).getByRole('checkbox', { name: '人事角色' })).not.toBeChecked()
    expect(h.saved).toHaveLength(0)
  })
})

test('手机即使有用户管理菜单也不能占用分配角色页面', async ({ page }) => {
  test.skip((page.viewportSize()?.width || 0) > 768, '验证手机配置进入规则')
  const h = await setup(page)
  await page.goto('/system/sysUser')
  await expect(page).toHaveURL(/\/work\/pending$/)
  await expect(page.getByRole('heading', { name: '待处理', exact: true })).toBeVisible()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  expect(h.saved).toHaveLength(0)
})
