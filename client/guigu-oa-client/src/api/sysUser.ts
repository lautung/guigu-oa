import request, { requestBinary, type ScopedRequestConfig } from '@/request/request'
import {
  deptTreeRequest,
  getRequest,
  postOptionsRequest,
  removeRequest,
  resetPasswordRequest,
  updateStatusRequest,
  writeRequest,
} from '@/config/sys-user'
import { exportRequest, importRequest, importTemplateRequest } from '@/config/user-export'
import type {
  ApiResponse,
  DeptRecord,
  EntityId,
  PostRecord,
  UserImportResult,
  UserRecord,
} from '@/types/domain'

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

/** 按当前筛选导出 xlsx（#259）：二进制响应不与 JSON 信封混用。 */
export function exportUsers(searchObj: Record<string, string | number>): Promise<Blob> {
  return requestBinary(exportRequest(searchObj))
}

/** 导入模板是空的 xlsx，下载走同一二进制入口（#259）；调用方弹窗内展示失败时静默。 */
export function downloadImportTemplate(
  options: { notifyNormalErrors?: boolean } = {},
): Promise<Blob> {
  return requestBinary(importTemplateRequest(), options)
}

/**
 * 逐行导入（#259）：multipart 字段 `file`，返回部分成功汇总。
 * 调用方在导入弹窗内展示失败文案时传 `notifyNormalErrors: false`，避免再弹一条全局提示。
 */
export function importUsers(
  file: File,
  options: { notifyNormalErrors?: boolean } = {},
): Promise<ApiResponse<UserImportResult>> {
  const config: ScopedRequestConfig = {
    ...importRequest(file),
    _notifyNormalErrors: options.notifyNormalErrors,
  }
  return request(config)
}
