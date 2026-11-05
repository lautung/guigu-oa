import type { DeepReadonly, Ref } from 'vue'
import type { EntityId } from '@/types/domain'
import {
  createAssignmentMachine,
  copy,
  isId,
  messageOf,
  sameIds,
  uniqueIds,
  type AssignmentLeaseBase,
  type AssignmentLeaseHandle,
  type AssignmentOutcome,
  type AssignmentPhase,
  type AssignmentRecord,
} from './assignment-machine'

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
  assign(roleId: EntityId, userIds: readonly EntityId[]): Promise<AssignmentOutcome>
  refreshOperator(): Promise<void>
  confirm(message: string, kind: 'leave' | 'end-assignment'): Promise<boolean>
  dismissConfirm(): void
  notify(message: string, kind: 'success' | 'warning' | 'error'): void
  replace(path: string): void
}

interface RoleUsersState {
  members: RoleUser[]
  candidates: RoleUser[]
  originalMemberIds: EntityId[]
  keyword: string
  page: number
  size: number
  total: number
  pageNumber: number
}

interface LatestMembers {
  members: RoleUser[]
}

interface SavedRoleUsers {
  version: 1
  entry: string
  roleId: EntityId
  roleName: string
  phase: 'sending' | 'unconfirmed'
  submittedUsers: RoleUser[]
  submittedUserIds: EntityId[]
}

