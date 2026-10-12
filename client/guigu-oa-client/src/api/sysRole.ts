import request from '@/utils/request'
import {
  assignRolesRequest,
  getRequest,
  listRequest,
  removeRequest,
  toAssignRolesRequest,
  writeRequest,
} from '@/config/sys-role'
import type { ApiResponse, EntityId, PageResult, RoleListItem, RoleRecord } from '@/types/domain'

export interface RoleAssignmentLists {
  allRolesList: RoleListItem[]
  assginRoleList: RoleListItem[]
}

export function getPageList(
  page: number,
  limit: number,
  searchObj: Record<string, string | number> = {},
): Promise<ApiResponse<PageResult<RoleRecord>>> {
  return request(listRequest(page, limit, searchObj))
}

export function getById(id: EntityId): Promise<ApiResponse<RoleRecord>> {
  return request(getRequest(id))
}

export function saveOrUpdate(form: RoleRecord): Promise<ApiResponse<void>> {
  return request(writeRequest(form))
}

export function removeById(id: EntityId): Promise<ApiResponse<void>> {
  return request(removeRequest(id))
}

export function getRoles(userId: EntityId): Promise<ApiResponse<RoleAssignmentLists>> {
  return request(toAssignRolesRequest(userId))
}

export function assignRoles(userId: EntityId, roleIdList: EntityId[]): Promise<ApiResponse<void>> {
  return request(assignRolesRequest(userId, roleIdList))
}
