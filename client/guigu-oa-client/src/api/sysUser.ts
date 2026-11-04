import request from '@/request/request'
import {
  getRequest,
  removeRequest,
  resetPasswordRequest,
  updateStatusRequest,
  writeRequest,
} from '@/config/sys-user'
import type { ApiResponse, EntityId, UserRecord } from '@/types/domain'

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

/** 管理员重置密码（#250）：成功后目标用户全部会话失效，操作者不受影响。 */
export function resetPassword(id: EntityId, newPassword: string): Promise<ApiResponse<void>> {
  return request(resetPasswordRequest(id, newPassword))
}
