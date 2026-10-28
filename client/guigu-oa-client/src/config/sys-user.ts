import type { ApiRequestConfig, EntityId, UserForm, UserListItem, UserRecord } from '@/types/domain'

export function presentListRecord(record: UserRecord = {}): UserListItem {
  return {
    id: record.id || '',
    username: record.username,
    name: record.name,
    phone: record.phone,
    status: record.status,
    statusEnabled: record.status === 1,
    createTime: record.createTime,
  }
}

export function presentListPage(
  page: { records?: UserRecord[]; total?: number; pages?: number } = {},
) {
  return {
    records: (page.records || []).map(presentListRecord),
    total: page.total || 0,
    pages: page.pages || 0,
  }
}

const SYS_USER_API = '/admin/system/sysUser'

export function listRequest(
  page: number,
  limit: number,
  searchObj: Record<string, string | number> = {},
): ApiRequestConfig {
  return {
    method: 'get',
    url: `${SYS_USER_API}/${page}/${limit}`,
    params: searchObj,
  }
}

export function getRequest(id: EntityId): ApiRequestConfig {
  return {
    method: 'get',
    url: `${SYS_USER_API}/get/${id}`,
  }
}

export function removeRequest(id: EntityId): ApiRequestConfig {
  return {
    method: 'delete',
    url: `${SYS_USER_API}/remove/${id}`,
  }
}

export function updateStatusRequest(id: EntityId, status: number): ApiRequestConfig {
  return {
    method: 'get',
    url: `${SYS_USER_API}/updateStatus/${id}/${status}`,
  }
}

function userPayload(form: UserForm | UserRecord, { includePassword = false } = {}) {
  const data: {
    id?: EntityId
    username?: string
    name?: string
    phone?: string
    status: number
    password?: string
  } = {
    username: form.username,
    name: form.name,
    phone: form.phone,
    status: form.status == null ? 1 : form.status,
  }

  if (form.id) {
    data.id = form.id
  }

  if (includePassword) {
    data.password = form.password
  }

  return data
}

export function writeRequest(form: UserForm | UserRecord): ApiRequestConfig {
  if (form.id) {
    return {
      method: 'put',
      url: `${SYS_USER_API}/update`,
      data: userPayload(form),
    }
  }

  return {
    method: 'post',
    url: `${SYS_USER_API}/save`,
    data: userPayload(form, { includePassword: true }),
  }
}

export function emptyForm(): UserForm {
  return {
    id: '',
    username: '',
    password: '',
    name: '',
    phone: '',
    status: 1,
  }
}

export function presentForm(record: UserRecord = {}): UserForm {
  return {
    id: record.id || '',
    username: record.username || '',
    password: '',
    name: record.name || '',
    phone: record.phone || '',
    status: record.status == null ? 1 : record.status,
  }
}
