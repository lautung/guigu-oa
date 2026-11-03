import request from '@/request/request'
import {
  cacheInfoRequest,
  cacheKeysRequest,
  clearPermissionCacheRequest,
  deleteCacheKeyRequest,
} from '@/config/monitor-cache'
import { dataMetricsRequest } from '@/config/monitor-data'
import { serverMetricsRequest } from '@/config/monitor-server'
import { forceLogoutRequest, listRequest } from '@/config/monitor-online'
import type {
  ApiResponse,
  CacheKeyPage,
  CacheMonitorSnapshot,
  MonitorDataSnapshot,
  OnlineSessionRecord,
  PageResult,
  ServerMetrics,
} from '@/types/domain'

export function findPage(
  page: number,
  limit: number,
  params: Record<string, string | number> = {},
): Promise<ApiResponse<PageResult<OnlineSessionRecord>>> {
  return request<PageResult<OnlineSessionRecord>>(listRequest(page, limit, params))
}

export function forceLogout(sessionId: string): Promise<ApiResponse<void>> {
  return request<void>(forceLogoutRequest(sessionId))
}

/** 服务监控快照（ADR 0060）：不代理 actuator，只读自研接口；页面走作用域发的 serverMetricsRequest。 */
export function getServerMetrics(): Promise<ApiResponse<ServerMetrics | null>> {
  return request<ServerMetrics | null>(serverMetricsRequest())
}

/** 数据监控快照（ADR 0060）：Hikari 实时段 + 慢 SQL 内存环；执行包装同上。 */
export function getDataMetrics(): Promise<ApiResponse<MonitorDataSnapshot | null>> {
  return request<MonitorDataSnapshot | null>(dataMetricsRequest())
}

/** 缓存监控快照（ADR 0060，#241）：Redis INFO 五段解析字段；页面走作用域发的 cacheInfoRequest。 */
export function getCacheInfo(): Promise<ApiResponse<CacheMonitorSnapshot>> {
  return request<CacheMonitorSnapshot>(cacheInfoRequest())
}

/** 按白名单前缀的 SCAN 游标窗口；页面走作用域发的 cacheKeysRequest。 */
export function listCacheKeys(params: {
  prefix: string
  cursor?: number
  count?: number
}): Promise<ApiResponse<CacheKeyPage>> {
  return request<CacheKeyPage>(cacheKeysRequest(params))
}

/** 单键删除：只收 perm: 前缀（服务端把关），按钮点 monitor:cache:clean。 */
export function deleteCacheKey(key: string): Promise<ApiResponse<void>> {
  return request<void>(deleteCacheKeyRequest(key))
}

/** 清空全部权限缓存键（perm:cache:*），返回删除数量；按钮点 monitor:cache:clean。 */
export function clearPermissionCache(): Promise<ApiResponse<number>> {
  return request<number>(clearPermissionCacheRequest())
}
