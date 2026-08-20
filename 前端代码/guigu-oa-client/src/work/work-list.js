import { ROUTES } from '@/navigation/constants'

export const WORK_LIST_KINDS = {
  PENDING: 'pending',
  PROCESSED: 'processed',
  STARTED: 'started'
}

const LIST_HIDDEN_FIELD_KEYS = ['图片']

const LIST_ENDPOINTS = {
  [WORK_LIST_KINDS.PENDING]: 'findPending',
  [WORK_LIST_KINDS.PROCESSED]: 'findProcessed',
  [WORK_LIST_KINDS.STARTED]: 'findStarted'
}

const LIST_PAGES = {
  [WORK_LIST_KINDS.PENDING]: {
    title: '待处理',
    emptyDescription: '暂无待处理审批',
    returnPath: ROUTES.PENDING,
    readOnly: false,
    emphasis: '审批动作',
    relationLabel: '等待我处理',
    detailPath: (record) => `${ROUTES.PENDING}/${record.id}/${record.taskId}`
  },
  [WORK_LIST_KINDS.PROCESSED]: {
    title: '已处理',
    emptyDescription: '暂无已处理审批',
    returnPath: ROUTES.PROCESSED,
    readOnly: true,
    emphasis: '回看',
    relationLabel: '我已处理',
    detailPath: (record) => `${ROUTES.PROCESSED}/${record.id}`
  },
  [WORK_LIST_KINDS.STARTED]: {
    title: '已发起',
    emptyDescription: '暂无已发起审批',
    returnPath: ROUTES.STARTED,
    readOnly: true,
    emphasis: '整体进度',
    relationLabel: '我发起的审批',
    detailPath: (record) => `${ROUTES.STARTED}/${record.id}`
  }
}

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

export function summaryFields(formShowData = {}) {
  return Object.keys(formShowData)
    .filter(key => !LIST_HIDDEN_FIELD_KEYS.includes(key))
    .map(key => ({ key, value: formShowData[key] }))
}

export function workListRequest(kind, page, limit) {
  return {
    method: 'get',
    url: `/admin/process/${LIST_ENDPOINTS[kind]}/${page}/${limit}`
  }
}

function presentWorkListItem(kind, record = {}) {
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
    progressLabel: kind === WORK_LIST_KINDS.STARTED ? (record.description || '') : '',
    rowKey: record.taskId ? `${record.id}-${record.taskId}` : String(record.id)
  }
}

export function presentWorkList(kind, page = {}) {
  const definition = LIST_PAGES[kind]
  return {
    kind,
    title: definition.title,
    emptyDescription: definition.emptyDescription,
    records: (page.records || []).map(record => presentWorkListItem(kind, record)),
    total: page.total || 0,
    pages: page.pages || 0
  }
}

export async function loadWorkList(kind, page, limit, request) {
  const response = await request(workListRequest(kind, page, limit))
  return presentWorkList(kind, (response && response.data) || {})
}

export function workListPerspective(kind) {
  const definition = LIST_PAGES[kind]
  return {
    kind,
    title: definition.title,
    returnPath: definition.returnPath,
    readOnly: definition.readOnly,
    emphasis: definition.emphasis
  }
}

export function workListPerspectiveFromRoute(route = {}) {
  const kind = route.meta && route.meta.workListKind
  if (!kind || !LIST_PAGES[kind]) {
    return {
      kind: null,
      title: '',
      returnPath: '',
      readOnly: true,
      emphasis: ''
    }
  }
  return workListPerspective(kind)
}

export function afterPendingAction({ isInitiator = false } = {}) {
  const remainingKinds = [WORK_LIST_KINDS.PROCESSED]
  if (isInitiator) {
    remainingKinds.push(WORK_LIST_KINDS.STARTED)
  }
  return {
    leaveKind: WORK_LIST_KINDS.PENDING,
    enterKind: WORK_LIST_KINDS.PROCESSED,
    remainingKinds
  }
}
