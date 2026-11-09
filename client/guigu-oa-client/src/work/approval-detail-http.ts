import { classifySubmissionFailure } from '@/request/submission-outcome'
import type { RequestScope } from '@/request/request-scope'
import type {
  ApiRequestConfig,
  AttachmentRecord,
  ProcessAttachmentList,
  ProcessDetailPayload,
  AttachmentUploadGrant,
} from '@/types/domain'
import type { DetailAdapter } from './approval-detail'
import { uploadAttachment } from './attachments'

interface HttpDetailOptions {
  openScope: () => RequestScope
  entryId(): string
  storage: Pick<Storage, 'getItem' | 'setItem'>
  navigate(path: string): void
  openDownload(url: string, fileName: string): void
}

export function createHttpDetailAdapter(options: HttpDetailOptions): DetailAdapter {
  function scoped(isCurrent: () => boolean): DetailAdapter {
    const scope = options.openScope()
    const request = <TData = unknown>(config: ApiRequestConfig) =>
      scope.request<TData>(config, isCurrent)
    return {
      forOpening: scoped,
      entryId: () => options.entryId(),
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
              classifySubmissionFailure(error, 'process-approval') === 'refused'
                ? 'rejected'
                : 'uncertain',
            message: error instanceof Error ? error.message : '无法确认处理结果',
          }
        }
      },
      // 撤回/催办（#287，ADR 0068）：失败一律 classifySubmissionFailure 二分，
      // refused 是服务端命名拒绝（确定未提交），uncertain 才进待确认。
      async withdraw(processId) {
        try {
          await request({ method: 'post', url: `/admin/process/withdraw/${processId}` })
          return { kind: 'success' }
        } catch (error: unknown) {
          return {
            kind:
              classifySubmissionFailure(error, 'process-approval') === 'refused'
                ? 'rejected'
                : 'uncertain',
            message: error instanceof Error ? error.message : '无法确认撤回结果',
          }
        }
      },
      async urge(processId) {
        try {
          await request({ method: 'post', url: `/admin/process/urge/${processId}` })
          return { kind: 'success' }
        } catch (error: unknown) {
          return {
            kind:
              classifySubmissionFailure(error, 'process-approval') === 'refused'
                ? 'rejected'
                : 'uncertain',
            message: error instanceof Error ? error.message : '无法确认催办结果',
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
