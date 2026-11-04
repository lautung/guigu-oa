import { describe, expect, it, vi } from 'vitest'
import { createRoleUsers } from '@/config/role-users'

const alice = { id: 11, username: 'alice', name: '爱丽丝' }
const bob = { id: 12, username: 'bob', name: '鲍勃' }
const carol = { id: 13, username: 'carol', name: '卡罗尔', phone: '13800002222' }
const dave = { id: 14, username: 'dave', name: '戴夫' }
const role = { id: 7, roleName: '审批人' }

function deferred() {
  let resolve, reject
  const promise = new Promise((yes, no) => {
    resolve = yes
    reject = no
  })
  return { promise, resolve, reject }
}

function harness(overrides = {}, sharedValues = new Map()) {
  const values = sharedValues
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

describe('角色分配用户域适配', () => {
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

  it('候选读取失败回显文案且不静默', async () => {
    const h = harness({
      readCandidates: vi
        .fn()
        .mockResolvedValueOnce({ users: [carol, dave], total: 2 })
        .mockRejectedValue(new Error('断网')),
    })
    const page = await open(h)
    await page.search('x')
    expect(page.view.value.error).toBe('断网')
    // 候选页失败不改变会话 phase，成员侧仍可操作，可重新搜索。
    expect(page.view.value.phase).toBe('ready')
    expect(page.view.value.canMove).toBe(true)
  })

  it('成员读取投影校验失败按既有文案进入读取失败', async () => {
    const h = harness({ readMembers: vi.fn(async () => '不是数组') })
    const page = h.module.open(role)
    await page.ready
    expect(page.view.value.phase).toBe('read-error')
    expect(page.view.value.error).toBe('用户读取结果无效，请重试')
  })

  it('初始与核实的读取失败兜底文案区分', async () => {
    const h = harness({
      assign: vi.fn(async () => ({ kind: 'unconfirmed' })),
      readMembers: vi.fn().mockRejectedValue('中断'),
    })
    const page = h.module.open(role)
    await page.ready
    expect(page.view.value.error).toBe('成员与用户读取失败，请重试')
    page.dispose()

    const h2 = harness({ assign: vi.fn(async () => ({ kind: 'unconfirmed' })) })
    const page2 = await open(h2)
    page2.add([13])
    await page2.save()
    h2.adapter.readMembers.mockRejectedValue('中断')
    await page2.reload()
    expect(page2.view.value.error).toBe('最新成员读取失败，请重试')
  })

  it('待确认记录保留既有键前缀与 JSON 字段形状，成员只留 id/username/name', async () => {
    const h = harness({ assign: vi.fn(async () => ({ kind: 'unconfirmed' })) })
    const page = await open(h)
    page.add([13])
    await page.save()
    const [key, raw] = [...h.values.entries()][0]
    expect(key).toBe('oa-role-users:v1:entry-admin:7')
    const record = JSON.parse(raw)
    expect(record).toMatchObject({
      version: 1,
      entry: 'entry-admin',
      roleId: 7,
      roleName: '审批人',
      phase: 'unconfirmed',
      submittedUserIds: [11, 12, 13],
    })
    expect(record.submittedUsers).toEqual([
      { id: 11, username: 'alice', name: '爱丽丝' },
      { id: 12, username: 'bob', name: '鲍勃' },
      { id: 13, username: 'carol', name: '卡罗尔' },
    ])
    expect(JSON.stringify(record)).not.toContain('phone')
  })

  it('旧记录按既有形状无损恢复为待确认，候选分页回到默认', () => {
    const values = new Map()
    values.set(
      'oa-role-users:v1:entry-admin:7',
      JSON.stringify({
        version: 1,
        entry: 'entry-admin',
        roleId: 7,
        roleName: '审批人',
        phase: 'unconfirmed',
        submittedUsers: [
          { id: 11, username: 'alice', name: '爱丽丝' },
          { id: 12, username: 'bob', name: '鲍勃', phone: '13800000000' },
        ],
        submittedUserIds: [11, 12],
      }),
    )
    const h = harness({}, values)
    expect(h.module.pendingRoleIds.value).toEqual(['7'])
    const lease = h.module.open(role)
    expect(lease.view.value).toMatchObject({
      phase: 'unconfirmed',
      members: [alice, bob],
      submittedUserIds: [11, 12],
      submittedUsers: [alice, bob],
      keyword: '',
      page: 1,
      size: 10,
      candidates: [],
    })
    expect(h.adapter.readMembers).not.toHaveBeenCalled()
  })

  it('恢复失败按批量与单条分别提示', () => {
    const values = new Map()
    values.set('oa-role-users:v1:entry-admin:7', '{oops')
    const h = harness({}, values)
    expect(h.effects).toContainEqual([
      'notify',
      '未能恢复上次分配记录，请核实相关角色的当前成员后再分配',
      'warning',
    ])

    const later = harness()
    later.values.set('oa-role-users:v1:entry-admin:8', '{oops')
    later.module.open({ id: 8, roleName: '观察人' })
    expect(later.effects).toContainEqual([
      'notify',
      '未能恢复上次分配记录，请核实该角色的当前成员后再分配',
      'warning',
    ])
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
    expect(h.adapter.readCandidates).toHaveBeenLastCalledWith(7, {
      keyword: '',
      page: 1,
      size: 10,
    })
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

  it('离开文案映射：保存中与未保存调整各自提示', async () => {
    const flight = deferred()
    const h = harness({ assign: vi.fn(() => flight.promise) })
    const page = await open(h)
    page.add([13])
    const saving = page.save()
    const leaving = page.requestLeave()
    expect(h.adapter.confirm).toHaveBeenCalledWith(
      '正在保存成员分配。离开不撤销已提交的保存，原请求仍可能生效，确认关闭？',
      'leave',
    )
    await leaving
    flight.resolve({ kind: 'saved' })
    await saving

    const second = await open(h)
    second.add([13])
    const leaving2 = second.requestLeave()
    expect(h.adapter.confirm).toHaveBeenCalledWith(
      '当前有未保存的成员调整，离开将丢弃本次修改，确认关闭？',
      'leave',
    )
    await leaving2
  })

  it('重开时以新入参刷新目标 roleName', async () => {
    const h = harness({ assign: vi.fn(async () => ({ kind: 'unconfirmed' })) })
    const page = await open(h)
    page.add([13])
    await page.save()
    await page.requestLeave()

    const again = await open(h, { id: 7, roleName: '新角色名' })
    expect(again.view.value).toMatchObject({ roleId: 7, roleName: '新角色名' })
  })
})
