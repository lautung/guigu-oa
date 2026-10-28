import { reactive, readonly, shallowRef, watch, type DeepReadonly, type Ref } from 'vue'
import type { CarbonCopyRecipient } from '@/api/carbon-copy'
import type {
  AttachmentRecord,
  EntityId,
  FormDataRecord,
  FormDesignerOption,
  FormRule,
  ProcessTemplateRecord,
  StartProcessRequest,
} from '@/types/domain'
import { ROUTES } from '@/navigation/constants'
import { canInitiate } from '@/config/process-template'

export interface StartTemplate {
  id: EntityId
  processTypeId: EntityId
  name: string
  rule: FormRule[]
  option: FormDesignerOption
}
export interface DraftAttachmentItem extends AttachmentRecord {
  availability: 'pending' | 'unavailable'
}
interface InterruptedUpload {
  id: string
  name: string
  replaces?: EntityId
}
export type AttachmentReplacement =
  { kind: 'unavailable'; id: EntityId } | { kind: 'interrupted'; id: string }
type Phase = 'editing' | 'sending' | 'unconfirmed' | 'completed'
interface SavedStart {
  version: 1
  id: string
  draftId: string
  phase: Phase
  formData: FormDataRecord
  ccUserIds: EntityId[]
  attachments: DraftAttachmentItem[]
  interrupted: InterruptedUpload[]
  snapshot?: StartProcessRequest
  previousSnapshots: StartProcessRequest[]
  legacyDraftId?: string
}
export interface ApprovalStartView {
  template: StartTemplate | null
  recipients: CarbonCopyRecipient[]
  attachments: DraftAttachmentItem[]
  interrupted: InterruptedUpload[]
  loading: boolean
  reading: boolean
  uploading: number
  deleting: EntityId[]
  attachmentsReady: boolean
  locked: boolean
  attachmentLocked: boolean
  phase: Phase
  error: string
}
/** The designer-facing draft; the module owns it and keeps it equal to the record. */
export interface ApprovalStartDraft {
  formData: FormDataRecord
  ccUserIds: EntityId[]
}
export type StartResult =
  { kind: 'started' } | { kind: 'rejected'; message: string } | { kind: 'unconfirmed' }
export interface ApprovalStartAdapter {
  entryId(): string
  storage: Pick<Storage, 'getItem' | 'setItem' | 'removeItem'>
  id(): string
  readTemplate(id: EntityId): Promise<ProcessTemplateRecord>
  recipients(): Promise<CarbonCopyRecipient[]>
  listDraft(draftId: string): Promise<AttachmentRecord[]>
  upload(file: File, draftId: string, assertCurrent: () => void): Promise<AttachmentRecord>
  remove(id: EntityId): Promise<void>
  start(snapshot: StartProcessRequest): Promise<StartResult>
  notify(message: string, kind: 'success' | 'warning' | 'error'): void
  navigate(path: string): void
}
interface Session {
  key: string
  entry: string
  templateId: EntityId
  record: SavedStart
  storedId?: string
  visit: number
  template: StartTemplate | null
  recipients: CarbonCopyRecipient[]
  loading: boolean
  reading: boolean
  uploading: number
  deleting: Set<EntityId>
  ready: boolean
  error: string
  storageFailed: boolean
  preparation?: Promise<void>
  listeners: Set<() => void>
}
export interface ApprovalStartPage {
  view: DeepReadonly<Ref<ApprovalStartView>>
  draft: ApprovalStartDraft
  ready: Promise<void>
  edit(formData: FormDataRecord, ccUserIds: readonly EntityId[]): boolean
  reload(): Promise<void>
  add(files: readonly File[], replacement?: AttachmentReplacement): Promise<void>
  remove(id: EntityId): Promise<void>
  omitUnavailable(id: EntityId): void
  omitInterrupted(id: string): void
  submit(values: FormDataRecord): Promise<void>
  finishFoundApproval(): void
  prepareStartAgain(acceptPossibleDuplicate: true): Promise<void>
  release(): void
}

