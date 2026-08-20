import {
  afterPendingAction,
  parseFormValues,
  statusLabel,
  statusTagType,
  workListPerspective
} from '@/work/work-list'

function fieldsFromShowData(formShowData = {}) {
  return Object.keys(formShowData).map(key => ({ key, value: formShowData[key] }))
}

export function presentDetail(payload = {}, perspective = {}) {
  const process = payload.process || {}
  const formValues = parseFormValues(process.formValues)
  const formShowData = formValues.formShowData || {}
  const readOnly = !!perspective.readOnly
  const emphasizeProgress = perspective.emphasis === '整体进度'

  return {
    id: process.id,
    title: process.title,
    createTime: process.createTime,
    status: process.status,
    statusLabel: statusLabel(process.status),
    statusTagType: statusTagType(process.status),
    progressLabel: emphasizeProgress ? (process.description || '') : '',
    recordsFirst: emphasizeProgress,
    formFields: fieldsFromShowData(formShowData),
    records: (payload.processRecordList || []).map(record => ({
      operateUser: record.operateUser,
      description: record.description,
      createTime: record.createTime
    })),
    canApprove: !readOnly && !!payload.isApprove,
    perspective
  }
}

export function buildApprovalPayload({ processId, taskId, status }) {
  return { processId, taskId, status }
}

export function postApprovalRoute() {
  return workListPerspective(afterPendingAction().enterKind).returnPath
}
