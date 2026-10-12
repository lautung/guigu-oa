import request from '@/utils/request'
import type {
  ApiResponse,
  AttachmentRecord,
  AttachmentUploadGrant,
  AttachmentUploadRequest,
  EntityId,
  ProcessAttachmentList,
} from '@/types/domain'

export function createUpload(
  data: AttachmentUploadRequest,
): Promise<ApiResponse<AttachmentUploadGrant>> {
  return request({
    url: '/admin/attachments/uploads',
    method: 'post',
    data,
  })
}

export function completeUpload(attachmentId: EntityId): Promise<ApiResponse<AttachmentRecord>> {
  return request({
    url: `/admin/attachments/${attachmentId}/complete`,
    method: 'post',
  })
}

export function listDraft(draftId: string): Promise<ApiResponse<AttachmentRecord[]>> {
  return request({
    url: `/admin/attachments/drafts/${draftId}`,
    method: 'get',
  })
}

export function deletePending(attachmentId: EntityId): Promise<ApiResponse<void>> {
  return request({
    url: `/admin/attachments/pending/${attachmentId}`,
    method: 'delete',
  })
}

export function listForProcess(processId: EntityId): Promise<ApiResponse<ProcessAttachmentList>> {
  return request({
    url: `/admin/attachments/processes/${processId}`,
    method: 'get',
  })
}

export function createDownloadGrant(
  processId: EntityId,
  attachmentId: EntityId,
): Promise<ApiResponse<{ downloadUrl: string }>> {
  return request({
    url: `/admin/attachments/processes/${processId}/${attachmentId}/download-url`,
    method: 'post',
  })
}
