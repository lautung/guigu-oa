import { test, expect } from './helpers/fixtures'
import { poll } from './helpers/oracle'
import { marker, runId } from './helpers/stack'
import {
  clickWorkItem,
  fillDemoForm,
  openWorkList,
  submitStart,
  uiLoginExpectWork,
  uiLogout,
} from './helpers/ui'

/**
 * P0-9（#290）：部门负责人动态审批人主路径（ADR 0069）。
 *
 * 演示栈没有 DEPT_LEADER 规则数据，按票面约定在 setup 里用 API 配置（不改服务端种子）：
 * 建部门（负责人=carol）→ alice 划入 → 造模板并整体替换审批人规则（DEPT_LEADER）→ 发布
 * （发布合成 oa-approvers sidecar，任务创建时按当前组织解析）。
 *
 * 断言的关键点是「规则覆盖 assignee」：BPMN 写死 assignee=bob，任务必须落到 alice
 * 部门负责人 carol 名下，而不是 bob。UI 只走发起与通过两个真动作，持久化结果交给 oracle。
 */
test('alice 发起后由其部门负责人（而非 BPMN assignee）审批', async ({ page, oracle }) => {
  const admin = 'admin'
  const carolId = await oracle.userId(admin, 'carol')
  const aliceId = await oracle.userId(admin, 'alice')

  const deptId = await oracle.createDeptWithLeader(admin, marker('动态部门'), carolId)
  await oracle.setUserDept(admin, aliceId, deptId)

  const templateName = marker('部门负责人模板')
  await oracle.publishTemplateWithRules(admin, templateName, `stk${runId()}dl`, [
    { taskDefinitionKey: 'review', sourceType: 'DEPT_LEADER' },
  ])

  // alice 经真实 UI 发起（发起页模板卡片来自 findStartable）。
  await uiLoginExpectWork(page, 'alice')
  await openWorkList(page, '发起审批')
  await page.locator('.template-card', { hasText: templateName }).click()
  await expect(page.getByRole('heading', { name: templateName, level: 2 })).toBeVisible()
  const reason = marker('部门负责人路径')
  await fillDemoForm(page, { reason, days: '1' })
  await submitStart(page)

  const started = await poll(20_000, 500, async () => oracle.findMarked('alice', 'started', reason))
  expect(started, '提交后已发起列表应有这条审批').toBeTruthy()
  const processId = started!.id

  // 规则覆盖 assignee：负责人 carol 收到任务，BPMN 写死的 bob 不应有行。
  const carolRow = await poll(20_000, 500, () => oracle.pendingRow('carol', processId))
  expect(carolRow, 'carol（alice 部门负责人）应收到待处理').toBeTruthy()
  expect(carolRow!.taskId).toBeTruthy()
  expect(
    await oracle.pendingRow('bob', processId),
    'BPMN assignee bob 被规则覆盖，不应收到待处理',
  ).toBeUndefined()

  // 负责人经真实 UI 通过，流程收口（alice 仍持会话，先登出再换人）。
  await uiLogout(page)
  await uiLoginExpectWork(page, 'carol')
  await clickWorkItem(page, reason)
  await expect(page.getByRole('heading', { name: '审批流转记录' })).toBeVisible()
  await page.getByRole('button', { name: '通过', exact: true }).click()
  await expect(page.getByRole('heading', { name: '已处理', level: 2 })).toBeVisible()

  const finished = await poll(20_000, 500, async () => {
    const record = await oracle.findMarked('alice', 'started', reason)
    return record?.status === 2 ? record : undefined
  })
  expect(finished?.id, 'alice 的已发起应翻到已通过').toBe(processId)
})
