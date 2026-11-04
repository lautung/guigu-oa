import { readonly, shallowRef, type DeepReadonly, type Ref } from 'vue'
import { getDefaultPostLoginRoute } from '@/navigation/constants'
import type { EntityId } from '@/types/domain'

/**
 * 分配机的会话生命周期唯一 owner（ADR 0006）：phase、lease、待确认记录、
 * 读取序号与迟到归属、保存结果分类、离开/结束/身份替换判定都在这里。
 * 两个分配面（用户侧分配角色、角色侧分配用户）只提供目标、读取、提交、
 * 投影与文案适配；本文件不 import 任何域业务类型（仅共享原语 EntityId）。
 */

export type AssignmentPhase =
  'loading' | 'ready' | 'saving' | 'unconfirmed' | 'read-error' | 'saved' | 'closed'

export type AssignmentOutcome =
  { kind: 'saved' } | { kind: 'rejected'; message: string } | { kind: 'unconfirmed' }

export interface AssignmentStorage {
  getItem(key: string): string | null
  setItem(key: string, value: string): void
  removeItem(key: string): void
  keys(): string[]
}

export interface AssignmentEnvironment {
  entryId(): string
  capture(): { generation: number; entryId: string }
  storage: AssignmentStorage
  refreshOperator(): Promise<void>
  confirm(message: string, kind: 'leave' | 'end-assignment'): Promise<boolean>
  dismissConfirm(): void
  notify(message: string, kind: 'success' | 'warning' | 'error'): void
  replace(path: string): void
}

/** 待确认记录的规范化形状；域 JSON 形状由适配器 encode/decode 拥有。 */
export interface AssignmentRecord<TTarget, TItem> {
  entry: string
  target: TTarget
  phase: 'sending' | 'unconfirmed'
  ids: EntityId[]
  items: TItem[]
}

/** 交给域适配器的会话句柄：域动作据此守卫、改域状态并刷新视图。 */
export interface AssignmentLeaseHandle<TTarget, TState> {
  readonly target: TTarget
  readonly state: TState
  phase(): AssignmentPhase
  reading(): boolean
  active(): boolean
  owns(): boolean
  setError(message: string): void
  clearError(): void
  beginReading(): void
  endReading(): void
  refresh(): void
}

/** phase 派生的操作资格；域视图投影据此给出 can* 字段。 */
export interface AssignmentCapabilities {
  editable: boolean
  confirmable: boolean
}

export interface AssignmentViewInput<TTarget, TState, TItem, TLatest> {
  target: TTarget
  state: TState
  phase: AssignmentPhase
  reading: boolean
  error: string
  submittedIds: EntityId[]
  submittedItems: TItem[]
  latest: TLatest | null
  capabilities: AssignmentCapabilities
}

export interface AssignmentLeaseBase<TView> {
  view: DeepReadonly<Ref<TView>>
  released: DeepReadonly<Ref<boolean>>
  ready: Promise<void>
  save(): Promise<void>
  reload(): Promise<void>
  endAssignment(): Promise<boolean>
  requestLeave(): Promise<boolean>
  environmentChanged(): Promise<void>
  dispose(): void
}

export interface AssignmentMachineAdapter<
  TTarget extends { id: EntityId },
  TState,
  TItem,
  TLatest,
  TView,
  TActions extends object,
> extends AssignmentEnvironment {
  recordPrefix: string
  normalizeTarget(target: TTarget): TTarget
  refreshTarget(current: TTarget, incoming: TTarget): TTarget
  createState(): TState
  restoreState(record: AssignmentRecord<TTarget, TItem>): TState
  decodeRecord(raw: string): AssignmentRecord<TTarget, TItem>
  encodeRecord(record: AssignmentRecord<TTarget, TItem>): string
  readInitial(handle: AssignmentLeaseHandle<TTarget, TState>): Promise<void>
  readVerify(handle: AssignmentLeaseHandle<TTarget, TState>): Promise<TLatest>
  hasUnsavedChanges(handle: AssignmentLeaseHandle<TTarget, TState>): boolean
  buildSubmission(handle: AssignmentLeaseHandle<TTarget, TState>): {
    ids: EntityId[]
    items: TItem[]
  }
  assign(targetId: EntityId, ids: readonly EntityId[]): Promise<AssignmentOutcome>
  afterSaved(handle: AssignmentLeaseHandle<TTarget, TState>): Promise<void>
  projectView(input: AssignmentViewInput<TTarget, TState, TItem, TLatest>): TView
  leaseActions(handle: AssignmentLeaseHandle<TTarget, TState>): TActions
  saveSuccessMessage: string
  operatorRefreshFailedMessage: string
  initialReadErrorFallback: string
  verifyReadErrorFallback: string
  leaveWhileSavingMessage: string
  unsavedChangesMessage: string
  restoreOneFailedMessage: string
  restoreScanFailedMessage: string
}

