import { ROUTES } from '@/navigation/constants'
import type { RouteLocationNormalizedLoaded } from 'vue-router'
import type {
  ApiClient,
  ApiRequestConfig,
  ApiResponse,
  FormDataRecord,
  PageResult,
  ProcessFormValues,
  ProcessInstanceRecord,
  PresentedWorkListRecord,
} from '@/types/domain'

export const WORK_LIST_KINDS = {
  PENDING: 'pending',
  PROCESSED: 'processed',
  STARTED: 'started',
  COPIED: 'copied',
} as const

export type WorkListKind = (typeof WORK_LIST_KINDS)[keyof typeof WORK_LIST_KINDS]

const LIST_HIDDEN_FIELD_KEYS = ['图片']

const LIST_ENDPOINTS: Record<WorkListKind, string> = {
  [WORK_LIST_KINDS.PENDING]: 'findPending',
  [WORK_LIST_KINDS.PROCESSED]: 'findProcessed',
  [WORK_LIST_KINDS.STARTED]: 'findStarted',
  [WORK_LIST_KINDS.COPIED]: 'findCarbonCopied',
}

interface WorkListDefinition {
  title: string
  emptyDescription: string
  returnPath: string
  readOnly: boolean
  emphasis: string
  relationLabel: string
  detailPath: (record: ProcessInstanceRecord) => string
}

const LIST_PAGES: Record<WorkListKind, WorkListDefinition> = {
  [WORK_LIST_KINDS.PENDING]: {
    title: '待处理',
    emptyDescription: '暂无待处理审批',
    returnPath: ROUTES.PENDING,
    readOnly: false,
    emphasis: '审批动作',
    relationLabel: '等待我处理',
    detailPath: (record) => `${ROUTES.PENDING}/${record.id}/${record.taskId}`,
  },
  [WORK_LIST_KINDS.PROCESSED]: {
    title: '已处理',
    emptyDescription: '暂无已处理审批',
    returnPath: ROUTES.PROCESSED,
    readOnly: true,
    emphasis: '回看',
    relationLabel: '我已处理',
    detailPath: (record) => `${ROUTES.PROCESSED}/${record.id}`,
  },
  [WORK_LIST_KINDS.STARTED]: {
    title: '已发起',
    emptyDescription: '暂无已发起审批',
    returnPath: ROUTES.STARTED,
    readOnly: true,
    emphasis: '整体进度',
    relationLabel: '我发起的审批',
    detailPath: (record) => `${ROUTES.STARTED}/${record.id}`,
  },
  [WORK_LIST_KINDS.COPIED]: {
    title: '抄送我的',
    emptyDescription: '暂无抄送审批',
    returnPath: ROUTES.COPIED,
    readOnly: true,
    emphasis: '知情',
    relationLabel: '抄送给我',
    detailPath: (record) => `${ROUTES.COPIED}/${record.id}`,
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
  return Object.keys(formShowData)
    .filter((key) => !LIST_HIDDEN_FIELD_KEYS.includes(key))
    .map((key) => ({ key, value: formShowData[key] }))
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
    detailPath: page.detailPath(record),
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

export async function loadWorkList(
  kind: WorkListKind,
  page: number,
  limit: number,
  request: ApiClient,
): Promise<WorkListPage> {
  const response: ApiResponse<PageResult<ProcessInstanceRecord>> = await request(
    workListRequest(kind, page, limit),
  )
  return presentWorkList(kind, response.data || {})
}

export function workListPerspective(kind: WorkListKind): WorkListPerspective {
  const definition = LIST_PAGES[kind]
  return {
    kind,
    title: definition.title,
    returnPath: definition.returnPath,
    readOnly: definition.readOnly,
    emphasis: definition.emphasis,
  }
}

function isWorkListKind(value: unknown): value is WorkListKind {
  return typeof value === 'string' && Object.hasOwn(LIST_PAGES, value)
}

export function workListPerspectiveFromRoute(
  route: Pick<RouteLocationNormalizedLoaded, 'meta'>,
): WorkListPerspective {
  const kind = route.meta?.workListKind
  if (!isWorkListKind(kind)) {
    return {
      kind: null,
      title: '',
      returnPath: '',
      readOnly: true,
      emphasis: '',
    }
  }
  return workListPerspective(kind)
}

export function afterPendingAction({ isInitiator = false }: { isInitiator?: boolean } = {}) {
  const remainingKinds: WorkListKind[] = [WORK_LIST_KINDS.PROCESSED]
  if (isInitiator) {
    remainingKinds.push(WORK_LIST_KINDS.STARTED)
  }
  return {
    leaveKind: WORK_LIST_KINDS.PENDING,
    enterKind: WORK_LIST_KINDS.PROCESSED,
    remainingKinds,
  }
}
