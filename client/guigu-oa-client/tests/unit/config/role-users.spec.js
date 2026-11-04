import { describe, expect, it, vi } from 'vitest'
import { createRoleUsers } from '@/config/role-users'
import { getDefaultPostLoginRoute } from '@/navigation/constants'

function deferred() {
  let resolve, reject
  const promise = new Promise((yes, no) => {
    resolve = yes
    reject = no
  })
  return { promise, resolve, reject }
}

const alice = { id: 11, username: 'alice', name: '爱丽丝' }
const bob = { id: 12, username: 'bob', name: '鲍勃' }
const carol = { id: 13, username: 'carol', name: '卡罗尔' }
const dave = { id: 14, username: 'dave', name: '戴夫' }
const role = { id: 7, roleName: '审批人' }

function harness(overrides = {}) {
  const values = new Map()
  let entry = 'entry-admin'
  let generation = 1
  const storage = {
    getItem: (key) => values.get(key) ?? null,
    setItem: (key, value) => {
      values.set(key, value)
    },
    removeItem: (key) => {
      values.delete(key)
    },
    keys: () => [...values.keys()],
  }
  const effects = []
  const adapter = {
    entryId: () => entry,
    capture: () => ({ generation, entryId: entry }),
    storage,
    readMembers: vi.fn(async () => [alice, bob]),
    readCandidates: vi.fn(async () => ({ users: [carol, dave], total: 2 })),
    assign: vi.fn(async () => ({ kind: 'saved' })),
    refreshOperator: vi.fn(async () => {}),
    confirm: vi.fn(async () => true),
    dismissConfirm: vi.fn(),
    notify: (message, kind) => effects.push(['notify', message, kind]),
    replace: vi.fn(),
    ...overrides,
  }
  return {
    adapter,
    storage,
    values,
    effects,
    module: createRoleUsers(adapter),
    setEntry: (value) => {
      entry = value
    },
    setGeneration: (value) => {
      generation = value
    },
  }
}

async function open(h, target = role) {
  const lease = h.module.open(target)
  await lease.ready
  return lease
}

