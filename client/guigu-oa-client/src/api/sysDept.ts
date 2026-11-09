import request from '@/request/request'
import { findTreeRequest, removeRequest, userOptionsRequest, writeRequest } from '@/config/sys-dept'
import type { ApiResponse, DeptForm, DeptRecord, EntityId, UserOption } from '@/types/domain'

export function findTree(): Promise<ApiResponse<DeptRecord[]>> {
  return request(findTreeRequest())
}

/** 部门负责人选择器的启用用户选项（#286）：与部门树同页授予的只读端点。 */
export function fetchUserOptions(): Promise<ApiResponse<UserOption[]>> {
  return request(userOptionsRequest())
}

export function saveOrUpdate(form: DeptForm): Promise<ApiResponse<void>> {
  return request(writeRequest(form))
}

export function removeById(id: EntityId): Promise<ApiResponse<void>> {
  return request(removeRequest(id))
}
