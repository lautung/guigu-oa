import { reactive, readonly } from 'vue'
import type { Recognition } from '@/recognition'
import type { ApiClient, ApiRequestConfig, PageResult } from '@/types/domain'

export type ListReadStatus = 'loading' | 'ready' | 'failed'
export interface ListReadQuery {
  [key: string]: string | number | undefined
}
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
  recognition: Pick<Recognition, 'capture'>
  request: ApiClient
  toRequest: (command: ListReadCommand<TQuery>) => ApiRequestConfig
  page?: number
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

/** One list opening owns the applied read command and the latest-read rule. */
export function createListRead<TRecord, TQuery extends ListReadQuery = ListReadQuery>(
  options: ListReadOptions<TQuery>,
): ListReadOpening<TRecord, TQuery> {
  const context = options.recognition.capture()
  // The public contract exposes mutable array shapes; Vue's deep readonly proxy is enforced at runtime.
  const view = reactive<ListReadView<TRecord>>({
    status: 'loading',
    records: [],
    total: 0,
    pages: 0,
    page: options.page ?? 1,
    size: options.size ?? 10,
    error: '',
  }) as unknown as ListReadView<TRecord>
  let command: ListReadCommand<TQuery> = {
    page: options.page ?? 1,
    size: options.size ?? 10,
    query: { ...(options.query ?? {}) } as TQuery,
  }
  let released = false
  let sequence = 0

  async function read(partial: Partial<ListReadCommand<TQuery>> = {}): Promise<void> {
    if (released) return
    const page = partial.page ?? command.page
    const size = partial.size ?? command.size
    command = {
      page:
        partial.page == null && partial.size != null && partial.size !== command.size ? 1 : page,
      size,
      query: partial.query === undefined ? command.query : ({ ...partial.query } as TQuery),
    }
    const number = ++sequence
    view.status = 'loading'
    view.error = ''
    view.page = command.page
    view.size = command.size
    try {
      const response = await options.request<PageResult<TRecord>>({
        ...options.toRequest(command),
        _recognition: context,
        _isRelevant: () => !released && number === sequence,
        normalErrorNotification: 'caller',
      })
      if (released || number !== sequence) return
      const data = response?.data || {}
      view.records = data.records || []
      view.total = data.total || 0
      view.pages = data.pages || 0
      view.status = 'ready'
    } catch (error: unknown) {
      if (released || number !== sequence) return
      if (TERMINAL_FAILURES.has(failureKind(error) ?? '')) return
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
