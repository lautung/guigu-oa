import { test, expect } from './helpers/fixtures'
import { demoTemplate, poll } from './helpers/oracle'
import { composeService, marker } from './helpers/stack'
import { fillDemoForm, openWorkList, submitStart, uiLoginExpectWork } from './helpers/ui'

/**
 * P0-8（#121）：发起结果不明 → 核实 → 明确重新发起 → 两条审批都真实存在。
 * 对应 GLOSSARY「发起结果待确认」「重新发起」与 ADR 0008（重新发起可能另产生一条，不承诺幂等）。
 *
 * 真实栈触发方式：docker compose pause service-oa 冻结服务端——请求已送达但永远等不到响应，
 * 客户端 axios 15s 超时后进入「发起结果待确认」；解冻后服务端把积压的这次提交真实落库。
 * 全程没有伪造任何请求或响应。
 */

test('发起结果不明后核实并重新发起，两条审批都真实存在', async ({ page, oracle }) => {
  const template = await demoTemplate(oracle)
  const reason = marker('发起不明')

  await uiLoginExpectWork(page, 'alice')
  await openWorkList(page, '发起审批')
  await page.locator('.template-card', { hasText: template.name }).click()
  await expect(page.getByRole('heading', { name: template.name, level: 2 })).toBeVisible()
  await fillDemoForm(page, { reason, days: '1' })

  // 等发起页完全静止（准备请求全部完成、提交按钮可用）再冻结服务端，
  // 避免 pause 把在途的读取请求冻成「loading 永不结束」。
  const submit = page.getByRole('button', { name: '提交', exact: true })
  await expect(submit).toBeEnabled()
  composeService('pause')
  try {
    await page.getByRole('button', { name: '提交', exact: true }).click()
    await expect(page.getByRole('heading', { name: '发起结果待确认' })).toBeVisible({
      timeout: 30_000,
    })
    await expect(page.getByText('请先核实已发起')).toBeVisible()
  } finally {
    composeService('unpause')
  }

  // 核实：待确认不等于失败——解冻后原提交真实落库。
  const original = await poll(30_000, 1_000, async () => {
    const records = await oracle.findMarkedAll('alice', 'started', reason)
    return records.length >= 1 ? records[0] : undefined
  })
  expect(original, '冻结窗口结束后原提交应当已落库').toBeTruthy()

  // 明确重新发起：UI 要求先过「接受可能重复，继续」确认（ADR 0008），两次提交都真实存在。
  await page.getByRole('button', { name: '仍未找到，重新发起' }).click()
  await expect(
    page.locator('.el-message-box').getByRole('button', { name: '接受可能重复，继续' }),
  ).toBeVisible()
  await page.locator('.el-message-box').getByRole('button', { name: '接受可能重复，继续' }).click()
  await submitStart(page, reason)

  const both = await oracle.findMarkedAll('alice', 'started', reason)
  expect(both, '原提交与重新发起的审批都应真实存在').toHaveLength(2)
})
