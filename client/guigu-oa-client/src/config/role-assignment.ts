import { reactive, readonly } from 'vue'
import { BusinessRequestFailure } from '@/utils/request'
import type { Recognition, RequestContext } from '@/recognition'
import type { ApiClient, EntityId, RoleListItem } from '@/types/domain'

interface Target {
  id: EntityId
  username: string
}
interface Submission {
  version: 1
  operationId: string
  entryId: string
  target: Target
  roleIdList: EntityId[]
  roles: RoleListItem[]
  phase: 'saving' | 'uncertain'
}
interface Options {
  recognition: Pick<Recognition, 'capture' | 'recognize'>
  request: ApiClient
  records: {
    load(key: string): unknown
    save(key: string, value: unknown): void
    remove(key: string): void
  }
  confirm(message: string, kind: 'leave' | 'end-assignment'): Promise<boolean>
  cancelConfirmation(): void
  notify(message: string): void
}
type Phase = 'closed' | 'loading' | 'load-error' | 'ready' | 'saving' | 'uncertain' | 'saved'
function message(error: unknown, fallback: string) {
  return error instanceof Error ? error.message : fallback
}
function validId(value: unknown): value is EntityId {
  return typeof value === 'string'
    ? !!value.trim()
    : typeof value === 'number' && Number.isSafeInteger(value) && value > 0
}
function roleLists(value: unknown): { roles: RoleListItem[]; ids: EntityId[] } {
  if (!value || typeof value !== 'object') throw new Error('角色读取结果不完整，请重新读取')
  const { allRolesList, assginRoleList } = value as Record<string, unknown>
  if (!Array.isArray(allRolesList) || !Array.isArray(assginRoleList))
    throw new Error('角色读取结果不完整，请重新读取')
  const ids = new Set<string>()
  const roles = allRolesList.map((role) => {
    if (
      !role ||
      !validId(role.id) ||
      typeof role.roleName !== 'string' ||
      !role.roleName.trim() ||
      ids.has(String(role.id))
    )
      throw new Error('可选角色无法正确理解，请重新读取')
    ids.add(String(role.id))
    return { id: role.id, roleName: role.roleName }
  })
  const assigned = new Set<string>()
  const selected = assginRoleList.map((role) => {
    if (!role || !validId(role.id) || !ids.has(String(role.id)) || assigned.has(String(role.id)))
      throw new Error('已分配角色无法正确理解，请重新读取')
    assigned.add(String(role.id))
    return roles.find((item) => String(item.id) === String(role.id))!.id
  })
  return { roles, ids: selected }
}

