import { reactive, ref, type Ref } from 'vue'

import { batchDataRequest } from '@/config/dict'
import request from '@/request/request'
import type { ApiResponse, DictOption } from '@/types/domain'

/**
 * 字典标签的批量取数与缓存（#251，ADR 0062）。
 *
 * 同一微任务里请求的多个类型合并成一次 `GET /admin/system/dict/data/batch`：
 * 页面同时挂多个 `DictTag` 不会打出 N 个请求。结果按类型模块级缓存，直到
 * 管理页写后调用 `clearDictCache()` 或页面重载；服务端侧还有 `dict:<dictType>` Redis 缓存。
 */

type Waiter = {
  resolve: (options: DictOption[]) => void
  reject: (error: unknown) => void
}

const cache = new Map<string, DictOption[]>()
let waiting = new Map<string, Waiter[]>()
let scheduled = false

function requestBatch(
  types: readonly string[],
): Promise<ApiResponse<Record<string, DictOption[]>>> {
  return request<Record<string, DictOption[]>>(batchDataRequest(types))
}

async function flush(): Promise<void> {
  scheduled = false
  const batch = waiting
  waiting = new Map()
  const types = [...batch.keys()]
  if (!types.length) return
  try {
    const response = await requestBatch(types)
    const data = response.data || {}
    for (const type of types) {
      const options = data[type] || []
      cache.set(type, options)
      for (const waiter of batch.get(type) || []) waiter.resolve(options)
    }
  } catch (error) {
    for (const waiters of batch.values()) {
      for (const waiter of waiters) waiter.reject(error)
    }
  }
}

/** 取一组字典：命中模块缓存直接回答；否则加入下一微任务的批量请求（同类型去重）。 */
export function loadDict(dictType: string): Promise<DictOption[]> {
  if (!dictType) return Promise.resolve([])
  const cached = cache.get(dictType)
  if (cached) return Promise.resolve(cached)
  return new Promise<DictOption[]>((resolve, reject) => {
    const waiters = waiting.get(dictType) || []
    waiters.push({ resolve, reject })
    waiting.set(dictType, waiters)
    if (!scheduled) {
      scheduled = true
      void Promise.resolve().then(flush)
    }
  })
}

/** 管理页写后/手动刷新后清空客户端缓存：下次挂载重新批量取数。 */
export function clearDictCache(): void {
  cache.clear()
}

export interface DictHandle {
  /** 类型 → 选项；`DictTag` 读它，加载完成后自动重渲染。 */
  dicts: Record<string, DictOption[]>
  loading: Ref<boolean>
  loaded: Ref<boolean>
  error: Ref<string>
  load: () => Promise<void>
  optionsOf: (dictType: string) => DictOption[]
  labelOf: (dictType: string, value: string | number | null | undefined) => string
}

/**
 * 页面级字典句柄：一次声明多组类型。`load()` 由句柄自动触发一次；
 * 返回的 `dicts` 是响应式的，晚到的批量结果会驱动模板更新。
 */
export function useDict(...types: string[]): DictHandle {
  const requested = [...new Set(types.filter(Boolean))]
  const dicts = reactive<Record<string, DictOption[]>>({})
  const loading = ref(requested.length > 0)
  const loaded = ref(requested.length === 0)
  const error = ref('')

  function optionsOf(dictType: string): DictOption[] {
    return dicts[dictType] || []
  }

  function labelOf(dictType: string, value: string | number | null | undefined): string {
    const option = optionsOf(dictType).find((item) => String(item.dictValue) === String(value))
    return option?.dictLabel || ''
  }

  async function load(): Promise<void> {
    if (!requested.length) return
    loading.value = true
    error.value = ''
    try {
      const results = await Promise.all(requested.map((type) => loadDict(type)))
      requested.forEach((type, index) => {
        dicts[type] = results[index]
      })
      loaded.value = true
    } catch (failure) {
      error.value = failure instanceof Error ? failure.message : ''
    } finally {
      loading.value = false
    }
  }

  void load()

  return { dicts, loading, loaded, error, load, optionsOf, labelOf }
}
