import { computed, type ComputedRef } from 'vue'
import {
  createListRead,
  type ListReadCommand,
  type ListReadQuery,
  type ListReadView,
} from '@/list/list-read'
import type { RequestScope } from '@/request/request-scope'
import type { ApiRequestConfig } from '@/types/domain'

export interface ListPageEffects {
  notify(message: string, kind: 'success' | 'error'): void
  confirm(message: string): Promise<boolean>
}

export interface ListPageOptions<
  TRecord,
  TItem,
  TQuery extends ListReadQuery = ListReadQuery,
> extends ListPageEffects {
  openScope: () => RequestScope
  toRequest: (command: ListReadCommand<TQuery>) => ApiRequestConfig
  present?: (record: TRecord) => TItem
  /** 整表读（树列表）：见 createListRead 的同名选项（#247）。 */
  fetchAll?: boolean
  size?: number
  query?: TQuery
}

export interface ListPage<TRecord, TItem = TRecord, TQuery extends ListReadQuery = ListReadQuery> {
  view: Readonly<ListReadView<TRecord>>
  rows: ComputedRef<TItem[]>
  emptyText: ComputedRef<string>
  ready: Promise<void>
  search(query?: TQuery): void
  retry(): void
  refresh(): void
  handlePageChange(page: number): void
  handleSizeChange(size: number): void
  rowIndex(index: number): number
  runWrite(operation: () => Promise<{ message?: string }>, successText?: string): Promise<boolean>
  confirmRemove(
    message: string,
    operation: () => Promise<{ message?: string }>,
    successText?: string,
  ): Promise<boolean>
  settle<T>(operation: Promise<T>): Promise<T | undefined>
  release(): void
}

/** One list page owns its read command, pagination, refresh-after-write and failure policy. */
export function createListPage<
  TRecord,
  TItem = TRecord,
  TQuery extends ListReadQuery = ListReadQuery,
>(options: ListPageOptions<TRecord, TItem, TQuery>): ListPage<TRecord, TItem, TQuery> {
  const opening = createListRead<TRecord, TQuery>({
    openScope: options.openScope,
    toRequest: options.toRequest,
    fetchAll: options.fetchAll,
    size: options.size,
    query: options.query,
  })
  const rows = computed<TItem[]>(() =>
    options.present
      ? opening.view.records.map(options.present)
      : (opening.view.records as unknown as TItem[]),
  )
  const emptyText = computed(() => (opening.view.status === 'ready' ? '暂无数据' : ''))
  function search(query?: TQuery): void {
    void opening.read({ page: 1, query })
  }
  function retry(): void {
    void opening.read()
  }
  async function runWrite(
    operation: () => Promise<{ message?: string }>,
    successText = '操作成功',
  ): Promise<boolean> {
    let result: { message?: string }
    try {
      result = await operation()
    } catch {
      // The request layer already surfaced the failure; do not refresh on it.
      return false
    }
    options.notify(result?.message || successText, 'success')
    void opening.read()
    return true
  }
  async function confirmRemove(
    message: string,
    operation: () => Promise<{ message?: string }>,
    successText = '删除成功',
  ): Promise<boolean> {
    if (!(await options.confirm(message))) return false
    return runWrite(operation, successText)
  }
  async function settle<T>(operation: Promise<T>): Promise<T | undefined> {
    try {
      return await operation
    } catch {
      return undefined
    }
  }
  return {
    view: opening.view,
    rows,
    emptyText,
    ready: opening.ready,
    search,
    retry,
    refresh: retry,
    handlePageChange(page) {
      void opening.read({ page })
    },
    handleSizeChange(size) {
      void opening.read({ size })
    },
    rowIndex(index) {
      return (opening.view.page - 1) * opening.view.size + index + 1
    },
    runWrite,
    confirmRemove,
    settle,
    release: opening.release,
  }
}
