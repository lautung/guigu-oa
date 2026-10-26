import type { ApiResponse, AttachmentRecord, AttachmentUploadGrant, EntityId } from '@/types/domain'

export interface AttachmentUploadOptions {
  draftId: string
  processId?: EntityId
  createUpload: (data: {
    draftId: string
    processId?: EntityId
    fileName: string
    contentType: string
    sizeBytes: number
  }) => Promise<ApiResponse<AttachmentUploadGrant>>
  put: (url: string, options: RequestInit) => Promise<Response>
  completeUpload: (attachmentId: EntityId) => Promise<ApiResponse<AttachmentRecord>>
  assertCurrent?: () => void
}

export async function uploadAttachment(
  file: File,
  options: AttachmentUploadOptions,
): Promise<AttachmentRecord> {
  const contentType = file.type || 'application/octet-stream'
  options.assertCurrent?.()
  const grantResponse = await options.createUpload({
    draftId: options.draftId,
    processId: options.processId,
    fileName: file.name,
    contentType,
    sizeBytes: file.size,
  })
  const grant = grantResponse.data
  options.assertCurrent?.()
  const response = await options.put(grant.uploadUrl, {
    method: 'PUT',
    headers: grant.headers,
    body: file,
  })
  if (!response.ok) throw new Error('附件上传失败，请重试')
  options.assertCurrent?.()
  const completed = await options.completeUpload(grant.attachmentId)
  options.assertCurrent?.()
  return completed.data
}
