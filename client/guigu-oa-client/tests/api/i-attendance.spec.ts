import { test, expect, type APIRequestContext } from '@playwright/test'

import {
  ADMIN,
  ALICE,
  Api,
  BOB,
  CAROL,
  PLAIN_USER,
  assignMenus,
  assignRoles,
  createRole,
  createUser,
  deleteRole,
  deleteUser,
  expectCode,
  expectCodeAndMessage,
  flattenMenus,
  unique,
  type MenuRecord,
  type PageResult,
  type ProcessRecord,
  type UserRecord,
} from './helpers/api'

/**
 * I 考勤（#284，ADR 0073）：演示种子 + 考勤新端点的接口契约。
 *
 * 覆盖：办事面（今日打卡/打卡/当月记录/余额/我的请假与加班）只读本人；
 * 后台面（打卡记录/补卡/假期类型/发放流水/月统计与下钻）的页面授予与数据范围；
 * 业务审批映射（attendance.leave / attendance.overtime）提交、幂等重放、
 * 部门负责人审批与终态回调（余额扣减、加班入账、月统计变化）；
 * 401/404/405 容器语义与越权 205/201/403 形态。
 *
 * 造数：演示种子提供班次/考勤组/年假余额/模板映射；请假日期动态选当月一个
 * 未被本人请假覆盖的工作日（避开今天，避免与 e2e 主路径抢同一天），先用补卡
 * 把该日做成「已出勤」，审批通过后月统计的实出勤应恰好减 1。所有名称/事由带
 * runId 标记，两轮连跑不互相污染。
 */

interface TodayRecord {
  date: string
  weekday: number
  dayType: number
  workday: boolean
  hasGroup: boolean
  groupId?: number
  groupName?: string
  shift?: {
    id: number
    name: string
    startTime: string
    endTime: string
    lunchStart?: string
    lunchEnd?: string
    graceMinutes: number
  }
  clockInTime?: string | null
  clockOutTime?: string | null
}

interface DayStatusRecord {
  date: string
  weekday: number
  dayType: number
  workday: boolean
  status?: number | null
  leaveTypeName?: string | null
  pending?: boolean
  clockInTime?: string | null
  clockOutTime?: string | null
}

interface MonthRecord {
  month: string
  days: DayStatusRecord[]
}

interface LeaveTypeRecord {
  id: number
  name: string
  balanceRequired: number
  overtimeCredit: number
  status: number
  sortNo: number
  remark?: string
}

interface BalanceRecord {
  leaveTypeId: number
  leaveTypeName: string
  status: number
  grantedHours: number
  creditedHours: number
  deductedHours: number
  pendingHours: number
  availableHours: number
}

interface ApprovalSummaryRecord {
  processId: number
  status: number
  currentApprover?: string | null
  description?: string | null
}

interface LeaveRecord {
  id: number
  leaveTypeId: number
  leaveTypeName: string
  startDate: string
  endDate: string
  days: number
  hours: number
  reason?: string | null
  status: number
  approval?: ApprovalSummaryRecord | null
}

interface OvertimeRecord {
  id: number
  overtimeDate: string
  startTime: string
  endTime: string
  hours: number
  reason?: string | null
  status: number
  approval?: ApprovalSummaryRecord | null
}

interface HolidayRecord {
  id: number
  holidayDate: string
  name: string
  dayType: number
}

interface PunchRecord {
  id: number
  userId: number
  username: string
  name: string
  punchDate: string
  slot: number
  punchTime: string
  source: number
  operatorName?: string | null
}

interface LedgerRecord {
  id: number
  userId: number
  username: string
  leaveTypeId: number
  leaveTypeName: string
  entryType: number
  changeHours: number
  businessType?: string | null
  businessId?: number | null
  operatorName?: string | null
  remark?: string | null
}

interface StatRecord {
  userId: number
  username: string
  name: string
  requiredDays: number
  actualDays: number
  lateCount: number
  earlyCount: number
  missingDays: number
  leaveDays: { leaveTypeName: string; days: number }[]
  overtimeHours: number
}

const DEDUCT = 2
const OVERTIME_CREDIT = 3
const APPROVED = 2

