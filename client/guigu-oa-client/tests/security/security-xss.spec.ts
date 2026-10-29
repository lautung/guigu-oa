import { expect, test, type APIRequestContext, type Locator, type Page } from '@playwright/test'

import { PASSWORD, uiLogin } from './helpers'

/**
 * SEC-XSS（#113）：审批表单字段的存储型 XSS。
 *
 * 期望的安全行为：`formShowData` 里的 HTML 只作为文本显示，不会执行脚本。
 * 用例先在 alice 的审批里写入带 `onerror` 的标记，再由 bob 在待处理列表与详情页查看；
 * `window.__secXss` 被置位即证明脚本执行（缺陷），断言失败本身就是证据。
 */

const PAYLOAD = '<img src=x onerror="window.__secXss=1">'

function runId(): string {
  return process.env.SEC_RUN_ID || Date.now().toString(36)
}

async function login(request: APIRequestContext, username: string): Promise<string> {
  const response = await request.post('/dev-api/admin/system/index/login', {
    data: { username, password: PASSWORD },
  })
  const body = (await response.json()) as { code: number; data?: { token?: string } }
  expect(body.code, `登录 ${username}`).toBe(200)
  const token = body.data?.token
  expect(token, `登录 ${username} 返回 token`).toBeTruthy()
  return token as string
}

async function startApprovalWithPayload(
  request: APIRequestContext,
  token: string,
  marker: string,
): Promise<void> {
  const templatesResponse = await request.get('/dev-api/admin/process/findStartable', {
    headers: { token },
  })
  const templatesBody = (await templatesResponse.json()) as {
    code: number
    data?: {
      id: number
      processTemplateList?: { id: number; name: string; availableForInitiation?: boolean }[]
    }[]
  }
  expect(templatesBody.code).toBe(200)

  let template: { id: number; processTypeId: number } | null = null
  for (const group of templatesBody.data || []) {
    for (const candidate of group.processTemplateList || []) {
      if (candidate.name === '演示请假申请' && candidate.availableForInitiation) {
        template = { id: candidate.id, processTypeId: group.id }
      }
    }
  }
  expect(template, '存在可发起的演示请假申请').toBeTruthy()

  const response = await request.post('/dev-api/admin/process/startUp', {
    headers: { token },
    data: {
      processTemplateId: template?.id,
      processTypeId: template?.processTypeId,
      formValues: JSON.stringify({
        formData: { reason: marker, days: 1 },
        formShowData: { 请假原因: `${marker}${PAYLOAD}`, 请假天数: 1 },
      }),
      attachmentIds: [],
    },
  })
  const body = (await response.json()) as { code: number; message?: string }
  expect(body.code, `发起审批：${body.message ?? ''}`).toBe(200)
}

async function findWorkItem(page: Page, marker: string): Promise<Locator | null> {
  for (let visited = 1; visited <= 40; visited += 1) {
    const item = page.locator('.work-list-item', { hasText: marker })
    if ((await item.count()) > 0) return item.first()
    const next = page.locator('.el-pagination .btn-next')
    if ((await next.count()) === 0 || (await next.isDisabled())) return null
    await next.click()
    await page.waitForTimeout(400)
  }
  return null
}

test('审批表单字段的存储型 XSS 不执行', async ({ page, request }, testInfo) => {
  const marker = `secxss-${runId()}`
  const aliceToken = await login(request, 'alice')
  await startApprovalWithPayload(request, aliceToken, marker)

  await uiLogin(page, 'bob')
  const item = await findWorkItem(page, marker)
  expect(item, `待处理列表翻页后找不到 ${marker}`).not.toBeNull()
  await expect(item as Locator).toBeVisible()
  await page.waitForTimeout(1_500)

  const listFlag = await page.evaluate(() => (window as { __secXss?: number }).__secXss ?? null)
  await testInfo.attach('list-xss-flag', {
    body: JSON.stringify({ marker, listFlag }),
    contentType: 'application/json',
  })
  expect.soft(listFlag, '列表页 v-html 执行了脚本').toBeNull()

  await page.evaluate(() => {
    ;(window as { __secXss?: number }).__secXss = undefined
  })
  await (item as Locator).click()
  await expect(page.locator('.form-field p').first()).toBeVisible()
  await page.waitForTimeout(1_500)

  const detailFlag = await page.evaluate(() => (window as { __secXss?: number }).__secXss ?? null)
  await testInfo.attach('detail-xss-flag', {
    body: JSON.stringify({ marker, detailFlag }),
    contentType: 'application/json',
  })
  expect.soft(detailFlag, '详情页 v-html 执行了脚本').toBeNull()
})
