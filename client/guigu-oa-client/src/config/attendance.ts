import type {
  ApiRequestConfig,
  AttendanceGroupForm,
  AttendanceGroupListItem,
  AttendanceGroupRecord,
  AttendanceHolidayForm,
  AttendanceHolidayListItem,
  AttendanceHolidayRecord,
  AttendanceMemberRecord,
  AttendancePunchListItem,
  AttendancePunchQuery,
  AttendancePunchRecord,
  AttendanceShiftForm,
  AttendanceShiftListItem,
  AttendanceShiftRecord,
  AttendanceTodayRecord,
  AttendanceTodayView,
  EntityId,
  UserOption,
} from '@/types/domain'
import { requiredRule, type RequiredRule } from './form-rules'

/**
 * 考勤组与班次页（#278，ADR 0073）的投影与请求构造：
 * 班次、考勤组（含成员）与全局节假日三个区块共用一个页面、
 * 一套页面授予（读端点只判页面）与三个按钮点（attendance:group:add|edit|remove）。
 * 必填文案逐字镜像服务端 vo/attendance 的请求 DTO。
 */

/** 考勤页的按钮点：读只判页面授予，不设 attendance:*:query。 */
export const ATTENDANCE_PERMISSIONS = {
  add: 'attendance:group:add',
  edit: 'attendance:group:edit',
  remove: 'attendance:group:remove',
} as const

export const ATTENDANCE_API = '/admin/attendance'

/** 每周工作日多选：1=周一 … 7=周日；表单默认周一至五（ADR 0073）。 */
export const WORKDAY_OPTIONS = [
  { value: 1, label: '周一' },
  { value: 2, label: '周二' },
  { value: 3, label: '周三' },
  { value: 4, label: '周四' },
  { value: 5, label: '周五' },
  { value: 6, label: '周六' },
  { value: 7, label: '周日' },
] as const

export const DEFAULT_WORKDAYS: number[] = [1, 2, 3, 4, 5]

/** 服务端 workdays 是升序逗号串（如 1,2,3,4,5），页面勾选要的是数字数组。 */
export function parseWorkdays(workdays: string | undefined | null): number[] {
  if (!workdays) return []
  return workdays
    .split(',')
    .map((part) => Number(part.trim()))
    .filter((day) => Number.isInteger(day) && day >= 1 && day <= 7)
}

export function workdayLabels(workdays: string | undefined | null): string {
  const days = parseWorkdays(workdays)
  return WORKDAY_OPTIONS.filter((option) => days.includes(option.value))
    .map((option) => option.label)
    .join('、')
}

// ---------------- 班次 ----------------

export const SHIFT_API = `${ATTENDANCE_API}/shift`

export function shiftListRequest(
  page: number,
  limit: number,
  searchObj: Record<string, string | number> = {},
): ApiRequestConfig {
  return { method: 'get', url: `${SHIFT_API}/${page}/${limit}`, params: searchObj }
}

export function shiftOptionsRequest(): ApiRequestConfig {
  return { method: 'get', url: `${SHIFT_API}/options` }
}

export function presentShiftRecord(record: AttendanceShiftRecord = {}): AttendanceShiftListItem {
  return {
    id: record.id || '',
    name: record.name,
    startTime: record.startTime,
    endTime: record.endTime,
    lunchStart: record.lunchStart,
    lunchEnd: record.lunchEnd,
    graceMinutes: record.graceMinutes,
    remark: record.remark,
    createTime: record.createTime,
  }
}

export function presentShiftListPage(
  page: { records?: AttendanceShiftRecord[]; total?: number; pages?: number } = {},
) {
  return {
    records: (page.records || []).map(presentShiftRecord),
    total: page.total || 0,
    pages: page.pages || 0,
  }
}

export function emptyShiftForm(): AttendanceShiftForm {
  return {
    id: '',
    name: '',
    startTime: '',
    endTime: '',
    lunchStart: '',
    lunchEnd: '',
    graceMinutes: 0,
    remark: '',
  }
}

export function presentShiftForm(record: AttendanceShiftRecord = {}): AttendanceShiftForm {
  return {
    id: record.id || '',
    name: record.name || '',
    startTime: record.startTime || '',
    endTime: record.endTime || '',
    lunchStart: record.lunchStart || '',
    lunchEnd: record.lunchEnd || '',
    graceMinutes: record.graceMinutes == null ? 0 : record.graceMinutes,
    remark: record.remark || '',
  }
}

