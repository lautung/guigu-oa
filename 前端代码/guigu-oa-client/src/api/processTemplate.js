import request from '@/utils/request'
import {
  getRequest,
  listRequest,
  publishRequest,
  removeRequest,
  writeRequest
} from '@/config/process-template'

export function getPageList(page, limit) {
  return request(listRequest(page, limit))
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

export function publish(id) {
  return request(publishRequest(id))
}
