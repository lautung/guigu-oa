import { expect, test, type Page, type Route } from '@playwright/test'

const unavailableMessage = '暂不可发起，需核实原审批内容'
const templateId = 195

function json(route: Route, data: unknown) {
  return route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ code: 200, data }),
  })
}

async function mockUnavailableTemplate(page: Page) {
  const sideEffectRequests: string[] = []
  const oldTemplate = {
    id: templateId,
    name: '历史请假模板',
    processTypeId: 1,
    processTypeName: '请假',
    processDefinitionKey: 'legacyLeave',
    status: 1,
    availableForInitiation: false,
    availabilityMessage: unavailableMessage,
    formProps: JSON.stringify([
      { type: 'input', field: 'reason', title: '请假原因', $required: true },
    ]),
  }

  await page.route('**/dev-api/**', async (route) => {
    const request = route.request()
    const path = new URL(request.url()).pathname.replace(/^\/dev-api/, '')
    const method = request.method()

    if (path === '/admin/system/index/login' && method === 'POST') {
      return json(route, { token: 'availability-token', refreshToken: 'availability-refresh' })
    }
    if (path === '/admin/system/index/info' && method === 'GET') {
      return json(route, {
        name: 'Alice',
        avatar: '',
        routers: [{ path: '/processSet/processTemplate', meta: { title: '审批模板' } }],
      })
    }
    if (path === '/admin/process/findStartable' && method === 'GET') {
      return json(route, [
        {
          id: 1,
          name: '请假',
          processTemplateList: [oldTemplate],
        },
      ])
    }
    if (path === `/admin/process/processTemplate/get/${templateId}` && method === 'GET') {
      return json(route, oldTemplate)
    }
    if (path === '/admin/process/processTemplate/1/10' && method === 'GET') {
      return json(route, { records: [oldTemplate], total: 1, pages: 1 })
    }
    if (path.startsWith('/admin/process/findPending/') && method === 'GET') {
      return json(route, { records: [], total: 0, pages: 0 })
    }
    if (
      path === '/admin/process/carbonCopyRecipients' ||
      path.startsWith('/admin/attachments/drafts/')
    ) {
      sideEffectRequests.push(`${method} ${path}`)
      return json(route, [])
    }
    if (path === '/admin/process/startUp' && method === 'POST') {
      sideEffectRequests.push(`${method} ${path}`)
      return json(route, null)
    }

    return json(route, { records: [], total: 0, pages: 0 })
  })

  return { sideEffectRequests }
}

test('keeps unverified published templates frozen and blocks direct start URLs', async ({
  page,
}) => {
  test.skip((page.viewportSize()?.width || 0) <= 768, 'configuration pages require desktop width')
  const { sideEffectRequests } = await mockUnavailableTemplate(page)

  await page.goto('/login')
  await page.getByPlaceholder('用户名').fill('alice')
  await page.getByPlaceholder('密码').fill('111111')
  await page.getByRole('button', { name: '登录' }).click()
  await expect(page.getByRole('heading', { name: '待处理' })).toBeVisible()

  await page.goto('/processSet/processTemplate')
  const templateRow = page.locator('.el-table__row').filter({ hasText: '历史请假模板' })
  await expect(templateRow.getByText('已发布')).toBeVisible()
  await expect(templateRow.getByText(unavailableMessage)).toBeVisible()
  await expect(templateRow.getByRole('button', { name: '查看' })).toBeVisible()
  await expect(templateRow.getByRole('button', { name: '编辑' })).toHaveCount(0)
  await expect(templateRow.getByRole('button', { name: '删除' })).toHaveCount(0)
  await expect(templateRow.getByRole('button', { name: '发布' })).toHaveCount(0)

  await page.goto('/work/start')
  await expect(page.getByText('暂无可发起的审批模板')).toBeVisible()
  await expect(page.getByRole('button', { name: '历史请假模板' })).toHaveCount(0)

  await page.goto(`/work/start/${templateId}`)
  await expect(page.getByRole('alert')).toContainText(unavailableMessage)
  await expect(page.getByLabel('抄送给')).toHaveCount(0)
  await expect(page.getByRole('heading', { name: '附件' })).toHaveCount(0)
  expect(sideEffectRequests).toEqual([])
})
