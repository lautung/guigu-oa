import { describe, expect, it } from 'vitest'

import {
  CACHE_PREFIX_OPTIONS,
  DEFAULT_KEY_COUNT,
  MONITOR_CACHE_PERMISSIONS,
  cacheInfoRequest,
  cacheKeysRequest,
  clearPermissionCacheRequest,
  deleteCacheKeyRequest,
  hasMoreKeys,
  presentCacheInfo,
  presentCacheKeyRow,
  refreshCountOf,
} from '@/config/monitor-cache'

/** 完整快照（与服务端 CacheMonitorVo 字段一一对应，单位：字节/秒/毫秒）。 */
const fullSnapshot = {
  server: { redisVersion: '7.4.1', uptimeSeconds: 90061 },
  memory: {
    usedMemoryBytes: 536870912,
    usedMemoryHuman: '512.00M',
    maxMemoryBytes: 1073741824,
  },
  clients: { connectedClients: 5 },
  stats: { keyspaceHits: 80, keyspaceMisses: 20, expiredKeys: 10 },
  keyspace: [{ db: 'db0', keys: 4, expires: 1, avgTtlMs: 1234 }],
}

function linesOf(section, label) {
  return section.lines.find((line) => line.label === label)?.value
}

describe('缓存监控快照投影', () => {
  it('projects version, uptime, memory, clients and hit rate', () => {
    const view = presentCacheInfo(fullSnapshot)

    expect(view.server.available).toBe(true)
    expect(linesOf(view.server, '版本')).toBe('7.4.1')
    // 90061 秒 = 1 天 1 小时 1 分 1 秒，与服务监控同一条时长读法。
    expect(linesOf(view.server, '运行时长')).toBe('1 天 1 小时 1 分')

    expect(view.memory.available).toBe(true)
    expect(linesOf(view.memory, '已用内存')).toBe('512.0 MB')
    expect(linesOf(view.memory, '内存上限')).toBe('1.0 GB')

    expect(view.clients.available).toBe(true)
    expect(linesOf(view.clients, '连接客户端')).toBe('5')
    expect(linesOf(view.clients, '命中')).toBe('80')
    expect(linesOf(view.clients, '未命中')).toBe('20')
    expect(linesOf(view.clients, '命中率')).toBe('80.0%')
    expect(linesOf(view.clients, '已过期键')).toBe('10')
  })

  it('shows 无上限 when maxmemory is 0 (Redis 原值)', () => {
    const view = presentCacheInfo({
      ...fullSnapshot,
      memory: { ...fullSnapshot.memory, maxMemoryBytes: 0 },
    })

    expect(linesOf(view.memory, '内存上限')).toBe('无上限')
  })

  it('computes the hit rate only from hits and misses the server reports', () => {
    const view = presentCacheInfo({
      ...fullSnapshot,
      stats: { keyspaceHits: 0, keyspaceMisses: 0, expiredKeys: 3 },
    })

    expect(linesOf(view.clients, '命中率')).toBe('—')
    expect(linesOf(view.clients, '已过期键')).toBe('3')
  })

  it('marks each section unavailable on its own when the section is null', () => {
    const view = presentCacheInfo({
      ...fullSnapshot,
      memory: null,
      clients: null,
      keyspace: null,
    })

    expect(view.server.available).toBe(true)
    expect(view.memory.available).toBe(false)
    expect(view.clients.available).toBe(true)
    expect(linesOf(view.clients, '连接客户端')).toBe('—')
    expect(linesOf(view.clients, '命中率')).toBe('80.0%')
    expect(view.keyspaceAvailable).toBe(false)
  })

  it('marks the clients card unavailable when both clients and stats are null', () => {
    const view = presentCacheInfo({ ...fullSnapshot, clients: null, stats: null })

    expect(view.clients.available).toBe(false)
    expect(view.clients.lines).toEqual([])
  })

  it('shows 不可用 for every section when the whole snapshot is missing', () => {
    const view = presentCacheInfo(null)

    expect(view.server.available).toBe(false)
    expect(view.memory.available).toBe(false)
    expect(view.clients.available).toBe(false)
    expect(view.keyspaceAvailable).toBe(false)
  })

  it('keeps the keyspace card available with no rows for an empty keyspace list', () => {
    const view = presentCacheInfo({ ...fullSnapshot, keyspace: [] })

    expect(view.keyspaceAvailable).toBe(true)
    expect(view.dbs).toEqual([])
  })

  it('projects keyspace rows as text with dashes for missing counters', () => {
    const view = presentCacheInfo({
      ...fullSnapshot,
      keyspace: [
        { db: 'db0', keys: 4, expires: 1, avgTtlMs: 1234 },
        { db: 'db1', keys: null, expires: null, avgTtlMs: null },
      ],
    })

    expect(view.dbs).toEqual([
      { db: 'db0', keys: '4', expires: '1', avgTtl: '1234 ms' },
      { db: 'db1', keys: '—', expires: '—', avgTtl: '—' },
    ])
  })
})

