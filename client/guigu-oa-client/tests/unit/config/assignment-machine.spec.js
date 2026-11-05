import { describe, expect, it, vi } from 'vitest'
import { createAssignmentMachine } from '@/config/assignment-machine'
import { getDefaultPostLoginRoute } from '@/navigation/constants'

function deferred() {
  let resolve, reject
  const promise = new Promise((yes, no) => {
    resolve = yes
    reject = no
  })
  return { promise, resolve, reject }
}

const target = { id: 7, label: '目标甲' }
const other = { id: 8, label: '目标乙' }

function recordKey(entry, id) {
  return `oa-fake:v1:${encodeURIComponent(entry)}:${encodeURIComponent(String(id))}`
}

/**
 * 共享机合同测试用最小合成域驱动：不 import 任何域类型。
 * 假域是一个「池选择器」——读取给出 pool（候选）与 origin（已选），
 * 提交内容就是 picked 本身。
 */
function harness(overrides = {}, sharedValues = new Map()) {
  const values = sharedValues
  let entry = 'entry-a'
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
    recordPrefix: 'oa-fake:v1:',
    entryId: () => entry,
    capture: () => ({ generation, entryId: entry }),
    storage,
    notify: (message, kind) => effects.push(['notify', message, kind]),
    confirm: vi.fn(async () => true),
    dismissConfirm: vi.fn(),
    replace: vi.fn(),
    refreshOperator: vi.fn(async () => {}),
    read: vi.fn(async () => ({ pool: ['甲', '乙'], origin: ['甲'] })),
    assign: vi.fn(async () => ({ kind: 'saved' })),
    afterSaved: vi.fn(async () => {}),
    normalizeTarget: (value) => ({ id: value.id, label: value.label || '' }),
    refreshTarget: (current, incoming) =>
      incoming.label && incoming.label !== current.label
        ? { id: current.id, label: incoming.label }
        : current,
    createState: () => ({ pool: [], picked: [], origin: [] }),
    restoreState: (record) => ({
      pool: [...record.items],
      picked: [...record.ids],
      origin: [...record.ids],
    }),
    decodeRecord: (raw) => {
      const value = JSON.parse(raw)
      if (
        !value ||
        value.version !== 1 ||
        typeof value.entry !== 'string' ||
        !value.targetId ||
        typeof value.targetLabel !== 'string' ||
        !['sending', 'unconfirmed'].includes(value.phase) ||
        !Array.isArray(value.ids) ||
        !Array.isArray(value.items)
      )
        throw new Error('分配记录无法恢复')
      return {
        entry: value.entry,
        target: { id: value.targetId, label: value.targetLabel },
        phase: value.phase,
        ids: value.ids,
        items: value.items,
      }
    },
    encodeRecord: (record) =>
      JSON.stringify({
        version: 1,
        entry: record.entry,
        targetId: record.target.id,
        targetLabel: record.target.label,
        phase: record.phase,
        ids: [...record.ids],
        items: [...record.items],
      }),
    hasUnsavedChanges: (handle) =>
      [...handle.state.picked].sort().join('|') !== [...handle.state.origin].sort().join('|'),
    buildSubmission: (handle) => ({
      ids: [...handle.state.picked],
      items: [...handle.state.picked],
    }),
    projectView: (input) => ({
      id: input.target.id,
      label: input.target.label,
      phase: input.phase,
      pool: [...input.state.pool],
      picked: [...input.state.picked],
      submittedIds: [...input.submittedIds],
      submittedItems: [...input.submittedItems],
      latest: input.latest,
      reading: input.reading,
      error: input.error,
      canEdit: input.capabilities.editable,
      canConfirm: input.capabilities.confirmable,
    }),
    leaseActions: (handle) => ({
      pick(ids) {
        if (!handle.active() || handle.phase() !== 'ready' || handle.reading()) return false
        handle.state.picked = [...ids]
        handle.clearError()
        handle.refresh()
        return true
      },
    }),
    saveSuccessMessage: '假域保存成功',
    operatorRefreshFailedMessage: '假域资料刷新失败',
    initialReadErrorFallback: '初始读取失败，请重试',
    verifyReadErrorFallback: '核实读取失败，请重试',
    leaveWhileSavingMessage: '假域：正在保存，确认关闭？',
    unsavedChangesMessage: '假域：有未保存的调整，确认关闭？',
    restoreOneFailedMessage: '假域：单条恢复失败',
    restoreScanFailedMessage: '假域：批量恢复失败',
    ...overrides,
  }
  adapter.readInitial = async (handle) => {
    const result = await adapter.read(handle.target.id)
    handle.state.pool = [...result.pool]
    handle.state.origin = [...result.origin]
    handle.state.picked = [...result.origin]
  }
  adapter.readVerify = async (handle) => {
    const result = await adapter.read(handle.target.id)
    return { current: [...result.origin] }
  }
  return {
    adapter,
    storage,
    values,
    effects,
    machine: createAssignmentMachine(adapter),
    setEntry: (value) => {
      entry = value
    },
    setGeneration: (value) => {
      generation = value
    },
  }
}

