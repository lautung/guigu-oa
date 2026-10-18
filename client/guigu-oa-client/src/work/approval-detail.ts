import { reactive } from 'vue'
import { presentDetail, type ProcessDetailViewModel } from './pending-flow'
import type { WorkListPerspective } from './work-list'
import type {
  AttachmentRecord,
  ProcessAttachmentList,
  EntityId,
  ProcessDetailPayload,
} from '@/types/domain'

export interface DetailTarget {
  processId: EntityId
  taskId?: EntityId
  perspective: WorkListPerspective
}
export interface Decision {
  processId: EntityId
  taskId: EntityId
  status: 1 | -1
}
export interface DecisionResult {
  kind: 'success' | 'rejected' | 'uncertain'
  message?: string
}
export interface RecoveryRecord extends Decision {
  operationId: string
}
export interface DetailAdapter {
  forOpening?(isCurrent: () => boolean): DetailAdapter
  entryId(): string
  read(processId: EntityId): Promise<ProcessDetailPayload>
  navigate(path: string): void
  submit(decision: Decision): Promise<DecisionResult>
  listAttachments(processId: EntityId): Promise<ProcessAttachmentList>
  upload(processId: EntityId, file: File): Promise<AttachmentRecord>
  download(processId: EntityId, attachment: AttachmentRecord): Promise<void>
  loadRecords(key: string): unknown
  saveRecords(key: string, records: RecoveryRecord[]): void
}
function message(error: unknown, fallback: string) {
  return error instanceof Error ? error.message : fallback
}
function forbidden(error: unknown) {
  return !!error && typeof error === 'object' && 'kind' in error && error.kind === 'forbidden'
}
function validRecords(value: unknown): value is RecoveryRecord[] {
  return (
    Array.isArray(value) &&
    value.every(
      (record) =>
        record &&
        typeof record.operationId === 'string' &&
        record.operationId &&
        ['string', 'number'].includes(typeof record.processId) &&
        String(record.processId) &&
        ['string', 'number'].includes(typeof record.taskId) &&
        String(record.taskId) &&
        (record.status === 1 || record.status === -1),
    )
  )
}

