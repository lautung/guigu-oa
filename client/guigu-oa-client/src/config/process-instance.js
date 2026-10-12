import { statusLabel } from '@/work/work-list'

function presentListItem(record = {}) {
  return {
    id: record.id,
    processCode: record.processCode,
    title: record.title,
    name: record.name,
    processTypeName: record.processTypeName,
    processTemplateName: record.processTemplateName,
    description: record.description,
    status: record.status,
    statusLabel: statusLabel(record.status),
    createTime: record.createTime,
    detailPath: instanceDetailPath(record.id)
  }
}

export function presentListPage(page = {}) {
  return {
    records: (page.records || []).map(presentListItem),
    total: page.total || 0,
    pages: page.pages || 0
  }
}

export function emptyQuery() {
  return {
    keyword: '',
    status: '',
    createTimeBegin: '',
    createTimeEnd: ''
  }
}

export function presentQuery(query = {}) {
  const params = {}
  if (query.keyword) {
    params.keyword = query.keyword
  }
  if (query.status !== '' && query.status != null) {
    params.status = query.status
  }
  if (query.createTimeBegin) {
    params.createTimeBegin = query.createTimeBegin
  }
  if (query.createTimeEnd) {
    params.createTimeEnd = query.createTimeEnd
  }
  return params
}

export function listRequest(page, limit, query = {}) {
  return {
    method: 'get',
    url: `/admin/process/${page}/${limit}`,
    params: presentQuery(query)
  }
}

export function instanceDetailPath(id) {
  return `/processMgr/process/${id}`
}

export const STATUS_OPTIONS = [
  { value: 1, label: '审批中' },
  { value: 2, label: '审批通过' },
  { value: -1, label: '审批拒绝' }
]
