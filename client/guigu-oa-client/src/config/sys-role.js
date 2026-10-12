function presentListItem(record = {}) {
  return {
    id: record.id,
    roleName: record.roleName,
    roleCode: record.roleCode,
    createTime: record.createTime
  }
}

export function presentListPage(page = {}) {
  return {
    records: (page.records || []).map(presentListItem),
    total: page.total || 0,
    pages: page.pages || 0
  }
}

const SYS_ROLE_API = '/admin/system/sysRole'

export function listRequest(page, limit, searchObj = {}) {
  return {
    method: 'get',
    url: `${SYS_ROLE_API}/${page}/${limit}`,
    params: searchObj
  }
}

export function getRequest(id) {
  return {
    method: 'get',
    url: `${SYS_ROLE_API}/get/${id}`
  }
}

export function removeRequest(id) {
  return {
    method: 'delete',
    url: `${SYS_ROLE_API}/remove/${id}`
  }
}

export function writeRequest(form) {
  const data = {
    roleName: form.roleName,
    roleCode: form.roleCode
  }

  if (form.id) {
    data.id = form.id
    return {
      method: 'put',
      url: `${SYS_ROLE_API}/update`,
      data
    }
  }

  return {
    method: 'post',
    url: `${SYS_ROLE_API}/save`,
    data
  }
}

export function assignAuthPath(id, roleName) {
  return `/system/assignAuth?id=${id}&roleName=${encodeURIComponent(roleName || '')}`
}

export function toAssignRolesRequest(userId) {
  return {
    method: 'get',
    url: `${SYS_ROLE_API}/toAssign/${userId}`
  }
}

export function assignRolesRequest(userId, roleIdList) {
  return {
    method: 'post',
    url: `${SYS_ROLE_API}/doAssign`,
    data: {
      userId,
      roleIdList
    }
  }
}

export function emptyForm() {
  return {
    id: '',
    roleName: '',
    roleCode: ''
  }
}

export function presentForm(record = {}) {
  return {
    id: record.id || '',
    roleName: record.roleName || '',
    roleCode: record.roleCode || ''
  }
}
