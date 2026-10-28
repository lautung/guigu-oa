import { test, expect } from './helpers/fixtures'
import { demoTemplate } from './helpers/oracle'
import { marker } from './helpers/stack'
import {
  clickWorkItem,
  decide,
  expectWorkItemVisible,
  openWorkList,
  uiLoginExpectWork,
} from './helpers/ui'

/**
 * P0-4（#121）：bob 待处理 → 通过 → bob 已处理可见、alice 已发起状态更新。
 * 对应 GLOSSARY「待处理」「处理审批」「已处理」「已发起」。
 */

test('bob 通过一条审批，双视角状态一致', async ({ page, oracle }) => {
  const template = await demoTemplate(oracle)
  const reason = marker('通过路径')
  const processId = await oracle.startApproval('alice', template, reason)

  await uiLoginExpectWork(page, 'bob')
  await openWorkList(page, '待处理')
  await clickWorkItem(page, reason)
  await expect(page.getByRole('heading', { name: '审批流转记录' })).toBeVisible()
  await decide(page, '通过')

  const processed = await oracle.findMarked('bob', 'processed', reason)
  expect(processed?.status).toBe(2)
  expect(processed?.id, '处理的是 oracle 造的那条审批').toBe(processId)
  const started = await oracle.findMarked('alice', 'started', reason)
  expect(started?.status).toBe(2)
})

test('bob 已处理列表出现刚通过的审批', async ({ page, oracle }) => {
  const template = await demoTemplate(oracle)
  const reason = marker('已处理可见')
  const processId = await oracle.startApproval('alice', template, reason)
  await oracle.approve('bob', processId, 1)

  await uiLoginExpectWork(page, 'bob')
  await openWorkList(page, '已处理')
  await expectWorkItemVisible(page, reason)
  expect(processId).toBeGreaterThan(0)
})
