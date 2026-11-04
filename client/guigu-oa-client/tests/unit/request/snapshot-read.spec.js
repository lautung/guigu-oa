import { expect, it } from 'vitest'

import { createSnapshotRead } from '@/request/snapshot-read'

function deferred() {
  let resolve, reject
  const promise = new Promise((yes, no) => {
    resolve = yes
    reject = no
  })
  return { promise, resolve, reject }
}

function harness() {
  const scope = { current: () => true, request: () => Promise.reject(new Error('unused')) }
  const read = createSnapshotRead(scope)
  return { read, scope }
}

it('初始状态是加载中的空快照，成功后才置 ready 与 loaded', async () => {
  const { read, scope } = harness()
  expect(read.status.value).toBe('loading')
  expect(read.error.value).toBe('')
  expect(read.value.value).toBeNull()
  expect(read.loaded.value).toBe(false)

  await read.load(async (received) => {
    expect(received).toBe(scope)
    return { hello: 'world' }
  })
  expect(read.status.value).toBe('ready')
  expect(read.error.value).toBe('')
  expect(read.value.value).toEqual({ hello: 'world' })
  expect(read.loaded.value).toBe(true)
})

it('成功但快照为空也是 ready，与失败态可区分', async () => {
  const { read } = harness()
  await read.load(async () => null)

  expect(read.status.value).toBe('ready')
  expect(read.value.value).toBeNull()
  expect(read.loaded.value).toBe(true)
})

it('失败进入 failed 并保留上次成功值与 loaded', async () => {
  const { read } = harness()
  await read.load(async () => ({ kept: true }))
  await read.load(async () => {
    throw new Error('网络故障')
  })

  expect(read.status.value).toBe('failed')
  expect(read.error.value).toBe('网络故障')
  expect(read.value.value).toEqual({ kept: true })
  expect(read.loaded.value).toBe(true)
})

it('没有 message 的失败只进失败态，error 交给调用方兜底', async () => {
  const { read } = harness()
  await read.load(async () => {
    throw 'boom'
  })

  expect(read.status.value).toBe('failed')
  expect(read.error.value).toBe('')
})

it('快速刷新时只让最新一次读取落屏，迟到的旧响应被丢弃', async () => {
  const { read } = harness()
  const first = deferred()
  const second = deferred()

  const slow = read.load(() => first.promise)
  const fresh = read.load(() => second.promise)
  second.resolve({ id: 'new' })
  await fresh
  first.resolve({ id: 'old' })
  await slow

  expect(read.value.value).toEqual({ id: 'new' })
  expect(read.status.value).toBe('ready')
})

it('装载器在 await 后能用 isCurrent 判断自己是否已被取代', async () => {
  const { read } = harness()
  const first = deferred()
  let currentAfterAwait

  const slow = read.load(async (_scope, isCurrent) => {
    const value = await first.promise
    currentAfterAwait = isCurrent()
    return value
  })
  const fresh = read.load(async () => ({ id: 'new' }))
  first.resolve({ id: 'old' })
  await Promise.all([slow, fresh])

  expect(currentAfterAwait).toBe(false)
  expect(read.value.value).toEqual({ id: 'new' })
})

it('身份变化后 scope.current() 为假时响应不落屏', async () => {
  const { read, scope } = harness()
  scope.current = () => false

  await read.load(async () => ({ stale: true }))

  expect(read.value.value).toBeNull()
  expect(read.loaded.value).toBe(false)
  expect(read.status.value).toBe('loading')
})

it('reset 清空值与 loaded，随后 load 仍正常提交', async () => {
  const { read } = harness()
  await read.load(async () => ({ old: true }))

  read.reset([])
  expect(read.value.value).toEqual([])
  expect(read.loaded.value).toBe(false)

  await read.load(async () => [{ fresh: true }])
  expect(read.value.value).toEqual([{ fresh: true }])
  expect(read.loaded.value).toBe(true)
  expect(read.status.value).toBe('ready')
})

it('release 丢弃在途读取，之后不再发起新的装载', async () => {
  const { read } = harness()
  await read.load(async () => ({ kept: true }))
  const pending = deferred()
  const inFlight = read.load(() => pending.promise)
  read.release()
  pending.resolve({ late: true })
  await inFlight

  expect(read.value.value).toEqual({ kept: true })
  // release 只丢弃结果，不负责把在途读取的 loading 改回去（调用方正在卸载）。
  expect(read.status.value).toBe('loading')

  let called = false
  await read.load(async () => {
    called = true
    return { never: true }
  })
  expect(called).toBe(false)
  expect(read.value.value).toEqual({ kept: true })
})
