import request from '@/request/request'
import {
  groupRemoveRequest,
  groupWriteRequest,
  holidayRemoveRequest,
  holidayWriteRequest,
  shiftRemoveRequest,
  shiftWriteRequest,
} from '@/config/attendance'
import type {
  ApiResponse,
  AttendanceGroupForm,
  AttendanceHolidayForm,
  AttendanceShiftForm,
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
