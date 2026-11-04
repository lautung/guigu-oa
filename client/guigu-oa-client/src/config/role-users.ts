import { readonly, shallowRef, type DeepReadonly, type Ref } from 'vue'
import { getDefaultPostLoginRoute } from '@/navigation/constants'
import type { EntityId } from '@/types/domain'
import type { AssignResult } from './assign-roles'

export interface RoleUser {
  id: EntityId
  username: string
  name?: string
  phone?: string
}

export interface RoleUserTarget {
  id: EntityId
  roleName?: string
}

export interface RoleCandidatePage {
  users: RoleUser[]
  total: number
}

export interface RoleUsersAdapter {
  entryId(): string
  capture(): { generation: number; entryId: string }
  storage: Pick<Storage, 'getItem' | 'setItem' | 'removeItem'> & { keys(): string[] }
  readMembers(roleId: EntityId): Promise<RoleUser[]>
  readCandidates(
    roleId: EntityId,
    query: { keyword: string; page: number; size: number },
  ): Promise<RoleCandidatePage>
  assign(roleId: EntityId, userIds: readonly EntityId[]): Promise<AssignResult>
  refreshOperator(): Promise<void>
  confirm(message: string, kind: 'leave' | 'end-assignment'): Promise<boolean>
  dismissConfirm(): void
  notify(message: string, kind: 'success' | 'warning' | 'error'): void
  replace(path: string): void
}

type Phase = 'loading' | 'ready' | 'saving' | 'unconfirmed' | 'read-error' | 'saved' | 'closed'

interface SavedRoleUsers {
  version: 1
  entry: string
  roleId: EntityId
  roleName: string
  phase: 'sending' | 'unconfirmed'
  submittedUsers: RoleUser[]
  submittedUserIds: EntityId[]
}

interface LatestMembers {
  members: RoleUser[]
}

