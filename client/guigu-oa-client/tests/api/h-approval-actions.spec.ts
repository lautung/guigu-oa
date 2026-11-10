import { test, expect, type APIRequestContext } from '@playwright/test'

import {
  ADMIN,
  ALICE,
  Api,
  BOB,
  CAROL,
  PLAIN_USER,
  createUnpublishedTemplate,
  createUser,
  deleteUser,
  expectCode,
  expectCodeAndMessage,
  getTemplate,
  poll,
  publishTemplate,
  startApproval,
  unique,
  type PageResult,
  type ProcessRecord,
  type UserRecord,
} from './helpers/api'
import { bpmnXml, buildZip, runId } from '../e2e-stack/helpers/stack'

/**
 * H W1 审批动作与动态审批人（#290，票 #285–#288 的 HTTP 面）。
 *
 * 覆盖：审批人规则读/保存（未发布可改、已发布冻结）、upload 返回 userTasks、
 * 部门负责人选择器 userOptions（启用过滤 + 页面授予 205）、
 * 转办/加签（当前处理人资格与目标合法性 201 命名拒绝）、actionTargets 排除申请人、
 * 撤回（仅申请人仅审批中、重复撤回 201）、催办（24h 频控 201）。
 *
 * 业务键三态回写不在本套件：业务发起无 HTTP 入口（域票面），服务端
 * BusinessApprovalContractTest 已按合同钉死（#290 范围勘误）。
 *
 * 撤回/催办/转办/加签沿用既有信封（HTTP 200 + 业务 code），状态码契约由
 * f-http-semantics.spec.ts 单独拥有，这里只断 body.code（#120 §4）。
 */

let api: Api
let context: APIRequestContext

interface UserTaskRef {
  key: string
  name?: string
  assignee?: string
}

interface ApproverRule {
  processTemplateId: number
  taskDefinitionKey: string
  sourceType: string
  targetId: number | null
}

interface RuleConfig {
  userTasks: UserTaskRef[]
  rules: ApproverRule[]
}

interface UserOption {
  id: number
  username: string
  name: string
}

test.beforeAll(async ({ playwright }) => {
  context = await playwright.request.newContext()
  api = new Api(context)
})

test.afterAll(async () => {
  await context.dispose()
})

/** 某用户待处理列表里的指定审批行（翻页找 id）。 */
async function pendingRow(user: string, processId: number): Promise<ProcessRecord> {
  for (let page = 1; page <= 20; page += 1) {
    const result = await api.call<PageResult<ProcessRecord>>(
      user,
      'get',
      `/admin/process/findPending/${page}/100`,
    )
    const found = result.records.find((record) => record.id === processId)
    if (found) return found
    if (page * 100 >= result.total) break
  }
  throw new Error(`${user} 的待处理里找不到审批 ${processId}`)
}

async function userIdOf(username: string): Promise<number> {
  const page = await api.call<PageResult<UserRecord>>(
    ADMIN,
    'get',
    `/admin/system/sysUser/1/50?keyword=${encodeURIComponent(username)}`,
  )
  const found = page.records.find((record) => record.username === username)
  if (!found) throw new Error(`找不到用户 ${username}`)
  return found.id
}

/** 造一条带流程 zip 的未发布模板（bpmnXml 的节点 id 固定是 review / 名称 复核）。 */
async function createZippedTemplate(name: string): Promise<number> {
  const id = await createUnpublishedTemplate(api, name)
  const processKey = `api${runId()}${Math.random().toString(36).slice(2, 6)}`
  const zip = buildZip([{ name: 'process.bpmn20.xml', data: bpmnXml(processKey) }])
  const upload = await api.uploadProcessZip(ADMIN, { name: `${processKey}.zip`, buffer: zip })
  if (upload.body.code !== 200 || !upload.body.data) {
    throw new Error(`上传流程 zip 失败 code=${upload.body.code} ${upload.body.message ?? ''}`)
  }
  const template = await getTemplate(api, id)
  await api.call(ADMIN, 'put', '/admin/process/processTemplate/update', {
    ...template,
    processDefinitionPath: upload.body.data.processDefinitionPath,
    processDefinitionKey: upload.body.data.processDefinitionKey,
  })
  return id
}

