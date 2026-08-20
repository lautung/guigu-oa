import request from '@/utils/request'
import { getCurrentUserRequest } from '@/work/profile'

export function show(id) {
  return request({
    url: `/admin/process/show/${id}`,
    method: 'get'
  })
}

export function approve(approvalVo) {
  return request({
    url: '/admin/process/approve',
    method: 'post',
    data: approvalVo
  })
}

export function getProcessTemplate(id) {
  return request({
    method: 'get',
    url: `/admin/process/getProcessTemplate/${id}`
  })
}

export function startUp(data) {
  return request({
    method: 'post',
    url: '/admin/process/startUp',
    data
  })
}

export function getCurrentUser() {
  return request(getCurrentUserRequest())
}