/**
 * 班次按完整表单覆盖（服务端约定）：名称/时刻必填，午休成对（成对留空=清除），
 * 宽限分钟缺省 0。文案镜像 AttendanceShiftSaveRequest。
 */
export function shiftWriteRequest(form: AttendanceShiftForm): ApiRequestConfig {
  const data = {
    name: form.name.trim(),
    startTime: form.startTime,
    endTime: form.endTime,
    lunchStart: form.lunchStart || null,
    lunchEnd: form.lunchEnd || null,
    graceMinutes: form.graceMinutes == null ? 0 : form.graceMinutes,
    remark: form.remark == null ? '' : form.remark.trim(),
  }
  if (form.id) {
    return { method: 'put', url: `${SHIFT_API}/update`, data: { ...data, id: form.id } }
  }
  return { method: 'post', url: `${SHIFT_API}/save`, data }
}

export function shiftRemoveRequest(id: EntityId): ApiRequestConfig {
  return { method: 'delete', url: `${SHIFT_API}/remove/${id}` }
}

// ---------------- 考勤组 ----------------

export const GROUP_API = `${ATTENDANCE_API}/group`

export function groupListRequest(
  page: number,
  limit: number,
  searchObj: Record<string, string | number> = {},
): ApiRequestConfig {
  return { method: 'get', url: `${GROUP_API}/${page}/${limit}`, params: searchObj }
}

export function groupMembersRequest(groupId: EntityId): ApiRequestConfig {
  return { method: 'get', url: `${GROUP_API}/${groupId}/members` }
}

export function userOptionsRequest(): ApiRequestConfig {
  return { method: 'get', url: `${GROUP_API}/userOptions` }
}

export function presentUserOptions(records: Array<Partial<UserOption>> = []): UserOption[] {
  return records
    .filter((record) => record.id != null)
    .map((record) => ({
      id: record.id as EntityId,
      username: record.username || '',
      name: record.name || '',
      label: record.name ? `${record.name}（${record.username}）` : record.username || '',
    }))
}

export function presentGroupRecord(record: AttendanceGroupRecord = {}): AttendanceGroupListItem {
  return {
    id: record.id || '',
    name: record.name,
    shiftId: record.shiftId,
    shiftName: record.shiftName,
    workdays: record.workdays,
    memberCount: record.memberCount,
    remark: record.remark,
    createTime: record.createTime,
  }
}

export function presentGroupListPage(
  page: { records?: AttendanceGroupRecord[]; total?: number; pages?: number } = {},
) {
  return {
    records: (page.records || []).map(presentGroupRecord),
    total: page.total || 0,
    pages: page.pages || 0,
  }
}

export function presentMemberIds(records: AttendanceMemberRecord[] = []): EntityId[] {
  return records
    .map((record) => record.userId)
    .filter((userId): userId is EntityId => userId != null)
}

export function emptyGroupForm(): AttendanceGroupForm {
  return {
    id: '',
    name: '',
    shiftId: '',
    workdays: [...DEFAULT_WORKDAYS],
    memberUserIds: [],
    remark: '',
  }
}

export function presentGroupForm(
  record: AttendanceGroupRecord,
  memberUserIds: EntityId[] = [],
): AttendanceGroupForm {
  return {
    id: record.id || '',
    name: record.name || '',
    shiftId: record.shiftId == null ? '' : record.shiftId,
    workdays: parseWorkdays(record.workdays),
    memberUserIds: [...memberUserIds],
    remark: record.remark || '',
  }
}

/**
 * 考勤组写载荷：save 带成员（缺省空组）；update 成员显式列表整体替换（空列表清空）、
 * 缺省保留——与 #249 postIds 的服务端语义一致。文案镜像 AttendanceGroupSaveRequest。
 */
export function groupWriteRequest(form: AttendanceGroupForm): ApiRequestConfig {
  const data: Record<string, unknown> = {
    name: form.name.trim(),
    shiftId: form.shiftId,
    workdays: [...form.workdays],
    remark: form.remark == null ? '' : form.remark.trim(),
  }
  if (form.id) {
    data.id = form.id
    data.memberUserIds = [...form.memberUserIds]
    return { method: 'put', url: `${GROUP_API}/update`, data }
  }
  data.memberUserIds = [...form.memberUserIds]
  return { method: 'post', url: `${GROUP_API}/save`, data }
}

export function groupRemoveRequest(id: EntityId): ApiRequestConfig {
  return { method: 'delete', url: `${GROUP_API}/remove/${id}` }
}

