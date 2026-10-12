import request from '@/utils/request'
import { listRequest } from '@/config/process-instance'

export function getPageList(page, limit, query) {
  return request(listRequest(page, limit, query))
}