test.describe.serial('H W1 审批动作与动态审批人', () => {
  test('H1 审批人规则：未发布可读可改，保存整体替换，目标校验逐条 201', async () => {
    const id = await createZippedTemplate(unique('api-rule-tpl'))
    try {
      // 读配置：userTasks 来自流程 zip，规则初始为空。
      const initial = await api.call<RuleConfig>(
        ADMIN,
        'get',
        `/admin/process/processTemplate/approverRules/${id}`,
      )
      expect(initial.userTasks.some((task) => task.key === 'review')).toBe(true)
      expect(initial.rules).toEqual([])

      // 正例：DEPT_LEADER 不带目标。
      await api.call(ADMIN, 'put', `/admin/process/processTemplate/approverRules/${id}`, [
        { taskDefinitionKey: 'review', sourceType: 'DEPT_LEADER' },
      ])
      const saved = await api.call<RuleConfig>(
        ADMIN,
        'get',
        `/admin/process/processTemplate/approverRules/${id}`,
      )
      expect(saved.rules).toHaveLength(1)
      expect(saved.rules[0].taskDefinitionKey).toBe('review')
      expect(saved.rules[0].sourceType).toBe('DEPT_LEADER')
      expect(saved.rules[0].targetId).toBeNull()

      const put = (requests: unknown) =>
        api.envelope(ADMIN, 'put', `/admin/process/processTemplate/approverRules/${id}`, requests)

      expectCodeAndMessage(
        (
          await put([
            { taskDefinitionKey: 'review', sourceType: 'DEPT_LEADER' },
            { taskDefinitionKey: 'review', sourceType: 'ROLE', targetId: 1 },
          ])
        ).body,
        201,
        '重复配置',
        'H1 重复节点',
      )
      expectCodeAndMessage(
        (await put([{ taskDefinitionKey: 'ghost', sourceType: 'DEPT_LEADER' }])).body,
        201,
        '不存在于流程 zip',
        'H1 未知节点',
      )
      expectCodeAndMessage(
        (await put([{ taskDefinitionKey: 'review', sourceType: 'NOBODY' }])).body,
        201,
        '审批人来源类型无效',
        'H1 来源类型无效',
      )
      expectCodeAndMessage(
        (await put([{ taskDefinitionKey: 'review', sourceType: 'DEPT_LEADER', targetId: 1 }])).body,
        201,
        '部门负责人来源按发起人所在部门解析，不指定目标',
        'H1 部门负责人带目标',
      )
      expectCodeAndMessage(
        (await put([{ taskDefinitionKey: 'review', sourceType: 'ROLE' }])).body,
        201,
        '角色不存在',
        'H1 角色缺目标',
      )
    } finally {
      await api.call(ADMIN, 'delete', `/admin/process/processTemplate/remove/${id}`)
    }
  })

  test('H2 审批人规则：已发布冻结（读 200 / 改 201），无授予 205', async () => {
    // publishTemplate 助手造的模板节点 assignee=bob，无规则也可发布；这里只验冻结契约。
    const id = await publishTemplate(api, unique('api-rule-frozen'))
    const read = await api.call<RuleConfig>(
      ADMIN,
      'get',
      `/admin/process/processTemplate/approverRules/${id}`,
    )
    expect(read.userTasks.some((task) => task.key === 'review')).toBe(true)

    expectCodeAndMessage(
      (
        await api.envelope(ADMIN, 'put', `/admin/process/processTemplate/approverRules/${id}`, [
          { taskDefinitionKey: 'review', sourceType: 'DEPT_LEADER' },
        ])
      ).body,
      201,
      '已发布的审批模板不能修改审批人规则',
      'H2 已发布冻结',
    )

    expect(
      await api.code(PLAIN_USER, 'get', `/admin/process/processTemplate/approverRules/${id}`),
    ).toBe(205)
    expect(
      await api.code(PLAIN_USER, 'put', `/admin/process/processTemplate/approverRules/${id}`, []),
    ).toBe(205)
  })

  test('H3 upload 返回 userTasks（BPMN 节点清单）', async () => {
    const processKey = `api${runId()}${Math.random().toString(36).slice(2, 6)}`
    const zip = buildZip([{ name: 'process.bpmn20.xml', data: bpmnXml(processKey) }])
    const upload = await api.uploadProcessZip(ADMIN, { name: `${processKey}.zip`, buffer: zip })
    expectCode(upload.body, 200, 'H3 上传')
    const userTasks = (upload.body.data as unknown as { userTasks: UserTaskRef[] }).userTasks
    expect(userTasks).toHaveLength(1)
    expect(userTasks[0]).toMatchObject({ key: 'review', name: '复核', assignee: 'bob' })
  })

  test('H4 userOptions：admin 可读、启用过滤、无授予 205', async () => {
    const options = await api.call<UserOption[]>(ADMIN, 'get', '/admin/system/sysDept/userOptions')
    expect(options.length).toBeGreaterThan(0)
    for (const option of options) {
      expect(Object.keys(option).sort()).toEqual(['id', 'name', 'username'])
    }
    expect(options.some((option) => option.username === ALICE)).toBe(true)

    // 启用过滤：新建用户可见，停用后消失。
    const created = await createUser(api, unique('api-opt-user'))
    try {
      const withNew = await api.call<UserOption[]>(
        ADMIN,
        'get',
        '/admin/system/sysDept/userOptions',
      )
      expect(withNew.some((option) => option.id === created.id)).toBe(true)
      await api.call(ADMIN, 'put', `/admin/system/sysUser/updateStatus/${created.id}/0`)
      const afterDisable = await api.call<UserOption[]>(
        ADMIN,
        'get',
        '/admin/system/sysDept/userOptions',
      )
      expect(afterDisable.some((option) => option.id === created.id)).toBe(false)
    } finally {
      await deleteUser(api, created.id)
    }

    expect(await api.code(PLAIN_USER, 'get', '/admin/system/sysDept/userOptions')).toBe(205)
    expect(await api.code(ALICE, 'get', '/admin/system/sysDept/userOptions')).toBe(205)
  })

  test('H5 转办：目标合法性逐条 201，非当前处理人 201，转办后目标成为处理人', async () => {
    const bobId = await userIdOf(BOB)
    const carolId = await userIdOf(CAROL)
    const aliceId = await userIdOf(ALICE)
    const process = await startApproval(api, ALICE)

    // actionTargets：除申请人外的启用用户，只带 id 与 name。
    const targets = await api.call<{ id: number; name: string }[]>(
      BOB,
      'get',
      `/admin/process/actionTargets/${process.id}`,
    )
    expect(targets.length).toBeGreaterThan(0)
    expect(targets.some((target) => target.id === bobId)).toBe(true)
    expect(
      targets.some((target) => target.id === aliceId),
      '排除申请人',
    ).toBe(false)
    for (const target of targets) {
      expect(Object.keys(target).sort()).toEqual(['id', 'name'])
    }

    const transfer = (data: unknown) =>
      api.envelope(BOB, 'post', `/admin/process/transfer/${process.id}`, data)
    expectCodeAndMessage(
      (await transfer({ targetUserId: bobId })).body,
      201,
      '不能转办给自己',
      'H5 转办给自己',
    )
    expectCodeAndMessage(
      (await transfer({ targetUserId: await userIdOf(ALICE) })).body,
      201,
      '不能转办给申请人',
      'H5 转办给申请人',
    )
    const disabled = await createUser(api, unique('api-tr-disabled'))
    try {
      await api.call(ADMIN, 'put', `/admin/system/sysUser/updateStatus/${disabled.id}/0`)
      expectCodeAndMessage(
        (await transfer({ targetUserId: disabled.id })).body,
        201,
        '转办目标不存在或已停用',
        'H5 目标停用',
      )
    } finally {
      await deleteUser(api, disabled.id)
    }
    expectCodeAndMessage(
      (
        await api.envelope(CAROL, 'post', `/admin/process/transfer/${process.id}`, {
          targetUserId: bobId,
        })
      ).body,
      201,
      '只有当前处理人可以转办',
      'H5 非当前处理人',
    )

    // 正例：bob → carol。carol 成为当前处理人，bob 的待处理行消失。
    await api.call(BOB, 'post', `/admin/process/transfer/${process.id}`, { targetUserId: carolId })
    const carolTask = await pendingRow(CAROL, process.id)
    expect(carolTask.taskId).toBeTruthy()
    // 转办后原处理人不再是处理人：bob 再转 201。
    expectCodeAndMessage(
      (await transfer({ targetUserId: carolId })).body,
      201,
      '只有当前处理人可以转办',
      'H5 转办后原处理人失格',
    )

    // 转办经手关系之外的人读不到（403 家族属办事可见性契约，此处只证 carol 可处理）。
    await api.call(CAROL, 'post', '/admin/process/approve', {
      processId: process.id,
      taskId: carolTask.taskId,
      status: 1,
    })
    const after = await api.call<{ process: ProcessRecord }>(
      ALICE,
      'get',
      `/admin/process/show/${process.id}`,
    )
    expect(after.process.status).toBe(2)
  })

  test('H6 加签：目标校验逐条 201，前加签挂起原任务，加签人处理后回到原处理人', async () => {
    const bobId = await userIdOf(BOB)
    const carolId = await userIdOf(CAROL)
    const process = await startApproval(api, ALICE)

    const addSign = (data: unknown) =>
      api.envelope(BOB, 'post', `/admin/process/addSign/${process.id}`, data)
    expectCodeAndMessage(
      (await addSign({ signType: 3, assigneeIds: [carolId] })).body,
      201,
      '加签类型只能为 1（前加签）或 2（后加签）',
      'H6 类型非法',
    )
    expectCodeAndMessage(
      (await addSign({ signType: 1, assigneeIds: [] })).body,
      201,
      '加签人不能为空',
      'H6 空加签人',
    )
    expectCodeAndMessage(
      (await addSign({ signType: 1, assigneeIds: [bobId] })).body,
      201,
      '不能加签给自己',
      'H6 加签给自己',
    )
    expectCodeAndMessage(
      (await addSign({ signType: 1, assigneeIds: [await userIdOf(ALICE)] })).body,
      201,
      '不能加签给申请人',
      'H6 加签给申请人',
    )
    expectCodeAndMessage(
      (await addSign({ signType: 1, assigneeIds: [999999] })).body,
      201,
      '加签人不存在或已停用',
      'H6 加签人不存在',
    )
    expectCodeAndMessage(
      (
        await api.envelope(CAROL, 'post', `/admin/process/addSign/${process.id}`, {
          signType: 1,
          assigneeIds: [bobId],
        })
      ).body,
      201,
      '只有当前处理人可以加签',
      'H6 非当前处理人',
    )

    // 正例：bob 前加签 carol → carol 收到加签任务行，bob 的引擎任务挂起。
    await api.call(BOB, 'post', `/admin/process/addSign/${process.id}`, {
      signType: 1,
      assigneeIds: [carolId],
    })
    const signRow = await pendingRow(CAROL, process.id)
    expect(signRow.taskId).toBeTruthy()
    // 挂起中的引擎任务不能转办（#288）：加签处理中 201。
    expectCodeAndMessage(
      (
        await api.envelope(BOB, 'post', `/admin/process/transfer/${process.id}`, {
          targetUserId: carolId,
        })
      ).body,
      201,
      '加签处理中',
      'H6 挂起中不能转办',
    )

    // 加签人通过 → 原处理人回来 → 通过收口。
    await api.call(CAROL, 'post', '/admin/process/approve', {
      processId: process.id,
      taskId: signRow.taskId,
      status: 1,
    })
    const backTask = await pendingRow(BOB, process.id)
    await api.call(BOB, 'post', '/admin/process/approve', {
      processId: process.id,
      taskId: backTask.taskId,
      status: 1,
    })
    const after = await api.call<{ process: ProcessRecord }>(
      ALICE,
      'get',
      `/admin/process/show/${process.id}`,
    )
    expect(after.process.status).toBe(2)
  })

  test('H7 撤回：非申请人 201、申请人审批中可撤、重复撤回 201、已通过不可撤', async () => {
    const process = await startApproval(api, ALICE)
    expectCodeAndMessage(
      (await api.envelope(BOB, 'post', `/admin/process/withdraw/${process.id}`)).body,
      201,
      '只有申请人可以撤回审批',
      'H7 非申请人',
    )
    expectCodeAndMessage(
      (await api.envelope(ADMIN, 'post', `/admin/process/withdraw/${process.id}`)).body,
      201,
      '只有申请人可以撤回审批',
      'H7 管理员也非申请人',
    )

    await api.call(ALICE, 'post', `/admin/process/withdraw/${process.id}`)
    const after = await api.call<{ process: ProcessRecord }>(
      ALICE,
      'get',
      `/admin/process/show/${process.id}`,
    )
    expect(after.process.status).toBe(-2)

    expectCodeAndMessage(
      (await api.envelope(ALICE, 'post', `/admin/process/withdraw/${process.id}`)).body,
      201,
      '只有审批中的审批可以撤回',
      'H7 重复撤回',
    )

    const approved = await startApproval(api, ALICE)
    const task = await pendingRow(BOB, approved.id)
    await api.call(BOB, 'post', '/admin/process/approve', {
      processId: approved.id,
      taskId: task.taskId,
      status: 1,
    })
    expectCodeAndMessage(
      (await api.envelope(ALICE, 'post', `/admin/process/withdraw/${approved.id}`)).body,
      201,
      '只有审批中的审批可以撤回',
      'H7 已通过不可撤',
    )
  })

  test('H8 催办：非申请人 201、申请人可催、24 小时频控 201', async () => {
    const process = await startApproval(api, ALICE)
    expectCodeAndMessage(
      (await api.envelope(BOB, 'post', `/admin/process/urge/${process.id}`)).body,
      201,
      '只有申请人可以催办审批',
      'H8 非申请人',
    )

    await api.call(ALICE, 'post', `/admin/process/urge/${process.id}`)
    // 频控按账本最近一次 URGE 判定：同一审批 24 小时内第二次 201。
    expectCodeAndMessage(
      (await api.envelope(ALICE, 'post', `/admin/process/urge/${process.id}`)).body,
      201,
      '24 小时内已催办过',
      'H8 频控',
    )

    // 催办不改状态；bob 待处理行带已催办标记（urged=true，#287/#288 三源合成）。
    const status = await poll(10_000, 300, async () => {
      const row = await api
        .call<PageResult<ProcessRecord>>(BOB, 'get', '/admin/process/findPending/1/100')
        .then((page) => page.records.find((record) => record.id === process.id))
      return row?.urged === true ? true : undefined
    })
    expect(status, 'bob 的待处理行应带已催办标记').toBe(true)
    const detail = await api.call<{ process: ProcessRecord }>(
      ALICE,
      'get',
      `/admin/process/show/${process.id}`,
    )
    expect(detail.process.status).toBe(1)
  })

  test('H9 匿名访问新动作端点：208 续期信封', async () => {
    expect((await api.anonymous('post', '/admin/process/withdraw/1')).body.code).toBe(208)
    expect(
      (await api.anonymous('post', '/admin/process/transfer/1', { targetUserId: 1 })).body.code,
    ).toBe(208)
    expect((await api.anonymous('get', '/admin/process/actionTargets/1')).body.code).toBe(208)
  })
})
