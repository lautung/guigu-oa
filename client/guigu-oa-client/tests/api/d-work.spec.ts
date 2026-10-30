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
  demoTemplate,
  expectCode,
  expectCodeAndMessage,
  startApproval,
  unique,
  type PageResult,
  type ProcessRecord,
  type RecipientRecord,
  type StartableGroup,
  type TemplateRecord,
  type UserRecord,
} from './helpers/api'

/**
 * D 办事（#120 §6 D1–D11）。
 *
 * 成员矩阵按 ADR 0011：申请人可读可补传不可处理、当前处理人可读可上传可处理、
 * 抄送人只读、历史处理人审批后只读；非成员 403（办事）或 205（附件）。
 *
 * 发起与流转是快照组（#120 §5）：本轮造两条审批，一条留给详情/抄送断言，
 * 一条由 bob 审批掉。已发起/已处理列表会累积，断言只用「标记存在」而不是总数。
 */

let api: Api
let context: APIRequestContext
let pendingProcess: ProcessRecord
let approveProcess: ProcessRecord

test.beforeAll(async ({ playwright }) => {
  context = await playwright.request.newContext()
  api = new Api(context)
  pendingProcess = await startApproval(api, ALICE)
  approveProcess = await startApproval(api, ALICE)
})

test.afterAll(async () => {
  await context.dispose()
})

async function pendingTask(processId: number): Promise<ProcessRecord> {
  for (let page = 1; page <= 20; page += 1) {
    const result = await api.call<PageResult<ProcessRecord>>(
      BOB,
      'get',
      `/admin/process/findPending/${page}/100`,
    )
    const found = result.records.find((record) => record.id === processId)
    if (found) return found
    if (page * 100 >= result.total) break
  }
  throw new Error(`bob 的待处理里找不到审批 ${processId}`)
}

async function countMarkedStarted(text: string): Promise<number> {
  let count = 0
  for (let page = 1; page <= 20; page += 1) {
    const result = await api.call<PageResult<ProcessRecord>>(
      ALICE,
      'get',
      `/admin/process/findStarted/${page}/100`,
    )
    count += result.records.filter((record) => (record.formValues ?? '').includes(text)).length
    if (page * 100 >= result.total) break
  }
  return count
}

async function aliceUserId(): Promise<number> {
  const page = await api.call<PageResult<UserRecord>>(
    ADMIN,
    'get',
    '/admin/system/sysUser/1/50?keyword=alice',
  )
  const alice = page.records.find((record) => record.username === ALICE)
  if (!alice) throw new Error('找不到 alice')
  return alice.id
}

