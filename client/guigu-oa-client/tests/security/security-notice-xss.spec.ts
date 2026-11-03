import { expect, test, type APIRequestContext, type Page } from '@playwright/test'

import { PASSWORD, uiLogin } from './helpers'

/**
 * SEC-XSS 公告变体（#219，ADR 0055/0028）：存储型载荷经公告正文进入唯一渲染面。
 *
 * admin 通过服务端接口发布一条带危险 HTML 的公告；bob 打开铃铛、全部公告与详情。
 * 服务端写入时按同源白名单消毒，客户端只渲染已消毒 HTML：`onerror`/`script` 必须
 * 不执行（`window.__noticeXss` 保持未设置），同时作为文本转义的载荷原样可见——
 * 这证明消毒不是「整段吞掉」，页面仍能读出原始内容。列表与详情同时监听
 * `securitypolicyviolation`，CSP 没有新增放行源（图片仍只认同源路径）。
 */

const PAYLOAD = '<img src=x onerror="window.__noticeXss=1">'
const ESCAPED_PAYLOAD = '&lt;img src=x onerror="window.__noticeXss=1"&gt;'

interface CspViolation {
  directive: string
  blockedURI: string
}

interface CreatedNotice {
  id: number
}

function runId(): string {
  return process.env.SEC_RUN_ID || Date.now().toString(36)
}

async function adminToken(request: APIRequestContext): Promise<string> {
  const response = await request.post('/dev-api/admin/system/index/login', {
    data: { username: 'admin', password: PASSWORD },
  })
  const body = (await response.json()) as { code: number; data?: { token?: string } }
  expect(body.code, 'admin 登录').toBe(200)
  expect(body.data?.token, 'admin 登录返回 token').toBeTruthy()
  return body.data?.token as string
}

async function createNotice(
  request: APIRequestContext,
  token: string,
  title: string,
): Promise<number> {
  const created = await request.post('/dev-api/admin/system/notices', {
    headers: { token },
    data: {
      title,
      type: 2,
      content: `<p>${title} ${ESCAPED_PAYLOAD}</p>${PAYLOAD}<script>window.__noticeXss=1</script>`,
    },
  })
  const createdBody = (await created.json()) as {
    code: number
    message?: string
    data?: CreatedNotice
  }
  expect(createdBody.code, `创建公告：${createdBody.message ?? ''}`).toBe(200)
  const id = createdBody.data?.id as number
  expect(id, '创建公告返回 id').toBeTruthy()
  return id
}

async function publishNotice(request: APIRequestContext, token: string, id: number): Promise<void> {
  const published = await request.post(`/dev-api/admin/system/notices/${id}/publish`, {
    headers: { token },
  })
  const publishedBody = (await published.json()) as { code: number; message?: string }
  expect(publishedBody.code, `发布公告：${publishedBody.message ?? ''}`).toBe(200)
}

async function noticeXssFlag(page: Page): Promise<number | null> {
  return page.evaluate(() => (window as { __noticeXss?: number }).__noticeXss ?? null)
}

test('公告正文的存储型 XSS 不执行且载荷文本保留', async ({ page, request }, testInfo) => {
  const marker = `secnotice-${runId()}`
  const violations: CspViolation[] = []
  await page.exposeFunction('__reportCspViolation', (violation: CspViolation) => {
    violations.push(violation)
  })
  await page.addInitScript(() => {
    document.addEventListener('securitypolicyviolation', (event) => {
      const report = (window as unknown as { __reportCspViolation?: (v: CspViolation) => void })
        .__reportCspViolation
      report?.({ directive: event.violatedDirective, blockedURI: event.blockedURI })
    })
  })

  const token = await adminToken(request)
  let noticeId: number | undefined
  try {
    noticeId = await createNotice(request, token, marker)
    await publishNotice(request, token, noticeId)

    await uiLogin(page, 'bob')

    // 1. 铃铛弹层：最近未读里有标题，脚本不执行。
    await page.getByRole('button', { name: '公告', exact: true }).click()
    const item = page.locator('.notice-item', { hasText: marker })
    await expect(item).toBeVisible()
    await page.waitForTimeout(1_000)
    const bellFlag = await noticeXssFlag(page)
    await testInfo.attach('notice-bell-xss-flag', {
      body: JSON.stringify({ marker, bellFlag }),
      contentType: 'application/json',
    })
    expect.soft(bellFlag, '铃铛弹层执行了公告脚本').toBeNull()

    // 2. 全部公告：列表不返回正文，但同样不能有脚本执行面。
    await page.getByRole('button', { name: '查看全部' }).click()
    await expect(page.getByRole('heading', { name: '全部公告' })).toBeVisible()
    await expect(page.locator('.notice-table')).toContainText(marker)
    await page.waitForTimeout(1_000)
    const listFlag = await noticeXssFlag(page)
    await testInfo.attach('notice-list-xss-flag', {
      body: JSON.stringify({ marker, listFlag }),
      contentType: 'application/json',
    })
    expect.soft(listFlag, '全部公告执行了公告脚本').toBeNull()

    // 3. 详情：唯一渲染面渲染消毒后的 HTML；危险节点被剥掉，文本载荷保留。
    await page.locator('.notice-title-link', { hasText: marker }).click()
    await expect(page.locator('.notice-content')).toBeVisible()
    await expect(page.locator('.notice-content')).toContainText(marker)
    await expect(page.locator('.notice-content')).toContainText(PAYLOAD)
    await expect(page.locator('.notice-content img[src="x"]')).toHaveCount(0)
    await expect(page.locator('.notice-content script')).toHaveCount(0)
    await page.waitForTimeout(1_500)

    const detailFlag = await noticeXssFlag(page)
    await testInfo.attach('notice-detail-xss-flag', {
      body: JSON.stringify({ marker, detailFlag }),
      contentType: 'application/json',
    })
    expect.soft(detailFlag, '公告详情执行了脚本').toBeNull()

    // @form-create/designer 的 core-js 探测是已知的 eval 违规；公告面不加载它，
    // 但沿用同一豁免口径，其他指令/源被拦都算失败。
    const unexpected = violations.filter(
      (violation) => !(violation.directive === 'script-src' && violation.blockedURI === 'eval'),
    )
    await testInfo.attach('notice-csp-violations', {
      body: JSON.stringify({ total: violations.length, unexpected }),
      contentType: 'application/json',
    })
    expect(unexpected, `CSP 拦截了公告页资源：${JSON.stringify(unexpected)}`).toEqual([])
  } finally {
    if (noticeId) {
      await request
        .delete(`/dev-api/admin/system/notices/${noticeId}`, { headers: { token } })
        .catch(() => undefined)
    }
  }
})
