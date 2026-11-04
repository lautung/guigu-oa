import type {
  ApiRequestConfig,
  DeptForm,
  DeptParentOption,
  DeptRecord,
  DeptTreeNode,
  EntityId,
} from '@/types/domain'
import { requiredRule, type RequiredRule } from './form-rules'

/** 部门页的按钮点（ADR 0056）：读只判页面授予，不设 system:dept:query。 */
export const DEPT_PERMISSIONS = {
  add: 'system:dept:add',
  edit: 'system:dept:edit',
  remove: 'system:dept:remove',
} as const

export const DEPT_STATUS_OPTIONS = [
  { value: 1, label: '正常' },
  { value: 0, label: '停用' },
] as const

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
    leader: record.leader || '',
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

/** ancestors 由服务端按父级路径重算，客户端只提交 parentId 与资料字段。 */
export function writeRequest(form: DeptForm): ApiRequestConfig {
  const data: {
    id?: EntityId
    parentId: EntityId
    name: string
    leader: string
    sortValue: number
    status: number
  } = {
    parentId: form.parentId == null ? ROOT_DEPT_ID : form.parentId,
    name: form.name,
    leader: form.leader,
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

export function emptyForm(parentId: EntityId = ROOT_DEPT_ID): DeptForm {
  return {
    id: '',
    parentId,
    name: '',
    leader: '',
    sortValue: DEFAULT_SORT_VALUE,
    status: DEFAULT_DEPT_STATUS,
  }
}

export function presentForm(record: DeptRecord = {}): DeptForm {
  return {
    id: record.id || '',
    parentId: record.parentId == null ? ROOT_DEPT_ID : record.parentId,
    name: record.name || '',
    leader: record.leader || '',
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
