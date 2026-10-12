import request from '@/utils/request'
import {
  getRequest,
  listRequest,
  removeRequest,
  updateStatusRequest,
  writeRequest,
} from '@/config/sys-user'
import type { ApiResponse, EntityId, PageResult, UserRecord } from '@/types/domain'

export function getPageList(
  page: number,
  limit: number,
  searchObj: Record<string, string | number> = {},
): Promise<ApiResponse<PageResult<UserRecord>>> {
  return request(listRequest(page, limit, searchObj))
}

export function getById(id: EntityId): Promise<ApiResponse<UserRecord>> {
  return request(getRequest(id))
}

export function saveOrUpdate(form: UserRecord): Promise<ApiResponse<void>> {
  return request(writeRequest(form))
}

export function removeById(id: EntityId): Promise<ApiResponse<void>> {
  return request(removeRequest(id))
}

export function updateStatus(id: EntityId, status: number): Promise<ApiResponse<void>> {
  return request(updateStatusRequest(id, status))
}
