import {
  afterPendingAction,
  parseFormValues,
  statusLabel,
  statusTagType,
  workListPerspective,
  type WorkListPerspective,
} from '@/work/work-list'
import type {
  EntityId,
  ProcessDetailPayload,
  ProcessInstanceRecord,
  WorkRecordEntry,
  WorkSummaryField,
} from '@/types/domain'

function fieldsFromShowData(formShowData: Record<string, unknown> = {}): WorkSummaryField[] {
  return Object.keys(formShowData).map((key) => ({ key, value: formShowData[key] }))
}

export interface ProcessDetailViewModel {
  id?: EntityId
  title?: string
  createTime?: string
  status?: number
  statusLabel: string
  statusTagType: 'warning' | 'success' | 'danger' | 'info'
  progressLabel: string
  recordsFirst: boolean
  formFields: WorkSummaryField[]
  records: WorkRecordEntry[]
  canApprove: boolean
  perspective: Partial<WorkListPerspective>
}

export function presentDetail(
  payload: ProcessDetailPayload = {},
  perspective: Partial<WorkListPerspective> = {},
): ProcessDetailViewModel {
  const process: ProcessInstanceRecord = payload.process || {}
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
    progressLabel: emphasizeProgress ? process.description || '' : '',
    recordsFirst: emphasizeProgress,
    formFields: fieldsFromShowData(formShowData),
    records: (payload.processRecordList || []).map((record) => ({
      operateUser: record.operateUser,
      description: record.description,
      createTime: record.createTime,
    })),
    canApprove: !readOnly && !!payload.isApprove,
    perspective,
  }
}

export function buildApprovalPayload({
  processId,
  taskId,
  status,
}: {
  processId: EntityId
  taskId: EntityId
  status: number
}): { processId: EntityId; taskId: EntityId; status: number } {
  return { processId, taskId, status }
}

export function postApprovalRoute(): string {
  return workListPerspective(afterPendingAction().enterKind).returnPath
}