// ---------------- 节假日 ----------------

export const HOLIDAY_API = `${ATTENDANCE_API}/holiday`

/** 节假日类型（ADR 0073）：1 休假日 / 2 补班日。 */
export const HOLIDAY_DAY_TYPE_OPTIONS = [
  { value: 1, label: '休假日' },
  { value: 2, label: '补班日' },
] as const

export function holidayDayTypeLabel(dayType: number | null | undefined): string {
  return HOLIDAY_DAY_TYPE_OPTIONS.find((option) => option.value === dayType)?.label || ''
}

export function holidayListRequest(
  page: number,
  limit: number,
  searchObj: Record<string, string | number> = {},
): ApiRequestConfig {
  return { method: 'get', url: `${HOLIDAY_API}/${page}/${limit}`, params: searchObj }
}

export function presentHolidayRecord(
  record: AttendanceHolidayRecord = {},
): AttendanceHolidayListItem {
  return {
    id: record.id || '',
    holidayDate: record.holidayDate,
    name: record.name,
    dayType: record.dayType,
    createTime: record.createTime,
  }
}

export function presentHolidayListPage(
  page: { records?: AttendanceHolidayRecord[]; total?: number; pages?: number } = {},
) {
  return {
    records: (page.records || []).map(presentHolidayRecord),
    total: page.total || 0,
    pages: page.pages || 0,
  }
}

export function emptyHolidayForm(): AttendanceHolidayForm {
  return { id: '', holidayDate: '', name: '', dayType: 1 }
}

export function presentHolidayForm(record: AttendanceHolidayRecord = {}): AttendanceHolidayForm {
  return {
    id: record.id || '',
    holidayDate: record.holidayDate || '',
    name: record.name || '',
    dayType: record.dayType == null ? 1 : record.dayType,
  }
}

export function holidayWriteRequest(form: AttendanceHolidayForm): ApiRequestConfig {
  const data = {
    holidayDate: form.holidayDate,
    name: form.name.trim(),
    dayType: form.dayType,
  }
  if (form.id) {
    return { method: 'put', url: `${HOLIDAY_API}/update`, data: { ...data, id: form.id } }
  }
  return { method: 'post', url: `${HOLIDAY_API}/save`, data }
}

export function holidayRemoveRequest(id: EntityId): ApiRequestConfig {
  return { method: 'delete', url: `${HOLIDAY_API}/remove/${id}` }
}

// ---------------- 打卡 ----------------

export const MY_ATTENDANCE_API = `${ATTENDANCE_API}/my`
export const PUNCH_API = `${ATTENDANCE_API}/punch`

/** 日类型（#279，ADR 0073）：0 未分组 / 1 工作日 / 2 休息日 / 3 休假日 / 4 补班日。 */
export const ATTENDANCE_DAY_TYPE_OPTIONS = [
  { value: 0, label: '未分组' },
  { value: 1, label: '工作日' },
  { value: 2, label: '休息日' },
  { value: 3, label: '休假日' },
  { value: 4, label: '补班日' },
] as const

export function attendanceDayTypeLabel(dayType: number | null | undefined): string {
  return ATTENDANCE_DAY_TYPE_OPTIONS.find((option) => option.value === dayType)?.label || '未分组'
}

export const PUNCH_SLOT_IN = 1
export const PUNCH_SLOT_OUT = 2

export const PUNCH_SLOT_OPTIONS = [
  { value: PUNCH_SLOT_IN, label: '上班卡' },
  { value: PUNCH_SLOT_OUT, label: '下班卡' },
] as const

export function punchSlotLabel(slot: number | null | undefined): string {
  return PUNCH_SLOT_OPTIONS.find((option) => option.value === slot)?.label || ''
}

/** 打卡来源（ADR 0073）：1 网页打卡 / 2 补卡。 */
export const PUNCH_SOURCE_OPTIONS = [
  { value: 1, label: '网页打卡' },
  { value: 2, label: '补卡' },
] as const

export function punchSourceLabel(source: number | null | undefined): string {
  return PUNCH_SOURCE_OPTIONS.find((option) => option.value === source)?.label || ''
}

export function todayRequest(): ApiRequestConfig {
  return { method: 'get', url: `${MY_ATTENDANCE_API}/today` }
}

