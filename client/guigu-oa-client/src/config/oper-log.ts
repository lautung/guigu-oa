import type {
  ApiRequestConfig,
  AuditTagType,
  EntityId,
  OperLogDetail,
  OperLogListItem,
  OperLogRecord,
  PageResult,
} from '@/types/domain'

/** 业务类型标签镜像服务端 BusinessType 的稳定 code（0..7），顺序即下拉顺序。 */
export const BUSINESS_TYPE_OPTIONS = [
  { value: 0, label: '其他' },
  { value: 1, label: '新增' },
  { value: 2, label: '修改' },
  { value: 3, label: '删除' },
  { value: 4, label: '授权' },
  { value: 5, label: '发布' },
  { value: 6, label: '清空' },
  { value: 7, label: '解锁' },
] as const

export const OPER_LOG_STATUS_OPTIONS = [
  { value: 0, label: '成功' },
  { value: 1, label: '失败' },
] as const

export const OPER_LOG_PERMISSIONS = {
  remove: 'system:operLog:remove',
  clean: 'system:operLog:clean',
} as const

export interface OperLogQuery {
  operName?: string
  title?: string
  businessType?: number | ''
  status?: number | ''
  createTimes?: string[]
}

export function businessTypeLabel(value: number | null | undefined): string {
  return BUSINESS_TYPE_OPTIONS.find((option) => option.value === value)?.label || ''
}

export function operLogStatusLabel(value: number | null | undefined): string {
  return OPER_LOG_STATUS_OPTIONS.find((option) => option.value === value)?.label || ''
}

export function operLogStatusTagType(value: number | null | undefined): AuditTagType {
  if (value === 0) return 'success'
  if (value === 1) return 'danger'
  return 'info'
}

/** 空字段不进查询串；0 是合法的业务类型/状态，不能被当成空值丢掉。 */
export function presentQuery(query: OperLogQuery = {}): Record<string, string | number> {
  const params: Record<string, string | number> = {}
  if (query.operName) params.operName = query.operName
  if (query.title) params.title = query.title
  if (query.businessType !== '' && query.businessType != null) {
    params.businessType = query.businessType
  }
  if (query.status !== '' && query.status != null) params.status = query.status
  const createTimes = query.createTimes || []
  if (createTimes.length === 2 && createTimes[0] && createTimes[1]) {
    params.createTimeBegin = createTimes[0]
    params.createTimeEnd = createTimes[1]
  }
  return params
}

export function emptyQuery(): OperLogQuery {
  return {
    operName: '',
    title: '',
    businessType: '',
    status: '',
    createTimes: [],
  }
}

export function presentListRecord(record: OperLogRecord = {}): OperLogListItem {
  return {
    id: record.id || '',
    title: record.title,
    businessType: record.businessType,
    businessTypeLabel: businessTypeLabel(record.businessType),
    operName: record.operName,
    deptName: record.deptName,
    operIp: record.operIp,
    status: record.status,
    statusLabel: operLogStatusLabel(record.status),
    statusTagType: operLogStatusTagType(record.status),
    costTime: record.costTime,
    costTimeText: costTimeText(record.costTime),
    createTime: record.createTime,
  }
}

export function presentListPage(page: PageResult<OperLogRecord> = {}) {
  return {
    records: (page.records || []).map(presentListRecord),
    total: page.total || 0,
    pages: page.pages || 0,
  }
}

export function costTimeText(value: number | null | undefined): string {
  return value == null ? '' : `${value} ms`
}

/** 参数是 JSON 时缩进展示；截断或非 JSON 原文回退，绝不丢信息。 */
export function formatOperParam(raw: string | null | undefined): string {
  if (!raw) return ''
  try {
    return JSON.stringify(JSON.parse(raw), null, 2)
  } catch {
    return raw
  }
}

export function presentDetail(record: OperLogRecord = {}): OperLogDetail {
  return {
    id: record.id || '',
    title: record.title || '',
    businessTypeLabel: businessTypeLabel(record.businessType),
    operName: record.operName || '',
    deptName: record.deptName || '',
    operIp: record.operIp || '',
    requestMethod: record.requestMethod || '',
    operUrl: record.operUrl || '',
    method: record.method || '',
    statusLabel: operLogStatusLabel(record.status),
    statusTagType: operLogStatusTagType(record.status),
    resultCodeText: record.resultCode == null ? '' : String(record.resultCode),
    resultMessage: record.resultMessage || '',
    errorMsg: record.errorMsg || '',
    costTimeText: costTimeText(record.costTime),
    operParamText: formatOperParam(record.operParam),
    createTime: record.createTime || '',
  }
}

const OPER_LOG_API = '/admin/system/sysOperLog'

export function listRequest(
  page: number,
  limit: number,
  params: Record<string, string | number> = {},
): ApiRequestConfig {
  return {
    method: 'get',
    url: `${OPER_LOG_API}/${page}/${limit}`,
    params,
  }
}

export function detailRequest(id: EntityId): ApiRequestConfig {
  return {
    method: 'get',
    url: `${OPER_LOG_API}/get/${id}`,
  }
}

export function removeRequest(idList: readonly EntityId[]): ApiRequestConfig {
  return {
    method: 'delete',
    url: `${OPER_LOG_API}/remove`,
    data: [...idList],
  }
}

export function cleanRequest(): ApiRequestConfig {
  return {
    method: 'delete',
    url: `${OPER_LOG_API}/clean`,
  }
}
