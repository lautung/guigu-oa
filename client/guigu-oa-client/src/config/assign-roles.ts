import { readonly, shallowRef, type DeepReadonly, type Ref } from 'vue'
import { getDefaultPostLoginRoute } from '@/navigation/constants'
import type { EntityId } from '@/types/domain'

export interface AssignableRole {
  id: EntityId
  roleName: string
}

export interface AssignRoleTarget {
  id: EntityId
  username: string
}

export interface RoleAssignmentRead {
  roles: AssignableRole[]
  assignedRoleIds: EntityId[]
}

export type AssignResult =
  { kind: 'saved' } | { kind: 'rejected'; message: string } | { kind: 'unconfirmed' }

export type AssignOccupancy = { action: 'allow' } | { action: 'redirect'; to: string }

export interface AssignRolesAdapter {
  entryId(): string
  capture(): { generation: number; entryId: string }
  storage: Pick<Storage, 'getItem' | 'setItem' | 'removeItem'> & { keys(): string[] }
  read(userId: EntityId): Promise<RoleAssignmentRead>
  assign(userId: EntityId, roleIds: readonly EntityId[]): Promise<AssignResult>
  refreshOperator(): Promise<void>
  occupancy(): AssignOccupancy
  confirm(message: string, kind: 'leave' | 'end-assignment'): Promise<boolean>
  dismissConfirm(): void
  notify(message: string, kind: 'success' | 'warning' | 'error'): void
  replace(path: string): void
}

type Phase = 'loading' | 'ready' | 'saving' | 'unconfirmed' | 'read-error' | 'saved' | 'closed'

interface SavedAssign {
  version: 1
  entry: string
  userId: EntityId
  username: string
  phase: 'sending' | 'unconfirmed'
  submittedRoles: AssignableRole[]
  submittedRoleIds: EntityId[]
}

interface LatestRoles {
  roles: AssignableRole[]
  roleIds: EntityId[]
}

interface Session {
  key: string
  entry: string
  target: AssignRoleTarget
  phase: Phase
  roles: AssignableRole[]
  assignedRoleIds: EntityId[]
  selectedRoleIds: EntityId[]
  submittedRoleIds: EntityId[]
  submittedRoles: AssignableRole[]
  latest: LatestRoles | null
  reading: boolean
  error: string
  storageFailed: boolean
  readNumber: number
  preparation?: Promise<void>
  lease?: LeaseState
  listeners: Set<() => void>
}

interface LeaseState {
  active: boolean
  session: Session
  leaving: boolean
  ending: boolean
}

export interface AssignRolesView {
  userId: EntityId | null
  username: string
  phase: Phase
  roles: AssignableRole[]
  selectedRoleIds: EntityId[]
  submittedRoleIds: EntityId[]
  submittedRoles: AssignableRole[]
  latest: LatestRoles | null
  reading: boolean
  error: string
  canSelect: boolean
  canSave: boolean
  canReRead: boolean
  canEnd: boolean
}

export interface AssignRolesLease {
  view: DeepReadonly<Ref<AssignRolesView>>
  released: DeepReadonly<Ref<boolean>>
  ready: Promise<void>
  select(roleIds: readonly EntityId[]): boolean
  save(): Promise<void>
  reload(): Promise<void>
  endAssignment(): Promise<boolean>
  requestLeave(): Promise<boolean>
  environmentChanged(): Promise<void>
  dispose(): void
}

export interface AssignRolesModule {
  open(target: AssignRoleTarget): AssignRolesLease
  pendingUserIds: DeepReadonly<Ref<readonly string[]>>
}

const PREFIX = 'oa-assign:v1:'

const copy = <T>(value: T): T => JSON.parse(JSON.stringify(value)) as T
const isObject = (value: unknown): value is Record<string, unknown> =>
  !!value && typeof value === 'object' && !Array.isArray(value)
const isId = (value: unknown): value is EntityId =>
  (typeof value === 'string' && value.length > 0) ||
  (typeof value === 'number' && Number.isFinite(value))
const messageOf = (error: unknown, fallback: string): string =>
  error instanceof Error && error.message ? error.message : fallback
