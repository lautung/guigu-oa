import { expect, it } from 'vitest'
import { isReadonly } from 'vue'
import { createListRead } from '@/list-read'

function deferred() {
  let resolve, reject
  const promise = new Promise((yes, no) => {
    resolve = yes
    reject = no
  })
  return { promise, resolve, reject }
}

const pageResult = (records, total = records.length, pages = 1) => ({
  code: 200,
  data: { records, total, pages },
})

const ids = (view) => view.records.map((record) => record.id)

function harness(options = {}) {
  const reads = []
  let scopeOpens = 0
  const scope = {
    current: () => true,
    request: (config, isRelevant) => {
      const pending = deferred()
      reads.push({ config, isRelevant, scope, ...pending })
      return pending.promise
    },
  }
  const opening = createListRead({
    openScope: () => {
      scopeOpens += 1
      return scope
    },
    toRequest:
      options.toRequest ??
      (({ page, size, query }) => ({
        method: 'get',
        url: `/admin/process/findPending/${page}/${size}`,
        params: query,
      })),
    size: options.size,
    query: options.query,
  })
  return { opening, reads, scopeOpens: () => scopeOpens, scope }
}

it('创建后立即自动读取一次，初始 view 是加载中的空页，ready 后提交结果', async () => {
  const h = harness()
  expect(h.reads).toHaveLength(1)
  expect(isReadonly(h.opening.view)).toBe(true)
  expect(h.opening.view).toMatchObject({
    status: 'loading',
    records: [],
    total: 0,
    pages: 0,
    page: 1,
    size: 10,
    error: '',
  })
  expect(h.opening.view).not.toHaveProperty('query')
  expect(h.reads[0].config).toEqual({
    method: 'get',
    url: '/admin/process/findPending/1/10',
    params: {},
  })
  expect(h.reads[0].isRelevant()).toBe(true)
  const records = [{ id: 'A', title: '审批 A' }]
  h.reads[0].resolve({ code: 200, data: { records, total: 1, pages: 1 } })
  await h.opening.ready
  expect(h.opening.view).toMatchObject({
    status: 'ready',
    total: 1,
    pages: 1,
    page: 1,
    size: 10,
    error: '',
  })
  expect(h.opening.view.records).toHaveLength(1)
  expect(h.opening.view.records[0]).toEqual(records[0])
})

it('切换列表种类时旧 opening 的迟到读取不污染新 opening', async () => {
  const old = harness()
  const fresh = harness()
  old.opening.release()
  fresh.reads[0].resolve(pageResult([{ id: 'B' }], 1, 1))
  await fresh.opening.ready
  old.reads[0].resolve(pageResult([{ id: 'A' }], 1, 1))
  await old.opening.ready
  expect(ids(fresh.opening.view)).toEqual(['B'])
  expect(fresh.opening.view.status).toBe('ready')
  expect(ids(old.opening.view)).toEqual([])
  expect(old.opening.view.status).toBe('loading')
})

it('快速翻页时只让最新一次读取提交，迟到的 page 2 不覆盖 page 3', async () => {
  const h = harness()
  h.reads[0].resolve(pageResult([{ id: 'P1' }], 30, 3))
  await h.opening.ready
  const second = h.opening.read({ page: 2 })
  const third = h.opening.read({ page: 3 })
  expect(h.reads).toHaveLength(3)
  expect(h.reads[1].config.url).toBe('/admin/process/findPending/2/10')
  expect(h.reads[2].config.url).toBe('/admin/process/findPending/3/10')
  h.reads[2].resolve(pageResult([{ id: 'P3' }], 30, 3))
  await third
  expect(ids(h.opening.view)).toEqual(['P3'])
  expect(h.opening.view.page).toBe(3)
  expect(h.opening.view.status).toBe('ready')
  h.reads[1].resolve(pageResult([{ id: 'P2' }], 30, 3))
  await second
  expect(ids(h.opening.view)).toEqual(['P3'])
  expect(h.opening.view.page).toBe(3)
  expect(h.opening.view.status).toBe('ready')
})

it('快速搜索时迟到的 query A 不覆盖先到的 query B，并记录每次命令', async () => {
  const h = harness()
  h.reads[0].resolve(pageResult([]))
  await h.opening.ready
  const a = h.opening.read({ query: { keyword: 'A', status: 1 } })
  const b = h.opening.read({ query: { keyword: 'B' } })
  expect(h.reads[1].config.url).toBe('/admin/process/findPending/1/10')
  expect(h.reads[1].config.params).toEqual({ keyword: 'A', status: 1 })
  expect(h.reads[2].config.url).toBe('/admin/process/findPending/1/10')
  expect(h.reads[2].config.params).toEqual({ keyword: 'B' })
  h.reads[2].resolve(pageResult([{ id: 'B' }], 1, 1))
  await b
  h.reads[1].resolve(pageResult([{ id: 'A' }], 1, 1))
  await a
  expect(ids(h.opening.view)).toEqual(['B'])
  expect(h.opening.view.status).toBe('ready')
})

