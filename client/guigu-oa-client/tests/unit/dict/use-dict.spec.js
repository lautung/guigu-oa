import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { flushPromises } from '@vue/test-utils'

import { clearDictCache, loadDict, useDict } from '@/dict/use-dict'
import { installHttp, ok, setupSession } from '../support/harness'

const DICTS = {
  alpha: [
    { dictLabel: '甲', dictValue: '1', sortValue: 1 },
    { dictLabel: '乙', dictValue: '2', sortValue: 2 },
  ],
  beta: [{ dictLabel: '成功', dictValue: '0', sortValue: 1 }],
}

let http

beforeEach(() => {
  clearDictCache()
})

/** adapter 必须先装再 configureRequest：request.ts 在 configureRequest 时创建 axios 实例。 */
function sessionWith(responder) {
  http = installHttp(responder)
  setupSession()
}

afterEach(() => {
  http?.restore()
  http = undefined
  clearDictCache()
})

describe('useDict 批量取数与缓存', () => {
  it('coalesces the same microtask into one batch request', async () => {
    sessionWith((config) =>
      config.url === '/admin/system/dict/data/batch' ? ok(DICTS) : ok(null),
    )

    const [alpha, beta] = await Promise.all([loadDict('alpha'), loadDict('beta')])

    expect(alpha).toEqual(DICTS.alpha)
    expect(beta).toEqual(DICTS.beta)
    const batches = http.calls.filter((call) => call.url === '/admin/system/dict/data/batch')
    expect(batches).toHaveLength(1)
    expect(batches[0].params).toEqual({ types: 'alpha,beta' })
  })

  it('dedupes concurrent requests for the same type and caches the result', async () => {
    sessionWith(() => ok(DICTS))

    const [first, second] = await Promise.all([loadDict('alpha'), loadDict('alpha')])
    expect(first).toEqual(DICTS.alpha)
    expect(second).toEqual(DICTS.alpha)
    expect(http.calls).toHaveLength(1)

    await loadDict('alpha')
    expect(http.calls).toHaveLength(1)
  })

  it('refetches after clearDictCache (management write/refresh)', async () => {
    sessionWith(() => ok(DICTS))
    await loadDict('alpha')
    expect(http.calls).toHaveLength(1)

    clearDictCache()
    await loadDict('alpha')
    expect(http.calls).toHaveLength(2)
  })

  it('exposes a reactive page handle with label lookup', async () => {
    sessionWith(() => ok(DICTS))
    const handle = useDict('alpha', 'beta')
    await flushPromises()

    expect(handle.loaded.value).toBe(true)
    expect(handle.loading.value).toBe(false)
    expect(handle.optionsOf('alpha')).toEqual(DICTS.alpha)
    expect(handle.labelOf('alpha', 2)).toBe('乙')
    expect(handle.labelOf('beta', '0')).toBe('成功')
    expect(handle.labelOf('alpha', 9)).toBe('')
    expect(handle.dicts.beta).toEqual(DICTS.beta)
  })

  it('keeps an empty list for a missing type and records failures without throwing', async () => {
    sessionWith(() => ok({ alpha: DICTS.alpha }))
    const handle = useDict('missing')
    await flushPromises()

    expect(handle.dicts.missing).toEqual([])
    expect(handle.labelOf('missing', '1')).toBe('')
    expect(handle.error.value).toBe('')
  })
})
