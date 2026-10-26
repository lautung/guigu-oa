import { describe, expect, it, vi } from 'vitest'
import { createAssignRoles } from '@/config/assign-roles'

function deferred() {
  let resolve, reject
  const promise = new Promise((yes, no) => {
    resolve = yes
    reject = no
  })
  return { promise, resolve, reject }
}

const roles = [
  { id: 1, roleName: '管理员' },
  { id: 2, roleName: '审批人' },
]
const alice = { id: 11, username: 'alice' }
const bob = { id: 12, username: 'bob' }

function harness(overrides = {}) {
  const values = new Map()
  let entry = 'entry-alice'
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
    read: vi.fn(async (userId) => ({
      roles,
      assignedRoleIds: String(userId) === String(bob.id) ? [2] : [1],
    })),
    assign: vi.fn(async () => ({ kind: 'saved' })),
    refreshOperator: vi.fn(async () => {}),
    confirm: vi.fn(async () => true),
    notify: (message, kind) => effects.push(['notify', message, kind]),
    ...overrides,
  }
  return {
    adapter,
    storage,
    values,
    effects,
    module: createAssignRoles(adapter),
    setEntry: (value) => {
      entry = value
    },
    setGeneration: (value) => {
      generation = value
    },
  }
}

async function open(h, target = alice) {
  const lease = h.module.open(target)
  await lease.ready
  return lease
}

