import request from '@/utils/request'
import {
  assignRolesRequest,
  getRequest,
  listRequest,
  removeRequest,
  toAssignRolesRequest,
  writeRequest
} from '@/config/sys-role'

export function getPageList(page, limit, searchObj) {
  return request(listRequest(page, limit, searchObj))
}

export function getById(id) {
  return request(getRequest(id))
}

export function saveOrUpdate(form) {
  return request(writeRequest(form))
}

export function removeById(id) {
  return request(removeRequest(id))
}

export function getRoles(userId) {
  return request(toAssignRolesRequest(userId))
}

export function assignRoles(userId, roleIdList) {
  return request(assignRolesRequest(userId, roleIdList))
}
