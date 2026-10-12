import request from '@/utils/request'
import {
  findAllRequest,
  getRequest,
  listRequest,
  removeRequest,
  writeRequest,
} from '@/config/process-type'
import type { ApiResponse, PageResult, ProcessTypeRecord } from '@/types/domain'

export function getPageList(
  page: number,
  limit: number,
): Promise<ApiResponse<PageResult<ProcessTypeRecord>>> {
  return request(listRequest(page, limit))
}

export function getById(id: string | number): Promise<ApiResponse<ProcessTypeRecord>> {
  return request(getRequest(id))
}

export function saveOrUpdate(form: ProcessTypeRecord): Promise<ApiResponse<void>> {
  return request(writeRequest(form))
}

export function removeById(id: string | number): Promise<ApiResponse<void>> {
  return request(removeRequest(id))
}

export function findAll(): Promise<ApiResponse<ProcessTypeRecord[]>> {
  return request(findAllRequest())
}
