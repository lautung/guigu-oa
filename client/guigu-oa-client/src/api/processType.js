import request from '@/utils/request'
import {
  findAllRequest,
  getRequest,
  listRequest,
  removeRequest,
  writeRequest
} from '@/config/process-type'

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

export function findAll() {
  return request(findAllRequest())
}