it('搜索条件被整体替换，重置为空查询后重放仍是空查询', async () => {
  const h = harness()
  h.reads[0].resolve(pageResult([]))
  await h.opening.ready
  const searched = h.opening.read({ query: { keyword: 'A' } })
  h.reads[1].resolve(pageResult([{ id: 'A' }], 1, 1))
  await searched
  const cleared = h.opening.read({ query: {} })
  expect(h.reads[2].config.params).toEqual({})
  h.reads[2].resolve(pageResult([], 0, 0))
  await cleared
  const replay = h.opening.read()
  expect(h.reads[3].config.params).toEqual({})
  h.reads[3].resolve(pageResult([], 0, 0))
  await replay
})

it('release() 中断在途读取，释放后 resolve 不改变释放前的 view', async () => {
  const h = harness()
  h.reads[0].resolve(pageResult([{ id: 'A' }], 7, 2))
  await h.opening.ready
  const pending = h.opening.read({ page: 2 })
  expect(h.opening.view).toMatchObject({ status: 'loading', page: 2, total: 7, pages: 2 })
  const before = {
    status: h.opening.view.status,
    records: h.opening.view.records.map((record) => ({ ...record })),
    total: h.opening.view.total,
    pages: h.opening.view.pages,
    page: h.opening.view.page,
    size: h.opening.view.size,
    error: h.opening.view.error,
  }
  h.opening.release()
  expect(h.opening.view).toMatchObject(before)
  h.reads[1].resolve(pageResult([{ id: 'B' }], 1, 1))
  await pending
  expect(h.opening.view).toMatchObject(before)
  expect(ids(h.opening.view)).toEqual(['A'])
  await h.opening.read({ page: 9 })
  expect(h.reads).toHaveLength(2)
})

it.each(['invalid', 'disabled', 'superseded'])(
  '识别层终态 %s 不进入失败态也不写 error，恢复上次成功状态',
  async (kind) => {
    const h = harness()
    h.reads[0].resolve(pageResult([{ id: 'A' }], 1, 1))
    await h.opening.ready
    const pending = h.opening.read()
    h.reads[1].reject(Object.assign(new Error('识别层已处理'), { kind }))
    await pending
    expect(h.opening.view.status).toBe('ready')
    expect(h.opening.view.error).toBe('')
    expect(ids(h.opening.view)).toEqual(['A'])
  },
)

it('翻页时被 superseded，view.page 回到上次成功页且随后 read() 仍重放被取代的目标页', async () => {
  const h = harness()
  h.reads[0].resolve(pageResult([{ id: 'A' }], 30, 3))
  await h.opening.ready
  const pending = h.opening.read({ page: 2 })
  expect(h.opening.view.page).toBe(2)
  h.reads[1].reject(Object.assign(new Error('识别层已处理'), { kind: 'superseded' }))
  await pending
  expect(h.opening.view.status).toBe('ready')
  expect(h.opening.view.error).toBe('')
  expect(h.opening.view.page).toBe(1)
  expect(h.opening.view.size).toBe(10)
  expect(ids(h.opening.view)).toEqual(['A'])
  const retry = h.opening.read()
  expect(h.reads[2].config.url).toBe('/admin/process/findPending/2/10')
  h.reads[2].resolve(pageResult([{ id: 'P2' }], 30, 3))
  await retry
  expect(h.opening.view.page).toBe(2)
  expect(ids(h.opening.view)).toEqual(['P2'])
})

it('失败后 read() 重放完全相同的已应用命令，成功后恢复 ready', async () => {
  const h = harness()
  h.reads[0].resolve(pageResult([]))
  await h.opening.ready
  const draft = { keyword: 'A', status: 1 }
  const first = h.opening.read({ page: 4, size: 20, query: draft })
  draft.keyword = '污染后的草稿'
  expect(h.reads[1].config.url).toBe('/admin/process/findPending/4/20')
  expect(h.reads[1].config.params).toEqual({ keyword: 'A', status: 1 })
  h.reads[1].reject(new Error('网络故障'))
  await first
  const retry = h.opening.read()
  expect(h.reads[2].config.url).toBe('/admin/process/findPending/4/20')
  expect(h.reads[2].config.params).toEqual(h.reads[1].config.params)
  h.reads[2].resolve(pageResult([{ id: 'A' }], 1, 1))
  await retry
  expect(h.opening.view.status).toBe('ready')
  expect(ids(h.opening.view)).toEqual(['A'])
  expect(h.opening.view.page).toBe(4)
  expect(h.opening.view.size).toBe(20)
})