/** One interface owns presentation, fixed submissions and recovery across page mounts. */
export function createRoleAssignment(options: Options) {
  let opening = 0,
    readNumber = 0
  let context = options.recognition.capture()
  let baseline: EntityId[] = []
  const operations = new Map<string, Submission>()
  const settlementErrors = new Map<
    string,
    { outcome: 'saved' | 'rejected' | 'uncertain'; error: string }
  >()
  const permissionRefresh = reactive({
    status: 'idle' as 'idle' | 'loading' | 'retryable',
    error: '',
  })
  let refreshOwner: RequestContext | null = null
  let refreshFlight: Promise<void> | null = null
  let refreshAgain = false
  const keyFor = (entryId: string, id: EntityId) =>
    `oa-role-assignment:${JSON.stringify([entryId, String(id)])}`
  function sameIdentity(owner: RequestContext) {
    const now = options.recognition.capture()
    return !!owner.entryId && now.entryId === owner.entryId && now.generation === owner.generation
  }
  const state = reactive({
    phase: 'closed' as Phase,
    target: null as Target | null,
    roles: [] as RoleListItem[],
    selectedIds: [] as EntityId[],
    error: '',
    recordError: '',
    leaving: false,
    get permissionRefresh() {
      return refreshOwner && sameIdentity(refreshOwner)
        ? permissionRefresh
        : { status: 'idle' as const, error: '' }
    },
    snapshot: null as Submission | null,
    reading: false,
    currentRoles: null as RoleListItem[] | null,
    get submittedRoleNames(): string {
      return (
        state.snapshot?.roles
          .filter((role) => state.snapshot!.roleIdList.some((id) => String(id) === String(role.id)))
          .map((role) => role.roleName)
          .join('、') || ''
      )
    },
    get isCurrent(): boolean {
      return sameIdentity(context)
    },
    get canEdit(): boolean {
      return state.isCurrent && state.phase === 'ready' && !state.leaving && !state.recordError
    },
    get canSave(): boolean {
      return state.canEdit
    },
    get dirty(): boolean {
      return (
        JSON.stringify([...state.selectedIds].map(String).sort()) !==
        JSON.stringify([...baseline].map(String).sort())
      )
    },
  })
  function release() {
    opening++
    readNumber++
    options.cancelConfirmation()
    state.phase = 'closed'
    state.target = null
    state.roles = []
    state.selectedIds = []
    state.snapshot = null
    state.error = ''
    state.recordError = ''
    state.leaving = false
    state.reading = false
    state.currentRoles = null
    baseline = []
  }
  async function leave(force = false): Promise<'accepted' | 'superseded' | false> {
    if (force || !state.isCurrent) {
      release()
      return 'accepted'
    }
    if (state.leaving) return false
    const owner = opening
    const warning =
      state.phase === 'saving'
        ? '正在保存分配角色。离开不撤销已提交的保存，确认离开？'
        : state.phase === 'uncertain'
          ? '分配结果待确认。关闭后仍保留本次提交，离开不撤销原保存，确认离开？'
          : state.dirty
            ? '当前有未保存修改，离开将丢弃本次勾选，确认离开？'
            : ''
    if (warning) {
      state.leaving = true
      try {
        const accepted = await options.confirm(warning, 'leave')
        if (owner !== opening || !state.isCurrent) return 'superseded'
        if (!accepted) return false
      } finally {
        if (owner === opening) state.leaving = false
      }
    }
    if (owner !== opening) return 'superseded'
    release()
    return 'accepted'
  }
  async function requestLeave(force = false) {
    return (await leave(force)) !== false
  }
  function readRecord(key: string, entryId: string, target: Target): Submission | null {
    const value = options.records.load(key)
    if (value == null) return null
    if (!value || typeof value !== 'object' || Array.isArray(value))
      throw new Error('分配提交记录损坏，已暂停保存，请保留记录并联系支持')
    const record = value as Submission
    if (
      record.version !== 1 ||
      !record.operationId ||
      typeof record.operationId !== 'string' ||
      record.entryId !== entryId ||
      !record.target ||
      !validId(record.target.id) ||
      String(record.target.id) !== String(target.id) ||
      typeof record.target.username !== 'string' ||
      !['saving', 'uncertain'].includes(record.phase) ||
      !Array.isArray(record.roleIdList)
    )
      throw new Error('分配提交记录无法确认，已暂停保存，请保留记录并联系支持')
    roleLists({
      allRolesList: record.roles,
      assginRoleList: record.roleIdList.map((id) => ({ id })),
    })
    return record
  }
  function writeRecord(key: string, operation: Submission) {
    options.records.save(key, operation)
    const readBack = readRecord(key, operation.entryId, operation.target)
    if (JSON.stringify(readBack) !== JSON.stringify(operation))
      throw new Error('分配提交记录未能可靠保留，已暂停保存')
  }
  function removeRecord(key: string, operation: Submission) {
    const existing = readRecord(key, operation.entryId, operation.target)
    if (existing?.operationId !== operation.operationId)
      throw new Error('分配提交记录已变化，已暂停保存')
    options.records.remove(key)
    if (options.records.load(key) != null) throw new Error('分配提交记录未能清理，已暂停保存')
  }
  function showOperation(operation: Submission) {
    state.snapshot = operation
    state.selectedIds = [...operation.roleIdList]
    state.roles = operation.roles.map((role) => ({ ...role }))
    baseline = [...state.selectedIds]
    state.phase = operation.phase
  }
  async function reload() {
    if (
      !state.target ||
      !state.isCurrent ||
      state.leaving ||
      state.phase === 'saving' ||
      state.recordError
    )
      return
    const owner = opening,
      number = ++readNumber,
      target = { ...state.target }
    if (state.phase === 'saved') state.snapshot = null
    const verifying = !!state.snapshot
    if (!verifying) state.phase = 'loading'
    state.reading = true
    state.error = ''
    try {
      const response = await options.request<unknown>({
        method: 'get',
        url: `/admin/system/sysRole/toAssign/${encodeURIComponent(target.id)}`,
        _recognition: context,
        _localErrors: true,
        _isRelevant: () => owner === opening && number === readNumber && state.isCurrent,
      })
      if (owner !== opening || number !== readNumber || !state.isCurrent) return
      if (response.code !== 200) throw new Error('角色读取失败，请重新读取')
      const lists = roleLists(response.data)
      if (verifying) state.currentRoles = lists.roles.filter((role) => lists.ids.includes(role.id))
      else {
        state.roles = lists.roles
        state.selectedIds = lists.ids
        baseline = [...state.selectedIds]
        state.phase = 'ready'
      }
    } catch (error) {
      if (owner !== opening || number !== readNumber || !state.isCurrent) return
      if (!verifying) state.phase = 'load-error'
      state.error = message(error, '角色读取失败')
    } finally {
      if (owner === opening && number === readNumber) state.reading = false
    }
  }
  async function open(target: Target) {
    if ((await leave()) !== 'accepted') return
    context = options.recognition.capture()
    if (refreshOwner && !sameIdentity(refreshOwner)) {
      Object.assign(permissionRefresh, { status: 'idle', error: '' })
      refreshOwner = null
    }
    if (!validId(target.id) || !context.entryId) return
    state.target = { ...target }
    const key = keyFor(context.entryId, target.id)
    try {
      const record = readRecord(key, context.entryId, target)
      const live = operations.get(key)
      if (live && record?.operationId !== live.operationId)
        throw new Error('分配提交记录与在途保存不一致，已暂停保存')
      if (record) {
        const operation = live || { ...record, phase: 'uncertain' as const }
        if (!live && record.phase === 'saving') writeRecord(key, operation)
        operations.set(key, operation)
        showOperation(operation)
        const settlement = settlementErrors.get(key)
        if (settlement) {
          state.recordError = settlement.error
          state.phase = settlement.outcome === 'rejected' ? 'ready' : settlement.outcome
          if (settlement.outcome === 'rejected') state.snapshot = null
        }
        return
      }
    } catch (error) {
      state.phase = 'load-error'
      state.recordError = message(error, '无法读取分配提交记录，已暂停保存')
      return
    }
    await reload()
  }

  function select(ids: EntityId[]) {
    if (
      state.canEdit &&
      ids.every((id) => state.roles.some((role) => String(role.id) === String(id)))
    )
      state.selectedIds = state.roles
        .filter((role) => ids.some((id) => String(role.id) === String(id)))
        .map((role) => role.id)
  }
  async function save() {
    if (!state.canSave || !state.target) return
    const owner = opening,
      identity = { ...context },
      key = keyFor(identity.entryId, state.target.id)
    const operation: Submission = {
      version: 1,
      operationId: crypto.randomUUID(),
      entryId: identity.entryId,
      target: { ...state.target },
      roleIdList: [...state.selectedIds],
      roles: state.roles.map((role) => ({ ...role })),
      phase: 'saving',
    }
    try {
      if (operations.has(key) || readRecord(key, identity.entryId, operation.target))
        throw new Error('该用户有尚未确认的分配，请关闭后重新打开核实')
      writeRecord(key, operation)
    } catch (error) {
      state.recordError = message(error, '无法可靠保留分配提交记录，未发出保存')
      return
    }
    operations.set(key, operation)
    state.snapshot = operation
    state.phase = 'saving'
    state.error = ''
    let successful = false
    try {
      const response = await options.request({
        method: 'post',
        url: '/admin/system/sysRole/doAssign',
        data: Object.freeze({
          userId: operation.target.id,
          roleIdList: Object.freeze([...operation.roleIdList]),
        }),
        _recognition: identity,
        _localErrors: true,
        _isRelevant: () => sameIdentity(identity),
      })
      if (response.code !== 200) throw new Error('无法确认分配结果')
      successful = true
    } catch (error) {
      const rejected =
        error instanceof BusinessRequestFailure && [208, 209, 205].includes(error.businessCode || 0)
      try {
        if (rejected) {
          removeRecord(key, operation)
          operations.delete(key)
        } else {
          operation.phase = 'uncertain'
          writeRecord(key, operation)
        }
      } catch (recordError) {
        settlementErrors.set(key, {
          outcome: rejected ? 'rejected' : 'uncertain',
          error: message(recordError, '无法更新分配提交记录，已暂停保存'),
        })
        if (sameIdentity(identity) && state.snapshot?.operationId === operation.operationId)
          state.recordError = message(recordError, '无法更新分配提交记录')
      }
      if (sameIdentity(identity) && state.snapshot?.operationId === operation.operationId) {
        state.phase = rejected ? 'ready' : 'uncertain'
        if (rejected) state.snapshot = null
        state.error = message(error, rejected ? '分配尚未提交，请核实后重试' : '无法确认分配结果')
      }
    }
    if (!successful) return
    try {
      removeRecord(key, operation)
      operations.delete(key)
    } catch (error) {
      settlementErrors.set(key, {
        outcome: 'saved',
        error: `分配已保存，但提交记录未能清理：${message(error, '已暂停保存')}`,
      })
      if (sameIdentity(identity) && state.snapshot?.operationId === operation.operationId)
        state.recordError = settlementErrors.get(key)!.error
    }
    if (!sameIdentity(identity)) return
    void refreshPermissions(identity)
    if (owner === opening && !state.recordError) {
      options.notify('分配角色成功')
      release()
    } else if (state.snapshot?.operationId === operation.operationId) state.phase = 'saved'
  }
  function refreshPermissions(identity: RequestContext): Promise<void> {
    if (!sameIdentity(identity)) return Promise.resolve()
    if (refreshFlight && refreshOwner && sameIdentity(refreshOwner)) {
      refreshAgain = true
      return refreshFlight
    }
    refreshOwner = identity
    Object.assign(permissionRefresh, { status: 'loading', error: '' })
    const flight = (async () => {
      do {
        refreshAgain = false
        try {
          await options.recognition.recognize(undefined, { quiet: true })
          if (sameIdentity(identity))
            Object.assign(permissionRefresh, { status: 'idle', error: '' })
        } catch (error) {
          if (sameIdentity(identity))
            Object.assign(permissionRefresh, {
              status: 'retryable',
              error: `分配已保存，权限刷新暂未完成：${message(error, '请重试权限刷新')}`,
            })
        }
      } while (refreshAgain && sameIdentity(identity))
    })().finally(() => {
      if (refreshFlight === flight) refreshFlight = null
    })
    refreshFlight = flight
    return flight
  }
  async function retryRecognition() {
    if (
      state.permissionRefresh.status === 'retryable' &&
      refreshOwner &&
      sameIdentity(refreshOwner)
    )
      await refreshPermissions(refreshOwner)
  }
  async function endAssignment() {
    if (
      !state.isCurrent ||
      state.phase !== 'uncertain' ||
      !state.snapshot ||
      state.leaving ||
      state.recordError
    )
      return false
    const owner = opening,
      operation = state.snapshot,
      key = keyFor(context.entryId, operation.target.id)
    state.leaving = true
    try {
      const accepted = await options.confirm(
        '原保存仍可能生效并影响后续分配。结束本次分配只清理本地记录，不代表原保存失败或撤销，确认结束？',
        'end-assignment',
      )
      if (!accepted || owner !== opening || !state.isCurrent) return false
      try {
        removeRecord(key, operation)
      } catch (error) {
        state.recordError = message(error, '无法清理本次提交记录，已暂停保存')
        return false
      }
      operations.delete(key)
      state.snapshot = null
      state.selectedIds = []
      state.roles = []
      state.currentRoles = null
      baseline = []
      state.phase = 'loading'
      state.leaving = false
      await reload()
      return true
    } finally {
      if (owner === opening) state.leaving = false
    }
  }
  return {
    state: readonly(state),
    open,
    reload,
    select,
    save,
    requestLeave,
    release,
    endAssignment,
    retryRecognition,
  }
}
export type RoleAssignment = ReturnType<typeof createRoleAssignment>
