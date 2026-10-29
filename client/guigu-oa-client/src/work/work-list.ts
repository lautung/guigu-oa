import {
  WORK_LIST_KINDS,
  returnPathOfRoute,
  workDetailPath,
  workListKindOf,
  workListPath,
  type WorkListKind,
} from '@/router/paths'
import { formFields } from '@/work/form-values'
import type { RouteLocationNormalizedLoaded } from 'vue-router'
import type {
  ApiRequestConfig,
  FormDataRecord,
  PageResult,
  ProcessFormValues,
  ProcessInstanceRecord,
  PresentedWorkListRecord,
} from '@/types/domain'

const LIST_HIDDEN_FIELD_KEYS = ['图片']

const LIST_ENDPOINTS: Record<WorkListKind, string> = {
  [WORK_LIST_KINDS.PENDING]: 'findPending',
  [WORK_LIST_KINDS.PROCESSED]: 'findProcessed',
  [WORK_LIST_KINDS.STARTED]: 'findStarted',
  [WORK_LIST_KINDS.COPIED]: 'findCarbonCopied',
}

export interface WorkListDefinition {
  title: string
  emptyDescription: string
  readOnly: boolean
  emphasis: string
  relationLabel: string
}

const LIST_PAGES: Record<WorkListKind, WorkListDefinition> = {
  [WORK_LIST_KINDS.PENDING]: {
    title: '待处理',
    emptyDescription: '暂无待处理审批',
    readOnly: false,
    emphasis: '审批动作',
    relationLabel: '等待我处理',
  },
  [WORK_LIST_KINDS.PROCESSED]: {
    title: '已处理',
    emptyDescription: '暂无已处理审批',
    readOnly: true,
    emphasis: '回看',
    relationLabel: '我已处理',
  },
  [WORK_LIST_KINDS.STARTED]: {
    title: '已发起',
    emptyDescription: '暂无已发起审批',
    readOnly: true,
    emphasis: '整体进度',
    relationLabel: '我发起的审批',
  },
  [WORK_LIST_KINDS.COPIED]: {
    title: '抄送我的',
    emptyDescription: '暂无抄送审批',
    readOnly: true,
    emphasis: '知情',
    relationLabel: '抄送给我',
  },
}

export interface WorkListPage {
  kind: WorkListKind
  title: string
  emptyDescription: string
  records: PresentedWorkListRecord[]
  total: number
  pages: number
}

export interface WorkListPerspective {
  kind: WorkListKind | null
  title: string
  returnPath: string
  readOnly: boolean
  emphasis: string
}

export function statusLabel(status: number | undefined): string {
  if (status === 1) return '审批中'
  if (status === 2) return '审批通过'
  if (status === -1) return '审批拒绝'
  return ''
}

export type StatusTagType = 'warning' | 'success' | 'danger' | 'info'

export function statusTagType(status: number | undefined): StatusTagType {
  if (status === 1) return 'warning'
  if (status === 2) return 'success'
  if (status === -1) return 'danger'
  return 'info'
}

export function parseFormValues(formValues: unknown): ProcessFormValues {
  if (!formValues) {
    return { formShowData: {} }
  }
  if (typeof formValues === 'object') {
    return formValues as ProcessFormValues
  }
  if (typeof formValues !== 'string') {
    return { formShowData: {} }
  }
  try {
    const parsed: unknown = JSON.parse(formValues)
    return typeof parsed === 'object' && parsed !== null
      ? (parsed as ProcessFormValues)
      : { formShowData: {} }
  } catch {
    return { formShowData: {} }
  }
}

export function summaryFields(formShowData: FormDataRecord = {}) {
  return formFields(formShowData).filter((field) => !LIST_HIDDEN_FIELD_KEYS.includes(field.key))
}

export function workListDefinition(kind: WorkListKind): WorkListDefinition {
  return LIST_PAGES[kind]
}

export function workListRequest(kind: WorkListKind, page: number, limit: number): ApiRequestConfig {
  return {
    method: 'get',
    url: `/admin/process/${LIST_ENDPOINTS[kind]}/${page}/${limit}`,
  }
}

function presentWorkListItem(
  kind: WorkListKind,
  record: ProcessInstanceRecord = {},
): PresentedWorkListRecord {
  const page = LIST_PAGES[kind]
  const formValues = parseFormValues(record.formValues)
  const formShowData = formValues.formShowData || {}

  return {
    id: record.id,
    taskId: record.taskId,
    title: record.title,
    name: record.name,
    processTypeName: record.processTypeName,
    createTime: record.createTime,
    status: record.status,
    statusLabel: statusLabel(record.status),
    statusTagType: statusTagType(record.status),
    summaryFields: summaryFields(formShowData),
    detailPath: workDetailPath(kind, record),
    kind,
    relationLabel: page.relationLabel,
    progressLabel: kind === WORK_LIST_KINDS.STARTED ? record.description || '' : '',
    rowKey: record.taskId ? `${record.id}-${record.taskId}` : String(record.id),
  }
}

export function presentWorkList(
  kind: WorkListKind,
  page: PageResult<ProcessInstanceRecord> = {},
): WorkListPage {
  const definition = LIST_PAGES[kind]
  return {
    kind,
    title: definition.title,
    emptyDescription: definition.emptyDescription,
    records: (page.records || []).map((record) => presentWorkListItem(kind, record)),
    total: page.total || 0,
    pages: page.pages || 0,
  }
}

export function workListPerspective(kind: WorkListKind): WorkListPerspective {
  const definition = LIST_PAGES[kind]
  return {
    kind,
    title: definition.title,
    returnPath: workListPath(kind),
    readOnly: definition.readOnly,
    emphasis: definition.emphasis,
  }
}

export function workListPerspectiveFromRoute(
  route: Pick<RouteLocationNormalizedLoaded, 'meta' | 'path'>,
): WorkListPerspective {
  const kind = workListKindOf(route.meta)
  if (!kind) {
    return {
      kind: null,
      title: '',
      returnPath: returnPathOfRoute(route.path),
      readOnly: true,
      emphasis: '',
    }
  }
  return workListPerspective(kind)
}
