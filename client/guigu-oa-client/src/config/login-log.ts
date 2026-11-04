import type {
  ApiRequestConfig,
  AuditTagType,
  EntityId,
  LoginLogListItem,
  LoginLogRecord,
  PageResult,
} from '@/types/domain'

/**
 * 登录状态四值（0 成功 / 1 失败 / 2 锁定 / 3 解锁）的标签由字典
 * `sys_login_status` 供值（#251，ADR 0062）；本文件只保留视觉 tag type。
 */

/** 事件类型（#253）：登录尝试与自助注册共用一张表，靠这一列区分。 */
export const LOGIN_EVENT_OPTIONS = [
  { value: 'login', label: '登录' },
  { value: 'register', label: '注册' },
] as const

export const LOGIN_LOG_PERMISSIONS = {
  remove: 'system:loginLog:remove',
  clean: 'system:loginLog:clean',
  unlock: 'system:loginLog:unlock',
} as const

export interface LoginLogQuery {
  username?: string
  ip?: string
  status?: number | ''
  event?: string
  createTimes?: string[]
}

export function loginEventLabel(value: string | null | undefined): string {
  return LOGIN_EVENT_OPTIONS.find((option) => option.value === value)?.label || ''
}

export function loginStatusTagType(value: number | null | undefined): AuditTagType {
  if (value === 0) return 'success'
  if (value === 1) return 'danger'
  if (value === 2) return 'warning'
  if (value === 3) return 'info'
  return 'info'
}

/** 空字段不进查询串；状态 0（成功）是合法值，不能被当成空值丢掉。 */
export function presentQuery(query: LoginLogQuery = {}): Record<string, string | number> {
  const params: Record<string, string | number> = {}
  if (query.username) params.username = query.username
  if (query.ip) params.ip = query.ip
  if (query.status !== '' && query.status != null) params.status = query.status
  if (query.event) params.event = query.event
  const createTimes = query.createTimes || []
  if (createTimes.length === 2 && createTimes[0] && createTimes[1]) {
    params.createTimeBegin = createTimes[0]
    params.createTimeEnd = createTimes[1]
  }
  return params
}

export function emptyQuery(): LoginLogQuery {
  return {
    username: '',
    ip: '',
    status: '',
    event: '',
    createTimes: [],
  }
}

export function presentListRecord(record: LoginLogRecord = {}): LoginLogListItem {
  return {
    id: record.id || '',
    username: record.username,
    ip: record.ip,
    browser: record.browser,
    os: record.os,
    status: record.status,
    statusTagType: loginStatusTagType(record.status),
    event: record.event,
    eventLabel: loginEventLabel(record.event),
    msg: record.msg,
    createTime: record.createTime,
  }
}

export function presentListPage(page: PageResult<LoginLogRecord> = {}) {
  return {
    records: (page.records || []).map(presentListRecord),
    total: page.total || 0,
    pages: page.pages || 0,
  }
}

const LOGIN_LOG_API = '/admin/system/sysLoginLog'

export function listRequest(
  page: number,
  limit: number,
  params: Record<string, string | number> = {},
): ApiRequestConfig {
  return {
    method: 'get',
    url: `${LOGIN_LOG_API}/${page}/${limit}`,
    params,
  }
}

export function removeRequest(idList: readonly EntityId[]): ApiRequestConfig {
  return {
    method: 'delete',
    url: `${LOGIN_LOG_API}/remove`,
    data: [...idList],
  }
}

export function cleanRequest(): ApiRequestConfig {
  return {
    method: 'delete',
    url: `${LOGIN_LOG_API}/clean`,
  }
}

export function unlockRequest(username: string): ApiRequestConfig {
  return {
    method: 'post',
    url: `${LOGIN_LOG_API}/unlock/${encodeURIComponent(username)}`,
  }
}
