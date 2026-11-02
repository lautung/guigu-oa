import request from '@/request/request'
import { cleanRequest, detailRequest, listRequest, removeRequest } from '@/config/oper-log'
import type { ApiResponse, EntityId, OperLogRecord, PageResult } from '@/types/domain'

export function findPage(
  page: number,
  limit: number,
  params: Record<string, string | number> = {},
): Promise<ApiResponse<PageResult<OperLogRecord>>> {
  return request<PageResult<OperLogRecord>>(listRequest(page, limit, params))
}

export function getById(id: EntityId): Promise<ApiResponse<OperLogRecord>> {
  return request<OperLogRecord>(detailRequest(id))
}

export function removeByIds(idList: readonly EntityId[]): Promise<ApiResponse<void>> {
  return request<void>(removeRequest(idList))
}

export function clean(): Promise<ApiResponse<void>> {
  return request<void>(cleanRequest())
}
