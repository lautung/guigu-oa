import request from '@/utils/request'

export function createUpload(data) {
  return request({
    url: '/admin/attachments/uploads',
    method: 'post',
    data
  })
}

export function completeUpload(attachmentId) {
  return request({
    url: `/admin/attachments/${attachmentId}/complete`,
    method: 'post'
  })
}

export function listDraft(draftId) {
  return request({
    url: `/admin/attachments/drafts/${draftId}`,
    method: 'get'
  })
}

export function deletePending(attachmentId) {
  return request({
    url: `/admin/attachments/pending/${attachmentId}`,
    method: 'delete'
  })
}

export function listForProcess(processId) {
  return request({
    url: `/admin/attachments/processes/${processId}`,
    method: 'get'
  })
}

export function createDownloadGrant(processId, attachmentId) {
  return request({
    url: `/admin/attachments/processes/${processId}/${attachmentId}/download-url`,
    method: 'post'
  })
}
