import { reactive } from 'vue'
import { WORK_LIST_KINDS, workListPath } from '@/router/paths'
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
/** 转办/加签选人（#288）：服务端排除申请人，排除本人由视图本地过滤（写前校验兜底）。 */
export interface ActionTarget {
  id: EntityId
  name: string
}
/** 处理动作的恢复记录（旧记录无 action 字段，按处理解读）。 */
export interface DecideRecoveryRecord extends Decision {
  operationId: string
  action?: 'decide'
}
/** 撤回动作的恢复记录：整条审批一个动作，没有任务与方向（ADR 0068）。 */
export interface WithdrawRecoveryRecord {
  operationId: string
  processId: EntityId
  action: 'withdraw'
}
/** 转办动作的恢复记录：审批维度 + 目标用户（ADR 0068，#288）。 */
export interface TransferRecoveryRecord {
  operationId: string
  processId: EntityId
  action: 'transfer'
  targetUserId: EntityId
}
/** 加签动作的恢复记录：审批维度 + 加签类型与加签人清单（1 前加签 / 2 后加签）。 */
export interface AddSignRecoveryRecord {
  operationId: string
  processId: EntityId
  action: 'addSign'
  signType: 1 | 2
  assigneeIds: EntityId[]
}
export type RecoveryRecord =
  DecideRecoveryRecord | WithdrawRecoveryRecord | TransferRecoveryRecord | AddSignRecoveryRecord
export interface DetailAdapter {
  forOpening?(isCurrent: () => boolean): DetailAdapter
  entryId(): string
  read(processId: EntityId): Promise<ProcessDetailPayload>
  navigate(path: string): void
  submit(decision: Decision): Promise<DecisionResult>
  withdraw(processId: EntityId): Promise<DecisionResult>
  urge(processId: EntityId): Promise<DecisionResult>
  actionTargets(processId: EntityId): Promise<ActionTarget[]>
  transfer(processId: EntityId, targetUserId: EntityId): Promise<DecisionResult>
  addSign(processId: EntityId, signType: 1 | 2, assigneeIds: EntityId[]): Promise<DecisionResult>
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
    value.every((record) => {
      if (!record || typeof record.operationId !== 'string' || !record.operationId) return false
      if (!['string', 'number'].includes(typeof record.processId) || !String(record.processId))
        return false
      // 撤回记录只有审批维度；转办带目标用户；加签带类型与加签人清单。
      if (record.action === 'withdraw') return true
      if (record.action === 'transfer')
        return (
          ['string', 'number'].includes(typeof record.targetUserId) && !!String(record.targetUserId)
        )
      if (record.action === 'addSign')
        return (
          (record.signType === 1 || record.signType === 2) &&
          Array.isArray(record.assigneeIds) &&
          record.assigneeIds.length > 0
        )
      // 处理记录（含无 action 的历史记录）必须有任务与方向。
      return (
        ['string', 'number'].includes(typeof record.taskId) &&
        String(record.taskId) &&
        (record.status === 1 || record.status === -1)
      )
    })
  )
}

interface AttachmentBatch {
  busy: boolean
  openings: Set<() => void>
}
// A route remount creates a new factory. Keep only in-flight batch timing in this JS process,
// scoped to the original entry and approval; file content and presentation stay with the opening.
const attachmentBatches = new Map<string, AttachmentBatch>()

