import request from '@/utils/request'
import { getCurrentUserRequest } from '@/work/profile'
import type {
  ApiResponse,
  EntityId,
  ProcessDetailPayload,
  ProcessInstanceRecord,
  Profile,
} from '@/types/domain'

export function show(id: EntityId): Promise<ApiResponse<ProcessDetailPayload>> {
  return request({
    url: `/admin/process/show/${id}`,
    method: 'get',
  })
}

export interface ApprovalRequest {
  processId: EntityId
  taskId: EntityId
  status: number
}

export function approve(approvalVo: ApprovalRequest): Promise<ApiResponse<void>> {
  return request({
    url: '/admin/process/approve',
    method: 'post',
    data: approvalVo,
  })
}

export interface StartProcessRequest {
  processTemplateId: EntityId
  processTypeId: EntityId
  formValues: string
  attachmentDraftId?: string
  attachmentIds?: EntityId[]
  ccUserIds?: EntityId[]
}

export function startUp(data: StartProcessRequest): Promise<ApiResponse<ProcessInstanceRecord>> {
  return request({
    method: 'post',
    url: '/admin/process/startUp',
    data,
  })
}

export function getCurrentUser(): Promise<ApiResponse<Profile>> {
  return request(getCurrentUserRequest())
}
