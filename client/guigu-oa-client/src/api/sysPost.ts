import request from '@/request/request'
import { removeRequest, writeRequest } from '@/config/sys-post'
import type { ApiResponse, EntityId, PostRecord } from '@/types/domain'

export function saveOrUpdate(form: PostRecord): Promise<ApiResponse<void>> {
  return request(writeRequest(form))
}

export function removeById(id: EntityId): Promise<ApiResponse<void>> {
  return request(removeRequest(id))
}
