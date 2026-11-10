import request from '@/request/request'
import {
  groupRemoveRequest,
  groupWriteRequest,
  holidayRemoveRequest,
  holidayWriteRequest,
  monthRecordsRequest,
  punchListRequest,
  punchQueryOf,
  punchRequest,
  repairDayRequest,
  repairListRequest,
  repairSaveRequest,
  repairUsersRequest,
  shiftRemoveRequest,
  shiftWriteRequest,
  todayRequest,
} from '@/config/attendance'
import type {
  ApiResponse,
  AttendanceDayStatusRecord,
  AttendanceGroupForm,
  AttendanceHolidayForm,
  AttendanceMonthRecord,
  AttendancePunchQuery,
  AttendancePunchRecord,
  AttendanceRepairForm,
  AttendanceRepairUserRecord,
  AttendanceShiftForm,
  AttendanceTodayRecord,
  EntityId,
} from '@/types/domain'

export function saveOrUpdateShift(form: AttendanceShiftForm): Promise<ApiResponse<void>> {
  return request(shiftWriteRequest(form))
}

export function removeShift(id: EntityId): Promise<ApiResponse<void>> {
  return request(shiftRemoveRequest(id))
}

export function saveOrUpdateGroup(form: AttendanceGroupForm): Promise<ApiResponse<void>> {
  return request(groupWriteRequest(form))
}

export function removeGroup(id: EntityId): Promise<ApiResponse<void>> {
  return request(groupRemoveRequest(id))
}

export function saveOrUpdateHoliday(form: AttendanceHolidayForm): Promise<ApiResponse<void>> {
  return request(holidayWriteRequest(form))
}

export function removeHoliday(id: EntityId): Promise<ApiResponse<void>> {
  return request(holidayRemoveRequest(id))
}

/** 办事打卡（#279）：登录即有，只读本人。 */
export function fetchTodayAttendance(): Promise<ApiResponse<AttendanceTodayRecord>> {
  return request(todayRequest())
}

export function punchAttendance(slot: number): Promise<ApiResponse<AttendanceTodayRecord>> {
  return request(punchRequest(slot))
}

export function fetchPunchPage(
  page: number,
  limit: number,
  query: AttendancePunchQuery = {},
): Promise<ApiResponse<{ records?: AttendancePunchRecord[]; total?: number; pages?: number }>> {
  return request(punchListRequest(page, limit, punchQueryOf(query)))
}

/** 办事「考勤记录」（#281）：本人当月逐日状态；month 为空取服务端当前月。 */
export function fetchMyAttendanceMonth(month = ''): Promise<ApiResponse<AttendanceMonthRecord>> {
  return request(monthRecordsRequest(month))
}

/** 后台补卡页的打卡流水分页（数据范围由服务端五档过滤）。 */
export function fetchRepairPage(
  page: number,
  limit: number,
  query: AttendancePunchQuery = {},
): Promise<ApiResponse<{ records?: AttendancePunchRecord[]; total?: number; pages?: number }>> {
  return request(repairListRequest(page, limit, punchQueryOf(query)))
}

/** 补卡页成员候选（按数据范围，姓名/用户名包含匹配）。 */
export function fetchRepairUsers(name = ''): Promise<ApiResponse<AttendanceRepairUserRecord[]>> {
  return request(repairUsersRequest(name))
}

/** 指定成员+日期的当前槽位状态（下钻也受数据范围约束）。 */
export function fetchRepairDay(
  userId: EntityId,
  date: string,
): Promise<ApiResponse<AttendanceDayStatusRecord>> {
  return request(repairDayRequest(userId, date))
}

/** 补卡：追加来源「补卡」的流水行，可只补一槽。 */
export function saveAttendanceRepair(
  form: AttendanceRepairForm,
): Promise<ApiResponse<AttendanceDayStatusRecord>> {
  return request(repairSaveRequest(form))
}
