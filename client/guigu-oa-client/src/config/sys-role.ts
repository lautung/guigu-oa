import type { ApiRequestConfig, EntityId, RoleForm, RoleListItem, RoleRecord } from '@/types/domain'
import { requiredListRule, requiredRule, type RequiredRule } from './form-rules'

/** 数据范围五档（ADR 0056/0057），顺序即下拉顺序。 */
export const DATA_SCOPE_OPTIONS = [
  { value: 1, label: '全部' },
  { value: 2, label: '自定部门' },
  { value: 3, label: '本部门' },
  { value: 4, label: '本部门及以下' },
  { value: 5, label: '仅本人' },
] as const

export const DEFAULT_DATA_SCOPE = 5

export function dataScopeLabel(scope: number | null | undefined): string {
  return DATA_SCOPE_OPTIONS.find((option) => option.value === scope)?.label || ''
}

export interface RoleAssignmentLists {
  allRolesList: RoleListItem[]
  assginRoleList: RoleListItem[]
}

export function presentListRecord(record: RoleRecord = {}): RoleListItem {
  return {
    id: record.id || '',
    roleName: record.roleName,
    roleCode: record.roleCode,
    createTime: record.createTime,
  }
}

export function presentListPage(
  page: { records?: RoleRecord[]; total?: number; pages?: number } = {},
) {
  return {
    records: (page.records || []).map(presentListRecord),
    total: page.total || 0,
    pages: page.pages || 0,
  }
}

const SYS_ROLE_API = '/admin/system/sysRole'

export function listRequest(
  page: number,
  limit: number,
  searchObj: Record<string, string | number> = {},
): ApiRequestConfig {
  return {
    method: 'get',
    url: `${SYS_ROLE_API}/${page}/${limit}`,
    params: searchObj,
  }
}

export function getRequest(id: EntityId): ApiRequestConfig {
  return {
    method: 'get',
    url: `${SYS_ROLE_API}/get/${id}`,
  }
}

export function removeRequest(id: EntityId): ApiRequestConfig {
  return {
    method: 'delete',
    url: `${SYS_ROLE_API}/remove/${id}`,
  }
}

export function writeRequest(form: RoleForm | RoleRecord): ApiRequestConfig {
  const data: {
    id?: EntityId
    roleName?: string
    roleCode?: string
    dataScope: number
    deptIds: EntityId[]
  } = {
    roleName: form.roleName,
    roleCode: form.roleCode,
    dataScope: form.dataScope == null ? DEFAULT_DATA_SCOPE : form.dataScope,
    deptIds: form.deptIds || [],
  }

  if (form.id) {
    data.id = form.id
    return {
      method: 'put',
      url: `${SYS_ROLE_API}/update`,
      data,
    }
  }

  return {
    method: 'post',
    url: `${SYS_ROLE_API}/save`,
    data,
  }
}

/** 数据范围「自定部门」的只读选项（#225）；部门 CRUD 不在客户端范围内。 */
export function deptOptionsRequest(): ApiRequestConfig {
  return {
    method: 'get',
    url: `${SYS_ROLE_API}/depts`,
  }
}

export function toAssignRolesRequest(userId: EntityId): ApiRequestConfig {
  return {
    method: 'get',
    url: `${SYS_ROLE_API}/toAssign/${userId}`,
  }
}

export function assignRolesRequest(
  userId: EntityId,
  roleIdList: readonly EntityId[],
): ApiRequestConfig {
  return {
    method: 'post',
    url: `${SYS_ROLE_API}/doAssign`,
    data: { userId, roleIdList },
  }
}

/** 角色页的按钮点（ADR 0052/0056，V110 种子）：分配用户不复用菜单授权点。 */
export const SYS_ROLE_PERMISSIONS = {
  add: 'system:role:add',
  edit: 'system:role:edit',
  remove: 'system:role:remove',
  assignAuth: 'system:role:assignAuth',
  assignUser: 'system:role:assignUser',
} as const

/** 角色侧成员读（#256）：assigned 缺省不过滤，true 只返回成员，false 只返回非成员。 */
export function roleUsersRequest(
  roleId: EntityId,
  query: { page: number; limit: number; keyword?: string; assigned?: boolean },
): ApiRequestConfig {
  return {
    method: 'get',
    url: `${SYS_ROLE_API}/${roleId}/users`,
    params: {
      page: query.page,
      limit: query.limit,
      keyword: query.keyword || undefined,
      assigned: query.assigned,
    },
  }
}

/** 角色侧成员写（#256）：userIds 整体替换该角色的成员集合，空数组清空。 */
export function assignRoleUsersRequest(
  roleId: EntityId,
  userIds: readonly EntityId[],
): ApiRequestConfig {
  return {
    method: 'post',
    url: `${SYS_ROLE_API}/doAssignUsers`,
    data: { roleId, userIds },
  }
}

export function emptyForm(): RoleForm {
  return {
    id: '',
    roleName: '',
    roleCode: '',
    dataScope: DEFAULT_DATA_SCOPE,
    deptIds: [],
  }
}

export function presentForm(record: RoleRecord = {}): RoleForm {
  return {
    id: record.id || '',
    roleName: record.roleName || '',
    roleCode: record.roleCode || '',
    dataScope: record.dataScope == null ? DEFAULT_DATA_SCOPE : record.dataScope,
    deptIds: [...(record.deptIds || [])],
  }
}

/**
 * 必填与文案镜像服务端（ADR 0034/0038）：名称/编码始终必填；
 * dataScope=2 时自定部门至少选一个（镜像 service 的「自定部门不能为空」）。
 */
export function formRules(
  dataScope: number = DEFAULT_DATA_SCOPE,
): Record<'roleName' | 'roleCode' | 'deptIds', RequiredRule[]> {
  return {
    roleName: [requiredRule('角色名称不能为空')],
    roleCode: [requiredRule('角色编码不能为空')],
    deptIds: dataScope === 2 ? [requiredListRule('自定部门不能为空')] : [],
  }
}
