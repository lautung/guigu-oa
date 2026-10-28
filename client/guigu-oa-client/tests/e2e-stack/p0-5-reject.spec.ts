import { test, expect } from './helpers/fixtures'
import { demoTemplate } from './helpers/oracle'
import { marker } from './helpers/stack'
import { clickWorkItem, decide, openWorkList, uiLoginExpectWork } from './helpers/ui'

/**
 * P0-5（#121）：bob 拒绝路径，状态与列表随之变化。对应 GLOSSARY「处理审批」与
 * 已处理/已发起的状态语义（审批拒绝）。
 */

test('bob 拒绝一条审批，双视角状态一致', async ({ page, oracle }) => {
  const template = await demoTemplate(oracle)
  const reason = marker('拒绝路径')
  const processId = await oracle.startApproval('alice', template, reason)

  await uiLoginExpectWork(page, 'bob')
  await openWorkList(page, '待处理')
  await clickWorkItem(page, reason)
  await expect(page.getByRole('heading', { name: '审批流转记录' })).toBeVisible()
  await decide(page, '拒绝')

  const processed = await oracle.findMarked('bob', 'processed', reason)
  expect(processed?.status).toBe(-1)
  expect(processed?.id, '处理的是 oracle 造的那条审批').toBe(processId)
  const started = await oracle.findMarked('alice', 'started', reason)
  expect(started?.status).toBe(-1)
})
