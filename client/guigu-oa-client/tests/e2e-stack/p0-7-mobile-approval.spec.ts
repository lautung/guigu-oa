import { test, expect } from './helpers/fixtures'
import { demoTemplate } from './helpers/oracle'
import { marker } from './helpers/stack'
import { clickWorkItem, decide, uiLoginExpectWork } from './helpers/ui'

/**
 * P0-7（#121）：手机 390 视口完成「登录 → 待处理 → 详情 → 通过」。
 * 对应 GLOSSARY「统一客户端」的硬约束：办事必须能在手机浏览器完成。
 */

test.use({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true })

test('手机视口完成登录到通过的全链路', async ({ page, oracle }) => {
  const template = await demoTemplate(oracle)
  const reason = marker('手机通过')
  const processId = await oracle.startApproval('alice', template, reason)

  await uiLoginExpectWork(page, 'bob')
  await clickWorkItem(page, reason)
  await expect(page.getByRole('heading', { name: '审批流转记录' })).toBeVisible()
  await decide(page, '通过')

  const processed = await oracle.findMarked('bob', 'processed', reason)
  expect(processed?.status).toBe(2)
  expect(processId).toBeGreaterThan(0)

  const viewport = await page.evaluate(() => ({
    width: window.innerWidth,
    documentWidth: document.documentElement.scrollWidth,
  }))
  expect(viewport.documentWidth).toBeLessThanOrEqual(viewport.width)
})