describe('分配角色 interface', () => {
  it('reads roles, freezes the submitted set, blocks duplicate saves and refreshes the operator', async () => {
    const flight = deferred()
    const h = harness({ assign: vi.fn(() => flight.promise) })
    const page = await open(h)
    expect(page.view.value).toMatchObject({
      userId: 11,
      username: 'alice',
      phase: 'ready',
      roles,
      selectedRoleIds: [1],
      canSave: true,
    })

    expect(page.select([2])).toBe(true)
    const saving = page.save()
    expect(page.view.value.phase).toBe('saving')
    expect(page.view.value.canSelect).toBe(false)
    page.select([1])
    await page.save()
    expect(page.view.value.selectedRoleIds).toEqual([2])
    expect(h.adapter.assign).toHaveBeenCalledTimes(1)
    expect(h.adapter.assign).toHaveBeenCalledWith(11, [2])

    flight.resolve({ kind: 'saved' })
    await saving
    expect(page.view.value.phase).toBe('saved')
    expect(h.adapter.refreshOperator).toHaveBeenCalledTimes(1)
    expect(h.effects).toContainEqual(['notify', '分配角色成功', 'success'])
    expect(h.values.size).toBe(0)
  })

  it('blocks saving after a failed read until a retry, and accepts a legal empty role list', async () => {
    const h = harness({
      read: vi
        .fn()
        .mockRejectedValueOnce(new Error('读失败'))
        .mockResolvedValue({ roles: [], assignedRoleIds: [] }),
    })
    const page = h.module.open(alice)
    await page.ready
    expect(page.view.value.phase).toBe('read-error')
    expect(page.view.value.canSave).toBe(false)
    await page.save()
    expect(h.adapter.assign).not.toHaveBeenCalled()

    await page.reload()
    expect(page.view.value.phase).toBe('ready')
    expect(page.view.value.canSave).toBe(true)
    await page.save()
    expect(h.adapter.assign).toHaveBeenCalledWith(11, [])
  })

  it('drops a late read for A after switching to B', async () => {
    const readA = deferred()
    const h = harness({
      read: vi.fn((userId) => (String(userId) === String(alice.id) ? readA.promise : readB())),
    })
    const a = h.module.open(alice)
    await a.requestLeave(true)
    const b = await open(h, bob)
    expect(b.view.value).toMatchObject({
      userId: 12,
      selectedRoleIds: [2],
      roles,
    })

    readA.resolve({ roles: [{ id: 9, roleName: '旧角色' }], assignedRoleIds: [9] })
    await vi.waitFor(() => expect(h.adapter.read).toHaveBeenCalledTimes(2))
    expect(b.view.value.roles).toEqual(roles)
    expect(b.view.value.selectedRoleIds).toEqual([2])

    function readB() {
      return Promise.resolve({ roles, assignedRoleIds: [2] })
    }
  })

  it('confirms an unsaved close, discards the selection and rereads on the next opening', async () => {
    const h = harness()
    const page = await open(h)
    page.select([2])
    const leaving = page.requestLeave()
    expect(h.adapter.confirm).toHaveBeenCalledWith(expect.stringContaining('未保存'), 'leave')
    await leaving
    expect(h.adapter.confirm).toHaveBeenCalledTimes(1)

    const again = await open(h)
    expect(h.adapter.read).toHaveBeenCalledTimes(2)
    expect(again.view.value.selectedRoleIds).toEqual([1])
  })

  it('keeps an unconfirmed record across close and refresh without inferring success from a reread', async () => {
    const h = harness({
      assign: vi.fn(async () => ({ kind: 'unconfirmed' })),
      read: vi
        .fn()
        .mockResolvedValueOnce({ roles, assignedRoleIds: [1] })
        .mockResolvedValueOnce({
          roles: [...roles, { id: 3, roleName: '新角色' }],
          assignedRoleIds: [1, 2],
        }),
    })
    const page = await open(h)
    page.select([2])
    await page.save()
    expect(page.view.value).toMatchObject({
      phase: 'unconfirmed',
      submittedRoleIds: [2],
      submittedRoles: [{ id: 2, roleName: '审批人' }],
      canSave: false,
      canEnd: true,
    })
    expect(h.module.pendingUserIds.value).toEqual(['11'])
    expect(JSON.parse([...h.values.values()][0])).toMatchObject({
      phase: 'unconfirmed',
      submittedRoleIds: [2],
    })

    await page.reload()
    expect(page.view.value.latest).toEqual({
      roles: [...roles, { id: 3, roleName: '新角色' }],
      roleIds: [1, 2],
    })
    expect(page.view.value.phase).toBe('unconfirmed')
    expect(page.view.value.submittedRoleIds).toEqual([2])
    expect(page.view.value.canSave).toBe(false)

    await page.requestLeave()
    const restored = createAssignRoles(h.adapter)
    expect(restored.pendingUserIds.value).toEqual(['11'])
    const reopened = restored.open(alice)
    await reopened.ready
    expect(reopened.view.value).toMatchObject({
      phase: 'unconfirmed',
      submittedRoleIds: [2],
      submittedRoles: [{ id: 2, roleName: '审批人' }],
    })
    expect(h.adapter.read).toHaveBeenCalledTimes(2)
    await reopened.save()
    expect(h.adapter.assign).toHaveBeenCalledTimes(1)
  })

  it('restores an in-flight record as unconfirmed after refresh and never resends it', async () => {
    const flight = deferred()
    const h = harness({ assign: vi.fn(() => flight.promise) })
    const page = await open(h)
    page.select([2])
    const pending = page.save()
    expect(JSON.parse([...h.values.values()][0]).phase).toBe('sending')
    page.release()

    const restored = createAssignRoles(h.adapter)
    const reopened = restored.open(alice)
    await reopened.ready
    expect(reopened.view.value.phase).toBe('unconfirmed')
    expect(reopened.view.value.submittedRoleIds).toEqual([2])
    await reopened.save()
    expect(h.adapter.assign).toHaveBeenCalledTimes(1)

    flight.resolve({ kind: 'saved' })
    await pending
    expect(h.effects).not.toContainEqual(['notify', '分配角色成功', 'success'])
  })

  it('ends an unconfirmed assignment only after confirmation and clears only the local record', async () => {
    const h = harness({
      assign: vi.fn(async () => ({ kind: 'unconfirmed' })),
    })
    const page = await open(h)
    await page.save()
    h.adapter.confirm.mockResolvedValueOnce(false)
    expect(await page.endAssignment()).toBe(false)
    expect(page.view.value.phase).toBe('unconfirmed')
    expect(h.values.size).toBe(1)

    h.adapter.confirm.mockResolvedValueOnce(true)
    expect(await page.endAssignment()).toBe(true)
    expect(page.view.value.phase).toBe('closed')
    expect(h.values.size).toBe(0)
    expect(h.module.pendingUserIds.value).toEqual([])
    expect(h.adapter.assign).toHaveBeenCalledTimes(1)

    const again = await open(h)
    expect(again.view.value.phase).toBe('ready')
    expect(again.view.value.selectedRoleIds).toEqual([1])
    expect(h.adapter.read).toHaveBeenCalledTimes(2)
  })

  it('does not end while the save is still awaiting a response, but allows a confirmed close', async () => {
    const flight = deferred()
    const h = harness({ assign: vi.fn(() => flight.promise) })
    const page = await open(h)
    const saving = page.save()
    expect(await page.endAssignment()).toBe(false)
    expect(page.view.value.canEnd).toBe(false)

    const leaving = page.requestLeave()
    expect(h.adapter.confirm).toHaveBeenCalledWith(expect.stringContaining('离开不撤销'), 'leave')
    await leaving
    expect(page.view.value.phase).toBe('saving')

    flight.resolve({ kind: 'saved' })
    await saving
    expect(h.adapter.refreshOperator).toHaveBeenCalledTimes(1)
    expect(h.effects).not.toContainEqual(['notify', '分配角色成功', 'success'])
  })

  it('returns a definitively rejected save to an editable selection and retries it', async () => {
    const h = harness({
      assign: vi
        .fn()
        .mockResolvedValueOnce({ kind: 'rejected', message: '认证失败' })
        .mockResolvedValue({ kind: 'saved' }),
    })
    const page = await open(h)
    page.select([2])
    await page.save()
    expect(page.view.value).toMatchObject({
      phase: 'ready',
      selectedRoleIds: [2],
      error: '认证失败',
    })
    expect(h.values.size).toBe(0)
    expect(h.effects).toContainEqual(['notify', '认证失败', 'error'])
    expect(h.adapter.refreshOperator).not.toHaveBeenCalled()

    await page.save()
    expect(h.adapter.assign).toHaveBeenCalledTimes(2)
    expect(h.adapter.assign.mock.calls[1][1]).toEqual([2])
  })

  it('does not send when the submission record cannot be retained', async () => {
    const h = harness()
    const page = await open(h)
    h.storage.setItem = () => {
      throw new Error('quota')
    }
    page.select([2])
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
    h.setEntry('entry-bob')
    h.setGeneration(2)
    flight.resolve({ kind: 'saved' })
    await saving
    expect(page.view.value.phase).toBe('saved')
    expect(h.adapter.refreshOperator).not.toHaveBeenCalled()
    expect(h.effects).toEqual([])
    expect(h.values.size).toBe(0)
  })

  it('keeps the saved conclusion when the operator refresh fails', async () => {
    const h = harness({
      refreshOperator: vi.fn(async () => {
        throw new Error('刷新失败')
      }),
    })
    const page = await open(h)
    await page.save()
    expect(page.view.value.phase).toBe('saved')
    expect(h.adapter.assign).toHaveBeenCalledTimes(1)
    expect(h.effects).toContainEqual(['notify', '分配角色成功', 'success'])
    expect(h.effects).toContainEqual([
      'notify',
      '角色已保存，但资料与菜单刷新失败，请稍后重新进入',
      'warning',
    ])
  })

  it('refreshes the operator after a late success without closing or toasting over a new dialog', async () => {
    const flight = deferred()
    const h = harness({ assign: vi.fn(() => flight.promise) })
    const page = await open(h)
    const saving = page.save()
    await page.requestLeave()
    const next = await open(h, bob)
    expect(next.view.value.userId).toBe(12)

    flight.resolve({ kind: 'saved' })
    await saving
    expect(h.adapter.refreshOperator).toHaveBeenCalledTimes(1)
    expect(next.view.value).toMatchObject({ userId: 12, phase: 'ready' })
    expect(h.effects).not.toContainEqual(['notify', '分配角色成功', 'success'])
  })

  it('keeps pending assignments scoped to their user while another user assigns normally', async () => {
    const h = harness({
      assign: vi.fn(async (userId) => ({ kind: String(userId) === '11' ? 'unconfirmed' : 'saved' })),
    })
    const first = await open(h)
    await first.save()
    await first.requestLeave()
    expect(h.module.pendingUserIds.value).toEqual(['11'])

    const second = await open(h, bob)
    expect(second.view.value.phase).toBe('ready')
    await second.save()
    expect(h.adapter.assign).toHaveBeenCalledTimes(2)
    expect(h.module.pendingUserIds.value).toEqual(['11'])
  })
})
