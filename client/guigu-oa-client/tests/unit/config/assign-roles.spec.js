import { describe, expect, it, vi } from 'vitest'
import { createAssignRoles } from '@/config/assign-roles'

const roles = [
  { id: 1, roleName: '管理员' },
  { id: 2, roleName: '审批人' },
]
const alice = { id: 11, username: 'alice' }

function harness(overrides = {}, sharedValues = new Map()) {
  const values = sharedValues
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
    read: vi.fn(async () => ({ roles, assignedRoleIds: [1] })),
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

describe('分配角色适配器映射', () => {
  it('待确认记录保留既有键前缀与 JSON 字段形状', async () => {
    const h = harness({ assign: vi.fn(async () => ({ kind: 'unconfirmed' })) })
    const page = await open(h)
    page.select([2])
    await page.save()
    const [key, raw] = [...h.values.entries()][0]
    expect(key).toBe('oa-assign:v1:entry-alice:11')
    const record = JSON.parse(raw)
    expect(record).toMatchObject({
      version: 1,
      entry: 'entry-alice',
      userId: 11,
      username: 'alice',
      phase: 'unconfirmed',
      submittedRoleIds: [2],
      submittedRoles: [{ id: 2, roleName: '审批人' }],
    })
  })

  it('快照投影校验读取结果并回显既有文案', async () => {
    const h = harness({
      read: vi
        .fn()
        .mockResolvedValueOnce({ assignedRoleIds: [1] })
        .mockResolvedValueOnce({
          roles: [
            { id: 1, roleName: '管理员' },
            { id: 1, roleName: '重复' },
          ],
          assignedRoleIds: [1],
        })
        .mockResolvedValueOnce({ roles, assignedRoleIds: [9] })
        .mockResolvedValue({ roles, assignedRoleIds: [1] }),
    })
    for (const expected of [
      '角色读取结果无效，请重试',
      '角色读取结果无效，请重试',
      '角色读取结果与可分配角色不一致，请重试',
    ]) {
      const page = h.module.open(alice)
      await page.ready
      expect(page.view.value.error).toBe(expected)
      expect(page.view.value.phase).toBe('read-error')
      await page.requestLeave()
    }
  })

  it('缺省角色名按 id 回退', async () => {
    const h = harness({
      read: vi.fn(async () => ({ roles: [{ id: 5 }], assignedRoleIds: [5] })),
    })
    const page = await open(h)
    expect(page.view.value.phase).toBe('ready')
    expect(page.view.value.roles).toEqual([{ id: 5, roleName: '5' }])
  })

  it('读取失败的兜底文案区分初次与核实', async () => {
    const h = harness({
      assign: vi.fn(async () => ({ kind: 'unconfirmed' })),
      read: vi.fn().mockRejectedValue('中断'),
    })
    const page = h.module.open(alice)
    await page.ready
    expect(page.view.value.error).toBe('角色读取失败，请重试')
    page.dispose()

    const h2 = harness({
      assign: vi.fn(async () => ({ kind: 'unconfirmed' })),
    })
    const page2 = await open(h2)
    page2.select([2])
    await page2.save()
    h2.adapter.read.mockRejectedValue('中断')
    await page2.reload()
    expect(page2.view.value.error).toBe('最新角色读取失败，请重试')
  })

  it('提交内容构造过滤未知与重复勾选，角色快照随 id 对齐', async () => {
    const h = harness()
    const page = await open(h)
    page.select([2, 2, 99])
    await page.save()
    expect(h.adapter.assign).toHaveBeenCalledWith(11, [2])
    expect(page.view.value.submittedRoleIds).toEqual([2])
    expect(page.view.value.submittedRoles).toEqual([{ id: 2, roleName: '审批人' }])
  })

  it('成功文案与资料刷新失败警告沿用既有文案', async () => {
    const h = harness({
      refreshOperator: vi.fn(async () => {
        throw new Error('刷新失败')
      }),
    })
    const page = await open(h)
    await page.save()
    expect(h.effects).toContainEqual(['notify', '分配角色成功', 'success'])
    expect(h.effects).toContainEqual([
      'notify',
      '角色已保存，但资料与菜单刷新失败，请稍后重新进入',
      'warning',
    ])
  })

  it('恢复失败按批量与单条分别提示', () => {
    const values = new Map()
    values.set('oa-assign:v1:entry-alice:11', '{oops')
    const h = harness({}, values)
    expect(h.effects).toContainEqual([
      'notify',
      '未能恢复上次分配记录，请核实相关用户的当前角色后再分配',
      'warning',
    ])

    const later = harness()
    later.values.set('oa-assign:v1:entry-alice:12', '{oops')
    later.module.open({ id: 12, username: 'bob' })
    expect(later.effects).toContainEqual([
      'notify',
      '未能恢复上次分配记录，请核实该用户的当前角色后再分配',
      'warning',
    ])
  })

  it('旧记录按既有形状无损恢复为待确认', () => {
    const values = new Map()
    values.set(
      'oa-assign:v1:entry-alice:11',
      JSON.stringify({
        version: 1,
        entry: 'entry-alice',
        userId: 11,
        username: 'alice',
        phase: 'unconfirmed',
        submittedRoles: [{ id: 2, roleName: '审批人' }],
        submittedRoleIds: [2],
      }),
    )
    const h = harness({}, values)
    expect(h.module.pendingUserIds.value).toEqual(['11'])
    const lease = h.module.open(alice)
    expect(lease.view.value).toMatchObject({
      phase: 'unconfirmed',
      submittedRoleIds: [2],
      submittedRoles: [{ id: 2, roleName: '审批人' }],
    })
    expect(h.adapter.read).not.toHaveBeenCalled()
  })

  it('重开时以新入参刷新目标 username', async () => {
    const h = harness({ assign: vi.fn(async () => ({ kind: 'unconfirmed' })) })
    const page = await open(h)
    page.select([2])
    await page.save()
    await page.requestLeave()

    const again = await open(h, { id: 11, username: 'alice2' })
    expect(again.view.value).toMatchObject({ userId: 11, username: 'alice2' })
  })
})