const copy = <T>(value: T): T => JSON.parse(JSON.stringify(value)) as T
const isObject = (value: unknown): value is Record<string, unknown> =>
  !!value && typeof value === 'object' && !Array.isArray(value)
const isId = (value: unknown): value is EntityId =>
  (typeof value === 'string' && value.length > 0) ||
  (typeof value === 'number' && Number.isFinite(value))
function validAttachment(value: unknown): value is AttachmentRecord {
  return (
    isObject(value) &&
    isId(value.id) &&
    typeof value.fileName === 'string' &&
    typeof value.sizeBytes === 'number' &&
    Number.isFinite(value.sizeBytes) &&
    value.sizeBytes >= 0
  )
}
function savedStart(raw: string): SavedStart {
  const value: unknown = JSON.parse(raw)
  if (
    !isObject(value) ||
    value.version !== 1 ||
    typeof value.id !== 'string' ||
    typeof value.draftId !== 'string' ||
    (value.legacyDraftId != null && typeof value.legacyDraftId !== 'string') ||
    !['editing', 'sending', 'unconfirmed', 'completed'].includes(String(value.phase)) ||
    !isObject(value.formData) ||
    !Array.isArray(value.ccUserIds) ||
    !value.ccUserIds.every(isId) ||
    !Array.isArray(value.attachments) ||
    !value.attachments.every(
      (a: unknown) =>
        isObject(a) &&
        isId(a.id) &&
        typeof a.fileName === 'string' &&
        typeof a.sizeBytes === 'number' &&
        ['pending', 'unavailable'].includes(String(a.availability)),
    ) ||
    !Array.isArray(value.interrupted) ||
    !value.interrupted.every(
      (u: unknown) =>
        isObject(u) &&
        typeof u.id === 'string' &&
        typeof u.name === 'string' &&
        (u.replaces == null || isId(u.replaces)),
    ) ||
    !Array.isArray(value.previousSnapshots)
  )
    throw new Error('未完成内容无法恢复，请先核实已发起')
  if (
    value.snapshot != null &&
    (!isObject(value.snapshot) ||
      typeof value.snapshot.formValues !== 'string' ||
      !isId(value.snapshot.processTemplateId) ||
      !isId(value.snapshot.processTypeId))
  )
    throw new Error('未完成提交无法恢复，请先核实已发起')
  return value as unknown as SavedStart
}
function parse<T>(value: unknown, fallback: T): T {
  try {
    return typeof value === 'string'
      ? (JSON.parse(value) as T)
      : value == null
        ? fallback
        : (value as T)
  } catch {
    return fallback
  }
}
function legacyDraft(raw: string | null, entry: string): string | null {
  if (!raw) return null
  if (!raw.startsWith('{')) return raw
  const claim: unknown = JSON.parse(raw)
  return isObject(claim) &&
    claim.version === 1 &&
    claim.entry === entry &&
    typeof claim.draftId === 'string'
    ? claim.draftId
    : null
}
function presentTemplate(record: ProcessTemplateRecord): StartTemplate | null {
  if (!canInitiate(record) || !isId(record.id) || !isId(record.processTypeId)) return null
  const rules = parse<unknown>(record.formProps, [])
  const option = parse<unknown>(record.formOptions, {})
  return {
    id: record.id,
    processTypeId: record.processTypeId,
    name: record.name || '发起审批',
    rule: Array.isArray(rules) ? (rules as FormRule[]) : [],
    option: {
      ...(isObject(option) ? option : {}),
      submitBtn: true,
      form: {
        ...(isObject(option) && isObject(option.form) ? option.form : {}),
        labelPosition: 'top',
      },
    },
  }
}
function payload(session: Session): StartProcessRequest {
  const template = session.template!
  const formData = copy(session.record.formData)
  const formShowData: FormDataRecord = {}
  template.rule.forEach((rule) => {
    if (rule.field in formData) formShowData[rule.title] = formData[rule.field]
  })
  return {
    processTemplateId: template.id,
    processTypeId: template.processTypeId,
    formValues: JSON.stringify({ formData, formShowData }),
    attachmentDraftId: session.record.draftId,
    attachmentIds: session.record.attachments.map((a) => a.id),
    ...(session.record.ccUserIds.length ? { ccUserIds: copy(session.record.ccUserIds) } : {}),
  }
}

