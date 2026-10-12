import request from '@/utils/request'
import { listRequest } from '@/config/process-instance'
import type { ApiResponse, PageResult, ProcessInstanceRecord } from '@/types/domain'

export function getPageList(
  page: number,
  limit: number,
  query: Record<string, string | number>,
): Promise<ApiResponse<PageResult<ProcessInstanceRecord>>> {
  return request(listRequest(page, limit, query))
}
