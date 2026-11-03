import request from '@/request/request'
import { cleanRequest, listRequest, removeRequest, unlockRequest } from '@/config/login-log'
import type { ApiResponse, EntityId, LoginLogRecord, PageResult } from '@/types/domain'

export function findPage(
  page: number,
  limit: number,
  params: Record<string, string | number> = {},
): Promise<ApiResponse<PageResult<LoginLogRecord>>> {
  return request<PageResult<LoginLogRecord>>(listRequest(page, limit, params))
}

export function removeByIds(idList: readonly EntityId[]): Promise<ApiResponse<void>> {
  return request<void>(removeRequest(idList))
}

export function clean(): Promise<ApiResponse<void>> {
  return request<void>(cleanRequest())
}

export function unlock(username: string): Promise<ApiResponse<void>> {
  return request<void>(unlockRequest(username))
}
