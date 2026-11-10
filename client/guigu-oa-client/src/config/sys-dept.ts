import type {
  ApiRequestConfig,
  DeptForm,
  DeptParentOption,
  DeptRecord,
  DeptTreeNode,
  EntityId,
  UserOption,
} from '@/types/domain'
import { requiredRule, type RequiredRule } from './form-rules'
import { ENABLED_STATUS_OPTIONS } from './status'

/** 部门页的按钮点（ADR 0056）：读只判页面授予，不设 system:dept:query。 */
export const DEPT_PERMISSIONS = {
  add: 'system:dept:add',
  edit: 'system:dept:edit',
  remove: 'system:dept:remove',
} as const

export const DEPT_STATUS_OPTIONS = ENABLED_STATUS_OPTIONS

export const ROOT_DEPT_ID = 0
export const DEFAULT_DEPT_STATUS = 1
export const DEFAULT_SORT_VALUE = 0

const SYS_DEPT_API = '/admin/system/sysDept'

/** 单节点投影（createListPage 的 present 逐条调用，递归装配 children）。 */
export function presentDeptNode(record: DeptRecord = {}): DeptTreeNode {
  return {
    id: record.id ?? '',
    parentId: record.parentId ?? ROOT_DEPT_ID,
    ancestors: record.ancestors || '',
    name: record.name || '',
    leaderUserId: record.leaderUserId ?? null,
    sortValue: record.sortValue ?? DEFAULT_SORT_VALUE,
    status: record.status ?? DEFAULT_DEPT_STATUS,
    createTime: record.createTime,
    children: (record.children || []).map(presentDeptNode),
  }
}

export function presentDeptTree(nodes: DeptRecord[] = []): DeptTreeNode[] {
  return nodes.map(presentDeptNode)
}

/** 整表读：服务端返回根节点数组，children 已嵌套（#247）。 */
export function findTreeRequest(): ApiRequestConfig {
  return {
    method: 'get',
    url: `${SYS_DEPT_API}/findTree`,
  }
}

/** ancestors 由服务端按父级路径重算；负责人是用户引用（ADR 0069），空选择写 null。 */
export function writeRequest(form: DeptForm): ApiRequestConfig {
  const data: {
    id?: EntityId
    parentId: EntityId
    name: string
    leaderUserId: EntityId | null
    sortValue: number
    status: number
  } = {
    parentId: form.parentId == null ? ROOT_DEPT_ID : form.parentId,
    name: form.name == null ? '' : form.name.trim(),
    leaderUserId: form.leaderUserId === '' || form.leaderUserId == null ? null : form.leaderUserId,
    sortValue: form.sortValue,
    status: form.status,
  }

  if (form.id) {
    data.id = form.id
    return {
      method: 'put',
      url: `${SYS_DEPT_API}/update`,
      data,
    }
  }

  return {
    method: 'post',
    url: `${SYS_DEPT_API}/save`,
    data,
  }
}

export function removeRequest(id: EntityId): ApiRequestConfig {
  return {
    method: 'delete',
    url: `${SYS_DEPT_API}/remove/${id}`,
  }
}

/**
 * 负责人选择器的启用用户选项（#286，ADR 0069）：挂部门页自己的只读端点——
 * 部门管理员未必有用户管理页授予，不借用 /admin/system/sysUser 的读。
 */
export function userOptionsRequest(): ApiRequestConfig {
  return {
    method: 'get',
    url: `${SYS_DEPT_API}/userOptions`,
  }
}

/** 选项投影：姓名（用户名）做展示名，无 id 的行丢弃；同名用户靠用户名区分。 */
export function presentUserOptions(records: Array<Partial<UserOption>> = []): UserOption[] {
  return records.flatMap((record) => {
    if (record.id == null || record.id === '') return []
    const name = record.name || ''
    const username = record.username || ''
    return [
      {
        id: record.id,
        username,
        name,
        label: name && username ? `${name}（${username}）` : name || username,
      },
    ]
  })
}

/**
 * 编辑回显：当前负责人已停用/已删除时不在启用选项里，补一个合成选项保住原值展示，
 * 管理员不动它就原样保留，动了才换成启用用户。
 */
export function leaderOptions(
  options: UserOption[],
  leaderUserId: EntityId | '' | null,
): UserOption[] {
  if (leaderUserId == null || leaderUserId === '') return options
  const exists = options.some((option) => String(option.id) === String(leaderUserId))
  return exists
    ? options
    : [...options, { id: leaderUserId, username: '', name: '', label: '当前负责人已停用或已删除' }]
}

export function emptyForm(parentId: EntityId = ROOT_DEPT_ID): DeptForm {
  return {
    id: '',
    parentId,
    name: '',
    leaderUserId: '',
    sortValue: DEFAULT_SORT_VALUE,
    status: DEFAULT_DEPT_STATUS,
  }
}

export function presentForm(record: DeptRecord = {}): DeptForm {
  return {
    id: record.id || '',
    parentId: record.parentId == null ? ROOT_DEPT_ID : record.parentId,
    name: record.name || '',
    leaderUserId: record.leaderUserId == null ? '' : record.leaderUserId,
    sortValue: record.sortValue == null ? DEFAULT_SORT_VALUE : record.sortValue,
    status: record.status == null ? DEFAULT_DEPT_STATUS : record.status,
  }
}

/** 必填文案镜像服务端 DTO（ADR 0038）：只有部门名称必填。 */
export function formRules(): Record<'name', RequiredRule[]> {
  return {
    name: [requiredRule('部门名称不能为空')],
  }
}

/**
 * 父级下拉：顶级选项 + 逐层缩进的全部节点。编辑时传 excludeId，
 * 该节点与整棵子树都不出现——服务端同样拒绝环，这里先少一次往返。
 */
export function parentOptions(nodes: DeptRecord[] = [], excludeId?: EntityId): DeptParentOption[] {
  const options: DeptParentOption[] = [{ id: ROOT_DEPT_ID, name: '顶级部门', depth: 0 }]
  const visit = (list: DeptRecord[], depth: number) => {
    for (const node of list) {
      if (node.id == null || node.id === '' || node.id === excludeId) continue
      options.push({ id: node.id, name: node.name || '', depth })
      visit(node.children || [], depth + 1)
    }
  }
  visit(nodes, 1)
  return options
}