const sameIds = (left: readonly EntityId[], right: readonly EntityId[]): boolean => {
  const a = left.map(String).sort()
  const b = right.map(String).sort()
  return a.length === b.length && a.every((id, index) => id === b[index])
}
const uniqueIds = (values: readonly EntityId[]): EntityId[] => {
  const seen = new Set<string>()
  return values.filter((id) => {
    const key = String(id)
    if (seen.has(key)) return false
    seen.add(key)
    return true
  })
}

function keyFor(entry: string, userId: EntityId): string {
  return `${PREFIX}${encodeURIComponent(entry)}:${encodeURIComponent(String(userId))}`
}

function savedAssign(raw: string): SavedAssign {
  const value: unknown = JSON.parse(raw)
  if (
    !isObject(value) ||
    value.version !== 1 ||
    typeof value.entry !== 'string' ||
    !isId(value.userId) ||
    typeof value.username !== 'string' ||
    !['sending', 'unconfirmed'].includes(String(value.phase)) ||
    !Array.isArray(value.submittedRoles) ||
    !value.submittedRoles.every(
      (role: unknown) => isObject(role) && isId(role.id) && typeof role.roleName === 'string',
    ) ||
    !Array.isArray(value.submittedRoleIds) ||
    !value.submittedRoleIds.every(isId)
  )
    throw new Error('分配记录无法恢复')
  return value as unknown as SavedAssign
}

function presentRead(value: RoleAssignmentRead): LatestRoles {
  if (!value || !Array.isArray(value.roles) || !Array.isArray(value.assignedRoleIds))
    throw new Error('角色读取结果无效，请重试')
  const roles: AssignableRole[] = value.roles.map((role) => {
    if (!role || !isId(role.id)) throw new Error('角色读取结果无效，请重试')
    return {
      id: role.id,
      roleName:
        typeof role.roleName === 'string' && role.roleName ? role.roleName : String(role.id),
    }
  })
  const ids = uniqueIds(roles.map((role) => role.id))
  if (ids.length !== roles.length || !value.assignedRoleIds.every(isId))
    throw new Error('角色读取结果无效，请重试')
  const known = new Set(ids.map(String))
  const assignedRoleIds = uniqueIds(value.assignedRoleIds)
  if (!assignedRoleIds.every((id) => known.has(String(id))))
    throw new Error('角色读取结果与可分配角色不一致，请重试')
  return { roles, roleIds: assignedRoleIds }
}