/** Owns one recoverable submission per entry/template; pages only lease its presentation. */
export function createApprovalStart(adapter: ApprovalStartAdapter) {
  const sessions = new Map<string, Session>()
  let visits = 0
  function owns(session: Session, record = session.record): boolean {
    return (
      adapter.entryId() === session.entry &&
      sessions.get(session.key) === session &&
      session.record === record
    )
  }
  function publish(session: Session) {
    session.listeners.forEach((listener) => listener())
  }
  function persist(session: Session): boolean {
    if (!owns(session)) return false
    try {
      const current = adapter.storage.getItem(session.key)
      if (
        (!current && session.storedId) ||
        (current && savedStart(current).id !== session.storedId)
      )
        throw new Error('未完成内容已变化，请重新打开后核实')
      const legacy = session.record.legacyDraftId
      const legacyKey = `oa-attachment-draft:${session.templateId}`
      if (legacy) {
        const raw = adapter.storage.getItem(legacyKey)
        const claim = JSON.stringify({ version: 1, entry: session.entry, draftId: legacy })
        if (raw === legacy) adapter.storage.setItem(legacyKey, claim)
        else if (raw && raw !== claim) throw new Error('旧附件内容已变化，请重试恢复')
      }
      adapter.storage.setItem(session.key, JSON.stringify(session.record))
      session.storedId = session.record.id
      if (legacy) {
        // Keep the entry claim and recovery marker until consumption succeeds.
        adapter.storage.removeItem(legacyKey)
        delete session.record.legacyDraftId
        adapter.storage.setItem(session.key, JSON.stringify(session.record))
      }
      session.storageFailed = false
      return true
    } catch (error) {
      session.storageFailed = true
      session.error = error instanceof Error ? error.message : '无法保存未完成内容，请重试'
      return false
    }
  }
  function clear(session: Session): boolean {
    if (!owns(session)) return false
    try {
      const raw = adapter.storage.getItem(session.key)
      if (raw && savedStart(raw).id !== session.record.id) return false
      adapter.storage.removeItem(session.key)
      sessions.delete(session.key)
      return true
    } catch {
      session.error = '本次已结束，但未完成内容清理失败，请重试结束本次'
      publish(session)
      return false
    }
  }
  function mutable(session: Session): boolean {
    return session.record.phase === 'editing' && !session.loading
  }
  function busy(session: Session): boolean {
    return session.reading || session.uploading > 0 || session.deleting.size > 0
  }
  function projection(session: Session): ApprovalStartView {
    const locked = !mutable(session)
    return copy({
      template: session.template,
      recipients: session.recipients,
      attachments: session.record.attachments,
      interrupted: session.record.interrupted,
      loading: session.loading,
      reading: session.reading,
      uploading: session.uploading,
      deleting: [...session.deleting],
      attachmentsReady: session.ready,
      locked,
      attachmentLocked: locked || busy(session),
      phase: session.record.phase,
      error: session.error,
    })
  }
  async function readAttachments(session: Session): Promise<void> {
    if (busy(session)) return
    const record = session.record
    session.reading = true
    session.ready = false
    publish(session)
    try {
      const attachments = await adapter.listDraft(record.draftId)
      if (!owns(session, record)) return
      if (!Array.isArray(attachments) || !attachments.every(validAttachment))
        throw new Error('附件读取结果无效')
      const remote = new Map(attachments.map((a) => [String(a.id), a]))
      record.attachments = record.attachments.map((a) => ({
        ...a,
        ...(remote.get(String(a.id)) || {}),
        availability: remote.has(String(a.id)) ? 'pending' : 'unavailable',
      }))
      for (const attachment of attachments) {
        if (!record.attachments.some((a) => String(a.id) === String(attachment.id)))
          record.attachments.push({ ...attachment, availability: 'pending' })
      }
      session.ready = true
      session.error = ''
      persist(session)
    } catch (error) {
      if (owns(session, record))
        session.error = error instanceof Error ? error.message : '附件读取失败，请重试'
    } finally {
      session.reading = false
      publish(session)
    }
  }
  function prepare(session: Session): Promise<void> {
    if (session.preparation) return session.preparation
    const record = session.record
    session.loading = true
    publish(session)
    const task: Promise<void> = Promise.resolve().then(async () => {
      try {
        const template = await adapter.readTemplate(session.templateId)
        if (!owns(session, record)) return
        session.template = presentTemplate(template)
        if (!session.template) {
          session.error =
            template?.status === 1
              ? template.availabilityMessage || '暂不可发起，需核实原审批内容'
              : '审批模板未发布或不存在，不能发起'
          return
        }
        const recipients = await adapter.recipients()
        if (!owns(session, record)) return
        session.recipients = recipients
        await readAttachments(session)
      } catch (error) {
        if (owns(session, record))
          session.error = error instanceof Error ? error.message : '发起内容读取失败，请重试'
      } finally {
        session.loading = false
        if (session.preparation === task) session.preparation = undefined
        publish(session)
      }
    })
    session.preparation = task
    return task
  }
  function open(templateId: EntityId): ApprovalStartPage {
    const entry = adapter.entryId()
    const key = `oa-start:v1:${encodeURIComponent(entry)}:${encodeURIComponent(String(templateId))}`
    let session = sessions.get(key)
    if (!session) {
      let error = ''
      let record: SavedStart
      let storedId: string | undefined
      const legacyKey = `oa-attachment-draft:${templateId}`
      try {
        const raw = adapter.storage.getItem(key)
        if (raw) {
          record = savedStart(raw)
          storedId = record.id
        } else {
          // Legacy storage contains only a draft ID. Remote metadata still checks its owner.
          const legacy = legacyDraft(adapter.storage.getItem(legacyKey), entry)
          record = {
            version: 1,
            id: adapter.id(),
            draftId: legacy || adapter.id(),
            phase: 'editing',
            formData: {},
            ccUserIds: [],
            attachments: [],
            interrupted: [],
            previousSnapshots: [],
            ...(legacy ? { legacyDraftId: legacy } : {}),
          }
        }
      } catch (reason) {
        error = reason instanceof Error ? reason.message : '无法恢复未完成内容'
        record = {
          version: 1,
          id: adapter.id(),
          draftId: adapter.id(),
          phase: 'unconfirmed',
          formData: {},
          ccUserIds: [],
          attachments: [],
          interrupted: [],
          previousSnapshots: [],
        }
      }
      if (record.phase === 'sending') record.phase = 'unconfirmed'
      session = {
        key,
        entry,
        templateId,
        record,
        storedId,
        visit: 0,
        template: null,
        recipients: [],
        loading: false,
        reading: false,
        uploading: 0,
        deleting: new Set(),
        ready: false,
        error,
        storageFailed: !!error,
        listeners: new Set(),
      }
      sessions.set(key, session)
      if (!error) persist(session)
    }
    const current = session
    const visit = ++visits
    current.visit = visit
    let active = true
    const isActive = () => active && owns(current) && current.visit === visit
    const draft = reactive<ApprovalStartDraft>({
      formData: copy(current.record.formData),
      ccUserIds: copy([...current.record.ccUserIds]),
    })
    const view = shallowRef(projection(current))
    const refreshView = () => {
      if (isActive()) view.value = projection(current)
    }
    current.listeners.add(refreshView)
    const notify = (message: string, kind: 'success' | 'warning' | 'error') => {
      if (isActive()) adapter.notify(message, kind)
    }
    function editable(): boolean {
      if (!isActive() || !mutable(current)) return false
      return true
    }
    function draftMatchesRecord(): boolean {
      return (
        JSON.stringify(draft.formData) === JSON.stringify(current.record.formData) &&
        JSON.stringify(draft.ccUserIds) === JSON.stringify(current.record.ccUserIds)
      )
    }
    let reflowing = false
    function reflowDraft(): void {
      reflowing = true
      try {
        draft.formData = copy(current.record.formData)
        draft.ccUserIds = copy([...current.record.ccUserIds])
      } finally {
        reflowing = false
      }
    }
    function changed() {
      persist(current)
      publish(current)
    }
    let stopDraft = () => {}
    const page: ApprovalStartPage = {
      view: readonly(view),
      draft,
      ready: Promise.resolve(),
      edit(formData, ccUserIds) {
        if (!editable()) return false
        try {
          if (!isObject(formData)) throw new Error('表单内容无效')
          const values = copy(formData),
            users = copy([...ccUserIds])
          if (
            JSON.stringify(values) === JSON.stringify(current.record.formData) &&
            JSON.stringify(users) === JSON.stringify(current.record.ccUserIds)
          )
            return !current.storageFailed
          current.record.formData = values
          current.record.ccUserIds = users
          if (draft.formData !== formData) reflowDraft()
          changed()
          return !current.storageFailed
        } catch {
          notify('表单内容无法保存，请检查后重试', 'error')
          return false
        }
      },
      async reload() {
        if (isActive()) await prepare(current)
      },
      async add(files, replacement) {
        if (!editable() || busy(current) || !current.ready || !files.length) return
        const record = current.record
        if (
          replacement &&
          (files.length !== 1 ||
            (replacement.kind === 'unavailable'
              ? !record.attachments.some(
                  (a) =>
                    String(a.id) === String(replacement.id) && a.availability === 'unavailable',
                )
              : !record.interrupted.some((u) => u.id === replacement.id)))
        )
          return
        const interrupted =
          replacement?.kind === 'interrupted'
            ? record.interrupted.find((u) => u.id === replacement.id)
            : replacement?.kind === 'unavailable'
              ? record.interrupted.find((u) => String(u.replaces) === String(replacement.id))
              : undefined
        const replaces =
          replacement?.kind === 'unavailable' ? replacement.id : interrupted?.replaces
        const tasks = files.map((file) => ({
          file,
          id: interrupted?.id || adapter.id(),
          name: file.name,
          replaces,
        }))
        current.uploading += tasks.length
        for (const { id, name, replaces } of tasks) {
          const existing = record.interrupted.find((u) => u.id === id)
          if (existing) existing.name = name
          else record.interrupted.push({ id, name, ...(replaces == null ? {} : { replaces }) })
        }
        changed()
        const assertCurrent = () => {
          if (!owns(current, record)) throw new Error('本次发起已变化')
        }
        try {
          for (const task of tasks) {
            try {
              assertCurrent()
              const attachment = await adapter.upload(task.file, record.draftId, assertCurrent)
              assertCurrent()
              if (!validAttachment(attachment)) throw new Error('附件登记结果无效，请重试读取')
              if (task.replaces != null)
                record.attachments = record.attachments.filter(
                  (a) =>
                    !(String(a.id) === String(task.replaces) && a.availability === 'unavailable'),
                )
              record.attachments.push({ ...attachment, availability: 'pending' })
              record.interrupted = record.interrupted.filter((u) => u.id !== task.id)
              persist(current)
            } catch (error) {
              if (owns(current, record))
                notify(error instanceof Error ? error.message : '附件上传失败', 'error')
            } finally {
              current.uploading -= 1
              publish(current)
            }
          }
        } finally {
          publish(current)
        }
      },
      async remove(id) {
        if (!editable() || busy(current)) return
        const record = current.record
        if (
          !record.attachments.some(
            (a) => String(a.id) === String(id) && a.availability === 'pending',
          )
        )
          return
        current.deleting.add(id)
        publish(current)
        try {
          await adapter.remove(id)
          if (!owns(current, record)) return
          record.attachments = record.attachments.filter((a) => String(a.id) !== String(id))
          persist(current)
        } catch (error) {
          if (owns(current, record)) {
            current.ready = false
            current.error = '附件删除结果需要核实，请重试读取'
            notify(error instanceof Error ? error.message : '附件删除失败', 'error')
          }
        } finally {
          current.deleting.delete(id)
          publish(current)
        }
      },
      omitUnavailable(id) {
        if (!editable() || busy(current)) return
        current.record.attachments = current.record.attachments.filter(
          (a) => !(String(a.id) === String(id) && a.availability === 'unavailable'),
        )
        changed()
      },
      omitInterrupted(id) {
        if (!editable() || busy(current)) return
        current.record.interrupted = current.record.interrupted.filter((u) => u.id !== id)
        changed()
      },
      async submit(values) {
        if (!isActive()) return
        if (!mutable(current)) {
          if (current.record.phase === 'unconfirmed') notify('请先核实本次发起结果', 'warning')
          return
        }
        if (!current.template) {
          notify('审批模板不可发起，请重试读取', 'warning')
          return
        }
        if (busy(current)) {
          notify('请等待附件读取、上传或删除完成', 'warning')
          return
        }
        if (!current.ready) {
          notify('请先重试读取附件', 'warning')
          return
        }
        if (
          current.record.interrupted.length ||
          current.record.attachments.some((a) => a.availability !== 'pending')
        ) {
          notify('请先处理未完成或不可用的附件', 'warning')
          return
        }
        if (!page.edit(values, current.record.ccUserIds)) return
        const record = current.record
        if (current.storageFailed) {
          notify('无法保存未完成内容，请重试读取后再发起', 'error')
          return
        }
        try {
          record.snapshot = payload(current)
        } catch {
          notify('表单内容无法提交，请检查后重试', 'error')
          return
        }
        record.phase = 'sending'
        if (!persist(current)) {
          record.phase = 'editing'
          publish(current)
          notify('无法保存本次提交，尚未发起', 'error')
          return
        }
        publish(current)
        let result: StartResult
        try {
          result = await adapter.start(copy(record.snapshot))
        } catch {
          result = { kind: 'unconfirmed' }
        }
        if (!owns(current, record)) return
        if (result.kind === 'started') {
          record.phase = 'completed'
          persist(current)
          publish(current)
          if (isActive()) {
            clear(current)
            // clear drops ownership, so presentation is checked before that operation.
            if (active && current.visit === visit && adapter.entryId() === current.entry) {
              adapter.notify('发起成功', 'success')
              adapter.navigate(ROUTES.STARTED)
            }
          }
        } else if (result.kind === 'rejected') {
          record.phase = 'editing'
          persist(current)
          publish(current)
          notify(result.message, 'error')
        } else {
          record.phase = 'unconfirmed'
          persist(current)
          publish(current)
        }
      },
      finishFoundApproval() {
        if (!isActive() || !['unconfirmed', 'completed'].includes(current.record.phase)) return
        current.record.phase = 'completed'
        publish(current)
        if (clear(current)) adapter.navigate(ROUTES.STARTED)
      },
      async prepareStartAgain(accepted) {
        if (
          !isActive() ||
          accepted !== true ||
          current.record.phase !== 'unconfirmed' ||
          current.storageFailed
        )
          return
        if (busy(current) || current.loading) {
          notify('请等待附件读取、上传或删除完成后再核实', 'warning')
          return
        }
        const previous = current.record
        current.record = {
          ...copy(previous),
          id: adapter.id(),
          phase: 'editing',
          previousSnapshots: [
            ...previous.previousSnapshots,
            ...(previous.snapshot ? [previous.snapshot] : []),
          ],
          snapshot: undefined,
        }
        if (!persist(current)) {
          current.record = previous
          publish(current)
          return
        }
        current.ready = false
        await readAttachments(current)
      },
      release() {
        active = false
        stopDraft()
        current.listeners.delete(refreshView)
      },
    }
    stopDraft = watch(
      () => [draft.formData, draft.ccUserIds],
      () => {
        if (reflowing || !isActive()) return
        if (editable()) {
          page.edit(draft.formData, draft.ccUserIds)
          return
        }
        if (!draftMatchesRecord()) reflowDraft()
      },
      { deep: true, flush: 'sync' },
    )
    page.ready = prepare(current)
    publish(current)
    return page
  }
  return { open }
}
