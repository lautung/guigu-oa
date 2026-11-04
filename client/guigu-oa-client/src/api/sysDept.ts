import request from '@/request/request'
import { findTreeRequest, removeRequest, writeRequest } from '@/config/sys-dept'
import type { ApiResponse, DeptForm, DeptRecord, EntityId } from '@/types/domain'

export function findTree(): Promise<ApiResponse<DeptRecord[]>> {
  return request(findTreeRequest())
}

export function saveOrUpdate(form: DeptForm): Promise<ApiResponse<void>> {
  return request(writeRequest(form))
}

export function removeById(id: EntityId): Promise<ApiResponse<void>> {
  return request(removeRequest(id))
}
