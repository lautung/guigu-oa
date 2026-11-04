import { describe, expect, it, vi } from 'vitest'
import { createListPage } from '@/list/list-page'

function deferred() {
  let resolve, reject
  const promise = new Promise((yes, no) => {
    resolve = yes
    reject = no
  })
  return { promise, resolve, reject }
}

const pageResult = (records = [], total = records.length, pages = 1) => ({
  code: 200,
  data: { records, total, pages },
})

function harness(options = {}) {
  const reads = []
  const notices = []
  const confirmations = []
  let confirmResult = true
  const scope = {
    current: () => true,
    request: (config) => {
      const pending = deferred()
      reads.push({ config, ...pending })
      return pending.promise
    },
  }
  const list = createListPage({
    openScope: () => scope,
    toRequest: ({ page, size, query }) => ({
      method: 'get',
      url: `/things/${page}/${size}`,
      params: query,
    }),
    present: (record) => ({ ...record, label: `#${record.id}` }),
    notify: (message, kind) => notices.push([message, kind]),
    confirm: async (message) => {
      confirmations.push(message)
      return confirmResult
    },
    ...options,
  })
  return {
    list,
    reads,
    notices,
    confirmations,
    confirm: (value) => {
      confirmResult = value
    },
  }
}

async function ready(h, records = [{ id: 1 }, { id: 2 }]) {
  h.reads[0].resolve(pageResult(records, 12, 2))
  await h.list.ready
}

describe('列表页 module', () => {
  it('自动读取一次，映射 rows 并提供行号与空态文案', async () => {
    const h = harness()
    expect(h.reads).toHaveLength(1)
    expect(h.reads[0].config).toEqual({ method: 'get', url: '/things/1/10', params: {} })
    await ready(h)
    expect(h.list.view).toMatchObject({ status: 'ready', page: 1, size: 10, total: 12 })
    expect(h.list.rows.value).toEqual([
      { id: 1, label: '#1' },
      { id: 2, label: '#2' },
    ])
    expect(h.list.rowIndex(0)).toBe(1)
    expect(h.list.emptyText.value).toBe('暂无数据')
  })

  it('搜索回到第一页并带上查询，切页大小也回到第一页', async () => {
    const h = harness()
    await ready(h)
    h.list.search({ keyword: 'alice' })
    expect(h.reads[1].config).toMatchObject({ url: '/things/1/10', params: { keyword: 'alice' } })
    h.reads[1].resolve(pageResult())
    h.list.handlePageChange(3)
    expect(h.reads[2].config.url).toBe('/things/3/10')
    h.reads[2].resolve(pageResult())
    h.list.handleSizeChange(20)
    expect(h.reads[3].config.url).toBe('/things/1/20')
    h.reads[3].resolve(pageResult())
    expect(h.list.view).toMatchObject({ page: 1, size: 20 })
  })

  it('runWrite 成功后提示并刷新当前页；失败时不提示也不刷新', async () => {
    const h = harness()
    await ready(h)
    expect(await h.list.runWrite(async () => ({ message: '已保存' }))).toBe(true)
    expect(h.notices).toEqual([['已保存', 'success']])
    expect(h.reads).toHaveLength(2)
    expect(h.reads[1].config.url).toBe('/things/1/10')

    expect(
      await h.list.runWrite(async () => {
        throw new Error('boom')
      }),
    ).toBe(false)
    expect(h.notices).toHaveLength(1)
    expect(h.reads).toHaveLength(2)
  })

  it('confirmRemove 取消不执行写入，确认后写入并刷新', async () => {
    const h = harness()
    await ready(h)
    const operation = vi.fn(async () => ({ message: '删除成功' }))
    h.confirm(false)
    expect(await h.list.confirmRemove('确认删除？', operation)).toBe(false)
    expect(operation).not.toHaveBeenCalled()
    expect(h.reads).toHaveLength(1)

    h.confirm(true)
    expect(await h.list.confirmRemove('确认删除？', operation)).toBe(true)
    expect(operation).toHaveBeenCalledTimes(1)
    expect(h.confirmations).toEqual(['确认删除？', '确认删除？'])
    expect(h.notices).toEqual([['删除成功', 'success']])
    expect(h.reads).toHaveLength(2)
  })

  it('整表读（fetchAll）接受数组响应，写后仍刷新同一读', async () => {
    const h = harness({ fetchAll: true })
    h.reads[0].resolve({ code: 200, data: [{ id: 1 }, { id: 2 }] })
    await h.list.ready
    expect(h.list.view).toMatchObject({ status: 'ready', total: 2, pages: 1 })
    expect(h.list.rows.value).toEqual([
      { id: 1, label: '#1' },
      { id: 2, label: '#2' },
    ])

    expect(await h.list.runWrite(async () => ({ message: '已保存' }))).toBe(true)
    expect(h.reads).toHaveLength(2)
    expect(h.notices).toEqual([['已保存', 'success']])
  })

  it('settle 在一次性读取失败时返回 undefined', async () => {
    const h = harness()
    await ready(h)
    expect(await h.list.settle(Promise.resolve({ code: 200, data: { id: 1 } }))).toEqual({
      code: 200,
      data: { id: 1 },
    })
    expect(await h.list.settle(Promise.reject(new Error('读取失败')))).toBeUndefined()
  })

  it('释放后不再发起读取', async () => {
    const h = harness()
    await ready(h)
    h.list.release()
    h.list.search({ keyword: 'after' })
    h.list.handlePageChange(2)
    h.list.retry()
    expect(h.reads).toHaveLength(1)
  })
})
