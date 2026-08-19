function presentListItem(record = {}) {
  return {
    id: record.id,
    username: record.username,
    name: record.name,
    phone: record.phone,
    status: record.status,
    statusEnabled: record.status === 1,
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

const SYS_USER_API = '/admin/system/sysUser'

export function listRequest(page, limit, searchObj = {}) {
  return {
    method: 'get',
    url: `${SYS_USER_API}/${page}/${limit}`,
    params: searchObj
  }
}

export function getRequest(id) {
  return {
    method: 'get',
    url: `${SYS_USER_API}/get/${id}`
  }
}

export function removeRequest(id) {
  return {
    method: 'delete',
    url: `${SYS_USER_API}/remove/${id}`
  }
}

export function updateStatusRequest(id, status) {
  return {
    method: 'get',
    url: `${SYS_USER_API}/updateStatus/${id}/${status}`
  }
}

function userPayload(form, { includePassword = false } = {}) {
  const data = {
    username: form.username,
    name: form.name,
    phone: form.phone,
    status: form.status == null ? 1 : form.status
  }

  if (form.id) {
    data.id = form.id
  }

  if (includePassword) {
    data.password = form.password
  }

  return data
}

export function writeRequest(form) {
  if (form.id) {
    return {
      method: 'put',
      url: `${SYS_USER_API}/update`,
      data: userPayload(form)
    }
  }

  return {
    method: 'post',
    url: `${SYS_USER_API}/save`,
    data: userPayload(form, { includePassword: true })
  }
}

export function emptyForm() {
  return {
    id: '',
    username: '',
    password: '',
    name: '',
    phone: '',
    status: 1
  }
}

export function presentForm(record = {}) {
  return {
    id: record.id || '',
    username: record.username || '',
    password: '',
    name: record.name || '',
    phone: record.phone || '',
    status: record.status == null ? 1 : record.status
  }
}