let api: Api
let context: APIRequestContext
let aliceId: number
let yearLeaveType: LeaveTypeRecord
let noBalanceType: LeaveTypeRecord
let today: TodayRecord
let month: string
let leaveDate: string
let statBeforeLeave: StatRecord

function currentMonth(date: string): string {
  return date.slice(0, 7)
}

function roundHours(value: number): number {
  return Math.round(Number(value) * 100) / 100
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

/**
 * 把 alice 放回演示部门：e2e 的部门负责人用例会把 alice 划到临时部门且不恢复，
 * 接口套件后跑时仍要保证「alice 提交 → 部门负责人 bob」这条链成立。
 */
async function ensureAliceInDemoDepartment(): Promise<void> {
  interface DeptNode {
    id: number
    name: string
    children?: DeptNode[]
  }
  const tree = await api.call<DeptNode[]>(ADMIN, 'get', '/admin/system/sysDept/findTree')
  const found: DeptNode[] = []
  const walk = (nodes: DeptNode[]): void => {
    for (const node of nodes) {
      if (node.name === '演示部门') found.push(node)
      if (node.children?.length) walk(node.children)
    }
  }
  walk(tree)
  if (found.length === 0) throw new Error('演示种子缺少演示部门')
  await api.call(ADMIN, 'put', '/admin/system/sysUser/update', {
    id: aliceId,
    deptId: found[0].id,
  })
}

/** 演示种子把 alice 放进了 9:00–18:00 的演示考勤组；周末/休假日则补一条补班日。 */
async function ensureTodayWorkday(): Promise<TodayRecord> {
  const current = await api.call<TodayRecord>(ALICE, 'get', '/admin/attendance/my/today')
  expect(current.hasGroup, '演示种子应把 alice 放进演示考勤组').toBe(true)
  expect(current.groupName).toBe('演示考勤组')
  expect(current.shift?.startTime).toContain('09:00')
  expect(current.shift?.endTime).toContain('18:00')
  if (current.workday) return current

  const page = await api.call<PageResult<HolidayRecord>>(
    ADMIN,
    'get',
    '/admin/attendance/holiday/1/200',
  )
  const existing = page.records.find((record) => record.holidayDate === current.date)
  if (existing) {
    await api.call(ADMIN, 'put', '/admin/attendance/holiday/update', {
      id: existing.id,
      holidayDate: existing.holidayDate,
      name: existing.name,
      dayType: 2,
    })
  } else {
    await api.call(ADMIN, 'post', '/admin/attendance/holiday/save', {
      holidayDate: current.date,
      name: '接口测试补班',
      dayType: 2,
    })
  }
  const updated = await api.call<TodayRecord>(ALICE, 'get', '/admin/attendance/my/today')
  expect(updated.workday, '补班日后今天应可打卡').toBe(true)
  return updated
}

/** 当月未被本人请假覆盖的工作日（避开今天，留给 e2e 主路径）。 */
async function chooseLeaveDate(): Promise<string> {
  const records = await api.call<MonthRecord>(
    ALICE,
    'get',
    `/admin/attendance/my/records?month=${month}`,
  )
  const leaves = await api.call<PageResult<LeaveRecord>>(
    ALICE,
    'get',
    '/admin/attendance/my/leave/1/200',
  )
  const active = leaves.records.filter(
    (record) => record.status === 1 || record.status === APPROVED,
  )
  const covered = (date: string): boolean =>
    active.some((record) => record.startDate <= date && record.endDate >= date)
  const candidate = records.days.find(
    (day) => day.workday && day.date !== today.date && !covered(day.date),
  )
  if (!candidate) {
    throw new Error('当月没有可用于接口套件的空闲工作日（已被请假覆盖或只剩今天）')
  }
  return candidate.date
}

async function statRow(): Promise<StatRecord> {
  const page = await api.call<PageResult<StatRecord>>(
    ADMIN,
    'get',
    `/admin/attendance/stat/1/200?month=${month}`,
  )
  const row = page.records.find((record) => record.username === ALICE)
  if (!row) throw new Error('月统计里找不到 alice（演示考勤组成员）')
  return row
}

function leaveDaysOf(row: StatRecord, typeName: string): number {
  return row.leaveDays.find((leave) => leave.leaveTypeName === typeName)?.days ?? 0
}

async function pendingRow(processId: number): Promise<ProcessRecord | undefined> {
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
  return undefined
}

async function approveAsBob(processId: number): Promise<void> {
  const pending = await pendingRow(processId)
  if (!pending?.taskId) throw new Error(`bob 的待处理里找不到审批 ${processId}`)
  await api.call(BOB, 'post', '/admin/process/approve', {
    processId,
    taskId: pending.taskId,
    status: 1,
  })
}

function newKey(tag: string): string {
  return `${tag}-${Date.now()}-${Math.random().toString(16).slice(2)}`
}

test.beforeAll(async ({ playwright }) => {
  context = await playwright.request.newContext()
  api = new Api(context)
  aliceId = await userIdOf(ALICE)
  await ensureAliceInDemoDepartment()
  today = await ensureTodayWorkday()
  month = currentMonth(today.date)
  leaveDate = await chooseLeaveDate()

  const types = await api.call<LeaveTypeRecord[]>(
    ADMIN,
    'get',
    '/admin/attendance/leave-type/options',
  )
  const year = types.find((record) => record.name === '年假')
  if (!year) throw new Error('演示种子缺少年假类型')
  yearLeaveType = year
  const noBalance = types.find((record) => record.balanceRequired === 0 && record.status === 1)
  if (!noBalance) throw new Error('演示种子缺少不限余额的启用假期类型')
  noBalanceType = noBalance

  // 把选中的工作日先补成已出勤：审批通过后这一天从「出勤」变「请假」，
  // 实出勤才会恰好减 1（若原本就是缺卡，缺卡与请假互换，实出勤不变）。
  await api.call(ADMIN, 'post', '/admin/attendance/repair/save', {
    userId: aliceId,
    repairDate: leaveDate,
    clockInTime: '09:00:00',
    clockOutTime: '18:00:00',
  })
})

test.afterAll(async () => {
  await context.dispose()
})

test.describe.serial('I 考勤', () => {
  test('I1 认证与容器语义：401 / 404 / 405 / 无授予 205', async () => {
    const anonymousToday = await api.anonymous('get', '/admin/attendance/my/today')
    expect(anonymousToday.status).toBe(401)
    expect(anonymousToday.body.code).toBe(208)

    const anonymousPunch = await api.anonymous('get', '/admin/attendance/punch/1/5')
    expect(anonymousPunch.status).toBe(401)
    expect(anonymousPunch.body.code).toBe(208)

    const missing = await api.envelope(ADMIN, 'get', '/admin/attendance/no-such-endpoint')
    expect(missing.status, '未知路径是容器级 404').toBe(404)
    expect(missing.body.code).toBe(201)
    expect(missing.body.message).toContain('不存在')

    const wrongMethod = await api.envelope(ADMIN, 'get', '/admin/attendance/repair/save')
    expect(wrongMethod.status, 'POST-only 端点上的 GET 是容器级 405').toBe(405)
    expect(wrongMethod.body.code).toBe(201)

    expect(await api.code(PLAIN_USER, 'get', '/admin/attendance/punch/1/5')).toBe(205)
    expect(await api.code(PLAIN_USER, 'get', '/admin/attendance/leave-type/1/5')).toBe(205)
    expect(await api.code(PLAIN_USER, 'get', '/admin/attendance/ledger/1/5')).toBe(205)
    expect(await api.code(PLAIN_USER, 'get', '/admin/attendance/repair/1/5')).toBe(205)
    expect(await api.code(PLAIN_USER, 'get', '/admin/attendance/stat/1/5')).toBe(205)
    expect(await api.code(ALICE, 'get', '/admin/attendance/stat/1/5')).toBe(205)
  })

  test('I2 今日打卡：班次摘要 / 上下班打卡 / 重复打卡追加流水', async () => {
    expect(today.hasGroup).toBe(true)
    expect(today.workday).toBe(true)
    expect(today.groupName).toBe('演示考勤组')

    const afterIn = await api.call<TodayRecord>(ALICE, 'post', '/admin/attendance/my/punch/in')
    expect(afterIn.clockInTime, '上班打卡后今日状态带有效上班时刻').toBeTruthy()

    const afterOut = await api.call<TodayRecord>(ALICE, 'post', '/admin/attendance/my/punch/out')
    expect(afterOut.clockOutTime, '下班打卡后今日状态带有效下班时刻').toBeTruthy()

    const repeated = await api.call<TodayRecord>(ALICE, 'post', '/admin/attendance/my/punch/in')
    expect(repeated.clockInTime).toBeTruthy()

    const page = await api.call<PageResult<PunchRecord>>(
      ADMIN,
      'get',
      `/admin/attendance/punch/1/100?name=${encodeURIComponent(ALICE)}` +
        `&beginDate=${today.date}&endDate=${today.date}`,
    )
    const mine = page.records.filter((record) => record.userId === aliceId && record.source === 1)
    expect(mine.length).toBeGreaterThanOrEqual(3)
  })

  test('I3 我的请假：草稿/修改/提交幂等/不同键拒绝/审批通过扣减', async () => {
    // 基线在 I2 打卡之后取：今天的状态变化不再混入「请假前后」的差值。
    statBeforeLeave = await statRow()
    const reason = unique('api-leave')
    const draft = await api.call<LeaveRecord>(ALICE, 'post', '/admin/attendance/my/leave/save', {
      leaveTypeId: yearLeaveType.id,
      startDate: leaveDate,
      endDate: leaveDate,
      reason,
    })
    expect(draft.status).toBe(0)
    expect(draft.days).toBe(0)
    expect(draft.hours).toBe(0)

    const updated = await api.call<LeaveRecord>(ALICE, 'put', '/admin/attendance/my/leave/update', {
      id: draft.id,
      leaveTypeId: yearLeaveType.id,
      startDate: leaveDate,
      endDate: leaveDate,
      reason: `${reason}改`,
    })
    expect(updated.reason).toBe(`${reason}改`)

    const balancesBefore = await api.call<BalanceRecord[]>(
      ALICE,
      'get',
      '/admin/attendance/my/balances',
    )
    const availableBefore = roundHours(
      balancesBefore.find((balance) => balance.leaveTypeId === yearLeaveType.id)?.availableHours ??
        0,
    )

    const key = newKey('leave')
    const submitted = await api.call<LeaveRecord>(
      ALICE,
      'post',
      `/admin/attendance/my/leave/${draft.id}/submit`,
      { idempotencyKey: key },
    )
    expect(submitted.status).toBe(1)
    expect(submitted.days).toBe(1)
    expect(roundHours(submitted.hours)).toBe(8)
    const processId = submitted.approval?.processId
    expect(processId, '提交应回带最近一条审批摘要').toBeTruthy()

    const replayed = await api.call<LeaveRecord>(
      ALICE,
      'post',
      `/admin/attendance/my/leave/${draft.id}/submit`,
      { idempotencyKey: key },
    )
    expect(replayed.approval?.processId, '同幂等键重放返回同一条审批').toBe(processId)

    const otherKey = await api.envelope(
      ALICE,
      'post',
      `/admin/attendance/my/leave/${draft.id}/submit`,
      { idempotencyKey: newKey('other') },
    )
    expectCodeAndMessage(otherKey.body, 201, '单据已在审批中', 'I3 不同键二次提交')

    const removeSubmitted = await api.envelope(
      ALICE,
      'delete',
      `/admin/attendance/my/leave/remove/${draft.id}`,
    )
    expectCodeAndMessage(removeSubmitted.body, 201, '当前状态不能修改或删除', 'I3 审批中删除')

    expect(await api.code(CAROL, 'get', `/admin/process/show/${processId}`)).toBe(403)
    expect(await api.code(ALICE, 'get', `/admin/process/show/${processId}`)).toBe(200)

    await approveAsBob(processId as number)

    const detail = await api.call<LeaveRecord>(
      ALICE,
      'get',
      `/admin/attendance/my/leave/${draft.id}`,
    )
    expect(detail.status).toBe(APPROVED)
    expect(detail.approval?.status).toBe(APPROVED)

    const balancesAfter = await api.call<BalanceRecord[]>(
      ALICE,
      'get',
      '/admin/attendance/my/balances',
    )
    const availableAfter = roundHours(
      balancesAfter.find((balance) => balance.leaveTypeId === yearLeaveType.id)?.availableHours ??
        0,
    )
    expect(availableAfter, '审批通过扣减 8 小时年假').toBe(availableBefore - 8)

    const ledger = await api.call<PageResult<LedgerRecord>>(
      ADMIN,
      'get',
      `/admin/attendance/ledger/1/200?leaveTypeId=${yearLeaveType.id}`,
    )
    const deduction = ledger.records.find(
      (record) =>
        record.userId === aliceId &&
        record.businessType === 'attendance.leave' &&
        record.businessId === draft.id &&
        record.entryType === DEDUCT,
    )
    expect(deduction, '审批通过应写一条扣减流水').toBeTruthy()
    expect(roundHours(deduction?.changeHours ?? 0)).toBe(-8)

    const statAfter = await statRow()
    expect(leaveDaysOf(statAfter, '年假'), '月统计出现请假天数').toBe(
      leaveDaysOf(statBeforeLeave, '年假') + 1,
    )
    expect(statAfter.actualDays, '实出勤恰好减 1').toBe(statBeforeLeave.actualDays - 1)

    const daily = await api.call<MonthRecord>(
      ADMIN,
      'get',
      `/admin/attendance/stat/daily?userId=${aliceId}&month=${month}`,
    )
    const day = daily.days.find((item) => item.date === leaveDate)
    expect(day?.status).toBe(6)
    expect(day?.leaveTypeName).toBe('年假')
  })

  test('I4 我的加班：提交即发起 / 审批通过写入调休', async () => {
    const reason = unique('api-overtime')
    const draft = await api.call<OvertimeRecord>(
      ALICE,
      'post',
      '/admin/attendance/my/overtime/save',
      {
        overtimeDate: today.date,
        startTime: '18:00:00',
        endTime: '20:00:00',
        reason,
      },
    )
    expect(draft.status).toBe(0)

    const balancesBefore = await api.call<BalanceRecord[]>(
      ALICE,
      'get',
      '/admin/attendance/my/balances',
    )
    const creditType = balancesBefore.find((balance) => balance.leaveTypeName === '调休')
    expect(creditType, '演示种子应保留调休（加班入账目标）').toBeTruthy()
    const creditedBefore = roundHours(creditType?.availableHours ?? 0)

    const submitted = await api.call<OvertimeRecord>(
      ALICE,
      'post',
      `/admin/attendance/my/overtime/${draft.id}/submit`,
      { idempotencyKey: newKey('overtime') },
    )
    expect(submitted.status).toBe(1)
    expect(roundHours(submitted.hours)).toBe(2)
    const processId = submitted.approval?.processId
    expect(processId).toBeTruthy()

    await approveAsBob(processId as number)

    const detail = await api.call<OvertimeRecord>(
      ALICE,
      'get',
      `/admin/attendance/my/overtime/${draft.id}`,
    )
    expect(detail.status).toBe(APPROVED)

    const balancesAfter = await api.call<BalanceRecord[]>(
      ALICE,
      'get',
      '/admin/attendance/my/balances',
    )
    const creditedAfter = roundHours(
      balancesAfter.find((balance) => balance.leaveTypeName === '调休')?.availableHours ?? 0,
    )
    expect(creditedAfter, '加班通过入账 2 小时调休').toBe(creditedBefore + 2)

    const ledger = await api.call<PageResult<LedgerRecord>>(
      ADMIN,
      'get',
      `/admin/attendance/ledger/1/200?leaveTypeId=${creditType?.leaveTypeId}`,
    )
    const credit = ledger.records.find(
      (record) =>
        record.userId === aliceId &&
        record.businessType === 'attendance.overtime' &&
        record.businessId === draft.id &&
        record.entryType === OVERTIME_CREDIT,
    )
    expect(credit).toBeTruthy()
    expect(roundHours(credit?.changeHours ?? 0)).toBe(2)

    const stat = await statRow()
    expect(Number(stat.overtimeHours), '月统计加班时长只认已通过单据').toBeGreaterThanOrEqual(2)
  })

  test('I5 余额与发放：正发放 / 零调整 201 / 不限余额类型 201 / 空成员 201', async () => {
    const before = await api.call<BalanceRecord[]>(ALICE, 'get', '/admin/attendance/my/balances')
    const beforeHours = roundHours(
      before.find((balance) => balance.leaveTypeId === yearLeaveType.id)?.availableHours ?? 0,
    )

    await api.call(ADMIN, 'post', '/admin/attendance/ledger/grant', {
      leaveTypeId: yearLeaveType.id,
      userIds: [aliceId],
      changeHours: 16,
      remark: unique('api-grant'),
    })
    const after = await api.call<BalanceRecord[]>(ALICE, 'get', '/admin/attendance/my/balances')
    const afterHours = roundHours(
      after.find((balance) => balance.leaveTypeId === yearLeaveType.id)?.availableHours ?? 0,
    )
    expect(afterHours).toBe(beforeHours + 16)

    const zero = await api.envelope(ADMIN, 'post', '/admin/attendance/ledger/grant', {
      leaveTypeId: yearLeaveType.id,
      userIds: [aliceId],
      changeHours: 0,
    })
    expectCodeAndMessage(zero.body, 201, '调整小时数不能为 0', 'I5 零调整')

    const noBalance = await api.envelope(ADMIN, 'post', '/admin/attendance/ledger/grant', {
      leaveTypeId: noBalanceType.id,
      userIds: [aliceId],
      changeHours: 8,
    })
    expectCodeAndMessage(noBalance.body, 201, '不需要余额', 'I5 不限余额类型')

    const emptyUsers = await api.envelope(ADMIN, 'post', '/admin/attendance/ledger/grant', {
      leaveTypeId: yearLeaveType.id,
      userIds: [],
      changeHours: 8,
    })
    expectCodeAndMessage(emptyUsers.body, 201, '发放成员不能为空', 'I5 空成员')

    expect(
      await api.code(PLAIN_USER, 'post', '/admin/attendance/ledger/grant', {
        leaveTypeId: yearLeaveType.id,
        userIds: [aliceId],
        changeHours: 8,
      }),
    ).toBe(205)
  })

  test('I6 假期类型：分页/选项/新建/改名/删除与校验 201', async () => {
    const page = await api.call<PageResult<LeaveTypeRecord>>(
      ADMIN,
      'get',
      '/admin/attendance/leave-type/1/100',
    )
    expect(page.records.some((record) => record.name === '年假')).toBe(true)

    const options = await api.call<LeaveTypeRecord[]>(
      ADMIN,
      'get',
      '/admin/attendance/leave-type/options',
    )
    expect(options.some((record) => record.id === yearLeaveType.id)).toBe(true)

    const name = unique('api-type')
    await api.call(ADMIN, 'post', '/admin/attendance/leave-type/save', {
      name,
      balanceRequired: 0,
      overtimeCredit: 0,
      status: 1,
      sortNo: 99,
      remark: '接口测试临时类型',
    })
    const createdPage = await api.call<PageResult<LeaveTypeRecord>>(
      ADMIN,
      'get',
      `/admin/attendance/leave-type/1/100?name=${encodeURIComponent(name)}`,
    )
    const created = createdPage.records.find((record) => record.name === name)
    expect(created).toBeTruthy()

    await api.call(ADMIN, 'put', '/admin/attendance/leave-type/update', {
      id: created?.id,
      name: `${name}改`,
      balanceRequired: 0,
      overtimeCredit: 0,
      status: 1,
      sortNo: 100,
      remark: '改名',
    })
    const renamedPage = await api.call<PageResult<LeaveTypeRecord>>(
      ADMIN,
      'get',
      `/admin/attendance/leave-type/1/100?name=${encodeURIComponent(`${name}改`)}`,
    )
    expect(renamedPage.records.some((record) => record.id === created?.id)).toBe(true)

    await api.call(ADMIN, 'delete', `/admin/attendance/leave-type/remove/${created?.id}`)
    const deletedPage = await api.call<PageResult<LeaveTypeRecord>>(
      ADMIN,
      'get',
      `/admin/attendance/leave-type/1/100?name=${encodeURIComponent(`${name}改`)}`,
    )
    expect(deletedPage.records.some((record) => record.id === created?.id)).toBe(false)

    const emptyName = await api.envelope(ADMIN, 'post', '/admin/attendance/leave-type/save', {
      name: ' ',
      balanceRequired: 0,
      overtimeCredit: 0,
      status: 1,
    })
    expectCodeAndMessage(emptyName.body, 201, '假期类型名称不能为空', 'I6 空名称')

    const secondCredit = await api.envelope(ADMIN, 'post', '/admin/attendance/leave-type/save', {
      name: unique('api-credit'),
      balanceRequired: 1,
      overtimeCredit: 1,
      status: 1,
    })
    expectCodeAndMessage(secondCredit.body, 201, '加班入账目标已存在', 'I6 第二入账目标')

    expect(await api.code(PLAIN_USER, 'get', '/admin/attendance/leave-type/1/5')).toBe(205)
  })

  test('I7 打卡记录与补卡：来源筛选 / 补卡流水 / 校验 201 / 未入组 201', async () => {
    const groupPage = await api.call<PageResult<{ id: number; name: string; memberCount: number }>>(
      ADMIN,
      'get',
      `/admin/attendance/group/1/50?name=${encodeURIComponent('演示考勤组')}`,
    )
    const group = groupPage.records.find((record) => record.name === '演示考勤组')
    expect(group).toBeTruthy()
    const members = await api.call<{ id: number; username: string }[]>(
      ADMIN,
      'get',
      `/admin/attendance/group/${group?.id}/members`,
    )
    expect(members.some((member) => member.username === ALICE)).toBe(true)

    const repairRows = await api.call<PageResult<PunchRecord>>(
      ADMIN,
      'get',
      `/admin/attendance/punch/1/100?name=${encodeURIComponent(ALICE)}&source=2` +
        `&beginDate=${leaveDate}&endDate=${leaveDate}`,
    )
    const repaired = repairRows.records.find(
      (record) =>
        record.userId === aliceId && record.punchDate === leaveDate && record.source === 2,
    )
    expect(repaired, '补卡应追加 source=2 流水').toBeTruthy()
    expect(repaired?.operatorName).toBe('管理员')

    const users = await api.call<{ id: number; username: string }[]>(
      ADMIN,
      'get',
      `/admin/attendance/repair/users?name=${encodeURIComponent(ALICE)}`,
    )
    expect(users.some((user) => user.username === ALICE)).toBe(true)

    const day = await api.call<DayStatusRecord>(
      ADMIN,
      'get',
      `/admin/attendance/repair/day?userId=${aliceId}&date=${leaveDate}`,
    )
    expect(day.workday).toBe(true)
    expect(day.clockInTime).toContain('09:00')
    expect(day.clockOutTime).toContain('18:00')

    const badSource = await api.envelope(ADMIN, 'get', '/admin/attendance/punch/1/10?source=9')
    expectCodeAndMessage(badSource.body, 201, '打卡来源不合法', 'I7 非法来源')

    const badRange = await api.envelope(
      ADMIN,
      'get',
      `/admin/attendance/punch/1/10?beginDate=${today.date}&endDate=2000-01-01`,
    )
    expectCodeAndMessage(badRange.body, 201, '开始日期不能晚于结束日期', 'I7 日期倒置')

    const noSlot = await api.envelope(ADMIN, 'post', '/admin/attendance/repair/save', {
      userId: aliceId,
      repairDate: leaveDate,
    })
    expectCodeAndMessage(noSlot.body, 201, '至少补一个槽位', 'I7 空槽位')

    const badOrder = await api.envelope(ADMIN, 'post', '/admin/attendance/repair/save', {
      userId: aliceId,
      repairDate: leaveDate,
      clockInTime: '18:00:00',
      clockOutTime: '09:00:00',
    })
    expectCodeAndMessage(badOrder.body, 201, '上班时间必须早于下班时间', 'I7 时刻倒置')

    const outsider = await createUser(api, unique('api-nogroup'))
    try {
      const notInGroup = await api.envelope(ADMIN, 'post', '/admin/attendance/repair/save', {
        userId: outsider.id,
        repairDate: leaveDate,
        clockInTime: '09:00:00',
      })
      expectCodeAndMessage(notInGroup.body, 201, '成员未加入考勤组', 'I7 未入组')
    } finally {
      await deleteUser(api, outsider.id)
    }

    expect(await api.code(PLAIN_USER, 'get', '/admin/attendance/repair/1/5')).toBe(205)
  })

  test('I8 数据范围：自定部门外的成员不可读不可写（201），列表空集', async () => {
    const tree = await api.call<MenuRecord[]>(ADMIN, 'get', '/admin/system/sysMenu/findNodes')
    const menus = flattenMenus(tree)
    const repairPage = menus.find((menu) => menu.path === '/business/attendance/repair')
    const statPage = menus.find((menu) => menu.path === '/business/attendance/stat')
    const repairButton = menus.find((menu) => menu.perms === 'attendance:record:repair')
    expect(repairPage, '演示种子应有补卡页').toBeTruthy()
    expect(statPage, '演示种子应有月统计页').toBeTruthy()
    expect(repairButton, '演示种子应有补卡按钮点').toBeTruthy()

    const role = await createRole(api, unique('api-scope'), 5)
    const scoped = await createUser(api, unique('api-scoped'))
    try {
      await assignMenus(api, role.id, [
        (repairPage as MenuRecord).id,
        (statPage as MenuRecord).id,
        (repairButton as MenuRecord).id,
      ])
      await assignRoles(api, scoped.id, [role.id])

      const repairList = await api.call<PageResult<PunchRecord>>(
        scoped.username,
        'get',
        '/admin/attendance/repair/1/10',
      )
      expect(repairList.total, '仅本人范围且无流水 → 空列表').toBe(0)

      const day = await api.envelope(
        scoped.username,
        'get',
        `/admin/attendance/repair/day?userId=${aliceId}&date=${leaveDate}`,
      )
      expectCodeAndMessage(day.body, 201, '成员不在数据范围内', 'I8 读下钻越权')

      const repair = await api.envelope(scoped.username, 'post', '/admin/attendance/repair/save', {
        userId: aliceId,
        repairDate: leaveDate,
        clockInTime: '09:00:00',
      })
      expectCodeAndMessage(repair.body, 201, '成员不在数据范围内', 'I8 写补卡越权')

      const statList = await api.call<PageResult<StatRecord>>(
        scoped.username,
        'get',
        `/admin/attendance/stat/1/10?month=${month}`,
      )
      expect(statList.total, '仅本人范围且不在组 → 空列表').toBe(0)

      const daily = await api.envelope(
        scoped.username,
        'get',
        `/admin/attendance/stat/daily?userId=${aliceId}&month=${month}`,
      )
      expectCodeAndMessage(daily.body, 201, '成员不在数据范围内', 'I8 统计下钻越权')
    } finally {
      await deleteUser(api, scoped.id)
      await deleteRole(api, role.id)
    }
  })

  test('I9 我的申请边界：他人单据 201 / 我的申请只读本人', async () => {
    const own = await api.call<PageResult<LeaveRecord>>(
      ALICE,
      'get',
      '/admin/attendance/my/leave/1/100',
    )
    expect(own.total).toBeGreaterThanOrEqual(1)

    const foreign = await api.envelope(
      BOB,
      'get',
      `/admin/attendance/my/leave/${own.records[0].id}`,
    )
    expectCodeAndMessage(foreign.body, 201, '请假单不存在', 'I9 他人请假单')

    const foreignOvertime = await api.envelope(BOB, 'get', '/admin/attendance/my/overtime/1/100')
    expectCode(foreignOvertime.body, 200, 'I9 他人加班列表构造上只读本人')
    expect(foreignOvertime.body.data).toBeTruthy()

    const badStatus = await api.envelope(ALICE, 'get', '/admin/attendance/my/leave/1/10?status=9')
    expectCodeAndMessage(badStatus.body, 201, '单据状态不合法', 'I9 非法状态筛选')
  })
})