it('页大小变化且没有显式页码时回到第 1 页，显式页码优先', async () => {
  const h = harness()
  h.reads[0].resolve(pageResult([], 50, 5))
  await h.opening.ready
  const third = h.opening.read({ page: 3 })
  h.reads[1].resolve(pageResult([{ id: 'P3' }], 50, 5))
  await third
  expect(h.opening.view.page).toBe(3)
  const resized = h.opening.read({ size: 20 })
  expect(h.reads[2].config.url).toBe('/admin/process/findPending/1/20')
  expect(h.opening.view).toMatchObject({ page: 1, size: 20 })
  h.reads[2].resolve(pageResult([], 50, 3))
  await resized
  const explicit = h.opening.read({ page: 2, size: 5 })
  expect(h.reads[3].config.url).toBe('/admin/process/findPending/2/5')
  h.reads[3].resolve(pageResult([], 50, 10))
  await explicit
  expect(h.opening.view).toMatchObject({ page: 2, size: 5 })
  const sameSize = h.opening.read({ size: 5 })
  expect(h.reads[4].config.url).toBe('/admin/process/findPending/2/5')
  h.reads[4].resolve(pageResult([], 50, 10))
  await sameSize
  expect(h.opening.view.page).toBe(2)
})

it.each([
  ['空记录', { records: [], total: 0, pages: 0 }],
  ['缺失 data', undefined],
])('成功但%s时是 ready 空页，与失败态可区分', async (_, data) => {
  const h = harness()
  h.reads[0].resolve({ code: 200, data })
  await h.opening.ready
  expect(h.opening.view.status).toBe('ready')
  expect(h.opening.view.records).toEqual([])
  expect(h.opening.view.total).toBe(0)
  expect(h.opening.view.pages).toBe(0)
  expect(h.opening.view.error).toBe('')
})

it('失败保留上次成功结果，view 的 page/size 回到成功结果，重试重放失败的已应用命令', async () => {
  const h = harness()
  h.reads[0].resolve(pageResult([{ id: 'A' }], 1, 1))
  await h.opening.ready
  const retry = h.opening.read({ page: 2 })
  expect(h.opening.view.page).toBe(2)
  h.reads[1].reject(new Error('网络故障'))
  await retry
  expect(h.opening.view.status).toBe('failed')
  expect(h.opening.view.error).toBe('网络故障')
  expect(ids(h.opening.view)).toEqual(['A'])
  expect(h.opening.view.total).toBe(1)
  expect(h.opening.view.pages).toBe(1)
  expect(h.opening.view.page).toBe(1)
  expect(h.opening.view.size).toBe(10)
  const replay = h.opening.read()
  expect(h.reads[2].config.url).toBe('/admin/process/findPending/2/10')
  h.reads[2].resolve(pageResult([{ id: 'P2' }], 31, 4))
  await replay
  expect(h.opening.view.status).toBe('ready')
  expect(h.opening.view.page).toBe(2)
  expect(ids(h.opening.view)).toEqual(['P2'])
})

it('首次读取失败且尚无成功结果时保留目标 page/size 并进入失败态', async () => {
  const h = harness({ size: 20 })
  h.reads[0].reject(new Error('网络故障'))
  await h.opening.ready
  expect(h.opening.view.status).toBe('failed')
  expect(h.opening.view.error).toBe('网络故障')
  expect(h.opening.view.page).toBe(1)
  expect(h.opening.view.size).toBe(20)
  expect(h.opening.view.records).toEqual([])
  expect(h.opening.view.total).toBe(0)
  expect(h.opening.view.pages).toBe(0)
})

it('原始记录原样交给页面，不做 presenter 化或克隆', async () => {
  const h = harness()
  const first = { id: 'A', title: '审批 A', formValues: { formShowData: { 原因: '出差' } } }
  const second = { id: 'B', title: '审批 B' }
  h.reads[0].resolve(pageResult([first, second], 2, 1))
  await h.opening.ready
  expect(h.opening.view.records).toHaveLength(2)
  expect(h.opening.view.records[0]).toEqual(first)
  expect(h.opening.view.records[1]).toEqual(second)
  expect(Object.keys(h.opening.view.records[0]).sort()).toEqual(['formValues', 'id', 'title'])
})

it('创建时打开一次作用域，前后读取的相关性仍由本次打开回答', async () => {
  const h = harness()
  expect(h.scopeOpens()).toBe(1)
  expect(h.reads[0].isRelevant()).toBe(true)
  const next = h.opening.read({ page: 2 })
  expect(h.scopeOpens()).toBe(1)
  expect(h.reads[0].scope).toBe(h.reads[1].scope)
  expect(h.reads[0].isRelevant()).toBe(false)
  expect(h.reads[1].isRelevant()).toBe(true)
  h.opening.release()
  expect(h.reads[1].isRelevant()).toBe(false)
  h.reads[1].resolve(pageResult([], 0, 0))
  await next
})
