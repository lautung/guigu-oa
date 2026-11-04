import { formatBytes, formatPercent, formatUptime, type MetricLine } from '@/config/monitor-server'
import type { ApiRequestConfig, CacheKeyRecord, CacheMonitorSnapshot } from '@/types/domain'

/**
 * 缓存监控与缓存列表的投影（ADR 0060，#241）。INFO 快照按五段独立降级——
 * 服务端读不到的段是 `null`，卡片显示「不可用」，不整页报错。票面提到的
 * 「模式」与「碎片率」不在 #237 落地的 VO 合同里（INFO 只解析 server/memory/
 * clients/stats/keyspace 五段为字段），故这两项不展示；maxmemory 为 0 时
 * 按服务端注释视作「未设上限」。
 */

/** 缓存列表页的唯一动作按钮点（ADR 0052/0060）：读不设点，页面授予覆盖读。 */
export const MONITOR_CACHE_PERMISSIONS = {
  clean: 'monitor:cache:clean',
} as const

/** 浏览白名单（ADR 0060/0062）：五个前缀，与服务端 CacheMonitorService.LIST_PREFIXES 一致；顺序即下拉顺序。 */
export const CACHE_PREFIX_OPTIONS = [
  'perm:',
  'login:session:',
  'login:attempt:',
  'token:revoked:',
  'config:',
] as const

export type CachePrefix = (typeof CACHE_PREFIX_OPTIONS)[number]

/** 默认「加载更多」窗口大小：与服务端 keys 接口的 count 默认值一致。 */
export const DEFAULT_KEY_COUNT = 100

export function cacheInfoRequest(): ApiRequestConfig {
  return { method: 'get', url: '/admin/monitor/cache' }
}

export function cacheKeysRequest(params: {
  prefix: string
  cursor?: number
  count?: number
}): ApiRequestConfig {
  return {
    method: 'get',
    url: '/admin/monitor/cache/keys',
    params: {
      prefix: params.prefix,
      cursor: params.cursor ?? 0,
      count: params.count ?? DEFAULT_KEY_COUNT,
    },
  }
}

export function deleteCacheKeyRequest(key: string): ApiRequestConfig {
  return {
    method: 'delete',
    url: '/admin/monitor/cache/keys',
    params: { key },
  }
}

export function clearPermissionCacheRequest(): ApiRequestConfig {
  return { method: 'delete', url: '/admin/monitor/cache/perm' }
}

export interface CacheInfoSection {
  available: boolean
  lines: MetricLine[]
}

export interface CacheDbRow {
  db: string
  keys: string
  expires: string
  avgTtl: string
}

export interface CacheInfoView {
  server: CacheInfoSection
  memory: CacheInfoSection
  clients: CacheInfoSection
  keyspaceAvailable: boolean
  dbs: CacheDbRow[]
}

function unavailableSection(): CacheInfoSection {
  return { available: false, lines: [] }
}

function sectionOf(lines: MetricLine[]): CacheInfoSection {
  return lines.length > 0 ? { available: true, lines } : unavailableSection()
}

function textOf(value: number | null | undefined, suffix = ''): string {
  return typeof value === 'number' && Number.isFinite(value) ? `${value}${suffix}` : '—'
}

/** 内存上限：0 是 Redis 的「未设上限」，与服务端 VO 注释一致。 */
function maxMemoryLabel(value: number | null | undefined): string {
  return typeof value === 'number' && Number.isFinite(value) && value > 0
    ? formatBytes(value)
    : '无上限'
}

/** 命中率 = hits / (hits + misses)；两边都没读到（总数 0）就不给数。 */
function hitRateLabel(stats: CacheMonitorSnapshot['stats']): string {
  const hits = stats?.keyspaceHits
  const misses = stats?.keyspaceMisses
  if (typeof hits !== 'number' || typeof misses !== 'number') return '—'
  const total = hits + misses
  return total > 0 ? formatPercent((hits / total) * 100) : '—'
}

/** INFO 快照投影：各段独立降级；客户端与命中率合一张卡（命中率需要 stats 段）。 */
export function presentCacheInfo(snapshot: CacheMonitorSnapshot | null | undefined): CacheInfoView {
  const server = snapshot?.server
  const memory = snapshot?.memory
  const clients = snapshot?.clients
  const stats = snapshot?.stats

  return {
    server: server
      ? sectionOf([
          { label: '版本', value: server.redisVersion || '—' },
          { label: '运行时长', value: formatUptime(server.uptimeSeconds) },
        ])
      : unavailableSection(),
    memory: memory
      ? sectionOf([
          { label: '已用内存', value: formatBytes(memory.usedMemoryBytes) },
          { label: '内存上限', value: maxMemoryLabel(memory.maxMemoryBytes) },
        ])
      : unavailableSection(),
    clients:
      clients || stats
        ? sectionOf([
            { label: '连接客户端', value: textOf(clients?.connectedClients) },
            { label: '命中', value: textOf(stats?.keyspaceHits) },
            { label: '未命中', value: textOf(stats?.keyspaceMisses) },
            { label: '命中率', value: hitRateLabel(stats) },
            { label: '已过期键', value: textOf(stats?.expiredKeys) },
          ])
        : unavailableSection(),
    keyspaceAvailable: Array.isArray(snapshot?.keyspace),
    dbs: (snapshot?.keyspace ?? []).map((entry) => ({
      db: entry?.db || '—',
      keys: textOf(entry?.keys),
      expires: textOf(entry?.expires),
      avgTtl: textOf(entry?.avgTtlMs, ' ms'),
    })),
  }
}

export interface CacheKeyRow {
  key: string
  /** 服务端口径的秒数；-1 无过期，缺失为 null。 */
  ttl: number | null
  ttlLabel: string
  deletable: boolean
}

/** 单键删除的清理白名单（ADR 0060）：只允许权限缓存家族 perm:*，其余前缀只读。 */
export function isDeletableCacheKey(key: string): boolean {
  return key.startsWith('perm:')
}

/** 键行投影：TTL -1 显示「无过期」；行内删除只对 perm: 前缀出现。 */
export function presentCacheKeyRow(record: CacheKeyRecord | undefined): CacheKeyRow {
  const key = record?.key || ''
  const ttl = typeof record?.ttl === 'number' && Number.isFinite(record.ttl) ? record.ttl : null
  return {
    key,
    ttl,
    ttlLabel: ttl === null ? '—' : ttl < 0 ? '无过期' : `${ttl} 秒`,
    deletable: isDeletableCacheKey(key),
  }
}

/** 服务端 nextCursor 为 0（含缺失）即扫描结束。 */
export function hasMoreKeys(nextCursor: number | null | undefined): boolean {
  return typeof nextCursor === 'number' && Number.isFinite(nextCursor) && nextCursor > 0
}

/**
 * 写后「刷新当前页」的读法：把已累计的窗口整段重读（删除后键序前移，逐窗
 * 重放会错位），窗口为空时退回一个默认首页。
 */
export function refreshCountOf(rowCount: number): number {
  return rowCount > 0 ? rowCount : DEFAULT_KEY_COUNT
}
