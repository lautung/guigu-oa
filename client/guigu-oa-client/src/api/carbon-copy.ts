import request from '@/utils/request'
import type { ApiResponse, EntityId } from '@/types/domain'

export interface CarbonCopyRecipient {
  id: EntityId
  username: string
  name: string
}

export function findCarbonCopyRecipients(): Promise<ApiResponse<CarbonCopyRecipient[]>> {
  return request({
    url: '/admin/process/carbonCopyRecipients',
    method: 'get',
  })
}
