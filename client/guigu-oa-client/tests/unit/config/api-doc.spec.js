import { afterEach, describe, expect, it, vi } from 'vitest'

import { API_DOCS_PATH, API_DOC_PROBE_TIMEOUT_MS, probeApiDoc } from '@/config/api-doc'

afterEach(() => {
  vi.useRealTimers()
})

describe('接口文档探测', () => {
  it('reads /v3/api-docs and treats a 200 answer as available', async () => {
    const fetcher = vi.fn().mockResolvedValue({ ok: true })

    await expect(probeApiDoc(fetcher)).resolves.toBe('available')
    expect(fetcher).toHaveBeenCalledWith(
      API_DOCS_PATH,
      expect.objectContaining({ signal: expect.anything() }),
    )
  })

  it('treats a non-2xx answer as unavailable', async () => {
    await expect(probeApiDoc(vi.fn().mockResolvedValue({ ok: false }))).resolves.toBe('unavailable')
  })

  it('treats network errors as unavailable instead of a third failure state', async () => {
    const fetcher = vi.fn().mockRejectedValue(new TypeError('Failed to fetch'))

    await expect(probeApiDoc(fetcher)).resolves.toBe('unavailable')
  })

  it('aborts a hanging probe and folds it into unavailable after the timeout', async () => {
    vi.useFakeTimers()
    let signal
    const fetcher = vi.fn((_input, init) => {
      signal = init.signal
      return new Promise(() => {})
    })

    const pending = probeApiDoc(fetcher)
    await vi.advanceTimersByTimeAsync(API_DOC_PROBE_TIMEOUT_MS)
    await expect(pending).resolves.toBe('unavailable')
    expect(signal.aborted).toBe(true)
  })
})
