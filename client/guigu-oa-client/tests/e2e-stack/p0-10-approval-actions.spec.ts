import { test, expect } from './helpers/fixtures'
import { demoTemplate, poll } from './helpers/oracle'
import { marker } from './helpers/stack'
import {
  clickWorkItem,
  decide,
  fillDemoForm,
  openWorkList,
  submitStart,
  uiLoginExpectWork,
  uiLogout,
} from './helpers/ui'

/**
 * P0-10（#290）：转办 / 加签（前、后）/ 撤回 / 催办主路径。
 *
 * 造数与中转动作按票面走 oracle 直发 API（转办、加签、催办频控对照）；
 * 处理人通过与申请人撤回/催办走真实 UI（现有 helpers），持久化结果由 oracle 复核。
 * 每条路径独立发起一条标记审批，失败可原地重跑（stack.ts 数据策略）。
 */

test('转办 → 前加签 → 加签人通过 → 原处理人通过', async ({ page, oracle }) => {
  const admin = 'admin'
  const bobId = await oracle.userId(admin, 'bob')
  const carolId = await oracle.userId(admin, 'carol')
  const template = await demoTemplate(oracle)
  const reason = marker('转办前加签')
  const processId = await oracle.startApproval('alice', template, reason)

  // bob 转办给 carol：carol 成为当前处理人，bob 只读。
  await oracle.transfer('bob', processId, carolId)
  const carolRow = await poll(20_000, 500, () => oracle.pendingRow('carol', processId))
  expect(carolRow?.taskId, 'carol 应在转办后收到待处理').toBeTruthy()
  expect(await oracle.pendingRow('bob', processId), 'bob 转办后不再有可处理任务').toBeUndefined()

  // carol 前加签 bob：bob 先处理加签任务，carol 的引擎任务挂起。
  await oracle.addSign('carol', processId, 1, [bobId])
  const signRow = await poll(20_000, 500, () => oracle.pendingRow('bob', processId))
  expect(signRow?.taskId, 'bob 应收到前加签任务').toBeTruthy()
  expect(signRow?.actionKind).toBe('ADD_SIGN')

  // 加签人 bob 经真实 UI 通过。
  await uiLoginExpectWork(page, 'bob')
  await clickWorkItem(page, reason)
  await expect(page.getByRole('heading', { name: '审批流转记录' })).toBeVisible()
  await decide(page, '通过')

  // 原处理人 carol 回到任务面，经真实 UI 通过收口。
  await uiLogout(page)
  await uiLoginExpectWork(page, 'carol')
  await clickWorkItem(page, reason)
  await expect(page.getByRole('heading', { name: '审批流转记录' })).toBeVisible()
  await decide(page, '通过')

  const finished = await poll(20_000, 500, async () => {
    const record = await oracle.findMarked('alice', 'started', reason)
    return record?.status === 2 ? record : undefined
  })
  expect(finished?.id, '转办与加签链走完后应已通过').toBe(processId)
})

test('后加签：处理人通过先记账，加签人通过后流程收口', async ({ page, oracle }) => {
  const carolId = await oracle.userId('admin', 'carol')
  const template = await demoTemplate(oracle)
  const reason = marker('后加签')
  const processId = await oracle.startApproval('alice', template, reason)

  await oracle.addSign('bob', processId, 2, [carolId])
  const signRow = await poll(20_000, 500, () => oracle.pendingRow('carol', processId))
  expect(signRow?.taskId, 'carol 应收到后加签任务').toBeTruthy()
  expect(signRow?.actionKind).toBe('ADD_SIGN')

  // 加签人 carol 经真实 UI 通过；bob 的通过已在发起加签时记账（ADR：已记账未完成）。
  await uiLoginExpectWork(page, 'carol')
  await clickWorkItem(page, reason)
  await expect(page.getByRole('heading', { name: '审批流转记录' })).toBeVisible()
  await decide(page, '通过')

  const finished = await poll(20_000, 500, async () => {
    const record = await oracle.findMarked('alice', 'started', reason)
    return record?.status === 2 ? record : undefined
  })
  expect(finished?.id, '后加签人通过后流程应已通过').toBe(processId)
  const bobProcessed = await oracle.findMarked('bob', 'processed', reason)
  expect(bobProcessed, 'bob 的已处理应保留记账的通过').toBeTruthy()
})

test('发起 → 撤回：状态翻已撤回且不可恢复', async ({ page, oracle }) => {
  const template = await demoTemplate(oracle)
  const reason = marker('撤回路径')

  // alice 经真实 UI 发起，提交成功即跳已发起列表。
  await uiLoginExpectWork(page, 'alice')
  await openWorkList(page, '发起审批')
  await page.locator('.template-card', { hasText: template.name }).click()
  await expect(page.getByRole('heading', { name: template.name, level: 2 })).toBeVisible()
  await fillDemoForm(page, { reason, days: '1' })
  await submitStart(page)

  // 从已发起列表进入详情，撤回后留在详情重读（不导航），状态翻已撤回。
  await openWorkList(page, '已发起')
  await clickWorkItem(page, reason)
  const withdrawButton = page.getByRole('button', { name: '撤回审批' })
  await expect(withdrawButton).toBeEnabled()
  await withdrawButton.click()
  await expect(page.locator('.el-tag', { hasText: '已撤回' })).toBeVisible()
  await expect(withdrawButton).toHaveCount(0)

  const started = await poll(20_000, 500, async () => {
    const record = await oracle.findMarked('alice', 'started', reason)
    return record?.status === -2 ? record : undefined
  })
  expect(started, 'alice 的已发起应翻到已撤回').toBeTruthy()
})

test('发起 → 催办：待处理行出现已催办标记，24 小时频控 201', async ({ page, oracle }) => {
  const template = await demoTemplate(oracle)
  const reason = marker('催办路径')
  const processId = await oracle.startApproval('alice', template, reason)

  // alice 经真实 UI 催办：非阻塞动作，成功后详情出现提示文案。
  await uiLoginExpectWork(page, 'alice')
  await openWorkList(page, '已发起')
  await clickWorkItem(page, reason)
  const urgeButton = page.getByRole('button', { name: '催办', exact: true })
  await expect(urgeButton).toBeEnabled()
  await urgeButton.click()
  await expect(page.locator('.urge-hint')).toContainText('已提醒当前处理人')

  // 对照：bob 的待处理行带已催办标记（#288 三源合成 urged）。
  const urgedRow = await poll(20_000, 500, async () => {
    const row = await oracle.pendingRow('bob', processId)
    return row?.urged === true ? row : undefined
  })
  expect(urgedRow, 'bob 的待处理行应带已催办标记').toBeTruthy()

  // 频控对照：同一审批 24 小时内第二次催办 201。
  expect(await oracle.codeOf('alice', 'post', `/admin/process/urge/${processId}`)).toBe(201)
})
