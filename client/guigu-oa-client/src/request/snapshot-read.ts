import { shallowRef, type ShallowRef } from 'vue'

import { failureMessage } from '@/request/request-failure'
import type { RequestScope } from '@/request/request-scope'

export type SnapshotReadStatus = 'loading' | 'ready' | 'failed'

/** 装载器拿到页面作用域和「这次读取仍然当前」的判定；迟到的旧响应不该再改页面。 */
export type SnapshotLoader<T> = (scope: RequestScope, isCurrent: () => boolean) => Promise<T | null>

export interface SnapshotReadOpening<T> {
  status: ShallowRef<SnapshotReadStatus>
  error: ShallowRef<string>
  value: ShallowRef<T | null>
  loaded: ShallowRef<boolean>
  load: (loader: SnapshotLoader<T>) => Promise<void>
  /** 清空当前值与「已装载」标记；调用方随后自行 load()（列表页前缀切换用）。 */
  reset: (next?: T | null) => void
  release: () => void
}

/**
 * 一次页面打开的非分页读（工作台与五个监控页同款）：请求作用域、sequence、状态行与
 * 「已拿到过一次响应」标记都在这一处拥有。装载器只负责取数与投影，迟到的旧响应和
 * 身份变化后的无关响应由 `isCurrent()` 统一丢弃。
 */
export function createSnapshotRead<T>(scope: RequestScope): SnapshotReadOpening<T> {
  const status = shallowRef<SnapshotReadStatus>('loading')
  const error = shallowRef('')
  const value = shallowRef<T | null>(null)
  const loaded = shallowRef(false)
  let released = false
  let sequence = 0

  async function load(loader: SnapshotLoader<T>): Promise<void> {
    if (released) return
    const number = ++sequence
    const isCurrent = () => !released && number === sequence && scope.current()
    status.value = 'loading'
    error.value = ''
    try {
      const next = await loader(scope, isCurrent)
      if (!isCurrent()) return
      value.value = next
      loaded.value = true
      status.value = 'ready'
    } catch (failure) {
      if (!isCurrent()) return
      status.value = 'failed'
      error.value = failureMessage(failure)
    }
  }

  return {
    status,
    error,
    value,
    loaded,
    load,
    reset: (next = null) => {
      value.value = next
      loaded.value = false
    },
    release: () => {
      released = true
      sequence += 1
    },
  }
}
