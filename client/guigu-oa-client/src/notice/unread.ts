import { reactive } from 'vue'
import {
  NOTICE_UNREAD_LIMIT,
  presentFeedItem,
  unreadCountRequest,
  unreadRequest,
} from '@/notice/config'
import type {
  ApiRequestConfig,
  ApiResponse,
  NoticeFeedItem,
  NoticeFeedRecord,
} from '@/types/domain'

/**
 * 顶栏铃铛的未读数据源与轮询（ADR 0055）。
 *
 * 拉取时机固定为：登录后立即一次、每 60 秒一次、`window` 重新聚焦补拉。不做
 * SSE/WebSocket。轮询失败一律静默（作用域已关普通错误提示）；`invalid`/`disabled`/
 * `superseded` 是识别层已处理的终态，直接停表，避免在登录页继续打接口。
 */

export const UNREAD_POLL_INTERVAL_MS = 60_000

export interface NoticeUnreadSource {
  count(): Promise<number>
  recent(): Promise<NoticeFeedItem[]>
}

export type NoticeScopeRequest = (config: ApiRequestConfig) => Promise<ApiResponse<unknown>>

export function createNoticeUnreadSource(send: NoticeScopeRequest): NoticeUnreadSource {
  return {
    async count() {
      const response = await send(unreadCountRequest())
      return typeof response?.data === 'number' ? response.data : 0
    },
    async recent() {
      const response = await send(unreadRequest())
      const rows = Array.isArray(response?.data) ? response.data : []
      return rows
        .map((row) => presentFeedItem(row as NoticeFeedRecord))
        .slice(0, NOTICE_UNREAD_LIMIT)
    },
  }
}

export interface UnreadPollingClock {
  setInterval(handler: () => void, ms: number): number
  clearInterval(id: number): void
  addFocusListener(handler: () => void): void
  removeFocusListener(handler: () => void): void
}

export function browserUnreadClock(): UnreadPollingClock {
  return {
    setInterval: (handler, ms) => window.setInterval(handler, ms),
    clearInterval: (id) => window.clearInterval(id),
    addFocusListener: (handler) => window.addEventListener('focus', handler),
    removeFocusListener: (handler) => window.removeEventListener('focus', handler),
  }
}

export interface UnreadPollingState {
  count: number
  recent: NoticeFeedItem[]
}

export interface UnreadPolling {
  state: UnreadPollingState
  start(): void
  stop(): void
  isRunning(): boolean
  refresh(): Promise<void>
}

export interface UnreadPollingOptions {
  source: NoticeUnreadSource
  clock?: UnreadPollingClock
  intervalMs?: number
}

const TERMINAL_FAILURES = new Set(['invalid', 'disabled', 'superseded'])

function failureKind(error: unknown): string | undefined {
  if (!error || typeof error !== 'object' || !('kind' in error)) return undefined
  const kind = (error as { kind?: unknown }).kind
  return typeof kind === 'string' ? kind : undefined
}

export function createUnreadPolling(options: UnreadPollingOptions): UnreadPolling {
  const clock = options.clock ?? browserUnreadClock()
  const intervalMs = options.intervalMs ?? UNREAD_POLL_INTERVAL_MS
  const state = reactive<UnreadPollingState>({ count: 0, recent: [] })
  let running = false
  let timer: number | undefined
  let sequence = 0
  const onFocus = () => {
    void refresh()
  }

  async function refresh(): Promise<void> {
    if (!running) return
    const number = ++sequence
    const [countResult, recentResult] = await Promise.allSettled([
      options.source.count(),
      options.source.recent(),
    ])
    if (!running || number !== sequence) return
    const failures: unknown[] = []
    if (countResult.status === 'fulfilled') state.count = Math.max(0, countResult.value)
    else failures.push(countResult.reason)
    if (recentResult.status === 'fulfilled') {
      state.recent = recentResult.value.slice(0, NOTICE_UNREAD_LIMIT)
    } else failures.push(recentResult.reason)
    if (failures.some((error) => TERMINAL_FAILURES.has(failureKind(error) ?? ''))) stop()
  }

  function start(): void {
    if (running) return
    running = true
    clock.addFocusListener(onFocus)
    timer = clock.setInterval(() => {
      void refresh()
    }, intervalMs)
    void refresh()
  }

  function stop(): void {
    if (!running) return
    running = false
    sequence += 1
    if (timer !== undefined) clock.clearInterval(timer)
    timer = undefined
    clock.removeFocusListener(onFocus)
    state.count = 0
    state.recent = []
  }

  return {
    state,
    start,
    stop,
    isRunning: () => running,
    refresh,
  }
}
