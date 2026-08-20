import request from '@/utils/request'
import {
  getProcessTemplateRequest,
  startUpRequest
} from '@/work/start-flow'
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

export function findProcessType() {
  return request({
    url: '/admin/process/findProcessType',
    method: 'get'
  })
}

export function getProcessTemplate(id) {
  return request(getProcessTemplateRequest(id))
}

export function startUp(data) {
  return request(startUpRequest(data))
}

export function getCurrentUser() {
  return request(getCurrentUserRequest())
}
