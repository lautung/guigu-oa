import request from '@/utils/request'

const api_name = '/admin/process'

export function findPending(page, limit) {
  return request({
    url: `${api_name}/findPending/${page}/${limit}`,
    method: 'get'
  })
}

export function show(id) {
  return request({
    url: `${api_name}/show/${id}`,
    method: 'get'
  })
}

export function approve(approvalVo) {
  return request({
    url: `${api_name}/approve`,
    method: 'post',
    data: approvalVo
  })
}
