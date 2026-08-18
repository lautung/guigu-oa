function presentListItem(record = {}) {
  return {
    id: record.id,
    name: record.name,
    description: record.description,
    createTime: record.createTime,
    updateTime: record.updateTime
  }
}

export function presentListPage(page = {}) {
  return {
    records: (page.records || []).map(presentListItem),
    total: page.total || 0,
    pages: page.pages || 0
  }
}

const PROCESS_TYPE_API = '/admin/process/processType'

export function listRequest(page, limit) {
  return {
    method: 'get',
    url: `${PROCESS_TYPE_API}/${page}/${limit}`
  }
}

export function getRequest(id) {
  return {
    method: 'get',
    url: `${PROCESS_TYPE_API}/get/${id}`
  }
}

export function removeRequest(id) {
  return {
    method: 'delete',
    url: `${PROCESS_TYPE_API}/remove/${id}`
  }
}

export function findAllRequest() {
  return {
    method: 'get',
    url: `${PROCESS_TYPE_API}/findAll`
  }
}

export function writeRequest(form) {
  if (form.id) {
    return {
      method: 'put',
      url: `${PROCESS_TYPE_API}/update`,
      data: { id: form.id, name: form.name, description: form.description }
    }
  }

  return {
    method: 'post',
    url: `${PROCESS_TYPE_API}/save`,
    data: { name: form.name, description: form.description }
  }
}

export function emptyForm() {
  return {
    id: '',
    name: '',
    description: ''
  }
}

export function presentForm(record = {}) {
  return {
    id: record.id || '',
    name: record.name || '',
    description: record.description || ''
  }
}