describe('角色分配用户 interface', () => {
  it('loads both lists, moves users between them and submits the whole selected side', async () => {
    const h = harness()
    const page = await open(h)
    expect(page.view.value).toMatchObject({
      roleId: 7,
      roleName: '审批人',
      phase: 'ready',
      members: [alice, bob],
      candidates: [carol, dave],
      total: 2,
      canMove: true,
      canSave: true,
    })

    expect(page.add([13])).toBe(true)
    expect(page.view.value.members).toEqual([alice, bob, carol])
    expect(page.view.value.candidates).toEqual([dave])
    expect(page.view.value.total).toBe(1)

    // 同一 id 不允许重复进入已选侧。
    expect(page.add([13])).toBe(false)
    expect(page.view.value.members).toHaveLength(3)

    expect(page.remove([11])).toBe(true)
    expect(page.view.value.members).toEqual([bob, carol])
    expect(page.view.value.candidates).toEqual([alice, dave])
    expect(page.view.value.total).toBe(2)

    await page.save()
    expect(h.adapter.assign).toHaveBeenCalledTimes(1)
    expect(h.adapter.assign).toHaveBeenCalledWith(7, [12, 13])
    expect(h.effects).toContainEqual(['notify', '分配用户成功', 'success'])
    expect(h.values.size).toBe(0)
  })

  it('searches candidates with the keyword and drops a late search result', async () => {
    const slow = deferred()
    const h = harness({
      readCandidates: vi.fn(async (_roleId, query) =>
        query.keyword === 'old' ? slow.promise : { users: [dave], total: 1 },
      ),
    })
    const page = await open(h)

    const first = page.search('old')
    await page.search('dave')
    expect(h.adapter.readCandidates).toHaveBeenLastCalledWith(7, {
      keyword: 'dave',
      page: 1,
      size: 10,
    })
    expect(page.view.value.candidates).toEqual([dave])
    expect(page.view.value.keyword).toBe('dave')

    slow.resolve({ users: [carol], total: 1 })
    await first
    expect(page.view.value.candidates).toEqual([dave])
  })

  it('keeps the selection when paging or searching, and pages candidates on the server', async () => {
    const h = harness()
    const page = await open(h)
    page.add([13])

    await page.goPage(2)
    expect(h.adapter.readCandidates).toHaveBeenLastCalledWith(7, {
      keyword: '',
      page: 2,
      size: 10,
    })
    expect(page.view.value.page).toBe(2)
    // 已选侧不随候选页变化，本地加入的用户也不会重新出现在候选里。
    expect(page.view.value.members).toEqual([alice, bob, carol])
    expect(page.view.value.candidates).toEqual([dave])
  })

  it('blocks saving after a failed read and keeps a legal empty member set', async () => {
    const h = harness({
      readMembers: vi.fn().mockRejectedValueOnce(new Error('读失败')).mockResolvedValueOnce([]),
      readCandidates: vi.fn(async () => ({ users: [carol, dave], total: 2 })),
    })
    const page = h.module.open(role)
    await page.ready
    expect(page.view.value.phase).toBe('read-error')
    expect(page.view.value.canSave).toBe(false)
    await page.save()
    expect(h.adapter.assign).not.toHaveBeenCalled()

    await page.reload()
    expect(page.view.value.phase).toBe('ready')
    expect(page.view.value.canSave).toBe(true)
    await page.save()
    expect(h.adapter.assign).toHaveBeenCalledWith(7, [])
  })

  it('keeps an unconfirmed record across close and refresh without inferring success from a reread', async () => {
    const h = harness({
      assign: vi.fn(async () => ({ kind: 'unconfirmed' })),
      readMembers: vi.fn().mockResolvedValueOnce([alice, bob]).mockResolvedValueOnce([bob, carol]),
    })
    const page = await open(h)
    page.add([13])
    await page.save()
    expect(page.view.value).toMatchObject({
      phase: 'unconfirmed',
      submittedUserIds: [11, 12, 13],
      submittedUsers: [alice, bob, carol],
      canSave: false,
      canEnd: true,
    })
    expect(h.module.pendingRoleIds.value).toEqual(['7'])
    expect(JSON.parse([...h.values.values()][0])).toMatchObject({
      phase: 'unconfirmed',
      submittedUserIds: [11, 12, 13],
    })

    await page.reload()
    expect(page.view.value.latest).toEqual({ members: [bob, carol] })
    expect(page.view.value.phase).toBe('unconfirmed')
    expect(page.view.value.submittedUserIds).toEqual([11, 12, 13])
    expect(page.view.value.canSave).toBe(false)

    await page.requestLeave()
    const restored = createRoleUsers(h.adapter)
    expect(restored.pendingRoleIds.value).toEqual(['7'])
    const reopened = restored.open(role)
    await reopened.ready
    expect(reopened.view.value).toMatchObject({
      phase: 'unconfirmed',
      submittedUserIds: [11, 12, 13],
      submittedUsers: [alice, bob, carol],
    })
    await reopened.save()
    expect(h.adapter.assign).toHaveBeenCalledTimes(1)
  })

  it('restores an in-flight record as unconfirmed after refresh and never resends it', async () => {
    const flight = deferred()
    const h = harness({ assign: vi.fn(() => flight.promise) })
    const page = await open(h)
    page.add([13])
    const pending = page.save()
    expect(JSON.parse([...h.values.values()][0]).phase).toBe('sending')
    page.dispose()

    const restored = createRoleUsers(h.adapter)
    const reopened = restored.open(role)
    await reopened.ready
    expect(reopened.view.value.phase).toBe('unconfirmed')
    expect(reopened.view.value.submittedUserIds).toEqual([11, 12, 13])
    await reopened.save()
    expect(h.adapter.assign).toHaveBeenCalledTimes(1)

    flight.resolve({ kind: 'saved' })
    await pending
    expect(h.effects).not.toContainEqual(['notify', '分配用户成功', 'success'])
  })

  it('ends an unconfirmed assignment only after confirmation and clears only the local record', async () => {
    const h = harness({ assign: vi.fn(async () => ({ kind: 'unconfirmed' })) })
    const page = await open(h)
    page.add([13])
    await page.save()
    h.adapter.confirm.mockResolvedValueOnce(false)
    expect(await page.endAssignment()).toBe(false)
    expect(page.view.value.phase).toBe('unconfirmed')
    expect(h.values.size).toBe(1)

    h.adapter.confirm.mockResolvedValueOnce(true)
    expect(await page.endAssignment()).toBe(true)
    expect(page.view.value.phase).toBe('closed')
    expect(h.values.size).toBe(0)
    expect(h.module.pendingRoleIds.value).toEqual([])

    const again = await open(h)
    expect(again.view.value.phase).toBe('ready')
    expect(again.view.value.members).toEqual([alice, bob])
  })

  it('refreshes the members and the operator after a saved write', async () => {
    const h = harness({
      readMembers: vi.fn().mockResolvedValueOnce([alice, bob]).mockResolvedValueOnce([bob, carol]),
      readCandidates: vi
        .fn()
        .mockResolvedValueOnce({ users: [carol, dave], total: 2 })
        .mockResolvedValueOnce({ users: [alice, dave], total: 2 }),
    })
    const page = await open(h)
    page.add([13])
    await page.save()

    expect(page.view.value.phase).toBe('saved')
    expect(h.adapter.readMembers).toHaveBeenCalledTimes(2)
    expect(h.adapter.readCandidates).toHaveBeenCalledTimes(2)
    expect(h.adapter.refreshOperator).toHaveBeenCalledTimes(1)
  })

  it('keeps the saved conclusion when the post-write refresh fails', async () => {
    const h = harness({
      readMembers: vi
        .fn()
        .mockResolvedValueOnce([alice, bob])
        .mockRejectedValueOnce(new Error('刷新失败')),
      refreshOperator: vi.fn(async () => {
        throw new Error('资料刷新失败')
      }),
    })
    const page = await open(h)
    page.add([13])
    await page.save()

    expect(page.view.value.phase).toBe('saved')
    expect(h.adapter.assign).toHaveBeenCalledTimes(1)
    expect(h.effects).toContainEqual(['notify', '分配用户成功', 'success'])
    expect(h.effects).toContainEqual([
      'notify',
      '成员已保存，但列表刷新失败，请稍后重新进入',
      'warning',
    ])
    expect(h.effects).toContainEqual([
      'notify',
      '角色成员已保存，但资料与菜单刷新失败，请稍后重新进入',
      'warning',
    ])
  })

  it('returns a definitively rejected save to the editable selection and retries it', async () => {
    const h = harness({
      assign: vi
        .fn()
        .mockResolvedValueOnce({ kind: 'rejected', message: '只能分配已存在的用户' })
        .mockResolvedValue({ kind: 'saved' }),
    })
    const page = await open(h)
    page.add([13])
    await page.save()
    expect(page.view.value).toMatchObject({
      phase: 'ready',
      members: [alice, bob, carol],
      error: '只能分配已存在的用户',
    })
    expect(h.values.size).toBe(0)
    expect(h.effects).toContainEqual(['notify', '只能分配已存在的用户', 'error'])

    await page.save()
    expect(h.adapter.assign).toHaveBeenCalledTimes(2)
    expect(h.adapter.assign.mock.calls[1][1]).toEqual([11, 12, 13])
  })

  it('does not send when the submission record cannot be retained', async () => {
    const h = harness()
    const page = await open(h)
    h.storage.setItem = () => {
      throw new Error('quota')
    }
    page.add([13])
    await page.save()
    expect(h.adapter.assign).not.toHaveBeenCalled()
    expect(page.view.value.phase).toBe('ready')
    expect(page.view.value.error).toContain('无法可靠保留')
  })

  it('drops a late result after the entry identity changes', async () => {
    const flight = deferred()
    const h = harness({ assign: vi.fn(() => flight.promise) })
    const page = await open(h)
    const saving = page.save()
    h.setEntry('entry-other')
    h.setGeneration(2)
    flight.resolve({ kind: 'saved' })
    await saving
    expect(page.view.value.phase).toBe('saved')
    expect(h.adapter.refreshOperator).not.toHaveBeenCalled()
    expect(h.effects).toEqual([])
    expect(h.values.size).toBe(0)
  })

  it('confirms an unsaved close and discards the local moves', async () => {
    const h = harness()
    const page = await open(h)
    page.add([13])
    const leaving = page.requestLeave()
    expect(h.adapter.confirm).toHaveBeenCalledWith(expect.stringContaining('未保存'), 'leave')
    await leaving
    expect(h.adapter.confirm).toHaveBeenCalledTimes(1)

    const again = await open(h)
    expect(again.view.value.members).toEqual([alice, bob])
    expect(h.adapter.readMembers).toHaveBeenCalledTimes(2)
  })

  it('releases an unchanged opening without confirmation', async () => {
    const h = harness()
    const page = await open(h)
    expect(await page.requestLeave()).toBe(true)
    expect(page.released.value).toBe(true)
    expect(h.adapter.confirm).not.toHaveBeenCalled()
    expect(h.adapter.replace).not.toHaveBeenCalled()
  })

  it('keeps pending roles scoped to their own role while another role assigns normally', async () => {
    const other = { id: 8, roleName: '观察人' }
    const h = harness({
      assign: vi.fn(async (roleId) => ({ kind: String(roleId) === '7' ? 'unconfirmed' : 'saved' })),
    })
    const first = await open(h)
    await first.save()
    await first.requestLeave()
    expect(h.module.pendingRoleIds.value).toEqual(['7'])

    const second = await open(h, other)
    await second.save()
    expect(h.adapter.assign).toHaveBeenCalledTimes(2)
    expect(h.module.pendingRoleIds.value).toEqual(['7'])
  })

  it('lets an identity replacement preempt a pending confirmation without navigating twice', async () => {
    const confirming = deferred()
    const h = harness({ confirm: vi.fn(() => confirming.promise) })
    const page = await open(h)
    page.add([13])
    const leaving = page.requestLeave()
    await vi.waitFor(() => expect(h.adapter.confirm).toHaveBeenCalledTimes(1))

    h.setEntry('entry-other')
    await page.environmentChanged()
    expect(h.adapter.dismissConfirm).toHaveBeenCalledTimes(1)
    expect(h.adapter.replace).toHaveBeenCalledWith(getDefaultPostLoginRoute())
    expect(page.released.value).toBe(true)

    confirming.resolve(false)
    expect(await leaving).toBe(true)
    expect(h.adapter.replace).toHaveBeenCalledTimes(1)
  })
})