/** The page issues intentions here; this boundary owns opening, decision and recovery ordering. */
export function createApprovalDetail(adapter: DetailAdapter) {
  let releaseCurrent = () => {}
  function open(input: DetailTarget) {
    releaseCurrent()
    const target = { ...input, perspective: { ...input.perspective } }
    const entryId = adapter.entryId()
    const key = `oa-approval-recovery:${entryId}`
    const batchKey = JSON.stringify([entryId, String(target.processId)])
    const attachmentBatch = attachmentBatches.get(batchKey) || {
      busy: false,
      openings: new Set<() => void>(),
    }
    attachmentBatches.set(batchKey, attachmentBatch)
    function discardIdleBatch() {
      if (
        !attachmentBatch.busy &&
        !attachmentBatch.openings.size &&
        attachmentBatches.get(batchKey) === attachmentBatch
      )
        attachmentBatches.delete(batchKey)
    }
    function setBatchBusy(busy: boolean) {
      attachmentBatch.busy = busy
      for (const update of attachmentBatch.openings) update()
      discardIdleBatch()
    }
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
      attachmentBusy: attachmentBatch.busy,
      attachmentError: '',
      attachmentUploadAllowed: false,
      canAddAttachments: false,
      downloadingId: null as EntityId | null,
      returnPath: target.perspective.returnPath,
      showWithdraw: false,
      canWithdraw: false,
      withdrawing: false,
      showUrge: false,
      canUrge: false,
      urgeBusy: false,
      urgeDone: false,
      showTransfer: false,
      canTransfer: false,
      transferring: false,
      showAddSign: false,
      canAddSign: false,
      addingSign: false,
      actionTargets: [] as ActionTarget[],
      actionTargetsLoading: false,
      actionTargetsError: '',
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
      state.attachmentBusy = attachmentBatch.busy
      state.canSubmit =
        current() &&
        !state.loading &&
        !state.stale &&
        !state.recordError &&
        !state.submitting &&
        !state.withdrawing &&
        !state.attachmentBusy &&
        !state.ended &&
        target.perspective.kind === 'pending' &&
        !!target.taskId &&
        !!state.detail?.canApprove &&
        !state.recoveries.some(
          (record) =>
            (!record.action || record.action === 'decide') &&
            'taskId' in record &&
            String(record.taskId) === String(target.taskId),
        )
      state.canAddAttachments =
        current() &&
        !!state.detail &&
        state.attachmentUploadAllowed &&
        !state.attachmentLoading &&
        !state.attachmentBusy &&
        !state.submitting &&
        !state.recoveries.length &&
        !state.recordError
      // 申请人动作（#287，ADR 0068）：撤回/催办只出现在「已发起」视角的审批中，不进待处理主格。
      const applicantActive =
        current() &&
        !!state.detail &&
        state.detail.status === 1 &&
        target.perspective.kind === 'started'
      state.showWithdraw = applicantActive
      state.showUrge = applicantActive
      state.canWithdraw =
        applicantActive &&
        !state.loading &&
        !state.stale &&
        !state.recordError &&
        !state.submitting &&
        !state.withdrawing &&
        !state.recoveries.some((record) => record.action === 'withdraw')
      state.canUrge =
        applicantActive &&
        !state.loading &&
        !state.stale &&
        !state.submitting &&
        !state.withdrawing &&
        !state.urgeBusy &&
        !state.urgeDone
      // 转办/加签（#288，ADR 0068）：当前处理人且未挂起（挂起行与加签行不出现这两个动作）。
      const handlerActive =
        current() &&
        !!state.detail &&
        state.detail.canApprove &&
        target.perspective.kind === 'pending' &&
        !state.detail.pendingRow?.suspended &&
        state.detail.pendingRow?.actionKind !== 'ADD_SIGN'
      state.showTransfer = handlerActive
      state.showAddSign = handlerActive
      state.canTransfer =
        handlerActive &&
        !state.loading &&
        !state.stale &&
        !state.recordError &&
        !state.submitting &&
        !state.transferring &&
        !state.addingSign &&
        !state.recoveries.length
      state.canAddSign =
        handlerActive &&
        !state.loading &&
        !state.stale &&
        !state.recordError &&
        !state.submitting &&
        !state.transferring &&
        !state.addingSign &&
        !state.recoveries.length
    }
    attachmentBatch.openings.add(eligible)
    refreshRecords()
    const release = () => {
      released = true
      attachmentBatch.openings.delete(eligible)
      discardIdleBatch()
      state.canSubmit = false
      state.canAddAttachments = false
      state.showWithdraw = false
      state.canWithdraw = false
      state.showUrge = false
      state.canUrge = false
      state.showTransfer = false
      state.canTransfer = false
      state.showAddSign = false
      state.canAddSign = false
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
      const record: RecoveryRecord = {
        ...decision,
        operationId: crypto.randomUUID(),
        action: 'decide',
      }
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
        adapter.navigate(workListPath(WORK_LIST_KINDS.PROCESSED))
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
    /**
     * 撤回（ADR 0068，#287）：仅申请人、仅审批中、整条结束不可恢复。
     * 提交走「结果待确认」：refused 是确定未提交，unconfirmed 留恢复记录（动作维度键），
     * 人工核实后结束。成功后重读详情展示已撤回，不导航。
     */
    async function withdraw() {
      eligible()
      if (!state.canWithdraw) return
      const record: RecoveryRecord = {
        operationId: crypto.randomUUID(),
        processId: target.processId,
        action: 'withdraw',
      }
      if (!updateRecords((records) => [...records, record])) return
      state.withdrawing = true
      state.actionError = ''
      eligible()
      let result: DecisionResult
      try {
        result = await io.withdraw(target.processId)
      } catch (error: unknown) {
        result = { kind: 'uncertain', message: message(error, '无法确认撤回结果') }
      }
      // 与处理动作同一份可靠结论语义：只有非 uncertain 且记录可清理才算收口。
      const settled =
        result.kind !== 'uncertain' &&
        updateRecords((records) =>
          records.filter((item) => item.operationId !== record.operationId),
        )
      if (!current()) return
      state.withdrawing = false
      if (result.kind === 'success' && settled) {
        await reload()
        return
      }
      state.actionError =
        result.message ||
        (result.kind === 'uncertain'
          ? '撤回结果待确认，请核实后结束本次记录'
          : '撤回未获接受，请刷新详情核实当前资格')
      if (result.kind === 'rejected') state.stale = true
      eligible()
    }
    /**
     * 催办（ADR 0068，#287）：非阻塞动作。不改状态、不建恢复记录——
     * 24 小时频控在服务端，重复提交以命名拒绝收敛；成功后本开合内按钮停用。
     */
    async function urge() {
      eligible()
      if (!state.canUrge) return
      state.urgeBusy = true
      state.actionError = ''
      eligible()
      let result: DecisionResult
      try {
        result = await io.urge(target.processId)
      } catch (error: unknown) {
        result = { kind: 'uncertain', message: message(error, '无法确认催办结果') }
      }
      if (!current()) return
      state.urgeBusy = false
      if (result.kind === 'success') {
        state.urgeDone = true
        eligible()
        return
      }
      state.actionError = result.message || '催办未获接受，请稍后重试'
      eligible()
    }
    /**
     * 转办（#288，ADR 0068）：当前处理人把审批永久交给一个启用用户；成功后本处理人
     * 变为转办经手人（只读），待处理行消失，回到待处理列表。提交走「结果待确认」：
     * refused 是确定未提交（含目标非法 201），unconfirmed 留恢复记录，人工核实后结束。
     */
    async function transfer(targetUserId: EntityId) {
      eligible()
      if (!state.canTransfer) return
      const record: RecoveryRecord = {
        operationId: crypto.randomUUID(),
        processId: target.processId,
        action: 'transfer',
        targetUserId,
      }
      if (!updateRecords((records) => [...records, record])) return
      state.transferring = true
      state.actionError = ''
      eligible()
      let result: DecisionResult
      try {
        result = await io.transfer(target.processId, targetUserId)
      } catch (error: unknown) {
        result = { kind: 'uncertain', message: message(error, '无法确认转办结果') }
      }
      const settled =
        result.kind !== 'uncertain' &&
        updateRecords((records) =>
          records.filter((item) => item.operationId !== record.operationId),
        )
      if (!current()) return
      state.transferring = false
      if (result.kind === 'success' && settled) {
        release()
        adapter.navigate(workListPath(WORK_LIST_KINDS.PENDING))
        return
      }
      state.actionError =
        result.message ||
        (result.kind === 'uncertain'
          ? '转办结果待确认，请核实后结束本次记录'
          : '转办未获接受，请刷新详情核实当前资格')
      if (result.kind === 'rejected') state.stale = true
      eligible()
    }

    /**
     * 加签（#288，ADR 0068）：signType 1 前加签（本行转挂起，等待加签人）或 2 后加签
     * （通过已记账、本行离开待处理）。成功后回待处理列表看新行状态。
     */
    async function addSign(signType: 1 | 2, assigneeIds: EntityId[]) {
      eligible()
      if (!state.canAddSign || !assigneeIds.length) return
      const record: RecoveryRecord = {
        operationId: crypto.randomUUID(),
        processId: target.processId,
        action: 'addSign',
        signType,
        assigneeIds,
      }
      if (!updateRecords((records) => [...records, record])) return
      state.addingSign = true
      state.actionError = ''
      eligible()
      let result: DecisionResult
      try {
        result = await io.addSign(target.processId, signType, assigneeIds)
      } catch (error: unknown) {
        result = { kind: 'uncertain', message: message(error, '无法确认加签结果') }
      }
      const settled =
        result.kind !== 'uncertain' &&
        updateRecords((records) =>
          records.filter((item) => item.operationId !== record.operationId),
        )
      if (!current()) return
      state.addingSign = false
      if (result.kind === 'success' && settled) {
        release()
        adapter.navigate(workListPath(WORK_LIST_KINDS.PENDING))
        return
      }
      state.actionError =
        result.message ||
        (result.kind === 'uncertain'
          ? '加签结果待确认，请核实后结束本次记录'
          : '加签未获接受，请刷新详情核实当前资格')
      if (result.kind === 'rejected') state.stale = true
      eligible()
    }

    /** 转办/加签候选目标（#288）：登录即可读；打开弹层时取一次，排除本人由视图本地过滤。 */
    let targetsRead = 0
    async function loadActionTargets() {
      if (!current()) return
      const request = ++targetsRead
      state.actionTargetsLoading = true
      state.actionTargetsError = ''
      try {
        const targets = await io.actionTargets(target.processId)
        if (!current() || request !== targetsRead) return
        state.actionTargets = targets
      } catch (error: unknown) {
        if (current() && request === targetsRead)
          state.actionTargetsError = message(error, '候选目标读取失败，请重试')
      } finally {
        if (current() && request === targetsRead) {
          state.actionTargetsLoading = false
        }
      }
    }

    function canEndRecovery(operationId: string) {
      if (!current() || state.submitting || state.withdrawing) return false
      const record = state.recoveries.find((item) => item.operationId === operationId)
      if (!record) return false
      // 撤回记录按审批维度核实（已发起视角）；处理记录仍按任务维度（待处理视角）；
      // 转办/加签记录（#288）按审批维度核实（待处理视角发起的动作）。
      if (record.action === 'withdraw') {
        return (
          target.perspective.kind === 'started' &&
          String(record.processId) === String(target.processId)
        )
      }
      if (record.action === 'transfer' || record.action === 'addSign') {
        return (
          target.perspective.kind === 'pending' &&
          String(record.processId) === String(target.processId)
        )
      }
      return (
        target.perspective.kind === 'pending' &&
        !!target.taskId &&
        String(record.taskId) === String(target.taskId)
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
      if (!current()) return
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
      setBatchBusy(true)
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
        // Releasing an opening never cancels the already-running upload/registration.
        // Its eventual completion must unlock current openings even when its own view is gone.
        setBatchBusy(false)
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
    const viewProcessed = () => navigate(workListPath(WORK_LIST_KINDS.PROCESSED))
    const returnPending = () => navigate(workListPath(WORK_LIST_KINDS.PENDING))
    const ready = reload()
    return {
      state,
      ready,
      release,
      reload,
      back,
      decide,
      withdraw,
      urge,
      transfer,
      addSign,
      loadActionTargets,
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
