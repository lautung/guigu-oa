import request from '@/request/request'
import {
  doAssignRequest,
  findNodesRequest,
  removeRequest,
  toAssignRequest,
  writeRequest,
} from '@/config/sys-menu'
import type { ApiResponse, EntityId, MenuForm, MenuNode } from '@/types/domain'

export function findNodes(): Promise<ApiResponse<MenuNode[]>> {
  return request(findNodesRequest())
}

export function getByIdForAssign(roleId: EntityId): Promise<ApiResponse<MenuNode[]>> {
  return request(toAssignRequest(roleId))
}

export function doAssign(roleId: EntityId, menuIdList: EntityId[]): Promise<ApiResponse<void>> {
  return request(doAssignRequest(roleId, menuIdList))
}

export function saveOrUpdate(form: MenuForm): Promise<ApiResponse<void>> {
  return request(writeRequest(form))
}

export function removeById(id: EntityId): Promise<ApiResponse<void>> {
  return request(removeRequest(id))
}
