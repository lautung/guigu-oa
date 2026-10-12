export function getCurrentUserRequest() {
  return {
    method: 'get',
    url: '/admin/process/getCurrentUser'
  }
}

export function presentProfile(record = {}) {
  return {
    name: record.name || '',
    phone: record.phone || ''
  }
}