/** Owns one assignment per user entry; a dialog only leases its read-only presentation. */
export function createAssignRoles(adapter: AssignRolesAdapter) {
  const sessions = new Map<string, Session>()
  const pendingUserIds = shallowRef<readonly string[]>([])

  function publish(session: Session): void {
    session.listeners.forEach((listener) => listener())
  }

  function currentPending(): void {
    const entry = adapter.entryId()
    pendingUserIds.value = [...sessions.values()]
      .filter((session) => session.phase === 'unconfirmed' && session.entry === entry)
      .map((session) => String(session.target.id))
  }

  function owns(session: Session): boolean {
    return sessions.get(session.key) === session && session.entry === adapter.entryId()
  }

  function projection(session: Session): AssignRolesView {
    const editable = session.phase === 'ready' && !session.reading
    return copy({
      userId: session.target.id,
      username: session.target.username,
      phase: session.phase,
      roles: session.roles,
      selectedRoleIds: session.selectedRoleIds,
      submittedRoleIds: session.submittedRoleIds,
      submittedRoles: session.submittedRoles,
      latest: session.latest,
      reading: session.reading,
      error: session.error,
      canSelect: editable,
      canSave: editable,
      canReRead: session.phase === 'unconfirmed' && !session.reading,
      canEnd: session.phase === 'unconfirmed' && !session.reading,
    })
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
    const record: SavedAssign = {
      version: 1,
      entry: session.entry,
      userId: session.target.id,
      username: session.target.username,
      phase,
      submittedRoles: copy(session.submittedRoles),
      submittedRoleIds: copy(session.submittedRoleIds),
    }
    try {
      adapter.storage.setItem(session.key, JSON.stringify(record))
      session.storageFailed = false
      return true
    } catch {
      session.storageFailed = true
      return false
    }
  }

  function restored(record: SavedAssign, key: string): Session {
    return {
      key,
      entry: record.entry,
      target: { id: record.userId, username: record.username },
      phase: 'unconfirmed',
      roles: copy(record.submittedRoles),
      assignedRoleIds: copy(record.submittedRoleIds),
      selectedRoleIds: copy(record.submittedRoleIds),
      submittedRoleIds: copy(record.submittedRoleIds),
      submittedRoles: copy(record.submittedRoles),
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
      if (!key.startsWith(PREFIX)) continue
      const raw = adapter.storage.getItem(key)
      if (!raw) continue
      try {
        const record = savedAssign(raw)
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
    if (unreadable > 0)
      adapter.notify('未能恢复上次分配记录，请核实相关用户的当前角色后再分配', 'warning')
    currentPending()
  }

  async function read(session: Session, mode: 'initial' | 'verify'): Promise<void> {
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
      const result = await adapter.read(session.target.id)
      if (!owns(session) || number !== session.readNumber) return
      const presented = presentRead(result)
      if (mode === 'verify') {
        session.latest = presented
        session.error = ''
      } else {
        session.roles = presented.roles
        session.assignedRoleIds = presented.roleIds
        session.selectedRoleIds = copy(presented.roleIds)
        session.phase = 'ready'
        session.error = ''
      }
    } catch (error) {
      if (!owns(session) || number !== session.readNumber) return
      session.error = messageOf(
        error,
        mode === 'verify' ? '最新角色读取失败，请重试' : '角色读取失败，请重试',
      )
      if (mode === 'initial') session.phase = 'read-error'
    } finally {
      if (owns(session) && number === session.readNumber) {
        session.reading = false
        publish(session)
      }
    }
  }

  function startRead(session: Session): Promise<void> {
    if (session.preparation) return session.preparation
    const task = read(session, 'initial').finally(() => {
      if (session.preparation === task) session.preparation = undefined
    })
    session.preparation = task
    return task
  }

  function sessionFor(target: AssignRoleTarget): Session {
    const entry = adapter.entryId()
    const key = keyFor(entry, target.id)
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
        const record = savedAssign(raw)
        if (record.entry === entry && String(record.userId) === String(target.id)) {
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
      adapter.notify('未能恢复上次分配记录，请核实该用户的当前角色后再分配', 'warning')
    }
    const created: Session = {
      key,
      entry,
      target: { id: target.id, username: target.username },
      phase: 'loading',
      roles: [],
      assignedRoleIds: [],
      selectedRoleIds: [],
      submittedRoleIds: [],
      submittedRoles: [],
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

  function open(target: AssignRoleTarget): AssignRolesLease {
    const session = sessionFor(target)
    if (session.target.username !== target.username && target.username)
      session.target = { id: session.target.id, username: target.username }
    const state: LeaseState = { active: true, session, leaving: false, ending: false }
    session.lease = state
    const view = shallowRef<AssignRolesView>(projection(session))
    const released = shallowRef(false)
    let confirming = false
    const refreshView = () => {
      if (state.active) view.value = projection(session)
    }
    session.listeners.add(refreshView)
    if (session.phase === 'loading' && !session.reading) void startRead(session)

    function active(): boolean {
      return state.active && session.lease === state && sessions.get(session.key) === session
    }

    function invalid(message: string): void {
      if (active()) adapter.notify(message, 'warning')
    }

    function releaseLease(): void {
      state.active = false
      released.value = true
      session.listeners.delete(refreshView)
      if (session.lease === state) session.lease = undefined
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
      if (!state.active) return true
      if (adapter.occupancy().action === 'redirect') {
        dismissPendingConfirm()
        discardIfIdle()
        releaseLease()
        return true
      }
      if (state.leaving) return false
      state.leaving = true
      try {
        if (['saved', 'closed'].includes(session.phase)) {
          releaseLease()
          return true
        }
        let message = ''
        if (session.phase === 'saving') {
          message = '正在保存角色分配。离开不撤销已提交的保存，原请求仍可能生效，确认关闭？'
        } else if (session.phase === 'unconfirmed') {
          message = '本次分配结果仍待确认。关闭后仍须核实，原保存仍可能生效，确认关闭？'
        } else if (
          session.phase === 'ready' &&
          !sameIds(session.selectedRoleIds, session.assignedRoleIds)
        ) {
          message = '当前有未保存的角色勾选，离开将丢弃本次修改，确认关闭？'
        }
        if (message && !(await ask(message, 'leave'))) return !active()
        if (!state.active) return true
        discardIfIdle()
        releaseLease()
        return true
      } finally {
        state.leaving = false
      }
    }

    async function environmentChanged(): Promise<void> {
      if (!state.active) return
      const entry = adapter.entryId()
      if (session.entry !== entry) {
        dismissPendingConfirm()
        discardIfIdle()
        releaseLease()
        if (entry) adapter.replace(getDefaultPostLoginRoute())
        return
      }
      const occupancy = adapter.occupancy()
      if (occupancy.action === 'redirect') {
        dismissPendingConfirm()
        discardIfIdle()
        releaseLease()
        adapter.replace(occupancy.to)
      }
    }

    async function endAssignment(): Promise<boolean> {
      if (!active() || session.phase !== 'unconfirmed' || session.reading) return false
      if (state.ending) return false
      state.ending = true
      try {
        const accepted = await ask(
          '原保存仍可能生效，并可能影响后续分配。结束本次分配只清理本地记录，不代表原保存失败，也不恢复原提交内容为可再次保存的勾选。',
          'end-assignment',
        )
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
        state.ending = false
      }
    }

    async function save(): Promise<void> {
      if (!active() || session.phase !== 'ready' || session.reading) return
      const known = new Set(session.roles.map((role) => String(role.id)))
      const submitted = uniqueIds(
        session.selectedRoleIds.filter((id) => isId(id) && known.has(String(id))),
      )
      session.submittedRoleIds = copy(submitted)
      session.submittedRoles = copy(
        session.roles.filter((role) => submitted.some((id) => String(id) === String(role.id))),
      )
      if (!persist(session, 'sending')) {
        session.error = '无法可靠保留本次分配记录，尚未保存，请检查浏览器存储后重试'
        publish(session)
        invalid(session.error)
        return
      }
      session.phase = 'saving'
      session.error = ''
      session.latest = null
      publish(session)
      const context = adapter.capture()
      let result: AssignResult
      try {
        result = await adapter.assign(session.target.id, submitted)
      } catch {
        result = { kind: 'unconfirmed' }
      }
      if (sessions.get(session.key) !== session) return
      const sameIdentity =
        !!context.entryId &&
        adapter.entryId() === context.entryId &&
        adapter.capture().generation === context.generation
      if (result.kind === 'saved') {
        removeRecord(session)
        session.phase = 'saved'
        publish(session)
        if (sameIdentity && active()) adapter.notify('分配角色成功', 'success')
        discard(session)
        if (sameIdentity) {
          try {
            await adapter.refreshOperator()
          } catch {
            const current = adapter.capture()
            if (current.entryId === context.entryId && current.generation === context.generation)
              adapter.notify('角色已保存，但资料与菜单刷新失败，请稍后重新进入', 'warning')
          }
        }
        return
      }
      if (result.kind === 'rejected') {
        removeRecord(session)
        const detail = session.storageFailed
          ? `${result.message}；本地记录清理失败，请刷新后核实`
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

    const lease: AssignRolesLease = {
      view: readonly(view),
      released: readonly(released),
      ready: session.preparation || Promise.resolve(),
      select(roleIds) {
        if (!active() || session.phase !== 'ready' || session.reading) return false
        const known = new Set(session.roles.map((role) => String(role.id)))
        session.selectedRoleIds = uniqueIds(
          roleIds.filter((id) => isId(id) && known.has(String(id))),
        )
        session.error = ''
        publish(session)
        return true
      },
      save,
      async reload() {
        if (!active()) return
        if (session.phase === 'unconfirmed') await read(session, 'verify')
        else if (session.phase === 'read-error') await startRead(session)
      },
      endAssignment,
      requestLeave,
      environmentChanged,
      dispose() {
        releaseLease()
      },
    }
    if (session.phase === 'loading' && !session.reading) lease.ready = startRead(session)
    publish(session)
    return lease
  }

  restore()
  return { open, pendingUserIds: readonly(pendingUserIds) }
}