/** The page issues intentions here; this boundary owns opening, decision and recovery ordering. */
export function createApprovalDetail(adapter: DetailAdapter) {
  let releaseCurrent = () => {}
  function open(input: DetailTarget) {
    releaseCurrent()
    const target = { ...input, perspective: { ...input.perspective } }
    const entryId = adapter.entryId()
    const key = `oa-approval-recovery:${entryId}`
    let released = false
    const current = () => !released && !!entryId && adapter.entryId() === entryId
    const io = adapter.forOpening?.(current) || adapter
    const state = reactive({
      loading: true,
      detail: null as ProcessDetailViewModel | null,
      canSubmit: false,
      error: '',
      stale: false,
      submitting: false,
      actionError: '',
      recordError: '',
      recoveries: [] as RecoveryRecord[],
      ended: false,
      attachments: [] as AttachmentRecord[],
      attachmentLoading: false,
      attachmentBusy: false,
      attachmentError: '',
      attachmentUploadAllowed: false,
      canAddAttachments: false,
      downloadingId: null as EntityId | null,
      returnPath: target.perspective.returnPath,
    })
    function readRecords(): RecoveryRecord[] {
      const records = adapter.loadRecords(key)
      if (!validRecords(records))
        throw new Error('处理恢复记录损坏，已暂停新的处理，请保留当前记录并联系支持')
      return records
    }
    function refreshRecords() {
      try {
        state.recoveries = readRecords().filter(
          (record) => String(record.processId) === String(target.processId),
        )
      } catch (error: unknown) {
        state.recordError = message(error, '无法读取处理恢复记录，已暂停新的处理')
      }
    }
    function updateRecords(change: (records: RecoveryRecord[]) => RecoveryRecord[]): boolean {
      try {
        adapter.saveRecords(key, change(readRecords()))
        if (current()) {
          refreshRecords()
          if (state.recordError) return false
        }
        return true
      } catch (error: unknown) {
        if (current()) {
          state.recordError = message(error, '无法保存处理恢复记录，已暂停新的处理')
          state.canSubmit = false
        }
        return false
      }
    }
    function eligible() {
      state.canSubmit =
        current() &&
        !state.loading &&
        !state.stale &&
        !state.recordError &&
        !state.submitting &&
        !state.attachmentBusy &&
        !state.ended &&
        target.perspective.kind === 'pending' &&
        !!target.taskId &&
        !!state.detail?.canApprove &&
        !state.recoveries.some((record) => String(record.taskId) === String(target.taskId))
      state.canAddAttachments =
        current() &&
        !!state.detail &&
        state.attachmentUploadAllowed &&
        !state.attachmentLoading &&
        !state.attachmentBusy &&
        !state.submitting &&
        !state.recoveries.length &&
        !state.recordError
    }
    refreshRecords()
    const release = () => {
      released = true
      state.canSubmit = false
      state.canAddAttachments = false
    }
    releaseCurrent = release
    let readNumber = 0
    async function reload() {
      if (!current()) return
      const request = ++readNumber
      state.loading = true
      state.canSubmit = false
      state.error = ''
      let loaded = false
      try {
        const payload = await io.read(target.processId)
        if (!current() || request !== readNumber) return
        if (!payload.process || String(payload.process.id) !== String(target.processId))
          throw new Error('详情响应无法确认，请重试')
        state.detail = presentDetail(payload, target.perspective)
        state.stale = false
        loaded = true
      } catch (error: unknown) {
        if (!current() || request !== readNumber) return
        if (forbidden(error)) {
          state.detail = null
          state.attachments = []
          state.attachmentUploadAllowed = false
        }
        state.stale = !!state.detail
        state.error = message(error, '详情读取失败')
      } finally {
        if (current() && request === readNumber) {
          state.loading = false
          eligible()
        }
      }
      if (loaded && current()) await reloadAttachments()
    }
    async function decide(status: 1 | -1) {
      eligible()
      if (!state.canSubmit || !target.taskId) return
      const decision: Decision = { processId: target.processId, taskId: target.taskId, status }
      const record: RecoveryRecord = { ...decision, operationId: crypto.randomUUID() }
      if (!updateRecords((records) => [...records, record])) return
      state.submitting = true
      state.actionError = ''
      eligible()
      let result: DecisionResult
      try {
        result = await io.submit(decision)
      } catch (error: unknown) {
        result = { kind: 'uncertain', message: message(error, '无法确认处理结果') }
      }
      // Even after release a reliable response may conclude its own record, never the new opening.
      const settled =
        result.kind !== 'uncertain' &&
        updateRecords((records) =>
          records.filter((item) => item.operationId !== record.operationId),
        )
      if (!current()) return
      state.submitting = false
      if (result.kind === 'success' && settled) {
        release()
        adapter.navigate('/work/processed')
        return
      }
      state.actionError =
        result.message ||
        (result.kind === 'uncertain'
          ? '处理结果待确认，请核实后结束本次记录'
          : '处理未获接受，请刷新详情核实当前资格')
      if (result.kind === 'rejected') state.stale = true
      eligible()
    }
    function canEndRecovery(operationId: string) {
      return (
        current() &&
        !state.submitting &&
        target.perspective.kind === 'pending' &&
        state.recoveries.some(
          (record) =>
            record.operationId === operationId && String(record.taskId) === String(target.taskId),
        )
      )
    }
    function endRecovery(operationId: string) {
      if (!canEndRecovery(operationId)) return
      if (
        updateRecords((records) => records.filter((record) => record.operationId !== operationId))
      ) {
        state.ended = true
        state.actionError = ''
        eligible()
      }
    }
    let attachmentRead = 0
    async function reloadAttachments() {
      if (!current() || state.attachmentBusy) return
      const request = ++attachmentRead
      state.attachmentLoading = true
      state.attachmentError = ''
      eligible()
      try {
        const response = await io.listAttachments(target.processId)
        if (!current() || request !== attachmentRead) return
        state.attachments = response.attachments
        state.attachmentUploadAllowed = response.canUpload
      } catch (error: unknown) {
        if (current() && request === attachmentRead)
          state.attachmentError = message(error, '附件读取失败，请重试')
      } finally {
        if (current() && request === attachmentRead) {
          state.attachmentLoading = false
          eligible()
        }
      }
    }
    async function addAttachments(files: File[]) {
      eligible()
      if (!state.canAddAttachments || !files.length) return
      state.attachmentBusy = true
      state.attachmentError = ''
      eligible()
      try {
        for (const file of [...files]) {
          if (!current()) break
          try {
            const attachment = await io.upload(target.processId, file)
            if (current()) state.attachments.push(attachment)
          } catch (error: unknown) {
            if (current()) state.attachmentError = message(error, '附件上传失败')
          }
        }
      } finally {
        if (current()) {
          state.attachmentBusy = false
          eligible()
        }
      }
    }
    async function downloadAttachment(attachment: AttachmentRecord) {
      if (!current()) return
      state.downloadingId = attachment.id
      try {
        await io.download(target.processId, { ...attachment })
      } catch (error: unknown) {
        if (current()) state.attachmentError = message(error, '附件下载失败')
      } finally {
        if (current()) state.downloadingId = null
      }
    }
    function navigate(path: string) {
      if (current()) {
        release()
        adapter.navigate(path)
      }
    }
    const back = () => navigate(target.perspective.returnPath)
    const viewProcessed = () => navigate('/work/processed')
    const returnPending = () => navigate('/work/pending')
    const ready = reload()
    return {
      state,
      ready,
      release,
      reload,
      back,
      decide,
      endRecovery,
      canEndRecovery,
      viewProcessed,
      returnPending,
      reloadAttachments,
      addAttachments,
      downloadAttachment,
    }
  }
  return { open }
}
export type ApprovalDetailOpening = ReturnType<ReturnType<typeof createApprovalDetail>['open']>
