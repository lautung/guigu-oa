import { expect, type Page } from '@playwright/test'

import type { AttendanceStatRow, AttendanceToday, Oracle } from './oracle'

/**
 * 考勤主路径（#284，ADR 0073）的共享 UI 动作与 oracle 对照。
 *
 * 演示种子只保证 alice 在 9:00–18:00 的演示考勤组；今天是周末或休假日时，
 * setup 先用后台 API 把今天改成补班日，让「打卡 → 请假 → 审批 → 统计」在任意
 * 运行日都可重复。UI 动作只走真实页面，持久化结果交给 oracle。
 */

export const DEMO_GROUP = '演示考勤组'
export const ANNUAL_LEAVE = '年假'
export const ATTENDANCE_LEAVE_TEMPLATE = '演示考勤请假审批'

export function monthOf(date: string): string {
  return date.slice(0, 7)
}

/**
 * 当月一个未被本人请假覆盖的工作日。excludeDate 用于把「今天」留给打卡路径；
 * 桌面主路径再把选中的一天补成已出勤，审批通过后实出勤才会恰好减 1。
 */
export async function freeWorkday(
  oracle: Oracle,
  viewer = 'alice',
  excludeDate?: string,
): Promise<string> {
  const today = await oracle.attendanceToday(viewer)
  const month = monthOf(today.date)
  const records = await oracle.attendanceMonth(viewer, month)
  const leaves = await oracle.attendanceLeaves(viewer)
  const active = leaves.records.filter((record) => record.status === 1 || record.status === 2)
  const covered = (date: string): boolean =>
    active.some((record) => record.startDate <= date && record.endDate >= date)
  const candidate = records.days.find(
    (day) => day.workday && day.date !== excludeDate && !covered(day.date),
  )
  if (!candidate) {
    throw new Error('当月没有空闲工作日：请假已覆盖全部可选日期')
  }
  return candidate.date
}

export async function ensureTodayWorkday(
  oracle: Oracle,
  viewer = 'alice',
  admin = 'admin',
): Promise<AttendanceToday> {
  const today = await oracle.attendanceToday(viewer)
  expect(today.hasGroup, '演示种子应把 alice 放进演示考勤组').toBe(true)
  expect(today.groupName).toBe(DEMO_GROUP)
  const workday = await oracle.ensureAttendanceWorkday(admin, viewer, today.date)
  expect(workday.workday, '今天是工作日才能走打卡主路径').toBe(true)
  return workday
}

export async function punchViaUi(page: Page, slot: 'in' | 'out'): Promise<void> {
  await page.goto('/work/my-attendance')
  await expect(page.getByRole('heading', { name: '今日打卡' })).toBeVisible()
  await page.getByRole('button', { name: slot === 'in' ? '上班打卡' : '下班打卡' }).click()
  await expect(
    page.getByText(slot === 'in' ? '上班打卡成功' : '下班打卡成功').first(),
  ).toBeVisible()
}

/** 新建请假草稿并提交，返回页面刷新后的请假事由标记（调用方用 oracle 按事由回查单据）。 */
export async function createAndSubmitLeave(
  page: Page,
  fields: { reason: string; date: string; typeName?: string },
): Promise<void> {
  await page.goto('/work/my-applications')
  await expect(page.getByRole('heading', { name: '我的申请' })).toBeVisible()
  await page.getByRole('button', { name: '新建请假' }).click()

  const dialog = page.locator('.el-dialog', { hasText: '新建请假' })
  await expect(dialog).toBeVisible()
  await dialog.locator('.el-select').first().click()
  await page.getByRole('option', { name: new RegExp(fields.typeName ?? ANNUAL_LEAVE) }).click()

  for (const label of ['开始日期', '结束日期']) {
    const input = dialog.locator('.el-form-item', { hasText: label }).locator('input')
    await input.click()
    await input.fill(fields.date)
    await page.keyboard.press('Enter')
  }
  await dialog.locator('textarea').fill(fields.reason)
  await dialog.getByRole('button', { name: '保存草稿' }).click()
  await expect(page.getByText('草稿已保存').first()).toBeVisible()

  const card = page.locator('.apply-card', { hasText: fields.reason })
  await expect(card).toBeVisible()
  await card.getByRole('button', { name: '提交', exact: true }).click()
  await expect(page.getByText('已提交审批').first()).toBeVisible()
  await expect(card.getByText('审批中')).toBeVisible()
}

export async function aliceStatRow(oracle: Oracle, month: string): Promise<AttendanceStatRow> {
  const rows = await oracle.attendanceStat('admin', month)
  const row = rows.find((item) => item.username === 'alice')
  if (!row) throw new Error('月统计里找不到 alice')
  return row
}

export function leaveDaysOf(row: AttendanceStatRow, typeName = ANNUAL_LEAVE): number {
  return row.leaveDays.find((leave) => leave.leaveTypeName === typeName)?.days ?? 0
}

export function hoursOf(value: number): number {
  return Math.round(Number(value) * 100) / 100
}
