import { test, expect } from './helpers/fixtures'
import {
  ANNUAL_LEAVE,
  ATTENDANCE_LEAVE_TEMPLATE,
  aliceStatRow,
  createAndSubmitLeave,
  ensureTodayWorkday,
  freeWorkday,
  hoursOf,
  leaveDaysOf,
  monthOf,
  punchViaUi,
} from './helpers/attendance'
import { poll } from './helpers/oracle'
import { marker } from './helpers/stack'
import { clickWorkItem, decide, uiLoginExpectWork, uiLogout } from './helpers/ui'

/**
 * P0-11（#284）：W3 出口判据的桌面主路径（ADR 0073）。
 *
 * 登录 alice → 上班/下班打卡 → 新建请假（年假，当月一个空闲工作日）并提交 →
 * 切 bob（部门负责人）审批通过 → alice 年假余额扣减 8 小时 → 月统计出现请假天数、
 * 实出勤减 1。请假日先用补卡做成已出勤、统计基线在提交前取：这一天从「出勤」变
 * 「请假」是唯一变量；日期避开今天与已覆盖日期，连跑不撞重叠校验。
 */
test('桌面主路径：打卡 → 请假审批 → 余额扣减 → 月统计', async ({ page, oracle }) => {
  const today = await ensureTodayWorkday(oracle)
  const month = monthOf(today.date)
  await oracle.ensureAliceInDemoDept()
  const aliceId = await oracle.userId('admin', 'alice')
  const leaveDate = await freeWorkday(oracle, 'alice', today.date)
  await oracle.repairAttendance('admin', aliceId, leaveDate)

  await uiLoginExpectWork(page, 'alice')
  await punchViaUi(page, 'in')
  await punchViaUi(page, 'out')

  const balancesBefore = await oracle.attendanceBalances('alice')
  const annualBefore = balancesBefore.find((balance) => balance.leaveTypeName === ANNUAL_LEAVE)
  expect(annualBefore, '演示种子应有年假余额').toBeTruthy()
  await oracle.grantLeave('admin', aliceId, annualBefore!.leaveTypeId, 16, marker('考勤主路径发放'))
  const availableBefore = hoursOf(annualBefore!.availableHours) + 16

  const statBefore = await aliceStatRow(oracle, month)
  const reason = marker('考勤主路径')
  await createAndSubmitLeave(page, { reason, date: leaveDate })

  const leaves = await oracle.attendanceLeaves('alice')
  const leave = leaves.records.find((record) => record.reason === reason)
  expect(leave, '提交后我的申请里应有这张请假单').toBeTruthy()
  const processId = leave!.approval?.processId
  expect(processId, '提交应发起业务审批').toBeTruthy()

  await uiLogout(page)
  await uiLoginExpectWork(page, 'bob')
  await clickWorkItem(page, ATTENDANCE_LEAVE_TEMPLATE)
  await expect(page.getByRole('heading', { name: '审批流转记录' })).toBeVisible()
  await decide(page, '通过')

  const approved = await poll(20_000, 500, async () => {
    const detail = await oracle.attendanceLeave('alice', leave!.id)
    return detail.status === 2 ? detail : undefined
  })
  expect(approved?.id, '请假单应翻到已通过').toBe(leave!.id)

  const balancesAfter = await oracle.attendanceBalances('alice')
  const availableAfter = hoursOf(
    balancesAfter.find((balance) => balance.leaveTypeName === ANNUAL_LEAVE)?.availableHours ?? 0,
  )
  expect(availableAfter, '审批通过扣减一天年假（8 小时）').toBe(availableBefore - 8)

  const statAfter = await aliceStatRow(oracle, month)
  expect(leaveDaysOf(statAfter), '月统计出现年假天数').toBe(leaveDaysOf(statBefore) + 1)
  expect(statAfter.actualDays, '当天从出勤变请假，实出勤减 1').toBe(statBefore.actualDays - 1)

  // 月统计页：真 UI 看到 alice 的年假天数与实出勤。
  await uiLogout(page)
  await uiLoginExpectWork(page, 'admin')
  await page.goto('/business/attendance/stat')
  await expect(page.getByRole('heading', { name: '月度统计' })).toBeVisible()

  const monthPicker = page.locator('.month-picker input')
  await monthPicker.click()
  await monthPicker.fill(month)
  await page.keyboard.press('Enter')

  const row = page.locator('.stat-table .el-table__body tr', { hasText: 'alice' }).first()
  await expect(row).toBeVisible()
  const headers = await page.locator('.stat-table .el-table__header th').allTextContents()
  const leaveColumn = headers.findIndex((header) => header.includes(`${ANNUAL_LEAVE}（天）`))
  const actualColumn = headers.findIndex((header) => header.includes('实出勤'))
  expect(leaveColumn, '统计表应出现年假列').toBeGreaterThanOrEqual(0)
  expect(actualColumn, '统计表应有实出勤列').toBeGreaterThanOrEqual(0)
  await expect(row.locator('td').nth(leaveColumn)).toHaveText(String(leaveDaysOf(statAfter)))
  await expect(row.locator('td').nth(actualColumn)).toHaveText(String(statAfter.actualDays))
})
