import { reactive, readonly } from 'vue'
import type { RequestScope } from '@/request-scope'
import type { ApiRequestConfig, PageResult } from '@/types/domain'

export type ListReadStatus = 'loading' | 'ready' | 'failed'
export interface ListReadQuery {
  [key: string]: string | number | undefined
}
export type NoListQuery = Record<string, never>
export interface ListReadCommand<TQuery extends ListReadQuery = ListReadQuery> {
  page: number
  size: number
  query: TQuery
}
export interface ListReadView<TRecord> {
  status: ListReadStatus
  records: TRecord[]
  total: number
  pages: number
  page: number
  size: number
  error: string
}
export interface ListReadOptions<TQuery extends ListReadQuery = ListReadQuery> {
  openScope: () => RequestScope
  toRequest: (command: ListReadCommand<TQuery>) => ApiRequestConfig
  size?: number
  query?: TQuery
}
export interface ListReadOpening<TRecord, TQuery extends ListReadQuery = ListReadQuery> {
  view: Readonly<ListReadView<TRecord>>
  read: (partial?: Partial<ListReadCommand<TQuery>>) => Promise<void>
  release: () => void
  ready: Promise<void>
}

const TERMINAL_FAILURES = new Set(['invalid', 'disabled', 'superseded'])

function failureKind(error: unknown): string | undefined {
  if (!error || typeof error !== 'object' || !('kind' in error)) return undefined
  const kind = (error as { kind?: unknown }).kind
  return typeof kind === 'string' ? kind : undefined
}

function failureMessage(error: unknown): string {
  if (error && typeof error === 'object' && 'message' in error) {
    const message = (error as { message?: unknown }).message
    if (typeof message === 'string' && message) return message
  }
  return '加载失败'
}

function nextCommand<TQuery extends ListReadQuery>(
  command: ListReadCommand<TQuery>,
  partial: Partial<ListReadCommand<TQuery>>,
): ListReadCommand<TQuery> {
  const sizeChanged = partial.size != null && partial.size !== command.size
  return {
    page: partial.page ?? (sizeChanged ? 1 : command.page),
    size: partial.size ?? command.size,
    query: partial.query === undefined ? command.query : ({ ...partial.query } as TQuery),
  }
}

/** One list opening owns the applied read command and the latest-read rule. */
export function createListRead<TRecord, TQuery extends ListReadQuery = ListReadQuery>(
  options: ListReadOptions<TQuery>,
): ListReadOpening<TRecord, TQuery> {
  const scope = options.openScope()
  // The public contract exposes mutable array shapes; Vue's deep readonly proxy is enforced at runtime.
  const view = reactive<ListReadView<TRecord>>({
    status: 'loading',
    records: [],
    total: 0,
    pages: 0,
    page: 1,
    size: options.size ?? 10,
    error: '',
  }) as unknown as ListReadView<TRecord>
  let command: ListReadCommand<TQuery> = {
    page: 1,
    size: options.size ?? 10,
    query: { ...(options.query ?? {}) } as TQuery,
  }
  // Page and size of the last successful read; the view falls back to these when a read fails.
  let settled: { page: number; size: number } | null = null
  let released = false
  let sequence = 0

  async function read(partial: Partial<ListReadCommand<TQuery>> = {}): Promise<void> {
    if (released) return
    command = nextCommand(command, partial)
    const number = ++sequence
    const previous = { status: view.status, error: view.error }
    view.status = 'loading'
    view.error = ''
    view.page = command.page
    view.size = command.size
    try {
      const response = await scope.request<PageResult<TRecord>>(
        options.toRequest(command),
        () => !released && number === sequence,
      )
      if (released || number !== sequence) return
      const data = response?.data || {}
      view.records = data.records || []
      view.total = data.total || 0
      view.pages = data.pages || 0
      settled = { page: command.page, size: command.size }
      view.status = 'ready'
    } catch (error: unknown) {
      if (released || number !== sequence) return
      if (settled) {
        view.page = settled.page
        view.size = settled.size
      }
      if (TERMINAL_FAILURES.has(failureKind(error) ?? '')) {
        view.status = previous.status
        view.error = previous.error
        return
      }
      view.error = failureMessage(error)
      view.status = 'failed'
    }
  }

  return {
    view: readonly(view) as unknown as Readonly<ListReadView<TRecord>>,
    read,
    release: () => {
      released = true
    },
    ready: read(),
  }
}