// 以下四条在两域旧实现逐字相同，属 ADR 0006 合同语义，由机器拥有；域差异文案走适配器注入。
const UNCONFIRMED_LEAVE_MESSAGE =
  '本次分配结果仍待确认。关闭后仍须核实，原保存仍可能生效，确认关闭？'
const END_ASSIGNMENT_MESSAGE =
  '原保存仍可能生效，并可能影响后续分配。结束本次分配只清理本地记录，不代表原保存失败，也不恢复原提交内容为可再次保存的勾选。'
const RETENTION_FAILED_MESSAGE = '无法可靠保留本次分配记录，尚未保存，请检查浏览器存储后重试'
const RECORD_CLEANUP_FAILED_SUFFIX = '；本地记录清理失败，请刷新后核实'

export const copy = <T>(value: T): T => JSON.parse(JSON.stringify(value)) as T
export const messageOf = (error: unknown, fallback: string): string =>
  error instanceof Error && error.message ? error.message : fallback
export const sameIds = (left: readonly EntityId[], right: readonly EntityId[]): boolean => {
  const a = left.map(String).sort()
  const b = right.map(String).sort()
  return a.length === b.length && a.every((id, index) => id === b[index])
}
export const uniqueIds = (values: readonly EntityId[]): EntityId[] => {
  const seen = new Set<string>()
  return values.filter((id) => {
    const key = String(id)
    if (seen.has(key)) return false
    seen.add(key)
    return true
  })
}
export const isId = (value: unknown): value is EntityId =>
  (typeof value === 'string' && value.length > 0) ||
  (typeof value === 'number' && Number.isFinite(value))

interface LeaseState {
  active: boolean
  leaving: boolean
  ending: boolean
}

interface MachineSession<TTarget, TState, TItem, TLatest> {
  key: string
  entry: string
  target: TTarget
  state: TState
  phase: AssignmentPhase
  submittedIds: EntityId[]
  submittedItems: TItem[]
  latest: TLatest | null
  reading: boolean
  error: string
  storageFailed: boolean
  readNumber: number
  preparation?: Promise<void>
  lease?: LeaseState
  listeners: Set<() => void>
}

/** Owns one assignment session per entry; a dialog or drawer only leases its read-only view. */
export function createAssignmentMachine<
  TTarget extends { id: EntityId },
  TState,
  TItem,
  TLatest,
  TView,
  TActions extends object,