export interface RoleUsersView {
  roleId: EntityId | null
  roleName: string
  phase: AssignmentPhase
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

interface RoleUsersActions {
  add(userIds: readonly EntityId[]): boolean
  remove(userIds: readonly EntityId[]): boolean
  search(keyword: string): Promise<void>
  goPage(page: number): Promise<void>
}

export type RoleUsersLease = AssignmentLeaseBase<RoleUsersView> & RoleUsersActions

export interface RoleUsersModule {
  open(target: RoleUserTarget): RoleUsersLease
  pendingRoleIds: DeepReadonly<Ref<readonly string[]>>
}

const PREFIX = 'oa-role-users:v1:'
const DEFAULT_SIZE = 10

const isObject = (value: unknown): value is Record<string, unknown> =>
  !!value && typeof value === 'object' && !Array.isArray(value)

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

/** 分配用户的域适配：会话生命周期、待确认记录与离开判定在共享分配机（ADR 0006）。 */
export function createRoleUsers(adapter: RoleUsersAdapter) {
  function candidatesWithoutMembers(state: RoleUsersState, users: RoleUser[]): RoleUser[] {
    const memberIds = new Set(state.members.map((user) => String(user.id)))
    return users.filter((user) => !memberIds.has(String(user.id)))
  }

  async function readCandidatePage(
    handle: AssignmentLeaseHandle<RoleUserTarget, RoleUsersState>,
  ): Promise<void> {
    if (handle.phase() !== 'ready') return
    const number = ++handle.state.pageNumber
    handle.beginReading()
    try {
      const page = await adapter.readCandidates(handle.target.id, {
        keyword: handle.state.keyword,
        page: handle.state.page,
        size: handle.state.size,
      })
      if (!handle.owns() || number !== handle.state.pageNumber) return
      const presented = presentCandidatePage(page)
      handle.state.candidates = candidatesWithoutMembers(handle.state, presented.users)
      handle.state.total = presented.total
      handle.clearError()
    } catch (error) {
      if (!handle.owns() || number !== handle.state.pageNumber) return
      handle.setError(messageOf(error, '用户读取失败，请重试'))
    } finally {
      if (handle.owns() && number === handle.state.pageNumber) handle.endReading()
    }
  }

  const machine = createAssignmentMachine<
    RoleUserTarget,
    RoleUsersState,
    RoleUser,
    LatestMembers,
    RoleUsersView,
    RoleUsersActions
  >({
    recordPrefix: PREFIX,
    entryId: adapter.entryId,
    capture: adapter.capture,
    storage: adapter.storage,
    refreshOperator: adapter.refreshOperator,
    confirm: adapter.confirm,
    dismissConfirm: adapter.dismissConfirm,
    notify: adapter.notify,
    replace: adapter.replace,
    normalizeTarget: (target) => ({ id: target.id, roleName: target.roleName || '' }),
    refreshTarget: (current, incoming) =>
      incoming.roleName && incoming.roleName !== current.roleName
        ? { id: current.id, roleName: incoming.roleName }
        : current,
    createState: () => ({
      members: [],
      candidates: [],
      originalMemberIds: [],
      keyword: '',
      page: 1,
      size: DEFAULT_SIZE,
      total: 0,
      pageNumber: 0,
    }),
    restoreState: (record) => ({
      members: copy(record.items),
      candidates: [],
      originalMemberIds: copy(record.ids),
      keyword: '',
      page: 1,
      size: DEFAULT_SIZE,
      total: 0,
      pageNumber: 0,
    }),
    decodeRecord: (raw) => {
      const value = savedRoleUsers(raw)
      return {
        entry: value.entry,
        target: { id: value.roleId, roleName: value.roleName },
        phase: value.phase,
        ids: value.submittedUserIds,
        items: value.submittedUsers,
      }
    },
    encodeRecord: (record: AssignmentRecord<RoleUserTarget, RoleUser>) =>
      JSON.stringify({
        version: 1,
        entry: record.entry,
        roleId: record.target.id,
        roleName: record.target.roleName,
        phase: record.phase,
        submittedUsers: record.items.map(({ id, username, name }) => ({ id, username, name })),
        submittedUserIds: record.ids,
      }),
    readInitial: async (handle) => {
      const [members, page] = await Promise.all([
        adapter.readMembers(handle.target.id),
        adapter.readCandidates(handle.target.id, {
          keyword: handle.state.keyword,
          page: handle.state.page,
          size: handle.state.size,
        }),
      ])
      const presentedMembers = presentUsers(members)
      const presentedPage = presentCandidatePage(page)
      handle.state.members = presentedMembers
      handle.state.originalMemberIds = presentedMembers.map((user) => user.id)
      handle.state.candidates = candidatesWithoutMembers(handle.state, presentedPage.users)
      handle.state.total = presentedPage.total
    },
    readVerify: async (handle) => ({
      members: presentUsers(await adapter.readMembers(handle.target.id)),
    }),
    hasUnsavedChanges: (handle) =>
      !sameIds(
        handle.state.members.map((user) => user.id),
        handle.state.originalMemberIds,
      ),
    buildSubmission: (handle) => ({
      ids: uniqueIds(handle.state.members.map((user) => user.id)),
      items: handle.state.members,
    }),
    assign: adapter.assign,
    afterSaved: async (handle) => {
      try {
        const [members, page] = await Promise.all([
          adapter.readMembers(handle.target.id),
          adapter.readCandidates(handle.target.id, {
            keyword: '',
            page: 1,
            size: handle.state.size,
          }),
        ])
        if (!handle.owns()) return
        const presentedMembers = presentUsers(members)
        const presentedPage = presentCandidatePage(page)
        handle.state.members = presentedMembers
        handle.state.originalMemberIds = presentedMembers.map((user) => user.id)
        handle.state.keyword = ''
        handle.state.page = 1
        handle.state.candidates = candidatesWithoutMembers(handle.state, presentedPage.users)
        handle.state.total = presentedPage.total
      } catch {
        adapter.notify('成员已保存，但列表刷新失败，请稍后重新进入', 'warning')
      }
    },
    projectView: (input) => ({
      roleId: input.target.id,
      // normalizeTarget 已保证 roleName 是字符串；入参类型可选，这里兜底同值。
      roleName: input.target.roleName || '',
      phase: input.phase,
      members: input.state.members,
      candidates: input.state.candidates,
      keyword: input.state.keyword,
      page: input.state.page,
      size: input.state.size,
      total: input.state.total,
      submittedUserIds: input.submittedIds,
      submittedUsers: input.submittedItems,
      latest: input.latest,
      reading: input.reading,
      error: input.error,
      canSearch: input.capabilities.editable,
      canMove: input.capabilities.editable,
      canSave: input.capabilities.editable,
      canReRead: input.capabilities.confirmable,
      canEnd: input.capabilities.confirmable,
    }),
    leaseActions: (handle) => ({
      add(userIds) {
        if (!handle.active() || handle.phase() !== 'ready' || handle.reading()) return false
        const chosen = new Set(userIds.map(String))
        const moved = handle.state.candidates.filter((user) => chosen.has(String(user.id)))
        if (!moved.length) return false
        const movedIds = new Set(moved.map((user) => String(user.id)))
        handle.state.candidates = handle.state.candidates.filter(
          (user) => !movedIds.has(String(user.id)),
        )
        handle.state.members = [...handle.state.members, ...moved]
        handle.state.total = Math.max(0, handle.state.total - moved.length)
        handle.clearError()
        handle.refresh()
        return true
      },
      remove(userIds) {
        if (!handle.active() || handle.phase() !== 'ready' || handle.reading()) return false
        const chosen = new Set(userIds.map(String))
        const moved = handle.state.members.filter((user) => chosen.has(String(user.id)))
        if (!moved.length) return false
        const movedIds = new Set(moved.map((user) => String(user.id)))
        handle.state.members = handle.state.members.filter((user) => !movedIds.has(String(user.id)))
        handle.state.candidates = [...moved, ...handle.state.candidates]
        handle.state.total += moved.length
        handle.clearError()
        handle.refresh()
        return true
      },
      async search(keyword) {
        // 新搜索直接取代在途的旧搜索：旧结果按 pageNumber 丢弃，不阻塞输入。
        if (!handle.active() || handle.phase() !== 'ready') return
        handle.state.keyword = keyword
        handle.state.page = 1
        await readCandidatePage(handle)
      },
      async goPage(page) {
        if (!handle.active() || handle.phase() !== 'ready') return
        if (!Number.isFinite(page) || page < 1) return
        handle.state.page = page
        await readCandidatePage(handle)
      },
    }),
    saveSuccessMessage: '分配用户成功',
    operatorRefreshFailedMessage: '角色成员已保存，但资料与菜单刷新失败，请稍后重新进入',
    initialReadErrorFallback: '成员与用户读取失败，请重试',
    verifyReadErrorFallback: '最新成员读取失败，请重试',
    leaveWhileSavingMessage:
      '正在保存成员分配。离开不撤销已提交的保存，原请求仍可能生效，确认关闭？',
    unsavedChangesMessage: '当前有未保存的成员调整，离开将丢弃本次修改，确认关闭？',
    restoreOneFailedMessage: '未能恢复上次分配记录，请核实该角色的当前成员后再分配',
    restoreScanFailedMessage: '未能恢复上次分配记录，请核实相关角色的当前成员后再分配',
  })
  return { open: machine.open, pendingRoleIds: machine.pendingIds }
}
