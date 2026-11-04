import { expect, test, type Page } from '@playwright/test'

import { expectWorkListLoaded, workListItems } from '../helpers/work-list'
import { CLIENT_PAGES, uiLogin } from './helpers'

/**
 * SEC-HDR（#139）：统一客户端的安全响应头与 CSP 兼容性。
 *
 * 响应头的取值由 scripts/security-scan.mjs 的 SEC-HDR-01..04 断言；这个 spec 只补充
 * 浏览器视角：CSP 放进页面后，登录、办事列表/详情、模板列表与表单设计器都不产生
 * `securitypolicyviolation`，也就是没有资源被策略拦截。
 */

interface CspViolation {
  directive: string
  blockedURI: string
}

async function clearSession(page: Page): Promise<void> {
  await page.context().clearCookies()
  await page.goto('/login')
  await page.evaluate(() => window.localStorage.clear())
}

test('CSP 生效时主要页面无安全策略违规', async ({ page, request }, testInfo) => {
  for (const path of CLIENT_PAGES) {
    const response = await request.get(path)
    expect(response.status(), `${path} 状态`).toBe(200)
    const headers = response.headers()
    const csp = headers['content-security-policy'] ?? ''
    expect(csp, `${path} 缺 CSP`).toContain("frame-ancestors 'none'")
    expect(csp, `${path} CSP 缺 script-src 'self'`).toContain("script-src 'self'")
    expect(csp, `${path} CSP 含未展开占位符`).not.toContain('$')
    expect(headers['x-frame-options'], `${path} 缺 X-Frame-Options`).toBe('DENY')
    expect(headers['x-content-type-options'], `${path} 缺 nosniff`).toBe('nosniff')
  }

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

  // 办事侧：列表与详情（form-create 渲染检查表单、附件区）。
  await uiLogin(page, 'bob')
  await page.locator('.app-sidebar').getByRole('link', { name: '待处理' }).click()
  await expect(page.getByRole('heading', { name: '待处理', level: 2 })).toBeVisible()
  // 首行点击前先等列表加载正信号（#226）：loading 中行未渲染，裸点会空转 15s。
  await expectWorkListLoaded(page)
  await workListItems(page).first().click()
  await expect(page.getByRole('heading', { name: '审批流转记录' })).toBeVisible()
  await page.waitForTimeout(1_000)

  // 配置侧：模板列表图标、查看弹窗（form-create）与编辑向导的表单设计器。
  await clearSession(page)
  await uiLogin(page, 'admin')
  await page.goto('/system/processSet/processTemplate')
  await expect(page.getByRole('heading', { name: '审批模板', level: 2 })).toBeVisible()
  await page.locator('.el-table__row').first().getByRole('button', { name: '查看' }).click()
  await expect(page.getByRole('dialog', { name: '查看审批模板' })).toBeVisible()
  await page
    .getByRole('dialog', { name: '查看审批模板' })
    .getByRole('button', { name: '关闭', exact: true })
    .click()

  const editable = page.locator('.el-table__row').filter({ hasText: '未发布病假' })
  await expect(editable).toBeVisible()
  await editable.getByRole('button', { name: '编辑' }).click()
  await expect(page.locator('#template-name')).toBeVisible()
  await page.getByRole('button', { name: '下一步' }).click()
  await expect(page.getByRole('heading', { name: '基础组件' })).toBeVisible()
  await page.waitForTimeout(1_000)

  // @form-create/designer 把 core-js 打进自己的 dist：模块初始化的全局探测会执行
  // `Function('return this')()`，严格模式下每次加载必然被 script-src 拦一次，catch 里
  // 回退到 window，功能不受影响。只容许这一种已知违规，其他指令/脚本被拦都算失败。
  const unexpected = violations.filter(
    (violation) => !(violation.directive === 'script-src' && violation.blockedURI === 'eval'),
  )
  await testInfo.attach('csp-violations', {
    body: JSON.stringify({ total: violations.length, unexpected }),
    contentType: 'application/json',
  })
  expect(unexpected, `CSP 拦截了页面资源：${JSON.stringify(unexpected)}`).toEqual([])
})
