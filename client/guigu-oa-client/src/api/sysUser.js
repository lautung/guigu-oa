import request from '@/utils/request'
import {
  getRequest,
  listRequest,
  removeRequest,
  updateStatusRequest,
  writeRequest
} from '@/config/sys-user'

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

export function updateStatus(id, status) {
  return request(updateStatusRequest(id, status))
}
