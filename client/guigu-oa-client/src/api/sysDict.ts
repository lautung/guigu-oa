import request from '@/request/request'
import {
  dataRemoveRequest,
  dataWriteRequest,
  refreshRequest,
  typeRemoveRequest,
  typeWriteRequest,
} from '@/config/dict'
import type { ApiResponse, DictDataForm, DictTypeForm, EntityId } from '@/types/domain'

export function saveOrUpdateType(form: DictTypeForm): Promise<ApiResponse<void>> {
  return request(typeWriteRequest(form))
}

export function removeType(id: EntityId): Promise<ApiResponse<void>> {
  return request(typeRemoveRequest(id))
}

export function saveOrUpdateData(form: DictDataForm): Promise<ApiResponse<void>> {
  return request(dataWriteRequest(form))
}

export function removeData(id: EntityId): Promise<ApiResponse<void>> {
  return request(dataRemoveRequest(id))
}

export function refreshCache(): Promise<ApiResponse<number>> {
  return request(refreshRequest())
}
