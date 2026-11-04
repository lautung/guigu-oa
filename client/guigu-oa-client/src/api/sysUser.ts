import request from '@/request/request'
import {
  deptTreeRequest,
  getRequest,
  postOptionsRequest,
  removeRequest,
  resetPasswordRequest,
  updateStatusRequest,
  writeRequest,
} from '@/config/sys-user'
import type { ApiResponse, DeptRecord, EntityId, PostRecord, UserRecord } from '@/types/domain'

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

/** 用户页只读部门树（#249）：只判 `/system/sysUser` 页面授予，维护入口在部门管理页。 */
export function findDeptTree(): Promise<ApiResponse<DeptRecord[]>> {
  return request(deptTreeRequest())
}

/** 用户页只读岗位选项（#249）：服务端只回启用岗位。 */
export function findPostOptions(): Promise<ApiResponse<PostRecord[]>> {
  return request(postOptionsRequest())
}
