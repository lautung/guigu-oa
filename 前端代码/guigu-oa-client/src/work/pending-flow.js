import { ROUTES } from '@/navigation/access-policy'
import {
  WORK_LIST_KINDS,
  parseFormValues,
  presentWorkList,
  statusLabel,
  statusTagType
} from '@/work/work-list'

export {
  parseFormValues,
  statusLabel,
  statusTagType,
  summaryFields
} from '@/work/work-list'

export function pendingDetailPath(id, taskId) {
  return `${ROUTES.PENDING}/${id}/${taskId}`
}

export function processedDetailPath(id) {
  return `${ROUTES.PROCESSED}/${id}`
}

export function startedDetailPath(id) {
  return `${ROUTES.STARTED}/${id}`
}

function toListPage(presented) {
  return {
    records: presented.records,
    total: presented.total,
    pages: presented.pages
  }
}

export function presentListItem(record) {
  return presentWorkList(WORK_LIST_KINDS.PENDING, { records: [record] }).records[0]
}

export function presentProcessedListItem(record) {
  return presentWorkList(WORK_LIST_KINDS.PROCESSED, { records: [record] }).records[0]
}

export function presentStartedListItem(record) {
  return presentWorkList(WORK_LIST_KINDS.STARTED, { records: [record] }).records[0]
}

export function presentListPage(page = {}) {
  return toListPage(presentWorkList(WORK_LIST_KINDS.PENDING, page))
}

export function presentProcessedListPage(page = {}) {
  return toListPage(presentWorkList(WORK_LIST_KINDS.PROCESSED, page))
}

export function presentStartedListPage(page = {}) {
  return toListPage(presentWorkList(WORK_LIST_KINDS.STARTED, page))
}

function fieldsFromShowData(formShowData = {}) {
  return Object.keys(formShowData).map(key => ({ key, value: formShowData[key] }))
}

export function presentDetail(payload = {}, { readOnly = false } = {}) {
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
    canApprove: !readOnly && !!payload.isApprove
  }
}

export const READ_ONLY_DETAIL_ROUTE_NAMES = ['ProcessedDetail', 'StartedDetail', 'ProcessMgrDetail']

export function isReadOnlyDetailRoute(routeName) {
  return READ_ONLY_DETAIL_ROUTE_NAMES.includes(routeName)
}

export function buildApprovalPayload({ processId, taskId, status }) {
  return { processId, taskId, status }
}

export function postApprovalRoute() {
  return ROUTES.PROCESSED
}
