import { expect, test, type Page, type Route } from '@playwright/test'
import { fulfillNoticeUnread } from './helpers/notice-mocks'
import { openShellLink } from './helpers/shell'

const templateId = 101
const processId = 'smoke-process-1'
const taskId = 'smoke-task-1'

interface ApprovalCase {
  action: '通过' | '拒绝'
  status: 1 | -1
  result: '审批通过' | '审批拒绝'
}

interface SmokeState {
  startPayload?: Record<string, unknown>
  uploadRequest?: Record<string, unknown>
  approvalStatus?: number
  unexpectedRequests: string[]
}

const approvalCases: ApprovalCase[] = [
  { action: '通过', status: 1, result: '审批通过' },
  { action: '拒绝', status: -1, result: '审批拒绝' },
]

// Keep this browser smoke independent of backend and object-storage services.
// It verifies the client UI flow and request payloads, not server persistence.
function json(route: Route, data: unknown, status = 200) {
  return route.fulfill({
    status,
    contentType: 'application/json',
    body: JSON.stringify({ code: status, data }),
  })
}

async function mockApi(page: Page): Promise<SmokeState> {
  const state: SmokeState = { unexpectedRequests: [] }
  const attachment = {
    id: 'smoke-attachment-1',
    fileName: 'approval-note.txt',
    sizeBytes: 20,
    contentType: 'text/plain',
  }

  await page.route('**/dev-api/**', async (route) => {
    const request = route.request()
    const path = new URL(request.url()).pathname.replace(/^\/dev-api/, '')
    const method = request.method()

    if (path === '/admin/system/index/login' && method === 'POST') {
      return json(route, { token: 'smoke-token', refreshToken: 'smoke-refresh-token' })
    }
    if (path === '/admin/system/index/info' && method === 'GET') {
      return json(route, { name: 'Alice', avatar: '', menus: [] })
    }
    if (path === '/admin/process/findStartable' && method === 'GET') {
      return json(route, [
        {
          id: 11,
          name: '人事',
          processTemplateList: [
            { id: templateId, name: '请假申请', status: 1, availableForInitiation: true },
          ],
        },
      ])
    }
    if (path === '/admin/process/carbonCopyRecipients' && method === 'GET') {
      return json(route, [{ id: 3, name: 'Carol' }])
    }
    if (path === `/admin/process/getProcessTemplate/${templateId}` && method === 'GET') {
      return json(route, {
        id: templateId,
        processTypeId: 11,
        name: '请假申请',
        availableForInitiation: true,
        status: 1,
        formProps: JSON.stringify([
          {
            type: 'input',
            field: 'reason',
            title: '请假原因',
            $required: true,
            props: { placeholder: '请输入请假原因' },
          },
        ]),
        formOptions: JSON.stringify({ submitBtn: false }),
      })
    }
    if (path.startsWith('/admin/attachments/drafts/') && method === 'GET') {
      return json(route, [])
    }
    if (path === '/admin/attachments/uploads' && method === 'POST') {
      state.uploadRequest = request.postDataJSON() as Record<string, unknown>
      return json(route, {
        attachmentId: attachment.id,
        uploadUrl: 'http://localhost:9000/upload/smoke-attachment-1',
        headers: {},
      })
    }
    if (path === `/admin/attachments/${attachment.id}/complete` && method === 'POST') {
      return json(route, attachment)
    }
    if (path === '/admin/process/startUp' && method === 'POST') {
      state.startPayload = request.postDataJSON() as Record<string, unknown>
      return json(route, null)
    }
    if (path.startsWith('/admin/process/findStarted/') && method === 'GET') {
      return json(route, {
        records: state.startPayload ? [processRecord(state, 1)] : [],
        total: state.startPayload ? 1 : 0,
        pages: state.startPayload ? 1 : 0,
      })
    }
    if (path.startsWith('/admin/process/findPending/') && method === 'GET') {
      const pending = !!state.startPayload && state.approvalStatus == null
      return json(route, {
        records: pending ? [processRecord(state, 1)] : [],
        total: pending ? 1 : 0,
        pages: pending ? 1 : 0,
      })
    }
    if (path.startsWith('/admin/process/findProcessed/') && method === 'GET') {
      const processed = !!state.startPayload && state.approvalStatus != null
      const resultingStatus = state.approvalStatus === 1 ? 2 : -1
      return json(route, {
        records: processed ? [processRecord(state, resultingStatus)] : [],
        total: processed ? 1 : 0,
        pages: processed ? 1 : 0,
      })
    }
    if (path === `/admin/process/show/${processId}` && method === 'GET') {
      return json(route, {
        process: {
          ...processRecord(state, 1),
          formValues: state.startPayload?.formValues,
        },
        processRecordList: [],
        isApprove: true,
      })
    }
    if (path === `/admin/attachments/processes/${processId}` && method === 'GET') {
      return json(route, { attachments: [attachment], canUpload: false })
    }
    if (path === '/admin/process/approve' && method === 'POST') {
      const body = request.postDataJSON() as { status?: number }
      state.approvalStatus = body.status
      return json(route, null)
    }

    if (await fulfillNoticeUnread(route, path)) return

    state.unexpectedRequests.push(`${method} ${path}`)
    return json(route, { message: `Unexpected API request: ${method} ${path}` }, 500)
  })

  // 生产构建的 CSP 只放行 connect-src 里的对象存储源（#139），mock 必须使用同一来源。
  await page.route('http://localhost:9000/**', (route) => route.fulfill({ status: 200, body: '' }))

  return state
}

