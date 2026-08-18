import { ROUTES } from '@/navigation/access-policy'

const LIST_HIDDEN_FIELD_KEYS = ['图片']

export function statusLabel(status) {
  if (status === 1) return '审批中'
  if (status === 2) return '审批通过'
  if (status === -1) return '审批拒绝'
  return ''
}

export function statusTagType(status) {
  if (status === 1) return 'warning'
  if (status === 2) return 'success'
  if (status === -1) return 'danger'
  return 'info'
}

export function parseFormValues(formValues) {
  if (!formValues) {
    return { formShowData: {} }
  }
  if (typeof formValues === 'object') {
    return formValues
  }
  try {
    return JSON.parse(formValues)
  } catch (error) {
    return { formShowData: {} }
  }
}

function fieldsFromShowData(formShowData = {}, { hideImageFields = false } = {}) {
  return Object.keys(formShowData)
    .filter(key => !(hideImageFields && LIST_HIDDEN_FIELD_KEYS.includes(key)))
    .map(key => ({ key, value: formShowData[key] }))
}

export function summaryFields(formShowData = {}) {
  return fieldsFromShowData(formShowData, { hideImageFields: true })
}

export function pendingDetailPath(id, taskId) {
  return `${ROUTES.PENDING}/${id}/${taskId}`
}

export function presentListItem(record) {
  const formValues = parseFormValues(record.formValues)
  const formShowData = formValues.formShowData || {}

  return {
    id: record.id,
    taskId: record.taskId,
    title: record.title,
    createTime: record.createTime,
    status: record.status,
    statusLabel: statusLabel(record.status),
    statusTagType: statusTagType(record.status),
    summaryFields: summaryFields(formShowData),
    detailPath: pendingDetailPath(record.id, record.taskId)
  }
}

export function presentListPage(page = {}) {
  return {
    records: (page.records || []).map(presentListItem),
    total: page.total || 0,
    pages: page.pages || 0
  }
}

export function presentDetail(payload = {}) {
  const process = payload.process || {}
  const formValues = parseFormValues(process.formValues)
  const formShowData = formValues.formShowData || {}

  return {
    id: process.id,
    title: process.title,
    createTime: process.createTime,
    status: process.status,
    statusLabel: statusLabel(process.status),
    statusTagType: statusTagType(process.status),
    formFields: fieldsFromShowData(formShowData),
    records: (payload.processRecordList || []).map(record => ({
      operateUser: record.operateUser,
      description: record.description,
      createTime: record.createTime
    })),
    canApprove: !!payload.isApprove
  }
}

export function buildApprovalPayload({ processId, taskId, status }) {
  return { processId, taskId, status }
}

export function postApprovalRoute() {
  return ROUTES.PROCESSED
}
