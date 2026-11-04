import request from '@/request/request'
import { refreshRequest, removeRequest, writeRequest } from '@/config/params'
import type { ApiResponse, EntityId, ParamForm } from '@/types/domain'

export function saveOrUpdate(form: ParamForm): Promise<ApiResponse<void>> {
  return request(writeRequest(form))
}

export function removeById(id: EntityId): Promise<ApiResponse<void>> {
  return request(removeRequest(id))
}

export function refreshCache(): Promise<ApiResponse<number>> {
  return request(refreshRequest())
}