interface Session {
  key: string
  entry: string
  target: { id: EntityId; roleName: string }
  phase: Phase
  members: RoleUser[]
  candidates: RoleUser[]
  originalMemberIds: EntityId[]
  keyword: string
  page: number
  size: number
  total: number
  submittedUserIds: EntityId[]
  submittedUsers: RoleUser[]
  latest: LatestMembers | null
  reading: boolean
  error: string
  storageFailed: boolean
  readNumber: number
  pageNumber: number
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

export interface RoleUsersView {
  roleId: EntityId | null
  roleName: string
  phase: Phase
  members: RoleUser[]
  candidates: RoleUser[]
  keyword: string
  page: number
  size: number
  total: number
  submittedUserIds: EntityId[]
  submittedUsers: RoleUser[]
  latest: LatestMembers | null
  reading: boolean
  error: string
  canSearch: boolean
  canMove: boolean
  canSave: boolean
  canReRead: boolean
  canEnd: boolean
}

export interface RoleUsersLease {
  view: DeepReadonly<Ref<RoleUsersView>>
  released: DeepReadonly<Ref<boolean>>
  ready: Promise<void>
  add(userIds: readonly EntityId[]): boolean
  remove(userIds: readonly EntityId[]): boolean
  search(keyword: string): Promise<void>
  goPage(page: number): Promise<void>
  save(): Promise<void>
  reload(): Promise<void>
  endAssignment(): Promise<boolean>
  requestLeave(): Promise<boolean>
  environmentChanged(): Promise<void>
  dispose(): void
}

export interface RoleUsersModule {
  open(target: RoleUserTarget): RoleUsersLease
  pendingRoleIds: DeepReadonly<Ref<readonly string[]>>
}

const PREFIX = 'oa-role-users:v1:'
const DEFAULT_SIZE = 10

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

function keyFor(entry: string, roleId: EntityId): string {
  return `${PREFIX}${encodeURIComponent(entry)}:${encodeURIComponent(String(roleId))}`
}

function presentUsers(value: unknown): RoleUser[] {
  if (!Array.isArray(value)) throw new Error('用户读取结果无效，请重试')
  const users = value.map((item) => {
    if (!isObject(item) || !isId(item.id)) throw new Error('用户读取结果无效，请重试')
    return {
      id: item.id,
      username:
        typeof item.username === 'string' && item.username ? item.username : String(item.id),
      name: typeof item.name === 'string' ? item.name : undefined,
      phone: typeof item.phone === 'string' ? item.phone : undefined,
    }
  })
  const ids = uniqueIds(users.map((user) => user.id))
  if (ids.length !== users.length) throw new Error('用户读取结果无效，请重试')
  return users
}

function presentCandidatePage(value: unknown): RoleCandidatePage {
  if (!isObject(value)) throw new Error('用户读取结果无效，请重试')
  const users = presentUsers(value.users)
  const total =
    typeof value.total === 'number' && Number.isFinite(value.total) && value.total >= 0
      ? Math.max(value.total, users.length)
      : users.length
  return { users, total }
}

function savedRoleUsers(raw: string): SavedRoleUsers {
  const value: unknown = JSON.parse(raw)
  if (
    !isObject(value) ||
    value.version !== 1 ||
    typeof value.entry !== 'string' ||
    !isId(value.roleId) ||
    typeof value.roleName !== 'string' ||
    !['sending', 'unconfirmed'].includes(String(value.phase)) ||
    !Array.isArray(value.submittedUsers) ||
    !value.submittedUsers.every(
      (user: unknown) => isObject(user) && isId(user.id) && typeof user.username === 'string',
    ) ||
    !Array.isArray(value.submittedUserIds) ||
    !value.submittedUserIds.every(isId)
  )
    throw new Error('分配记录无法恢复')
  return value as unknown as SavedRoleUsers
}

/** Owns one role membership session per entry; a drawer only leases its read-only presentation. */
export function createRoleUsers(adapter: RoleUsersAdapter) {
  const sessions = new Map<string, Session>()
  const pendingRoleIds = shallowRef<readonly string[]>([])

  function publish(session: Session): void {
    session.listeners.forEach((listener) => listener())
  }

  function currentPending(): void {
    const entry = adapter.entryId()
    pendingRoleIds.value = [...sessions.values()]
      .filter((session) => session.phase === 'unconfirmed' && session.entry === entry)
      .map((session) => String(session.target.id))
  }

  function owns(session: Session): boolean {
    return sessions.get(session.key) === session && session.entry === adapter.entryId()
  }

  function projection(session: Session): RoleUsersView {
    const editable = session.phase === 'ready' && !session.reading
    return copy({
      roleId: session.target.id,
      roleName: session.target.roleName,
      phase: session.phase,
      members: session.members,
      candidates: session.candidates,
      keyword: session.keyword,
      page: session.page,
      size: session.size,
      total: session.total,
      submittedUserIds: session.submittedUserIds,
      submittedUsers: session.submittedUsers,
      latest: session.latest,
      reading: session.reading,
      error: session.error,
      canSearch: editable,
      canMove: editable,
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
    const record: SavedRoleUsers = {
      version: 1,
      entry: session.entry,
      roleId: session.target.id,
      roleName: session.target.roleName,
      phase,
      submittedUsers: copy(session.submittedUsers),
      submittedUserIds: copy(session.submittedUserIds),
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

  function restored(record: SavedRoleUsers, key: string): Session {
    return {
      key,
      entry: record.entry,
      target: { id: record.roleId, roleName: record.roleName },
      phase: 'unconfirmed',
      members: copy(record.submittedUsers),
      candidates: [],
      originalMemberIds: copy(record.submittedUserIds),
      keyword: '',
      page: 1,
      size: DEFAULT_SIZE,
      total: 0,
      submittedUserIds: copy(record.submittedUserIds),
      submittedUsers: copy(record.submittedUsers),
      latest: null,
      reading: false,
      error: '',
      storageFailed: false,
      readNumber: 0,
      pageNumber: 0,
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
        const record = savedRoleUsers(raw)
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
      adapter.notify('未能恢复上次分配记录，请核实相关角色的当前成员后再分配', 'warning')
    currentPending()
  }

  function candidatesWithoutMembers(session: Session, users: RoleUser[]): RoleUser[] {
    const memberIds = new Set(session.members.map((user) => String(user.id)))
    return users.filter((user) => !memberIds.has(String(user.id)))
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
      if (mode === 'verify') {
        const members = await adapter.readMembers(session.target.id)
        if (!owns(session) || number !== session.readNumber) return
        session.latest = { members: presentUsers(members) }
        session.error = ''
      } else {
        const [members, page] = await Promise.all([
          adapter.readMembers(session.target.id),
          adapter.readCandidates(session.target.id, {
            keyword: session.keyword,
            page: session.page,
            size: session.size,
          }),
        ])
        if (!owns(session) || number !== session.readNumber) return
        const presentedMembers = presentUsers(members)
        const presentedPage = presentCandidatePage(page)
        session.members = presentedMembers
        session.originalMemberIds = presentedMembers.map((user) => user.id)
        session.candidates = candidatesWithoutMembers(session, presentedPage.users)
        session.total = presentedPage.total
        session.phase = 'ready'
        session.error = ''
      }
    } catch (error) {
      if (!owns(session) || number !== session.readNumber) return
      session.error = messageOf(
        error,
        mode === 'verify' ? '最新成员读取失败，请重试' : '成员与用户读取失败，请重试',
      )
      if (mode === 'initial') session.phase = 'read-error'
    } finally {
      if (owns(session) && number === session.readNumber) {
        session.reading = false
        publish(session)
      }
    }
  }

  async function readCandidatePage(session: Session): Promise<void> {
    if (session.phase !== 'ready') return
    const number = ++session.pageNumber
    session.reading = true
    publish(session)
    try {
      const page = await adapter.readCandidates(session.target.id, {
        keyword: session.keyword,
        page: session.page,
        size: session.size,
      })
      if (!owns(session) || number !== session.pageNumber) return
      const presented = presentCandidatePage(page)
      session.candidates = candidatesWithoutMembers(session, presented.users)
      session.total = presented.total
      session.error = ''
    } catch (error) {
      if (!owns(session) || number !== session.pageNumber) return
      session.error = messageOf(error, '用户读取失败，请重试')
    } finally {
      if (owns(session) && number === session.pageNumber) {
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

  function sessionFor(target: RoleUserTarget): Session {
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
        const record = savedRoleUsers(raw)
        if (record.entry === entry && String(record.roleId) === String(target.id)) {
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
      adapter.notify('未能恢复上次分配记录，请核实该角色的当前成员后再分配', 'warning')
    }
    const created: Session = {
      key,
      entry,
      target: {
        id: target.id,
        roleName: target.roleName || '',
      },
      phase: 'loading',
      members: [],
      candidates: [],
      originalMemberIds: [],
      keyword: '',
      page: 1,
      size: DEFAULT_SIZE,
      total: 0,
      submittedUserIds: [],
      submittedUsers: [],
      latest: null,
      reading: false,
      error: '',
      storageFailed: false,
      readNumber: 0,
      pageNumber: 0,
      listeners: new Set(),
    }
    sessions.set(key, created)
    currentPending()
    return created
  }

  function open(target: RoleUserTarget): RoleUsersLease {
    const session = sessionFor(target)
    if (session.target.roleName !== target.roleName && target.roleName)
      session.target = { id: session.target.id, roleName: target.roleName }
    const state: LeaseState = { active: true, session, leaving: false, ending: false }
    session.lease = state
    const view = shallowRef<RoleUsersView>(projection(session))
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
      if (state.leaving) return false
      state.leaving = true
      try {
        if (['saved', 'closed'].includes(session.phase)) {
          releaseLease()
          return true
        }
        let message = ''
        if (session.phase === 'saving') {
          message = '正在保存成员分配。离开不撤销已提交的保存，原请求仍可能生效，确认关闭？'
        } else if (session.phase === 'unconfirmed') {
          message = '本次分配结果仍待确认。关闭后仍须核实，原保存仍可能生效，确认关闭？'
        } else if (
          session.phase === 'ready' &&
          !sameIds(
            session.members.map((u) => u.id),
            session.originalMemberIds,
          )
        ) {
          message = '当前有未保存的成员调整，离开将丢弃本次修改，确认关闭？'
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

    async function refreshAfterSave(session: Session): Promise<void> {
      try {
        const [members, page] = await Promise.all([
          adapter.readMembers(session.target.id),
          adapter.readCandidates(session.target.id, {
            keyword: '',
            page: 1,
            size: session.size,
          }),
        ])
        if (sessions.get(session.key) !== session) return
        const presentedMembers = presentUsers(members)
        const presentedPage = presentCandidatePage(page)
        session.members = presentedMembers
        session.originalMemberIds = presentedMembers.map((user) => user.id)
        session.keyword = ''
        session.page = 1
        session.candidates = candidatesWithoutMembers(session, presentedPage.users)
        session.total = presentedPage.total
      } catch {
        adapter.notify('成员已保存，但列表刷新失败，请稍后重新进入', 'warning')
      }
    }

    async function save(): Promise<void> {
      if (!active() || session.phase !== 'ready' || session.reading) return
      session.submittedUserIds = copy(uniqueIds(session.members.map((user) => user.id)))
      session.submittedUsers = copy(session.members)
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
        result = await adapter.assign(session.target.id, session.submittedUserIds)
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
        if (sameIdentity && active()) adapter.notify('分配用户成功', 'success')
        if (sameIdentity) {
          await refreshAfterSave(session)
          try {
            await adapter.refreshOperator()
          } catch {
            const current = adapter.capture()
            if (current.entryId === context.entryId && current.generation === context.generation)
              adapter.notify('角色成员已保存，但资料与菜单刷新失败，请稍后重新进入', 'warning')
          }
        }
        discard(session)
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

    const lease: RoleUsersLease = {
      view: readonly(view),
      released: readonly(released),
      ready: session.preparation || Promise.resolve(),
      add(userIds) {
        if (!active() || session.phase !== 'ready' || session.reading) return false
        const chosen = new Set(userIds.map(String))
        const moved = session.candidates.filter((user) => chosen.has(String(user.id)))
        if (!moved.length) return false
        const movedIds = new Set(moved.map((user) => String(user.id)))
        session.candidates = session.candidates.filter((user) => !movedIds.has(String(user.id)))
        session.members = [...session.members, ...moved]
        session.total = Math.max(0, session.total - moved.length)
        session.error = ''
        publish(session)
        return true
      },
      remove(userIds) {
        if (!active() || session.phase !== 'ready' || session.reading) return false
        const chosen = new Set(userIds.map(String))
        const moved = session.members.filter((user) => chosen.has(String(user.id)))
        if (!moved.length) return false
        const movedIds = new Set(moved.map((user) => String(user.id)))
        session.members = session.members.filter((user) => !movedIds.has(String(user.id)))
        session.candidates = [...moved, ...session.candidates]
        session.total += moved.length
        session.error = ''
        publish(session)
        return true
      },
      async search(keyword) {
        // 新搜索直接取代在途的旧搜索：旧结果按 pageNumber 丢弃，不阻塞输入。
        if (!active() || session.phase !== 'ready') return
        session.keyword = keyword
        session.page = 1
        await readCandidatePage(session)
      },
      async goPage(page) {
        if (!active() || session.phase !== 'ready') return
        if (!Number.isFinite(page) || page < 1) return
        session.page = page
        await readCandidatePage(session)
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
  return { open, pendingRoleIds: readonly(pendingRoleIds) }
}
