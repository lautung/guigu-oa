export function draftStorageKey(templateId) {
  return `oa-attachment-draft:${templateId}`
}

export function getOrCreateDraftId(templateId, storage = window.sessionStorage, randomUUID = createUuid) {
  const key = draftStorageKey(templateId)
  const current = storage.getItem(key)
  if (current) {
    return current
  }
  const draftId = randomUUID()
  storage.setItem(key, draftId)
  return draftId
}

export function clearDraftId(templateId, storage = window.sessionStorage) {
  storage.removeItem(draftStorageKey(templateId))
}

export async function uploadAttachment(file, options) {
  const contentType = file.type || 'application/octet-stream'
  const grantResponse = await options.createUpload({
    draftId: options.draftId,
    processId: options.processId,
    fileName: file.name,
    contentType,
    sizeBytes: file.size
  })
  const grant = grantResponse.data
  const response = await options.put(grant.uploadUrl, {
    method: 'PUT',
    headers: grant.headers,
    body: file
  })
  if (!response.ok) {
    throw new Error('附件上传失败，请重试')
  }
  const completed = await options.completeUpload(grant.attachmentId)
  return completed.data
}

function createUuid() {
  if (globalThis.crypto && typeof globalThis.crypto.randomUUID === 'function') {
    return globalThis.crypto.randomUUID()
  }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, character => {
    const random = Math.floor(Math.random() * 16)
    const value = character === 'x' ? random : ((random & 0x3) | 0x8)
    return value.toString(16)
  })
}
