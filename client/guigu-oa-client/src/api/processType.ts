import request from '@/request/request'
import { findAllRequest, getRequest, removeRequest, writeRequest } from '@/config/process-type'
import type { ApiResponse, ProcessTypeRecord } from '@/types/domain'

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
