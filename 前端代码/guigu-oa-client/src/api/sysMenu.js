import request from '@/utils/request'
import {
  doAssignRequest,
  findNodesRequest,
  removeRequest,
  toAssignRequest,
  writeRequest
} from '@/config/sys-menu'

export function findNodes() {
  return request(findNodesRequest())
}

export function getByIdForAssign(roleId) {
  return request(toAssignRequest(roleId))
}

export function doAssign(roleId, menuIdList) {
  return request(doAssignRequest(roleId, menuIdList))
}

export function saveOrUpdate(form) {
  return request(writeRequest(form))
}

export function removeById(id) {
  return request(removeRequest(id))
}
