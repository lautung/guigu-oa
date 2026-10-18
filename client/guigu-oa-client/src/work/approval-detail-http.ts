import type { Recognition } from '@/recognition'
import { BusinessRequestFailure } from '@/utils/request'
import type {
  ApiClient,
  AttachmentRecord,
  ProcessAttachmentList,
  ProcessDetailPayload,
  AttachmentUploadGrant,
} from '@/types/domain'
import type { DetailAdapter } from './approval-detail'
import { uploadAttachment } from './attachments'

interface HttpDetailOptions {
  recognition: Recognition
  request: ApiClient
  storage: Pick<Storage, 'getItem' | 'setItem'>
  navigate(path: string): void
  openDownload(url: string, fileName: string): void
}

export function createHttpDetailAdapter(options: HttpDetailOptions): DetailAdapter {
  function scoped(isCurrent: () => boolean): DetailAdapter {
    const context = options.recognition.capture()
    const config = { _recognition: context, _localErrors: true, _isRelevant: isCurrent }
    const request: ApiClient = (input) => options.request({ ...input, ...config })
    return {
      forOpening: scoped,
      entryId: () => options.recognition.capture().entryId,
      read: async (processId) =>
        (
          await request<ProcessDetailPayload>({
            method: 'get',
            url: `/admin/process/show/${processId}`,
          })
        ).data,
      async submit(decision) {
        try {
          await request({ method: 'post', url: '/admin/process/approve', data: decision })
          return { kind: 'success' }
        } catch (error: unknown) {
          return {
            kind:
              error instanceof BusinessRequestFailure && error.businessCode
                ? 'rejected'
                : 'uncertain',
            message: error instanceof Error ? error.message : '无法确认处理结果',
          }
        }
      },
      listAttachments: async (processId) =>
        (
          await request<ProcessAttachmentList>({
            method: 'get',
            url: `/admin/attachments/processes/${processId}`,
          })
        ).data,
      upload: (processId, file) =>
        uploadAttachment(file, {
          draftId: '',
          processId,
          createUpload: (data) =>
            request<AttachmentUploadGrant>({
              method: 'post',
              url: '/admin/attachments/uploads',
              data,
            }),
          put: (url, init) => fetch(url, init),
          completeUpload: (attachmentId) =>
            request<AttachmentRecord>({
              method: 'post',
              url: `/admin/attachments/${attachmentId}/complete`,
            }),
        }),
      async download(processId, attachment) {
        const response = await request<{ downloadUrl: string }>({
          method: 'post',
          url: `/admin/attachments/processes/${processId}/${attachment.id}/download-url`,
        })
        if (isCurrent()) options.openDownload(response.data.downloadUrl, attachment.fileName)
      },
      loadRecords: (key) => {
        const saved = options.storage.getItem(key)
        return saved === null ? [] : JSON.parse(saved)
      },
      saveRecords: (key, records) => options.storage.setItem(key, JSON.stringify(records)),
      navigate: options.navigate,
    }
  }
  return scoped(() => false)
}