>(
  adapter: AssignmentMachineAdapter<TTarget, TState, TItem, TLatest, TView, TActions>,
): {
  open(target: TTarget): AssignmentLeaseBase<TView> & TActions
  pendingIds: DeepReadonly<Ref<readonly string[]>>
} {
  type Session = MachineSession<TTarget, TState, TItem, TLatest>
  const sessions = new Map<string, Session>()
  const pendingIds = shallowRef<readonly string[]>([])

  function publish(session: Session): void {
    session.listeners.forEach((listener) => listener())
  }

  function currentPending(): void {
    const entry = adapter.entryId()
    pendingIds.value = [...sessions.values()]
      .filter((session) => session.phase === 'unconfirmed' && session.entry === entry)
      .map((session) => String(session.target.id))
  }

  function isRegistered(session: Session): boolean {
    return sessions.get(session.key) === session
  }

  function ownsSession(session: Session): boolean {
    return isRegistered(session) && session.entry === adapter.entryId()
  }

  function projection(session: Session): TView {
    const editable = session.phase === 'ready' && !session.reading
    const confirmable = session.phase === 'unconfirmed' && !session.reading
    return copy(
      adapter.projectView({
        target: session.target,
        state: session.state,
        phase: session.phase,
        reading: session.reading,
        error: session.error,
        submittedIds: session.submittedIds,
        submittedItems: session.submittedItems,
        latest: session.latest,
        capabilities: { editable, confirmable },
      }),
    )
  }

  function discard(session: Session): void {
    sessions.delete(session.key)
    currentPending()
  }

  function removeRecord(session: Session): boolean {
    try {
      adapter.storage.removeItem(session.key)
      session.storageFailed = false
      return true
    } catch {
      session.storageFailed = true
      return false
    }
  }

  function persist(session: Session, phase: 'sending' | 'unconfirmed'): boolean {
    const record: AssignmentRecord<TTarget, TItem> = {
      entry: session.entry,
      target: session.target,
      phase,
      ids: session.submittedIds,
      items: session.submittedItems,
    }
    try {
      adapter.storage.setItem(session.key, adapter.encodeRecord(record))
      session.storageFailed = false
      return true
    } catch {
      session.storageFailed = true
      return false
    }
  }

  function restored(record: AssignmentRecord<TTarget, TItem>, key: string): Session {
    return {
      key,
      entry: record.entry,
      target: record.target,
      state: adapter.restoreState(record),
      phase: 'unconfirmed',
      submittedIds: copy(record.ids),
      submittedItems: copy(record.items),
      latest: null,
      reading: false,
      error: '',
      storageFailed: false,
      readNumber: 0,
      listeners: new Set(),
    }
  }

  function restore(): void {
    let unreadable = 0
    let keys: string[]
    try {
      keys = adapter.storage.keys()
    } catch {
      currentPending()
      return
    }
    for (const key of keys) {
      if (!key.startsWith(adapter.recordPrefix)) continue
      const raw = adapter.storage.getItem(key)
      if (!raw) continue
      try {
        const record = adapter.decodeRecord(raw)
        if (record.entry !== adapter.entryId()) continue
        const session = restored(record, key)
        sessions.set(key, session)
        if (record.phase === 'sending') persist(session, 'unconfirmed')
      } catch {
        unreadable += 1
        try {
          adapter.storage.removeItem(key)
        } catch {
          /* Keep the unreadable record rather than silently claiming recovery. */
        }
      }
    }
    if (unreadable > 0) adapter.notify(adapter.restoreScanFailedMessage, 'warning')
    currentPending()
  }

  async function read(
    session: Session,
    handle: AssignmentLeaseHandle<TTarget, TState>,
    mode: 'initial' | 'verify',
  ): Promise<void> {
    if (session.reading) return
    if (mode === 'initial' && !['loading', 'read-error'].includes(session.phase)) return
    if (mode === 'verify' && session.phase !== 'unconfirmed') return
    const number = ++session.readNumber
    session.reading = true
    if (mode === 'initial') {
      session.phase = 'loading'
      session.error = ''
    }
    publish(session)
    try {
      if (mode === 'verify') {
        const latest = await adapter.readVerify(handle)
        if (!ownsSession(session) || number !== session.readNumber) return
        session.latest = latest
        session.error = ''
      } else {
        await adapter.readInitial(handle)
        if (!ownsSession(session) || number !== session.readNumber) return
        session.phase = 'ready'
        session.error = ''
      }
    } catch (error) {
      if (!ownsSession(session) || number !== session.readNumber) return
      session.error = messageOf(
        error,
        mode === 'verify' ? adapter.verifyReadErrorFallback : adapter.initialReadErrorFallback,
      )
      if (mode === 'initial') session.phase = 'read-error'
    } finally {
      if (ownsSession(session) && number === session.readNumber) {
        session.reading = false
        publish(session)
      }
    }
  }

  function startRead(
    session: Session,
    handle: AssignmentLeaseHandle<TTarget, TState>,
  ): Promise<void> {
    if (session.preparation) return session.preparation
    const task = read(session, handle, 'initial').finally(() => {
      if (session.preparation === task) session.preparation = undefined
    })
    session.preparation = task
    return task
  }

  function sessionFor(target: TTarget): Session {
    const entry = adapter.entryId()
    const key = `${adapter.recordPrefix}${encodeURIComponent(entry)}:${encodeURIComponent(String(target.id))}`
    let session = sessions.get(key)
    if (session && ['saved', 'closed'].includes(session.phase)) {
      sessions.delete(key)
      session = undefined
    }
    if (session && session.phase === 'ready' && !session.lease) {
      sessions.delete(key)
      session = undefined
    }
    if (session) return session
    try {
      const raw = adapter.storage.getItem(key)
      if (raw) {
        const record = adapter.decodeRecord(raw)
        if (record.entry === entry && String(record.target.id) === String(target.id)) {
          const loaded = restored(record, key)
          sessions.set(key, loaded)
          if (record.phase === 'sending') persist(loaded, 'unconfirmed')
          currentPending()
          return loaded
        }
      }
    } catch {
      try {
        adapter.storage.removeItem(key)
      } catch {
        /* An unreadable record stays until the user resolves it. */
      }
      adapter.notify(adapter.restoreOneFailedMessage, 'warning')
    }
    const created: Session = {
      key,
      entry,
      target: adapter.normalizeTarget(target),
      state: adapter.createState(),
      phase: 'loading',
      submittedIds: [],
      submittedItems: [],
      latest: null,
      reading: false,
      error: '',
      storageFailed: false,
      readNumber: 0,
      listeners: new Set(),
    }
    sessions.set(key, created)
    currentPending()
    return created
  }

  restore()

  return {
    open(target: TTarget): AssignmentLeaseBase<TView> & TActions {
      const session = sessionFor(target)
      session.target = adapter.refreshTarget(session.target, target)
      const leaseState: LeaseState = { active: true, leaving: false, ending: false }
      session.lease = leaseState
      const view = shallowRef<TView>(projection(session))
      const released = shallowRef(false)
      let confirming = false
      const refreshView = () => {
        if (leaseState.active) view.value = projection(session)
      }
      session.listeners.add(refreshView)
      const handle: AssignmentLeaseHandle<TTarget, TState> = {
        get target() {
          return session.target
        },
        get state() {
          return session.state
        },
        phase: () => session.phase,
        reading: () => session.reading,
        active: () => leaseState.active && session.lease === leaseState && isRegistered(session),
        owns: () => ownsSession(session),
        setError: (message) => {
          session.error = message
        },
        clearError: () => {
          session.error = ''
        },
        beginReading: () => {
          session.reading = true
          publish(session)
        },
        endReading: () => {
          session.reading = false
          publish(session)
        },
        refresh: () => publish(session),
      }
      if (session.phase === 'loading' && !session.reading) void startRead(session, handle)

      function active(): boolean {
        return leaseState.active && session.lease === leaseState && isRegistered(session)
      }

      function invalid(message: string): void {
        if (active()) adapter.notify(message, 'warning')
      }

      function releaseLease(): void {
        leaseState.active = false
        released.value = true
        session.listeners.delete(refreshView)
        if (session.lease === leaseState) session.lease = undefined
      }

      function dismissPendingConfirm(): void {
        if (confirming) adapter.dismissConfirm()
      }

      function discardIfIdle(): void {
        if (['loading', 'read-error', 'ready'].includes(session.phase)) discard(session)
      }

      async function ask(message: string, kind: 'leave' | 'end-assignment'): Promise<boolean> {
        confirming = true
        try {
          return await adapter.confirm(message, kind)
        } finally {
          confirming = false
        }
      }

      async function requestLeave(): Promise<boolean> {
        if (!leaseState.active) return true
        if (leaseState.leaving) return false
        leaseState.leaving = true
        try {
          if (['saved', 'closed'].includes(session.phase)) {
            releaseLease()
            return true
          }
          let message = ''
          if (session.phase === 'saving') {
            message = adapter.leaveWhileSavingMessage
          } else if (session.phase === 'unconfirmed') {
            message = UNCONFIRMED_LEAVE_MESSAGE
          } else if (session.phase === 'ready' && adapter.hasUnsavedChanges(handle)) {
            message = adapter.unsavedChangesMessage
          }
          if (message && !(await ask(message, 'leave'))) return !active()
          if (!leaseState.active) return true
          discardIfIdle()
          releaseLease()
          return true
        } finally {
          leaseState.leaving = false
        }
      }

      async function environmentChanged(): Promise<void> {
        if (!leaseState.active) return
        const entry = adapter.entryId()
        if (session.entry !== entry) {
          dismissPendingConfirm()
          discardIfIdle()
          releaseLease()
          if (entry) adapter.replace(getDefaultPostLoginRoute())
        }
      }

      async function endAssignment(): Promise<boolean> {
        if (!active() || session.phase !== 'unconfirmed' || session.reading) return false
        if (leaseState.ending) return false
        leaseState.ending = true
        try {
          const accepted = await ask(END_ASSIGNMENT_MESSAGE, 'end-assignment')
          if (!accepted || !active()) return false
          try {
            adapter.storage.removeItem(session.key)
          } catch {
            session.error = '结束未完成，本地记录清理失败，请重试'
            publish(session)
            return false
          }
          session.phase = 'closed'
          publish(session)
          discard(session)
          return true
        } finally {
          leaseState.ending = false
        }
      }

      async function save(): Promise<void> {
        if (!active() || session.phase !== 'ready' || session.reading) return
        const submission = adapter.buildSubmission(handle)
        session.submittedIds = copy(submission.ids)
        session.submittedItems = copy(submission.items)
        if (!persist(session, 'sending')) {
          session.error = RETENTION_FAILED_MESSAGE
          publish(session)
          invalid(RETENTION_FAILED_MESSAGE)
          return
        }
        session.phase = 'saving'
        session.error = ''
        session.latest = null
        publish(session)
        const context = adapter.capture()
        let result: AssignmentOutcome
        try {
          result = await adapter.assign(session.target.id, session.submittedIds)
        } catch {
          result = { kind: 'unconfirmed' }
        }
        if (!isRegistered(session)) return
        const sameIdentity =
          !!context.entryId &&
          adapter.entryId() === context.entryId &&
          adapter.capture().generation === context.generation
        if (result.kind === 'saved') {
          removeRecord(session)
          session.phase = 'saved'
          publish(session)
          if (sameIdentity && active()) adapter.notify(adapter.saveSuccessMessage, 'success')
          if (sameIdentity) await adapter.afterSaved(handle)
          discard(session)
          if (sameIdentity) {
            try {
              await adapter.refreshOperator()
            } catch {
              const current = adapter.capture()
              if (current.entryId === context.entryId && current.generation === context.generation)
                adapter.notify(adapter.operatorRefreshFailedMessage, 'warning')
            }
          }
          return
        }
        if (result.kind === 'rejected') {
          removeRecord(session)
          const detail = session.storageFailed
            ? `${result.message}${RECORD_CLEANUP_FAILED_SUFFIX}`
            : result.message
          if (sameIdentity && active()) {
            session.phase = 'ready'
            session.error = detail
            publish(session)
            adapter.notify(detail, 'error')
          } else {
            session.phase = 'closed'
            publish(session)
            discard(session)
          }
          return
        }
        if (!sameIdentity) {
          removeRecord(session)
          session.phase = 'closed'
          publish(session)
          discard(session)
          return
        }
        session.phase = 'unconfirmed'
        persist(session, 'unconfirmed')
        publish(session)
        currentPending()
      }

      const lease = {
        view: readonly(view),
        released: readonly(released),
        ready: session.preparation || Promise.resolve(),
        ...adapter.leaseActions(handle),
        save,
        async reload() {
          if (!active()) return
          if (session.phase === 'unconfirmed') await read(session, handle, 'verify')
          else if (session.phase === 'read-error') await startRead(session, handle)
        },
        endAssignment,
        requestLeave,
        environmentChanged,
        dispose() {
          releaseLease()
        },
      }
      publish(session)
      return lease as AssignmentLeaseBase<TView> & TActions
    },
    pendingIds: readonly(pendingIds),
  }
}