async function open(h, targetOverride = target) {
  const lease = h.machine.open(targetOverride)
  await lease.ready
  return lease
}

describe('共享分配机合同', () => {
  it('读取成功进入 ready，按适配器投影视图', async () => {
    const h = harness()
    const lease = await open(h)
    expect(h.adapter.read).toHaveBeenCalledWith(7)
    expect(lease.view.value).toMatchObject({
      id: 7,
      label: '目标甲',
      phase: 'ready',
      pool: ['甲', '乙'],
      picked: ['甲'],
      error: '',
      reading: false,
      canEdit: true,
      canConfirm: false,
    })
    expect(lease.released.value).toBe(false)
  })

  it('读取失败进入 read-error：不能保存，错误行按失败消息或兜底文案展示，可重试', async () => {
    const h = harness({
      read: vi
        .fn()
        .mockRejectedValueOnce(new Error('网络断了'))
        .mockRejectedValueOnce('非错误对象')
        .mockResolvedValue({ pool: ['甲', '乙'], origin: ['甲'] }),
    })
    const lease = h.machine.open(target)
    await lease.ready
    expect(lease.view.value.phase).toBe('read-error')
    expect(lease.view.value.error).toBe('网络断了')
    expect(lease.view.value.canEdit).toBe(false)
    await lease.save()
    expect(h.adapter.assign).not.toHaveBeenCalled()

    await lease.reload()
    expect(lease.view.value.phase).toBe('read-error')
    expect(lease.view.value.error).toBe('初始读取失败，请重试')

    await lease.reload()
    expect(lease.view.value.phase).toBe('ready')
    expect(lease.view.value.canEdit).toBe(true)
  })

  it('A→B 迟到读取不覆盖已打开的 B', async () => {
    const flightA = deferred()
    const h = harness({
      read: vi.fn((id) =>
        String(id) === String(target.id)
          ? flightA.promise
          : Promise.resolve({ pool: ['乙池'], origin: [] }),
      ),
    })
    const a = h.machine.open(target)
    await a.requestLeave()
    const b = await open(h, other)
    expect(b.view.value).toMatchObject({ id: 8, pool: ['乙池'], picked: [] })

    flightA.resolve({ pool: ['旧池'], origin: ['旧'] })
    await vi.waitFor(() => expect(h.adapter.read).toHaveBeenCalledTimes(2))
    expect(b.view.value.pool).toEqual(['乙池'])
    expect(b.view.value.picked).toEqual([])
  })

  it('核实读取进行中重复 reload 被忽略，在途结果仍归属原会话', async () => {
    const slow = deferred()
    const h = harness({
      assign: vi.fn(async () => ({ kind: 'unconfirmed' })),
      read: vi
        .fn()
        .mockResolvedValueOnce({ pool: ['甲'], origin: ['甲'] })
        .mockReturnValueOnce(slow.promise)
        .mockResolvedValue({ pool: ['甲', '乙'], origin: ['乙'] }),
    })
    const lease = await open(h)
    await lease.save()
    const first = lease.reload()
    await lease.reload()
    expect(h.adapter.read).toHaveBeenCalledTimes(2)
    slow.resolve({ pool: [], origin: [] })
    await first
    expect(lease.view.value.latest).toEqual({ current: [] })
    expect(lease.view.value.phase).toBe('unconfirmed')
  })

  it('保存成功：先持久化 sending 记录，成功后清记录、按序触发域刷新与操作者刷新', async () => {
    const h = harness()
    const lease = await open(h)
    lease.pick(['乙'])
    const saving = lease.save()
    expect(lease.view.value.phase).toBe('saving')
    expect(lease.view.value.canEdit).toBe(false)
    expect(JSON.parse([...h.values.values()][0])).toMatchObject({
      phase: 'sending',
      ids: ['乙'],
      items: ['乙'],
    })

    await saving
    expect(h.adapter.assign).toHaveBeenCalledWith(7, ['乙'])
    expect(lease.view.value.phase).toBe('saved')
    expect(h.effects).toContainEqual(['notify', '假域保存成功', 'success'])
    expect(h.values.size).toBe(0)
    expect(h.machine.pendingIds.value).toEqual([])
    expect(h.adapter.afterSaved).toHaveBeenCalledTimes(1)
    expect(h.adapter.refreshOperator).toHaveBeenCalledTimes(1)
    expect(h.adapter.afterSaved.mock.invocationCallOrder[0]).toBeLessThan(
      h.adapter.refreshOperator.mock.invocationCallOrder[0],
    )
  })

  it('明确拒绝回到可编辑，记录清理，可原内容重试', async () => {
    const h = harness({
      assign: vi
        .fn()
        .mockResolvedValueOnce({ kind: 'rejected', message: '未获接受' })
        .mockResolvedValue({ kind: 'saved' }),
    })
    const lease = await open(h)
    lease.pick(['乙'])
    await lease.save()
    expect(lease.view.value).toMatchObject({ phase: 'ready', picked: ['乙'], error: '未获接受' })
    expect(h.values.size).toBe(0)
    expect(h.effects).toContainEqual(['notify', '未获接受', 'error'])
    expect(h.adapter.refreshOperator).not.toHaveBeenCalled()

    await lease.save()
    expect(h.adapter.assign).toHaveBeenCalledTimes(2)
    expect(h.adapter.assign.mock.calls[1][1]).toEqual(['乙'])
  })

  it('不确定结果进入待确认：记录持久化、暂停保存、可核实读取但不改结论', async () => {
    const h = harness({
      assign: vi.fn(async () => ({ kind: 'unconfirmed' })),
      read: vi
        .fn()
        .mockResolvedValueOnce({ pool: ['甲', '乙'], origin: ['甲'] })
        .mockResolvedValue({ pool: ['甲', '乙'], origin: ['乙'] }),
    })
    const lease = await open(h)
    lease.pick(['乙'])
    await lease.save()
    expect(lease.view.value).toMatchObject({
      phase: 'unconfirmed',
      submittedIds: ['乙'],
      submittedItems: ['乙'],
      canEdit: false,
      canConfirm: true,
    })
    expect(h.machine.pendingIds.value).toEqual(['7'])
    expect(JSON.parse([...h.values.values()][0]).phase).toBe('unconfirmed')

    await lease.reload()
    expect(lease.view.value.latest).toEqual({ current: ['乙'] })
    expect(lease.view.value.phase).toBe('unconfirmed')
    expect(lease.view.value.submittedIds).toEqual(['乙'])
    expect(lease.view.value.canEdit).toBe(false)
    await lease.save()
    expect(h.adapter.assign).toHaveBeenCalledTimes(1)
  })

  it('保存中重复保存与再次选择被阻止，结束被拒绝，确认离开不撤销保存', async () => {
    const flight = deferred()
    const h = harness({ assign: vi.fn(() => flight.promise) })
    const lease = await open(h)
    lease.pick(['乙'])
    const saving = lease.save()
    await lease.save()
    expect(h.adapter.assign).toHaveBeenCalledTimes(1)
    expect(lease.pick(['甲'])).toBe(false)
    expect(await lease.endAssignment()).toBe(false)
    expect(lease.view.value.canConfirm).toBe(false)

    const leaving = lease.requestLeave()
    expect(h.adapter.confirm).toHaveBeenCalledWith('假域：正在保存，确认关闭？', 'leave')
    await leaving
    expect(lease.view.value.phase).toBe('saving')
    expect(h.values.size).toBe(1)

    flight.resolve({ kind: 'saved' })
    await saving
    expect(h.adapter.refreshOperator).toHaveBeenCalledTimes(1)
    expect(h.effects).not.toContainEqual(['notify', '假域保存成功', 'success'])
    expect(h.values.size).toBe(0)
  })

  it('关闭后重开同一目标不绕过保存限制', async () => {
    const h = harness({ assign: vi.fn(async () => ({ kind: 'unconfirmed' })) })
    const lease = await open(h)
    lease.pick(['乙'])
    await lease.save()
    const leaving = lease.requestLeave()
    expect(h.adapter.confirm).toHaveBeenCalledWith(expect.stringContaining('仍待确认'), 'leave')
    await leaving
    expect(lease.released.value).toBe(true)

    const again = await open(h)
    expect(again.view.value).toMatchObject({
      phase: 'unconfirmed',
      submittedIds: ['乙'],
      canEdit: false,
    })
    expect(h.adapter.read).toHaveBeenCalledTimes(1)
    await again.save()
    expect(h.adapter.assign).toHaveBeenCalledTimes(1)
  })

  it('刷新后从存储恢复待确认记录，sending 记录改写为 unconfirmed 且不重发', async () => {
    const flight = deferred()
    const h = harness({ assign: vi.fn(() => flight.promise) })
    const lease = await open(h)
    lease.pick(['乙'])
    const saving = lease.save()
    expect(JSON.parse([...h.values.values()][0]).phase).toBe('sending')
    lease.dispose()

    const restored = harness({ assign: vi.fn(async () => ({ kind: 'saved' })) }, h.values)
    expect(restored.machine.pendingIds.value).toEqual(['7'])
    expect(JSON.parse([...restored.values.values()][0]).phase).toBe('unconfirmed')

    const reopened = await open(restored)
    expect(reopened.view.value).toMatchObject({
      phase: 'unconfirmed',
      submittedIds: ['乙'],
      submittedItems: ['乙'],
    })
    await reopened.save()
    expect(restored.adapter.assign).not.toHaveBeenCalled()

    flight.resolve({ kind: 'saved' })
    await saving
    expect(h.effects).not.toContainEqual(['notify', '假域保存成功', 'success'])
  })

  it('按适配器解码无损恢复旧记录', () => {
    const values = new Map()
    values.set(
      recordKey('entry-a', 7),
      JSON.stringify({
        version: 1,
        entry: 'entry-a',
        targetId: 7,
        targetLabel: '旧标题',
        phase: 'unconfirmed',
        ids: ['旧一'],
        items: ['旧一'],
      }),
    )
    const h = harness({}, values)
    expect(h.machine.pendingIds.value).toEqual(['7'])
    const lease = h.machine.open({ id: 7, label: '旧标题' })
    expect(lease.view.value).toMatchObject({
      phase: 'unconfirmed',
      submittedIds: ['旧一'],
      submittedItems: ['旧一'],
    })
    expect(h.adapter.read).not.toHaveBeenCalled()
  })

  it('启动扫描清掉不可读记录并提示；存储 keys 不可用时不崩', () => {
    const values = new Map()
    values.set(recordKey('entry-a', 7), '{oops')
    values.set('other-prefix:1', '不归共享机管')
    const h = harness({}, values)
    expect(h.effects).toContainEqual(['notify', '假域：批量恢复失败', 'warning'])
    expect(values.has(recordKey('entry-a', 7))).toBe(false)
    expect(values.has('other-prefix:1')).toBe(true)

    const broken = harness({
      storage: {
        getItem: () => null,
        setItem: () => {},
        removeItem: () => {},
        keys: () => {
          throw new Error('storage unavailable')
        },
      },
    })
    expect(broken.machine.pendingIds.value).toEqual([])
  })

  it('打开时单条记录不可读：清理并按单条文案提示', () => {
    const h = harness()
    h.values.set(recordKey('entry-a', 7), '{oops')
    const lease = h.machine.open(target)
    expect(h.effects).toContainEqual(['notify', '假域：单条恢复失败', 'warning'])
    expect(h.values.has(recordKey('entry-a', 7))).toBe(false)
    expect(lease.view.value.phase).toBe('loading')
  })

  it('结束待确认需确认且只清本地记录，重开重新读取', async () => {
    const h = harness({ assign: vi.fn(async () => ({ kind: 'unconfirmed' })) })
    const lease = await open(h)
    await lease.save()
    h.adapter.confirm.mockResolvedValueOnce(false)
    expect(await lease.endAssignment()).toBe(false)
    expect(lease.view.value.phase).toBe('unconfirmed')
    expect(h.values.size).toBe(1)

    h.adapter.confirm.mockResolvedValueOnce(true)
    expect(await lease.endAssignment()).toBe(true)
    expect(h.adapter.confirm).toHaveBeenCalledWith(
      expect.stringContaining('结束本次分配只清理本地记录'),
      'end-assignment',
    )
    expect(h.values.size).toBe(0)
    expect(h.machine.pendingIds.value).toEqual([])

    const again = await open(h)
    expect(again.view.value.phase).toBe('ready')
    expect(h.adapter.read).toHaveBeenCalledTimes(2)
  })

  it('身份替换释放会话、跳回默认页并丢弃迟到结果', async () => {
    const flight = deferred()
    const h = harness({ assign: vi.fn(() => flight.promise) })
    const lease = await open(h)
    lease.pick(['乙'])
    const saving = lease.save()
    h.setEntry('entry-b')
    h.setGeneration(2)
    await lease.environmentChanged()
    expect(lease.released.value).toBe(true)
    expect(h.adapter.replace).toHaveBeenCalledWith(getDefaultPostLoginRoute())
    expect(h.adapter.confirm).not.toHaveBeenCalled()
    expect(h.values.size).toBe(1)

    flight.resolve({ kind: 'saved' })
    await saving
    expect(h.adapter.refreshOperator).not.toHaveBeenCalled()
    expect(h.effects).toEqual([])
    expect(h.values.size).toBe(0)
  })

  it('未保存与空闲状态的身份替换不确认直接释放；身份清空时不跳页', async () => {
    const h = harness()
    const lease = await open(h)
    lease.pick(['乙'])
    h.setEntry('entry-b')
    h.setGeneration(2)
    await lease.environmentChanged()
    expect(lease.released.value).toBe(true)
    expect(h.adapter.replace).toHaveBeenCalledWith(getDefaultPostLoginRoute())
    expect(h.adapter.confirm).not.toHaveBeenCalled()
    expect(h.values.size).toBe(0)

    const cleared = harness()
    const other2 = await open(cleared)
    other2.pick(['乙'])
    cleared.setEntry('')
    await other2.environmentChanged()
    expect(other2.released.value).toBe(true)
    expect(cleared.adapter.replace).not.toHaveBeenCalled()
  })

  it('身份未变时 environmentChanged 不动会话', async () => {
    const h = harness()
    const lease = await open(h)
    lease.pick(['乙'])
    await lease.environmentChanged()
    expect(lease.released.value).toBe(false)
    expect(lease.view.value.picked).toEqual(['乙'])
    expect(h.adapter.confirm).not.toHaveBeenCalled()
    expect(h.adapter.replace).not.toHaveBeenCalled()
  })

  it('离开确认：未调整直接放行，有未保存调整按文案确认，拒绝则留在会话', async () => {
    const h = harness()
    const lease = await open(h)
    expect(await lease.requestLeave()).toBe(true)
    expect(lease.released.value).toBe(true)
    expect(h.adapter.confirm).not.toHaveBeenCalled()

    const second = await open(h)
    second.pick(['乙'])
    h.adapter.confirm.mockResolvedValueOnce(false)
    const leaving = second.requestLeave()
    expect(h.adapter.confirm).toHaveBeenCalledWith('假域：有未保存的调整，确认关闭？', 'leave')
    expect(await leaving).toBe(false)
    expect(second.released.value).toBe(false)
    expect(second.view.value.picked).toEqual(['乙'])

    h.adapter.confirm.mockResolvedValueOnce(true)
    expect(await second.requestLeave()).toBe(true)
    expect(second.released.value).toBe(true)
    expect(h.values.size).toBe(0)
  })

  it('存储不可靠时保存先不发出', async () => {
    const h = harness()
    const lease = await open(h)
    h.storage.setItem = () => {
      throw new Error('quota')
    }
    lease.pick(['乙'])
    await lease.save()
    expect(h.adapter.assign).not.toHaveBeenCalled()
    expect(lease.view.value.phase).toBe('ready')
    expect(lease.view.value.error).toContain('无法可靠保留')
    expect(h.effects).toContainEqual(['notify', expect.stringContaining('无法可靠保留'), 'warning'])
  })

  it('合法空集合可以保存', async () => {
    const h = harness()
    const lease = await open(h)
    lease.pick([])
    await lease.save()
    expect(h.adapter.assign).toHaveBeenCalledWith(7, [])
    expect(h.values.size).toBe(0)
    expect(h.effects).toContainEqual(['notify', '假域保存成功', 'success'])
  })

  it('迟到成功仍刷新操作者资料与菜单，不关闭新目标、不弹旧提示', async () => {
    const flight = deferred()
    const h = harness({ assign: vi.fn(() => flight.promise) })
    const lease = await open(h)
    lease.pick(['乙'])
    const saving = lease.save()
    await lease.requestLeave()
    const next = await open(h, other)
    expect(next.view.value.id).toBe(8)

    flight.resolve({ kind: 'saved' })
    await saving
    expect(h.adapter.refreshOperator).toHaveBeenCalledTimes(1)
    expect(next.view.value).toMatchObject({ id: 8, phase: 'ready' })
    expect(h.effects).not.toContainEqual(['notify', '假域保存成功', 'success'])
  })

  it('操作者刷新失败只告警，不改保存结论、不重发保存', async () => {
    const h = harness({
      refreshOperator: vi.fn(async () => {
        throw new Error('刷新失败')
      }),
    })
    const lease = await open(h)
    await lease.save()
    expect(lease.view.value.phase).toBe('saved')
    expect(h.adapter.assign).toHaveBeenCalledTimes(1)
    expect(h.effects).toContainEqual(['notify', '假域保存成功', 'success'])
    expect(h.effects).toContainEqual(['notify', '假域资料刷新失败', 'warning'])
  })

  it('待确认列表按当前进入身份投影', async () => {
    const h = harness({
      assign: vi.fn(async (id) => ({
        kind: String(id) === String(target.id) ? 'unconfirmed' : 'saved',
      })),
    })
    const first = await open(h)
    await first.save()
    await first.requestLeave()
    expect(h.machine.pendingIds.value).toEqual(['7'])

    const second = await open(h, other)
    await second.save()
    expect(h.adapter.assign).toHaveBeenCalledTimes(2)
    expect(h.machine.pendingIds.value).toEqual(['7'])
  })

  it('重开时按入参刷新目标标题', async () => {
    const h = harness({ assign: vi.fn(async () => ({ kind: 'unconfirmed' })) })
    const lease = await open(h)
    lease.pick(['乙'])
    await lease.save()
    await lease.requestLeave()

    const again = await open(h, { id: 7, label: '新标题' })
    expect(again.view.value).toMatchObject({ id: 7, label: '新标题' })
  })
})