function processRecord(state: SmokeState, status: number) {
  const formValues = state.startPayload?.formValues
  return {
    id: processId,
    taskId,
    title: '请假申请',
    name: '请假申请',
    processTypeName: '人事',
    processTemplateName: '请假申请',
    description: '等待审批',
    status,
    createTime: '2026-10-12 09:00:00',
    formValues,
  }
}

for (const approval of approvalCases) {
  test(`登录、发起含附件审批并${approval.action}`, async ({ page }) => {
    const state = await mockApi(page)

    await page.goto('/login')
    await page.getByPlaceholder('用户名').fill('alice')
    await page.getByPlaceholder('密码').fill('111111')
    await page.getByRole('button', { name: '登录' }).click()
    await expect(page.getByRole('heading', { name: '待处理' })).toBeVisible()

    await openShellLink(page, '发起审批')
    await page.getByRole('button', { name: '请假申请' }).click()
    await expect(page.getByRole('heading', { name: '请假申请' })).toBeVisible()
    await expect(page.getByRole('heading', { name: '附件' })).toBeVisible()
    await expect(page.getByText('暂无附件')).toBeVisible()
    await page.locator('.carbon-copy-select').click()
    await page.getByRole('option', { name: 'Carol' }).click()

    await page.locator('input[type="file"]').setInputFiles({
      name: 'approval-note.txt',
      mimeType: 'text/plain',
      buffer: Buffer.from('Please approve this request.'),
    })
    await expect(page.getByText('approval-note.txt')).toBeVisible()
    await page.getByPlaceholder('请输入请假原因').fill('家中有事')
    await page.getByRole('button', { name: '提交' }).click()

    await expect(page.getByRole('heading', { name: '已发起' })).toBeVisible()
    expect(state.startPayload).toMatchObject({
      processTemplateId: templateId,
      processTypeId: 11,
      attachmentIds: ['smoke-attachment-1'],
      ccUserIds: [3],
    })
    expect(state.uploadRequest).toMatchObject({
      fileName: 'approval-note.txt',
      contentType: 'text/plain',
    })
    expect(typeof state.uploadRequest?.draftId).toBe('string')
    expect(state.startPayload?.attachmentDraftId).toBe(state.uploadRequest?.draftId)
    expect(JSON.parse(state.startPayload?.formValues as string)).toMatchObject({
      formData: { reason: '家中有事' },
      formShowData: { 请假原因: '家中有事' },
    })

    await openShellLink(page, '待处理')
    await page.getByText('请假申请').first().click()
    await expect(page.getByRole('heading', { name: '审批流转记录' })).toBeVisible()
    await expect(page.getByText('approval-note.txt')).toBeVisible()

    await page.getByRole('button', { name: approval.action }).click()
    await expect(page.getByRole('heading', { name: '已处理' })).toBeVisible()
    await expect(page.getByText(approval.result)).toBeVisible()
    expect(state.approvalStatus).toBe(approval.status)
    expect(state.unexpectedRequests).toEqual([])

    const viewport = await page.evaluate(() => ({
      width: window.innerWidth,
      documentWidth: document.documentElement.scrollWidth,
    }))
    expect(viewport.documentWidth).toBeLessThanOrEqual(viewport.width)
  })
}
