import type { ApiRequestConfig, EntityId, RoleForm, RoleListItem, RoleRecord } from '@/types/domain'

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
  const data: { id?: EntityId; roleName?: string; roleCode?: string } = {
    roleName: form.roleName,
    roleCode: form.roleCode,
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

export function emptyForm(): RoleForm {
  return {
    id: '',
    roleName: '',
    roleCode: '',
  }
}

export function presentForm(record: RoleRecord = {}): RoleForm {
  return {
    id: record.id || '',
    roleName: record.roleName || '',
    roleCode: record.roleCode || '',
  }
}