test.describe.serial('D 办事', () => {
  test('D1 findPending：bob 有记录 / alice 空', async () => {
    const bobPending = await api.call<PageResult<ProcessRecord>>(
      BOB,
      'get',
      '/admin/process/findPending/1/5',
    )
    expect(bobPending.total).toBeGreaterThan(0)
    expect(bobPending.records[0].taskId).toBeTruthy()

    const alicePending = await api.call<PageResult<ProcessRecord>>(
      ALICE,
      'get',
      '/admin/process/findPending/1/5',
    )
    expect(alicePending.total).toBe(0)
  })

  test('D2 startUp：正例已发起 / 未发布模板 201 / 抄送人无效 201', async () => {
    const template = await demoTemplate(api)
    expect(pendingProcess.status).toBe(1)
    const task = await pendingTask(pendingProcess.id)
    expect(task.taskId, 'D2 正例：bob 收到待处理任务').toBeTruthy()

    const unpublishedId = await createUnpublishedTemplate(api, unique('api-unpublished-start'))
    try {
      const unpublished = await api.envelope(ALICE, 'post', '/admin/process/startUp', {
        processTemplateId: unpublishedId,
        processTypeId: template.processTypeId,
        formValues: '{"formData":{}}',
      })
      expectCodeAndMessage(unpublished.body, 201, '未发布的审批模板不能发起', 'D2 未发布模板')

      const ghostCc = await api.envelope(ALICE, 'post', '/admin/process/startUp', {
        processTemplateId: template.id,
        processTypeId: template.processTypeId,
        formValues: '{"formData":{}}',
        ccUserIds: [999999],
      })
      expectCodeAndMessage(ghostCc.body, 201, '抄送人不存在或已停用', 'D2 抄送人不存在')

      const disabled = await createUser(api, unique('api-cc-disabled'))
      try {
        await api.call(ADMIN, 'put', `/admin/system/sysUser/updateStatus/${disabled.id}/0`)
        const disabledCc = await api.envelope(ALICE, 'post', '/admin/process/startUp', {
          processTemplateId: template.id,
          processTypeId: template.processTypeId,
          formValues: '{"formData":{}}',
          ccUserIds: [disabled.id],
        })
        expectCodeAndMessage(disabledCc.body, 201, '抄送人不存在或已停用', 'D2 抄送人停用')
      } finally {
        await deleteUser(api, disabled.id)
      }
    } finally {
      await api.call(ADMIN, 'delete', `/admin/process/processTemplate/remove/${unpublishedId}`)
    }
  })

  test('D2b startUp：同一提交标识重复送达只产生一条审批', async () => {
    const template = await demoTemplate(api)
    const text = unique('api-idem')
    const body = {
      processTemplateId: template.id,
      processTypeId: template.processTypeId,
      formValues: JSON.stringify({
        formData: { reason: text, days: 1 },
        formShowData: { 请假原因: text, 请假天数: 1 },
      }),
      attachmentIds: [],
      idempotencyKey: text,
    }
    const [first, second] = await Promise.all([
      api.envelope(ALICE, 'post', '/admin/process/startUp', body),
      api.envelope(ALICE, 'post', '/admin/process/startUp', body),
    ])
    expectCode(first.body, 200, 'D2b 第一次发起')
    expectCode(second.body, 200, 'D2b 同键重复送达')
    expect(await countMarkedStarted(text), 'D2b 已发起只多一条').toBe(1)
  })

  test('D3 getProcessTemplate：已发布可读 / 未发布返回 null', async () => {
    const template = await demoTemplate(api)
    const published = await api.call<TemplateRecord>(
      PLAIN_USER,
      'get',
      `/admin/process/getProcessTemplate/${template.id}`,
    )
    expect(published.id).toBe(template.id)

    const unpublishedId = await createUnpublishedTemplate(api, unique('api-unpublished-read'))
    try {
      const hidden = await api.call<TemplateRecord | null>(
        PLAIN_USER,
        'get',
        `/admin/process/getProcessTemplate/${unpublishedId}`,
      )
      expect(hidden, 'D3 未发布模板不暴露').toBeNull()
    } finally {
      await api.call(ADMIN, 'delete', `/admin/process/processTemplate/remove/${unpublishedId}`)
    }

    const anonymous = await api.anonymous('get', `/admin/process/getProcessTemplate/${template.id}`)
    expectCode(anonymous.body, 208, 'D3 无 token')
  })

  test('D4 findStartable：只含已发布且可用', async () => {
    const groups = await api.call<StartableGroup[]>(ALICE, 'get', '/admin/process/findStartable')
    const templates = groups.flatMap((group) => group.processTemplateList ?? [])
    expect(templates.some((template) => template.name === '演示请假申请')).toBe(true)
    expect(templates.every((template) => template.availableForInitiation !== false)).toBe(true)

    const anonymous = await api.anonymous('get', '/admin/process/findStartable')
    expectCode(anonymous.body, 208, 'D4 无 token')
  })

  test('D5 show：申请人/处理人/抄送人可读，非成员 403', async () => {
    const aliceView = await api.call<{ isApprove: boolean; process: ProcessRecord }>(
      ALICE,
      'get',
      `/admin/process/show/${pendingProcess.id}`,
    )
    expect(aliceView.isApprove).toBe(false)
    expect(aliceView.process.id).toBe(pendingProcess.id)

    const bobView = await api.call<{ isApprove: boolean; process: ProcessRecord }>(
      BOB,
      'get',
      `/admin/process/show/${pendingProcess.id}`,
    )
    expect(bobView.isApprove).toBe(true)

    const carolView = await api.call<{ isApprove: boolean }>(
      CAROL,
      'get',
      `/admin/process/show/${pendingProcess.id}`,
    )
    expect(carolView.isApprove).toBe(false)

    expect(await api.code(PLAIN_USER, 'get', `/admin/process/show/${pendingProcess.id}`)).toBe(403)
    expect(await api.code(ADMIN, 'get', `/admin/process/show/${pendingProcess.id}`)).toBe(403)
  })

  test('D6 approve：当前处理人通过 / 非当前处理人 403', async () => {
    const task = await pendingTask(approveProcess.id)
    await api.call(BOB, 'post', '/admin/process/approve', {
      processId: approveProcess.id,
      taskId: task.taskId,
      status: 1,
    })

    const after = await api.call<{ isApprove: boolean; process: ProcessRecord }>(
      BOB,
      'get',
      `/admin/process/show/${approveProcess.id}`,
    )
    expect(after.process.status).toBe(2)
    expect(after.isApprove).toBe(false)

    const pending = await pendingTask(pendingProcess.id)
    const denied = await api.envelope(ALICE, 'post', '/admin/process/approve', {
      processId: pendingProcess.id,
      taskId: pending.taskId,
      status: 1,
    })
    expectCode(denied.body, 403, 'D6 非当前处理人')
  })

  test('D7 findProcessed：bob 正例', async () => {
    const page = await api.call<PageResult<ProcessRecord>>(
      BOB,
      'get',
      '/admin/process/findProcessed/1/5',
    )
    expect(page.total).toBeGreaterThan(0)
  })

  test('D8 findCarbonCopied：carol 正例', async () => {
    const page = await api.call<PageResult<ProcessRecord>>(
      CAROL,
      'get',
      '/admin/process/findCarbonCopied/1/5',
    )
    expect(page.total).toBeGreaterThan(0)
  })

  test('D9 carbonCopyRecipients：普通用户可读，只含 id 与 name', async () => {
    const recipients = await api.call<RecipientRecord[]>(
      PLAIN_USER,
      'get',
      '/admin/process/carbonCopyRecipients',
    )
    expect(recipients.length).toBeGreaterThan(0)
    for (const recipient of recipients) {
      expect(Object.keys(recipient).sort()).toEqual(['id', 'name'])
    }
  })

  test('D10 findStarted：alice 只看到本人，userId 参数被忽略', async () => {
    const aliceId = await aliceUserId()
    const page = await api.call<PageResult<ProcessRecord>>(
      ALICE,
      'get',
      '/admin/process/findStarted/1/100',
    )
    expect(page.total).toBeGreaterThan(0)
    expect(page.records.every((record) => record.userId === aliceId)).toBe(true)
    // ADR 0031：公共字段由应用填，已发起列表的创建时间不能为空（曾经整列为空）。
    expect(page.records.every((record) => !!record.createTime)).toBe(true)

    const overridden = await api.call<PageResult<ProcessRecord>>(
      ALICE,
      'get',
      '/admin/process/findStarted/1/100?userId=1',
    )
    expect(overridden.records.every((record) => record.userId === aliceId)).toBe(true)
  })

  test('D11 getCurrentUser：只含 name 与 phone', async () => {
    const current = await api.call<Record<string, unknown>>(
      ALICE,
      'get',
      '/admin/process/getCurrentUser',
    )
    expect(Object.keys(current).sort()).toEqual(['name', 'phone'])
    expect(current.name).toBeTruthy()
  })
})
