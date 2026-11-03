import type { ApiRequestConfig, OnlineSessionListItem, OnlineSessionRecord } from '@/types/domain'

/** 在线用户的唯一动作按钮点（ADR 0052/0060）：读不设点，页面授予覆盖读。 */
export const MONITOR_ONLINE_PERMISSIONS = {
  forceLogout: 'monitor:online:forceLogout',
} as const

export interface OnlineSessionQuery {
  username?: string
  loginTimes?: string[]
}

/**
 * 空字段不进查询串；时间范围两端齐了才发（服务端 QueryVo 是闭区间
 * beginTime/endTime，形状与登录日志页一致，只换参数名）。
 */
export function presentQuery(query: OnlineSessionQuery = {}): Record<string, string | number> {
  const params: Record<string, string | number> = {}
  if (query.username) params.username = query.username
  const loginTimes = query.loginTimes || []
  if (loginTimes.length === 2 && loginTimes[0] && loginTimes[1]) {
    params.beginTime = loginTimes[0]
    params.endTime = loginTimes[1]
  }
  return params
}

export function emptyQuery(): OnlineSessionQuery {
  return {
    username: '',
    loginTimes: [],
  }
}

export function presentListRecord(record: OnlineSessionRecord = {}): OnlineSessionListItem {
  return {
    sessionId: record.sessionId || '',
    username: record.username || '',
    name: record.name || '',
    loginTime: record.loginTime || '',
    ip: record.ip || '',
    browser: record.browser || '',
    os: record.os || '',
    lastActive: record.lastActive || '',
    current: record.current === true,
  }
}

const ONLINE_API = '/admin/monitor/online'

export function listRequest(
  page: number,
  limit: number,
  params: Record<string, string | number> = {},
): ApiRequestConfig {
  return {
    method: 'get',
    url: `${ONLINE_API}/${page}/${limit}`,
    params,
  }
}

export function forceLogoutRequest(sessionId: string): ApiRequestConfig {
  return {
    method: 'post',
    url: `${ONLINE_API}/${encodeURIComponent(sessionId)}/forceLogout`,
  }
}
