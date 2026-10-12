import type { ApiResponse, AttachmentRecord, AttachmentUploadGrant, EntityId } from '@/types/domain'

export function draftStorageKey(templateId: EntityId): string {
  return `oa-attachment-draft:${templateId}`
}

export function getOrCreateDraftId(
  templateId: EntityId,
  storage: Pick<Storage, 'getItem' | 'setItem'> = window.sessionStorage,
  randomUUID: () => string = createUuid,
): string {
  const key = draftStorageKey(templateId)
  const current = storage.getItem(key)
  if (current) {
    return current
  }
  const draftId = randomUUID()
  storage.setItem(key, draftId)
  return draftId
}

export function clearDraftId(
  templateId: EntityId,
  storage: Pick<Storage, 'removeItem'> = window.sessionStorage,
): void {
  storage.removeItem(draftStorageKey(templateId))
}

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
}

export async function uploadAttachment(
  file: File,
  options: AttachmentUploadOptions,
): Promise<AttachmentRecord> {
  const contentType = file.type || 'application/octet-stream'
  const grantResponse = await options.createUpload({
    draftId: options.draftId,
    processId: options.processId,
    fileName: file.name,
    contentType,
    sizeBytes: file.size,
  })
  const grant = grantResponse.data
  const response = await options.put(grant.uploadUrl, {
    method: 'PUT',
    headers: grant.headers,
    body: file,
  })
  if (!response.ok) {
    throw new Error('附件上传失败，请重试')
  }
  const completed = await options.completeUpload(grant.attachmentId)
  return completed.data
}

function createUuid(): string {
  if (globalThis.crypto && typeof globalThis.crypto.randomUUID === 'function') {
    return globalThis.crypto.randomUUID()
  }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (character) => {
    const random = Math.floor(Math.random() * 16)
    const value = character === 'x' ? random : (random & 0x3) | 0x8
    return value.toString(16)
  })
}
