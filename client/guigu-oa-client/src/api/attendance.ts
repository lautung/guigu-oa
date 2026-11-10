import request from '@/request/request'
import {
  groupRemoveRequest,
  groupWriteRequest,
  holidayRemoveRequest,
  holidayWriteRequest,
  punchListRequest,
  punchQueryOf,
  punchRequest,
  shiftRemoveRequest,
  shiftWriteRequest,
  todayRequest,
} from '@/config/attendance'
import type {
  ApiResponse,
  AttendanceGroupForm,
  AttendanceHolidayForm,
  AttendancePunchQuery,
  AttendancePunchRecord,
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