/** 上班/下班打卡：两个显式端点，服务器时间为准，可重复打。 */
export function punchRequest(slot: number): ApiRequestConfig {
  return {
    method: 'post',
    url: `${MY_ATTENDANCE_API}/punch/${slot === PUNCH_SLOT_OUT ? 'out' : 'in'}`,
  }
}

export function punchListRequest(
  page: number,
  limit: number,
  searchObj: Record<string, string | number> = {},
): ApiRequestConfig {
  return { method: 'get', url: `${PUNCH_API}/${page}/${limit}`, params: searchObj }
}

/** 时刻只展示到分钟；服务端返回 HH:mm:ss。 */
function clockText(value: string | null | undefined): string {
  return value ? value.slice(0, 5) : ''
}

function weekdayLabel(weekday: number | null | undefined): string {
  return WORKDAY_OPTIONS.find((option) => option.value === weekday)?.label || ''
}

export function presentToday(record: AttendanceTodayRecord = {}): AttendanceTodayView {
  const shift = record.shift
  return {
    date: record.date || '',
    weekdayLabel: weekdayLabel(record.weekday),
    dayType: record.dayType == null ? 0 : record.dayType,
    dayTypeLabel: attendanceDayTypeLabel(record.dayType),
    workday: Boolean(record.workday),
    hasGroup: Boolean(record.hasGroup),
    groupName: record.groupName || '',
    shift: shift
      ? {
          name: shift.name || '',
          timeRange: `${clockText(shift.startTime)} - ${clockText(shift.endTime)}`,
          lunchText:
            shift.lunchStart && shift.lunchEnd
              ? `${clockText(shift.lunchStart)} - ${clockText(shift.lunchEnd)}`
              : '无',
          graceMinutes: shift.graceMinutes == null ? 0 : shift.graceMinutes,
        }
      : null,
    clockInTime: clockText(record.clockInTime),
    clockOutTime: clockText(record.clockOutTime),
  }
}

export function presentPunchRecord(record: AttendancePunchRecord = {}): AttendancePunchListItem {
  return {
    id: record.id || '',
    userId: record.userId,
    username: record.username || '',
    name: record.name || '',
    deptName: record.deptName || '',
    punchDate: record.punchDate || '',
    slot: record.slot,
    slotLabel: punchSlotLabel(record.slot),
    punchTime: record.punchTime || '',
    source: record.source,
    sourceLabel: punchSourceLabel(record.source),
    operatorName: record.operatorName || '',
  }
}

export function presentPunchListPage(
  page: { records?: AttendancePunchRecord[]; total?: number; pages?: number } = {},
) {
  return {
    records: (page.records || []).map(presentPunchRecord),
    total: page.total || 0,
    pages: page.pages || 0,
  }
}

/** 查询表单投影：空值不进查询参数（服务端按缺省不过滤）。 */
export function punchQueryOf(query: AttendancePunchQuery): Record<string, string | number> {
  const params: Record<string, string | number> = {}
  if (query.name) params.name = query.name.trim()
  if (query.beginDate) params.beginDate = query.beginDate
  if (query.endDate) params.endDate = query.endDate
  if (query.source !== undefined && query.source !== '') params.source = query.source
  return params
}

// ---------------- 表单规则 ----------------

export function shiftFormRules(): Record<'name' | 'startTime' | 'endTime', RequiredRule[]> {
  return {
    name: [requiredRule('班次名称不能为空')],
    startTime: [requiredRule('上班时刻不能为空')],
    endTime: [requiredRule('下班时刻不能为空')],
  }
}

/** 午休成对：只填一边在本地就拦下（服务端同文案，ADR 0038）。 */
export function validateShiftLunch(form: AttendanceShiftForm): string {
  if ((form.lunchStart || '') !== '' && (form.lunchEnd || '') === '')
    return '午休开始与结束必须同时填写'
  if ((form.lunchStart || '') === '' && (form.lunchEnd || '') !== '')
    return '午休开始与结束必须同时填写'
  if ((form.lunchStart || '') !== '' && form.lunchEnd <= form.lunchStart)
    return '午休结束必须晚于午休开始'
  return ''
}

export function groupFormRules(): Record<'name' | 'shiftId', RequiredRule[]> {
  return {
    name: [requiredRule('考勤组名称不能为空')],
    shiftId: [requiredRule('绑定的班次不能为空')],
  }
}

export function holidayFormRules(): Record<'holidayDate' | 'name', RequiredRule[]> {
  return {
    holidayDate: [requiredRule('节假日日期不能为空')],
    name: [requiredRule('节假日名称不能为空')],
  }
}
