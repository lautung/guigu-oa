import type { DeepReadonly, Ref } from 'vue'
import type { EntityId } from '@/types/domain'
import {
  createAssignmentMachine,
  copy,
  isId,
  sameIds,
  uniqueIds,
  type AssignmentLeaseBase,
  type AssignmentOutcome,
  type AssignmentPhase,
  type AssignmentRecord,
} from './assignment-machine'

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

export type AssignResult = AssignmentOutcome

export interface AssignRolesAdapter {
  entryId(): string
  capture(): { generation: number; entryId: string }
  storage: Pick<Storage, 'getItem' | 'setItem' | 'removeItem'> & { keys(): string[] }
  read(userId: EntityId): Promise<RoleAssignmentRead>
  assign(userId: EntityId, roleIds: readonly EntityId[]): Promise<AssignResult>
  refreshOperator(): Promise<void>
  confirm(message: string, kind: 'leave' | 'end-assignment'): Promise<boolean>
  dismissConfirm(): void
  notify(message: string, kind: 'success' | 'warning' | 'error'): void
  replace(path: string): void
}

interface AssignState {
  roles: AssignableRole[]
  assignedRoleIds: EntityId[]
  selectedRoleIds: EntityId[]
}

interface LatestRoles {
  roles: AssignableRole[]
  roleIds: EntityId[]
}

interface SavedAssign {
  version: 1
  entry: string
  userId: EntityId
  username: string
  phase: 'sending' | 'unconfirmed'
  submittedRoles: AssignableRole[]
  submittedRoleIds: EntityId[]
}

export interface AssignRolesView {
  userId: EntityId | null
  username: string
  phase: AssignmentPhase
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

interface AssignRolesActions {
  select(roleIds: readonly EntityId[]): boolean
}

export type AssignRolesLease = AssignmentLeaseBase<AssignRolesView> & AssignRolesActions

export interface AssignRolesModule {
  open(target: AssignRoleTarget): AssignRolesLease
  pendingUserIds: DeepReadonly<Ref<readonly string[]>>
}

const PREFIX = 'oa-assign:v1:'

const isObject = (value: unknown): value is Record<string, unknown> =>
  !!value && typeof value === 'object' && !Array.isArray(value)

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

/** 分配角色的域适配：会话生命周期、待确认记录与离开判定在共享分配机（ADR 0006）。 */
export function createAssignRoles(adapter: AssignRolesAdapter) {
  const machine = createAssignmentMachine<
    AssignRoleTarget,
    AssignState,
    AssignableRole,
    LatestRoles,
    AssignRolesView,
    AssignRolesActions
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
    normalizeTarget: (target) => ({ id: target.id, username: target.username }),
    refreshTarget: (current, incoming) =>
      incoming.username && incoming.username !== current.username
        ? { id: current.id, username: incoming.username }
        : current,
    createState: () => ({ roles: [], assignedRoleIds: [], selectedRoleIds: [] }),
    restoreState: (record) => ({
      roles: copy(record.items),
      assignedRoleIds: copy(record.ids),
      selectedRoleIds: copy(record.ids),
    }),
    decodeRecord: (raw) => {
      const value = savedAssign(raw)
      return {
        entry: value.entry,
        target: { id: value.userId, username: value.username },
        phase: value.phase,
        ids: value.submittedRoleIds,
        items: value.submittedRoles,
      }
    },
    encodeRecord: (record: AssignmentRecord<AssignRoleTarget, AssignableRole>) =>
      JSON.stringify({
        version: 1,
        entry: record.entry,
        userId: record.target.id,
        username: record.target.username,
        phase: record.phase,
        submittedRoles: copy(record.items),
        submittedRoleIds: copy(record.ids),
      }),
    readInitial: async (handle) => {
      const presented = presentRead(await adapter.read(handle.target.id))
      handle.state.roles = presented.roles
      handle.state.assignedRoleIds = presented.roleIds
      handle.state.selectedRoleIds = copy(presented.roleIds)
    },
    readVerify: async (handle) => presentRead(await adapter.read(handle.target.id)),
    hasUnsavedChanges: (handle) =>
      !sameIds(handle.state.selectedRoleIds, handle.state.assignedRoleIds),
    buildSubmission: (handle) => {
      const known = new Set(handle.state.roles.map((role) => String(role.id)))
      const ids = uniqueIds(
        handle.state.selectedRoleIds.filter((id) => isId(id) && known.has(String(id))),
      )
      return {
        ids,
        items: handle.state.roles.filter((role) =>
          ids.some((id) => String(id) === String(role.id)),
        ),
      }
    },
    assign: adapter.assign,
    afterSaved: async () => {},
    projectView: (input) => ({
      userId: input.target.id,
      username: input.target.username,
      phase: input.phase,
      roles: input.state.roles,
      selectedRoleIds: input.state.selectedRoleIds,
      submittedRoleIds: input.submittedIds,
      submittedRoles: input.submittedItems,
      latest: input.latest,
      reading: input.reading,
      error: input.error,
      canSelect: input.capabilities.editable,
      canSave: input.capabilities.editable,
      canReRead: input.capabilities.confirmable,
      canEnd: input.capabilities.confirmable,
    }),
    leaseActions: (handle) => ({
      select(roleIds) {
        if (!handle.active() || handle.phase() !== 'ready' || handle.reading()) return false
        const known = new Set(handle.state.roles.map((role) => String(role.id)))
        handle.state.selectedRoleIds = uniqueIds(
          roleIds.filter((id) => isId(id) && known.has(String(id))),
        )
        handle.clearError()
        handle.refresh()
        return true
      },
    }),
    saveSuccessMessage: '分配角色成功',
    operatorRefreshFailedMessage: '角色已保存，但资料与菜单刷新失败，请稍后重新进入',
    initialReadErrorFallback: '角色读取失败，请重试',
    verifyReadErrorFallback: '最新角色读取失败，请重试',
    leaveWhileSavingMessage:
      '正在保存角色分配。离开不撤销已提交的保存，原请求仍可能生效，确认关闭？',
    unsavedChangesMessage: '当前有未保存的角色勾选，离开将丢弃本次修改，确认关闭？',
    restoreOneFailedMessage: '未能恢复上次分配记录，请核实该用户的当前角色后再分配',
    restoreScanFailedMessage: '未能恢复上次分配记录，请核实相关用户的当前角色后再分配',
  })
  return { open: machine.open, pendingUserIds: machine.pendingIds }
}
