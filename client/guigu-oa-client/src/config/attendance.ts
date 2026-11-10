import type {
  ApiRequestConfig,
  AttendanceApplyListItem,
  AttendanceBalanceRecord,
  AttendanceBalanceView,
  AttendanceDayStatusRecord,
  AttendanceDayStatusView,
  AttendanceGroupForm,
  AttendanceGroupListItem,
  AttendanceGroupRecord,
  AttendanceHolidayForm,
  AttendanceHolidayListItem,
  AttendanceHolidayRecord,
  AttendanceLeaveApplicationRecord,
  AttendanceLeaveApplyForm,
  AttendanceLeaveTypeForm,
  AttendanceLeaveTypeListItem,
  AttendanceLeaveTypeRecord,
  AttendanceLedgerGrantForm,
  AttendanceLedgerListItem,
  AttendanceLedgerQuery,
  AttendanceLedgerRecord,
  AttendanceMemberRecord,
  AttendanceMonthRecord,
  AttendanceMonthView,
  AttendanceMyLeaveTypeRecord,
  AttendanceMyLeaveTypeView,
  AttendanceOvertimeApplicationRecord,
  AttendanceOvertimeApplyForm,
  AttendancePunchListItem,
  AttendancePunchQuery,
  AttendancePunchRecord,
  AttendanceRepairForm,
  AttendanceRepairUserOption,
  AttendanceRepairUserRecord,
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
 * #280 增「假期类型与发放」页：类型 CRUD（attendance:type:add|edit|remove）、
 * 多人发放/调整（attendance:ledger:grant）、流水列表与本人余额。
 * 必填文案逐字镜像服务端 vo/attendance 的请求 DTO。
 */

/** 考勤页的按钮点：读只判页面授予，不设 attendance:*:query。 */
export const ATTENDANCE_PERMISSIONS = {
  add: 'attendance:group:add',
  edit: 'attendance:group:edit',
  remove: 'attendance:group:remove',
  repair: 'attendance:record:repair',
  typeAdd: 'attendance:type:add',
  typeEdit: 'attendance:type:edit',
  typeRemove: 'attendance:type:remove',
  grant: 'attendance:ledger:grant',
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

// ---------------- 逐日状态 / 办事考勤记录 ----------------

/** 九枚举日状态（#281，ADR 0073 §统计口径）：与 AttendanceDayStatus 常量一一对应。 */
export const ATTENDANCE_DAY_STATUS_OPTIONS = [
  { value: 1, label: '正常' },
  { value: 2, label: '迟到' },
  { value: 3, label: '早退' },
  { value: 4, label: '迟到且早退' },
  { value: 5, label: '缺卡' },
  { value: 6, label: '请假' },
  { value: 7, label: '休息日' },
  { value: 8, label: '休假日' },
  { value: 9, label: '补班日' },
] as const

/**
 * 日状态文案：未到下班时刻且缺槽位显示「未打卡」（pending），未来日期/未分组显示
 * 「—」；请假拼接类型名（请假·年假）。
 */
export function attendanceDayStatusLabel(
  status: number | null | undefined,
  pending?: boolean,
  leaveTypeName?: string,
): string {
  if (pending) return '未打卡'
  if (status == null) return '—'
  if (status === 6) return leaveTypeName ? `请假·${leaveTypeName}` : '请假'
  return ATTENDANCE_DAY_STATUS_OPTIONS.find((option) => option.value === status)?.label || '—'
}

/** 日状态标签颜色：正常绿、异常黄、缺卡红、请假蓝、非工作日灰。 */
export function attendanceDayStatusTagType(
  status: number | null | undefined,
  pending?: boolean,
): 'success' | 'warning' | 'danger' | 'primary' | 'info' {
  if (pending) return 'info'
  if (status === 1) return 'success'
  if (status === 5) return 'danger'
  if (status === 6) return 'primary'
  if (status === 2 || status === 3 || status === 4) return 'warning'
  return 'info'
}

export function presentDayStatus(record: AttendanceDayStatusRecord = {}): AttendanceDayStatusView {
  const status = record.status == null ? null : record.status
  return {
    date: record.date || '',
    weekdayLabel: weekdayLabel(record.weekday),
    dayType: record.dayType == null ? 0 : record.dayType,
    dayTypeLabel: attendanceDayTypeLabel(record.dayType),
    workday: Boolean(record.workday),
    status,
    statusLabel: attendanceDayStatusLabel(status, record.pending, record.leaveTypeName),
    leaveTypeName: record.leaveTypeName || '',
    pending: Boolean(record.pending),
    clockInText: clockText(record.clockInTime),
    clockOutText: clockText(record.clockOutTime),
  }
}

/** 办事考勤记录页的请求：month 为空时由服务端取注入时钟的当前月。 */
export function monthRecordsRequest(month = ''): ApiRequestConfig {
  return {
    method: 'get',
    url: `${MY_ATTENDANCE_API}/records`,
    params: month ? { month } : {},
  }
}

export function presentMonth(record: AttendanceMonthRecord = {}): AttendanceMonthView {
  return {
    month: record.month || '',
    days: (record.days || []).map(presentDayStatus),
  }
}

// ---------------- 补卡处理 ----------------

export const REPAIR_API = `${ATTENDANCE_API}/repair`

export function repairListRequest(
  page: number,
  limit: number,
  searchObj: Record<string, string | number> = {},
): ApiRequestConfig {
  return { method: 'get', url: `${REPAIR_API}/${page}/${limit}`, params: searchObj }
}

export function repairUsersRequest(name = ''): ApiRequestConfig {
  return {
    method: 'get',
    url: `${REPAIR_API}/users`,
    params: name ? { name: name.trim() } : {},
  }
}

export function presentRepairUserOptions(
  records: Array<Partial<AttendanceRepairUserRecord>> = [],
): AttendanceRepairUserOption[] {
  return records
    .filter((record) => record.id != null)
    .map((record) => ({
      id: record.id as EntityId,
      username: record.username || '',
      name: record.name || '',
      deptName: record.deptName || '',
      label: `${record.deptName ? `${record.deptName} / ` : ''}${
        record.name ? `${record.name}（${record.username || ''}）` : record.username || ''
      }`,
    }))
}

export function repairDayRequest(userId: EntityId, date: string): ApiRequestConfig {
  return { method: 'get', url: `${REPAIR_API}/day`, params: { userId, date } }
}

/** 补卡载荷：空槽位不带键（服务端按「只补一槽」处理）。 */
export function repairSaveRequest(form: AttendanceRepairForm): ApiRequestConfig {
  const data: Record<string, unknown> = {
    userId: form.userId,
    repairDate: form.repairDate,
  }
  if (form.clockInTime) data.clockInTime = form.clockInTime
  if (form.clockOutTime) data.clockOutTime = form.clockOutTime
  return { method: 'post', url: `${REPAIR_API}/save`, data }
}

// ---------------- 假期类型与发放 ----------------

export const LEAVE_TYPE_API = `${ATTENDANCE_API}/leave-type`
export const LEDGER_API = `${ATTENDANCE_API}/ledger`

/** 类型状态（ADR 0073）：1 启用 / 0 停用；停用后历史流水照读。 */
export const LEAVE_TYPE_STATUS_OPTIONS = [
  { value: 1, label: '启用' },
  { value: 0, label: '停用' },
] as const

export function leaveTypeStatusLabel(status: number | null | undefined): string {
  return LEAVE_TYPE_STATUS_OPTIONS.find((option) => option.value === status)?.label || '停用'
}

/** 流水条目（ADR 0073）：1 发放（可负调整）/ 2 扣减（恒负）/ 3 加班入账（恒正）。 */
export const LEDGER_ENTRY_TYPE_OPTIONS = [
  { value: 1, label: '发放' },
  { value: 2, label: '扣减' },
  { value: 3, label: '加班入账' },
] as const

export function ledgerEntryTypeLabel(entryType: number | null | undefined): string {
  return LEDGER_ENTRY_TYPE_OPTIONS.find((option) => option.value === entryType)?.label || ''
}

/** 小时数展示：整数不带小数，半分保留一位、其他两位；页面与流水共用。 */
export function attendanceHoursText(value: number | null | undefined): string {
  const hours = Number(value == null ? 0 : value)
  if (!Number.isFinite(hours)) return '0'
  if (Number.isInteger(hours)) return String(hours)
  return hours.toFixed(2).replace(/0$/, '')
}

export function leaveTypeListRequest(
  page: number,
  limit: number,
  searchObj: Record<string, string | number> = {},
): ApiRequestConfig {
  return { method: 'get', url: `${LEAVE_TYPE_API}/${page}/${limit}`, params: searchObj }
}

/** 类型选项：停用类型也返回，供流水筛选与历史展示；发放下拉再按余额/启用过滤。 */
export function leaveTypeOptionsRequest(): ApiRequestConfig {
  return { method: 'get', url: `${LEAVE_TYPE_API}/options` }
}

export function presentLeaveTypeRecord(
  record: AttendanceLeaveTypeRecord = {},
): AttendanceLeaveTypeListItem {
  return {
    id: record.id || '',
    name: record.name || '',
    balanceRequired: record.balanceRequired == null ? 0 : record.balanceRequired,
    overtimeCredit: record.overtimeCredit == null ? 0 : record.overtimeCredit,
    status: record.status == null ? 1 : record.status,
    sortNo: record.sortNo == null ? 0 : record.sortNo,
    remark: record.remark || '',
    createTime: record.createTime,
  }
}

export function presentLeaveTypeListPage(
  page: { records?: AttendanceLeaveTypeRecord[]; total?: number; pages?: number } = {},
) {
  return {
    records: (page.records || []).map(presentLeaveTypeRecord),
    total: page.total || 0,
    pages: page.pages || 0,
  }
}

/** 发放下拉只列启用且需要余额的类型（ADR 0073：事假/病假不限量不用发放）。 */
export function grantableLeaveTypes(
  records: AttendanceLeaveTypeRecord[] = [],
): AttendanceLeaveTypeListItem[] {
  return records
    .map(presentLeaveTypeRecord)
    .filter((type) => type.status === 1 && type.balanceRequired === 1)
}

export function emptyLeaveTypeForm(): AttendanceLeaveTypeForm {
  return {
    id: '',
    name: '',
    balanceRequired: 1,
    overtimeCredit: 0,
    status: 1,
    sortNo: 0,
    remark: '',
  }
}

export function presentLeaveTypeForm(
  record: AttendanceLeaveTypeRecord = {},
): AttendanceLeaveTypeForm {
  return {
    id: record.id || '',
    name: record.name || '',
    balanceRequired: record.balanceRequired == null ? 0 : record.balanceRequired,
    overtimeCredit: record.overtimeCredit == null ? 0 : record.overtimeCredit,
    status: record.status == null ? 1 : record.status,
    sortNo: record.sortNo == null ? 0 : record.sortNo,
    remark: record.remark || '',
  }
}

/** 完整表单覆盖：启停用也走这里（status=0 即停用），服务端同语义。 */
export function leaveTypeWriteRequest(form: AttendanceLeaveTypeForm): ApiRequestConfig {
  const data = {
    name: form.name.trim(),
    balanceRequired: form.balanceRequired,
    overtimeCredit: form.overtimeCredit,
    status: form.status,
    sortNo: form.sortNo == null ? 0 : form.sortNo,
    remark: form.remark == null ? '' : form.remark.trim(),
  }
  if (form.id) {
    return { method: 'put', url: `${LEAVE_TYPE_API}/update`, data: { ...data, id: form.id } }
  }
  return { method: 'post', url: `${LEAVE_TYPE_API}/save`, data }
}

export function leaveTypeRemoveRequest(id: EntityId): ApiRequestConfig {
  return { method: 'delete', url: `${LEAVE_TYPE_API}/remove/${id}` }
}

export function ledgerListRequest(
  page: number,
  limit: number,
  searchObj: Record<string, string | number> = {},
): ApiRequestConfig {
  return { method: 'get', url: `${LEDGER_API}/${page}/${limit}`, params: searchObj }
}

/** 发放成员候选：与补卡页同一五档过滤的只读端点。 */
export function ledgerUsersRequest(name = ''): ApiRequestConfig {
  return {
    method: 'get',
    url: `${LEDGER_API}/users`,
    params: name ? { name: name.trim() } : {},
  }
}

export function ledgerQueryOf(query: AttendanceLedgerQuery): Record<string, string | number> {
  const params: Record<string, string | number> = {}
  if (query.leaveTypeId !== undefined && query.leaveTypeId !== '')
    params.leaveTypeId = query.leaveTypeId
  if (query.beginDate) params.beginDate = query.beginDate
  if (query.endDate) params.endDate = query.endDate
  return params
}

export function presentLedgerRecord(record: AttendanceLedgerRecord = {}): AttendanceLedgerListItem {
  const hours = Number(record.changeHours == null ? 0 : record.changeHours)
  return {
    ...record,
    id: record.id || '',
    username: record.username || '',
    name: record.name || '',
    deptName: record.deptName || '',
    leaveTypeName: record.leaveTypeName || '',
    entryTypeLabel: ledgerEntryTypeLabel(record.entryType),
    changeHoursText: `${hours > 0 ? '+' : ''}${attendanceHoursText(hours)}`,
    negative: hours < 0,
    operatorName: record.operatorName || '',
    remark: record.remark || '',
  }
}

export function presentLedgerListPage(
  page: { records?: AttendanceLedgerRecord[]; total?: number; pages?: number } = {},
) {
  return {
    records: (page.records || []).map(presentLedgerRecord),
    total: page.total || 0,
    pages: page.pages || 0,
  }
}

export function emptyGrantForm(): AttendanceLedgerGrantForm {
  return { leaveTypeId: '', userIds: [], changeHours: 0, remark: '' }
}

/** 发放/调整载荷：一次多人，changeHours 正发放、负冲抵。 */
export function ledgerGrantRequest(form: AttendanceLedgerGrantForm): ApiRequestConfig {
  return {
    method: 'post',
    url: `${LEDGER_API}/grant`,
    data: {
      leaveTypeId: form.leaveTypeId,
      userIds: [...form.userIds],
      changeHours: form.changeHours,
      remark: form.remark == null ? '' : form.remark.trim(),
    },
  }
}

export function leaveTypeFormRules(): Record<'name', RequiredRule[]> {
  return { name: [requiredRule('假期类型名称不能为空')] }
}

/** 本人各类型余额（#280）：登录即有，服务端只读当前会话用户。 */
export function balancesRequest(): ApiRequestConfig {
  return { method: 'get', url: `${MY_ATTENDANCE_API}/balances` }
}

export function presentBalance(record: AttendanceBalanceRecord = {}): AttendanceBalanceView {
  const available = Number(record.availableHours == null ? 0 : record.availableHours)
  const pending = Number(record.pendingHours == null ? 0 : record.pendingHours)
  return {
    leaveTypeId: record.leaveTypeId || '',
    leaveTypeName: record.leaveTypeName || '',
    status: record.status == null ? 1 : record.status,
    availableHours: available,
    availableHoursText: attendanceHoursText(available),
    pendingHours: pending,
    pendingText: pending > 0 ? `在途 ${attendanceHoursText(pending)}` : '',
    negative: available < 0,
  }
}

export function presentBalances(records: AttendanceBalanceRecord[] = []): AttendanceBalanceView[] {
  return records.map(presentBalance)
}

// ---------------- 我的申请（请假 / 加班，#282） ----------------

export const LEAVE_APPLY_API = `${MY_ATTENDANCE_API}/leave`
export const OVERTIME_APPLY_API = `${MY_ATTENDANCE_API}/overtime`

/** 单据状态档（ADR 0073）：0 草稿 / 1 审批中 / 2 通过 / -1 拒绝 / -2 撤回。 */
export const APPLICATION_STATUS_OPTIONS = [
  { value: 0, label: '草稿', tag: 'info' },
  { value: 1, label: '审批中', tag: 'warning' },
  { value: 2, label: '已通过', tag: 'success' },
  { value: -1, label: '已拒绝', tag: 'danger' },
  { value: -2, label: '已撤回', tag: 'info' },
] as const

export function applicationStatusLabel(status: number | null | undefined): string {
  return APPLICATION_STATUS_OPTIONS.find((option) => option.value === status)?.label || '草稿'
}

export function applicationStatusTagType(
  status: number | null | undefined,
): 'success' | 'warning' | 'danger' | 'info' | 'primary' {
  const option = APPLICATION_STATUS_OPTIONS.find((candidate) => candidate.value === status)
  return (option?.tag || 'info') as 'success' | 'warning' | 'danger' | 'info' | 'primary'
}

/** 草稿/拒绝/撤回可改可重提；审批中与已通过锁定。 */
export function applicationEditable(status: number | null | undefined): boolean {
  return status === 0 || status === -1 || status === -2
}

export function myLeavePageRequest(
  page: number,
  limit: number,
  status?: number | '',
): ApiRequestConfig {
  return {
    method: 'get',
    url: `${LEAVE_APPLY_API}/${page}/${limit}`,
    params: status === undefined || status === '' ? {} : { status },
  }
}

export function myLeaveDetailRequest(id: EntityId): ApiRequestConfig {
  return { method: 'get', url: `${LEAVE_APPLY_API}/${id}` }
}

/** 请假类型选项：启用类型，需要余额的带本人可用余额。 */
export function myLeaveTypesRequest(): ApiRequestConfig {
  return { method: 'get', url: `${LEAVE_APPLY_API}/types` }
}

export function leaveSaveRequest(form: AttendanceLeaveApplyForm): ApiRequestConfig {
  return { method: 'post', url: `${LEAVE_APPLY_API}/save`, data: leavePayload(form) }
}

export function leaveUpdateRequest(form: AttendanceLeaveApplyForm): ApiRequestConfig {
  return {
    method: 'put',
    url: `${LEAVE_APPLY_API}/update`,
    data: { ...leavePayload(form), id: form.id },
  }
}

export function leaveRemoveRequest(id: EntityId): ApiRequestConfig {
  return { method: 'delete', url: `${LEAVE_APPLY_API}/remove/${id}` }
}

/** 提交只带客户端幂等键；天数/小时数由服务端定稿。 */
export function leaveSubmitRequest(id: EntityId, idempotencyKey: string): ApiRequestConfig {
  return {
    method: 'post',
    url: `${LEAVE_APPLY_API}/${id}/submit`,
    data: { idempotencyKey },
  }
}

function leavePayload(form: AttendanceLeaveApplyForm): Record<string, unknown> {
  return {
    leaveTypeId: form.leaveTypeId,
    startDate: form.startDate,
    endDate: form.endDate,
    reason: form.reason == null ? '' : form.reason.trim(),
  }
}

export function myOvertimePageRequest(
  page: number,
  limit: number,
  status?: number | '',
): ApiRequestConfig {
  return {
    method: 'get',
    url: `${OVERTIME_APPLY_API}/${page}/${limit}`,
    params: status === undefined || status === '' ? {} : { status },
  }
}

export function myOvertimeDetailRequest(id: EntityId): ApiRequestConfig {
  return { method: 'get', url: `${OVERTIME_APPLY_API}/${id}` }
}

export function overtimeSaveRequest(form: AttendanceOvertimeApplyForm): ApiRequestConfig {
  return { method: 'post', url: `${OVERTIME_APPLY_API}/save`, data: overtimePayload(form) }
}

export function overtimeUpdateRequest(form: AttendanceOvertimeApplyForm): ApiRequestConfig {
  return {
    method: 'put',
    url: `${OVERTIME_APPLY_API}/update`,
    data: { ...overtimePayload(form), id: form.id },
  }
}

export function overtimeRemoveRequest(id: EntityId): ApiRequestConfig {
  return { method: 'delete', url: `${OVERTIME_APPLY_API}/remove/${id}` }
}

export function overtimeSubmitRequest(id: EntityId, idempotencyKey: string): ApiRequestConfig {
  return {
    method: 'post',
    url: `${OVERTIME_APPLY_API}/${id}/submit`,
    data: { idempotencyKey },
  }
}

function overtimePayload(form: AttendanceOvertimeApplyForm): Record<string, unknown> {
  return {
    overtimeDate: form.overtimeDate,
    startTime: form.startTime,
    endTime: form.endTime,
    reason: form.reason == null ? '' : form.reason.trim(),
  }
}

export function emptyLeaveApplyForm(): AttendanceLeaveApplyForm {
  return { id: '', leaveTypeId: '', startDate: '', endDate: '', reason: '' }
}

export function presentLeaveApplyForm(
  record: AttendanceLeaveApplicationRecord = {},
): AttendanceLeaveApplyForm {
  return {
    id: record.id || '',
    leaveTypeId: record.leaveTypeId == null ? '' : record.leaveTypeId,
    startDate: record.startDate || '',
    endDate: record.endDate || '',
    reason: record.reason || '',
  }
}

export function emptyOvertimeApplyForm(): AttendanceOvertimeApplyForm {
  return { id: '', overtimeDate: '', startTime: '', endTime: '', reason: '' }
}

export function presentOvertimeApplyForm(
  record: AttendanceOvertimeApplicationRecord = {},
): AttendanceOvertimeApplyForm {
  return {
    id: record.id || '',
    overtimeDate: record.overtimeDate || '',
    startTime: record.startTime || '',
    endTime: record.endTime || '',
    reason: record.reason || '',
  }
}

export function presentMyLeaveTypes(
  records: AttendanceMyLeaveTypeRecord[] = [],
): AttendanceMyLeaveTypeView[] {
  return records
    .filter((record) => record.id != null)
    .map((record) => ({
      id: record.id as EntityId,
      name: record.name || '',
      balanceRequired: record.balanceRequired == null ? 0 : record.balanceRequired,
      availableHoursText:
        record.availableHours == null ? '' : attendanceHoursText(record.availableHours),
    }))
}

function applyTimeText(value: string | null | undefined): string {
  return value ? value.slice(0, 16) : ''
}

/** 请假行投影：草稿天数/小时数尚未定稿，展示「提交后定稿」。 */
export function presentLeaveApplication(
  record: AttendanceLeaveApplicationRecord = {},
): AttendanceApplyListItem {
  const status = record.status == null ? 0 : record.status
  const days = Number(record.days == null ? 0 : record.days)
  const hours = Number(record.hours == null ? 0 : record.hours)
  return {
    id: record.id || '',
    kind: 'leave',
    typeName: record.leaveTypeName || '请假',
    dateText: `${record.startDate || ''} 至 ${record.endDate || ''}`,
    durationText:
      days > 0 ? `${days} 天（${attendanceHoursText(hours)} 小时）` : '提交后按班次与日历定稿',
    reason: record.reason || '',
    status,
    statusLabel: applicationStatusLabel(status),
    statusTagType: applicationStatusTagType(status),
    submitTimeText: applyTimeText(record.submitTime),
    finishTimeText: applyTimeText(record.finishTime),
    editable: applicationEditable(status),
    submittable: applicationEditable(status),
    approval: record.approval || null,
    leave: record,
  }
}

export function presentLeaveApplications(
  records: AttendanceLeaveApplicationRecord[] = [],
): AttendanceApplyListItem[] {
  return records.map(presentLeaveApplication)
}

/** 加班行投影：时间服务端算，草稿显示「提交后计算」。 */
export function presentOvertimeApplication(
  record: AttendanceOvertimeApplicationRecord = {},
): AttendanceApplyListItem {
  const status = record.status == null ? 0 : record.status
  const hours = Number(record.hours == null ? 0 : record.hours)
  return {
    id: record.id || '',
    kind: 'overtime',
    typeName: '加班',
    dateText: `${record.overtimeDate || ''} ${clockText(record.startTime)}-${clockText(
      record.endTime,
    )}`,
    durationText: hours > 0 ? `${attendanceHoursText(hours)} 小时` : '提交后计算',
    reason: record.reason || '',
    status,
    statusLabel: applicationStatusLabel(status),
    statusTagType: applicationStatusTagType(status),
    submitTimeText: applyTimeText(record.submitTime),
    finishTimeText: applyTimeText(record.finishTime),
    editable: applicationEditable(status),
    submittable: applicationEditable(status),
    approval: record.approval || null,
    overtime: record,
  }
}

export function presentOvertimeApplications(
  records: AttendanceOvertimeApplicationRecord[] = [],
): AttendanceApplyListItem[] {
  return records.map(presentOvertimeApplication)
}

/** 请假表单必填文案逐字镜像服务端 AttendanceLeaveRequest。 */
export function leaveApplyFormRules(): Record<
  'leaveTypeId' | 'startDate' | 'endDate',
  RequiredRule[]
> {
  return {
    leaveTypeId: [requiredRule('假期类型不能为空')],
    startDate: [requiredRule('开始日期不能为空')],
    endDate: [requiredRule('结束日期不能为空')],
  }
}

export function overtimeApplyFormRules(): Record<
  'overtimeDate' | 'startTime' | 'endTime',
  RequiredRule[]
> {
  return {
    overtimeDate: [requiredRule('加班日期不能为空')],
    startTime: [requiredRule('开始时刻不能为空')],
    endTime: [requiredRule('结束时刻不能为空')],
  }
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
