import request from '@/request/request'
import { forceLogoutRequest, listRequest } from '@/config/monitor-online'
import type { ApiResponse, OnlineSessionRecord, PageResult } from '@/types/domain'

export function findPage(
  page: number,
  limit: number,
  params: Record<string, string | number> = {},
): Promise<ApiResponse<PageResult<OnlineSessionRecord>>> {
  return request<PageResult<OnlineSessionRecord>>(listRequest(page, limit, params))
}

export function forceLogout(sessionId: string): Promise<ApiResponse<void>> {
  return request<void>(forceLogoutRequest(sessionId))
}
