import request from '@/utils/request'
import { getRequest, listRequest, removeRequest, writeRequest } from '@/config/sys-role'
import type { ApiResponse, EntityId, PageResult, RoleRecord } from '@/types/domain'

export function getPageList(
  page: number,
  limit: number,
  searchObj: Record<string, string | number> = {},
): Promise<ApiResponse<PageResult<RoleRecord>>> {
  return request(listRequest(page, limit, searchObj))
}

export function getById(id: EntityId): Promise<ApiResponse<RoleRecord>> {
  return request(getRequest(id))
}

export function saveOrUpdate(form: RoleRecord): Promise<ApiResponse<void>> {
  return request(writeRequest(form))
}

export function removeById(id: EntityId): Promise<ApiResponse<void>> {
  return request(removeRequest(id))
}
