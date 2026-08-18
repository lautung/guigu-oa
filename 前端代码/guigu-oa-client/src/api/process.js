import request from '@/utils/request'

const api_name = '/admin/process'

export function findPending(page, limit) {
  return request({
    url: `${api_name}/findPending/${page}/${limit}`,
    method: 'get'
  })
}
