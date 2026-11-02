import request from '@/request/request'
import { getRequest, removeRequest, writeRequest } from '@/config/sys-role'
import type { ApiResponse, EntityId, RoleRecord } from '@/types/domain'

export function getById(id: EntityId): Promise<ApiResponse<RoleRecord>> {
  return request(getRequest(id))
}

export function saveOrUpdate(form: RoleRecord): Promise<ApiResponse<void>> {
  return request(writeRequest(form))
}

export function removeById(id: EntityId): Promise<ApiResponse<void>> {
  return request(removeRequest(id))
}