describe('缓存键行投影', () => {
  it('shows 无过期 for ttl -1 and seconds otherwise', () => {
    expect(presentCacheKeyRow({ key: 'perm:cache:admin', ttl: -1 }).ttlLabel).toBe('无过期')
    expect(presentCacheKeyRow({ key: 'login:session:s1', ttl: 120 }).ttlLabel).toBe('120 秒')
  })

  it('keeps a missing ttl as a dash but keeps the row readable', () => {
    const row = presentCacheKeyRow({ key: 'perm:cache:admin', ttl: null })

    expect(row.ttlLabel).toBe('—')
    expect(row.key).toBe('perm:cache:admin')
  })

  it('marks only perm: keys deletable', () => {
    expect(presentCacheKeyRow({ key: 'perm:cache:admin', ttl: -1 }).deletable).toBe(true)
    expect(presentCacheKeyRow({ key: 'login:session:s1', ttl: -1 }).deletable).toBe(false)
    expect(presentCacheKeyRow({ key: 'token:revoked:t1', ttl: -1 }).deletable).toBe(false)
    expect(presentCacheKeyRow({ key: 'login:attempt:a1', ttl: 60 }).deletable).toBe(false)
  })
})

describe('游标窗口辅助', () => {
  it('treats cursor 0 (and missing) as the end of the scan', () => {
    expect(hasMoreKeys(0)).toBe(false)
    expect(hasMoreKeys(null)).toBe(false)
    expect(hasMoreKeys(undefined)).toBe(false)
    expect(hasMoreKeys(120)).toBe(true)
  })

  it('re-reads the accumulated window after a write, or a full first page when empty', () => {
    expect(refreshCountOf(0)).toBe(DEFAULT_KEY_COUNT)
    expect(refreshCountOf(250)).toBe(250)
  })
})

describe('请求形状与白名单', () => {
  it('reads the backoffice INFO endpoint, not a raw redis passthrough', () => {
    expect(cacheInfoRequest()).toEqual({ method: 'get', url: '/admin/monitor/cache' })
  })

  it('browses keys with prefix, cursor and count on the keys endpoint', () => {
    expect(cacheKeysRequest({ prefix: 'perm:' })).toEqual({
      method: 'get',
      url: '/admin/monitor/cache/keys',
      params: { prefix: 'perm:', cursor: 0, count: DEFAULT_KEY_COUNT },
    })
    expect(cacheKeysRequest({ prefix: 'login:session:', cursor: 200, count: 50 })).toEqual({
      method: 'get',
      url: '/admin/monitor/cache/keys',
      params: { prefix: 'login:session:', cursor: 200, count: 50 },
    })
  })

  it('sends the single-key delete and the permission-cache clear as DELETE', () => {
    expect(deleteCacheKeyRequest('perm:cache:admin')).toEqual({
      method: 'delete',
      url: '/admin/monitor/cache/keys',
      params: { key: 'perm:cache:admin' },
    })
    expect(clearPermissionCacheRequest()).toEqual({
      method: 'delete',
      url: '/admin/monitor/cache/perm',
    })
  })

  it('offers exactly the six whitelist prefixes (ADR 0060/0062)', () => {
    expect([...CACHE_PREFIX_OPTIONS]).toEqual([
      'perm:',
      'login:session:',
      'login:attempt:',
      'token:revoked:',
      'config:',
      'dict:',
    ])
  })

  it('gates both clean actions behind monitor:cache:clean', () => {
    expect(MONITOR_CACHE_PERMISSIONS.clean).toBe('monitor:cache:clean')
  })
})
