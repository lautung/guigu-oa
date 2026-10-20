import { flushPromises } from '@vue/test-utils'
import { createRoleAssignment } from '@/config/role-assignment'
import { BusinessRequestFailure } from '@/utils/request'

function deferred() {
  let resolve, reject
  const promise = new Promise((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}
const roles = (assigned = [1]) => ({ code: 200, data: {
  allRolesList: [{ id: 1, roleName: '用户配置' }, { id: 2, roleName: '审批配置' }],
  assginRoleList: assigned.map(id => ({ id }))
} })
function harness() {
  const reads = [], saves = [], notices = [], confirmations = [], recognitions = []
  const storage = new Map()
  let entry = 'original', confirm = true
  const options = {
    recognition: {
      capture: () => ({ entryId: entry, generation: 0, silent: false }),
      recognize: () => { const d = deferred(); recognitions.push(d); return d.promise }
    },
    request: config => { const d = deferred(); (config.method === 'get' ? reads : saves).push({ ...d, config }); return d.promise },
    records: {
      load: key => storage.get(key) ?? null,
      save: (key, value) => storage.set(key, JSON.parse(JSON.stringify(value))),
      remove: key => storage.delete(key)
    },
    confirm: async message => { confirmations.push(message); return confirm },
    cancelConfirmation: () => {},
    notify: message => notices.push(message)
  }
  return { options, module: createRoleAssignment(options), reads, saves, notices, confirmations, recognitions, storage,
    identity: value => { entry = value }, confirm: value => { confirm = value } }
}
async function loaded(h, target = { id: 'A', username: '张三' }, assigned = [1]) {
  const opened = h.module.open(target)
  await flushPromises()
  h.reads.at(-1).resolve(roles(assigned))
  await opened
}

it('关闭 A 后打开 B，A 的迟到角色不能改变 B 的选择或提交内容', async () => {
  const h = harness()
  const first = h.module.open({ id: 'A', username: '张三' })
  await flushPromises()
  await h.module.requestLeave()
  const second = h.module.open({ id: 'B', username: '李四' })
  await flushPromises()
  h.reads[1].resolve(roles([2]))
  await second
  h.reads[0].resolve(roles([1]))
  await first
  expect(h.module.state.target).toEqual({ id: 'B', username: '李四' })
  expect(h.module.state.selectedIds).toEqual([2])
  void h.module.save()
  expect(h.saves[0].config.data).toEqual({ userId: 'B', roleIdList: [2] })
})

it.each([
  null, {}, { allRolesList: [], assginRoleList: null },
  { allRolesList: [{ id: 1, roleName: '用户配置' }], assginRoleList: [{ id: 2 }] },
  { allRolesList: [{ id: 1 }, { id: 1 }], assginRoleList: [] }
])('无法理解的角色读取 %j 阻止保存，重新读取合法空集合才允许清空', async data => {
  const h = harness()
  const opened = h.module.open({ id: 'A', username: '张三' })
  await flushPromises()
  expect(h.module.state.canSave).toBe(false)
  h.reads[0].resolve({ code: 200, data }); await opened
  await h.module.save()
  expect(h.module.state.phase).toBe('load-error')
  expect(h.saves).toHaveLength(0)
  const reloaded = h.module.reload()
  h.reads[1].resolve({ code: 200, data: { allRolesList: [], assginRoleList: [] } })
  await reloaded
  expect(h.module.state.canSave).toBe(true)
  void h.module.save()
  expect(h.saves[0].config.data).toEqual({ userId: 'A', roleIdList: [] })
})

it('同一目标重复读取只接受最新一次有效结果，读取错误可重试', async () => {
  const h = harness()
  const opened = h.module.open({ id: 'A', username: '张三' })
  await flushPromises()
  const refreshed = h.module.reload()
  h.reads[1].reject(new Error('暂时离线')); await refreshed
  expect(h.module.state.canSave).toBe(false)
  const retried = h.module.reload()
  h.reads[2].resolve(roles([2])); await retried
  h.reads[0].resolve(roles([1])); await opened
  expect(h.module.state.selectedIds).toEqual([2])
})

it('未修改直接关闭，修改关闭与切换取消均保留原目标，强制离开使旧确认失效', async () => {
  const h = harness()
  await loaded(h)
  expect(await h.module.requestLeave()).toBe(true)
  expect(h.confirmations).toHaveLength(0)
  await loaded(h)
  h.module.select([2]); h.confirm(false)
  await h.module.open({ id: 'B', username: '李四' })
  expect(h.module.state.target.id).toBe('A')
  expect(h.module.state.selectedIds).toEqual([2])
  const pending = deferred()
  h.options.confirm = () => pending.promise
  const leaving = h.module.requestLeave()
  await h.module.requestLeave(true)
  await loaded(h, { id: 'B', username: '李四' }, [2])
  pending.resolve(true); await leaving
  expect(h.module.state.target.id).toBe('B')
  expect(h.module.state.canSave).toBe(true)
})

it('固定提交、重复保存与关闭重开共享在途限制，其他用户可分配，旧成功只刷新权限', async () => {
  const h = harness()
  await loaded(h)
  h.module.select([])
  const saving = h.module.save()
  h.module.select([2]); void h.module.save()
  expect(h.module.state.selectedIds).toEqual([])
  expect(h.saves).toHaveLength(1)
  expect(await h.module.requestLeave()).toBe(true)
  expect(h.confirmations.at(-1)).toContain('不撤销')
  await h.module.open({ id: 'A', username: '张三' })
  expect(h.module.state.phase).toBe('saving')
  expect(h.module.state.snapshot.roleIdList).toEqual([])
  expect(await h.module.endAssignment()).toBe(false)
  const other = h.module.open({ id: 'B', username: '李四' })
  await flushPromises()
  h.reads.at(-1).resolve(roles([2])); await other
  h.saves[0].resolve({ code: 200, data: null }); await saving
  expect(h.module.state.target.id).toBe('B')
  expect(h.module.state.selectedIds).toEqual([2])
  expect(h.module.state.canSave).toBe(true)
  expect(h.notices).toEqual([])
  expect(h.recognitions).toHaveLength(1)
  expect(h.storage.size).toBe(0)
})

it.each(['网络断开', 'timeout', '通用失败'])('%s 保留待确认，核实相同角色或失败均不释放，结束后必须重新读取', async failure => {
  const h = harness(); await loaded(h)
  h.module.select([2]); const saved = h.module.save()
  h.saves[0].reject(new Error(failure)); await saved
  expect(h.module.state.phase).toBe('uncertain')
  expect(h.module.state.snapshot.roleIdList).toEqual([2])
  const reread = h.module.reload(); h.reads[1].resolve(roles([2])); await reread
  expect(h.module.state.phase).toBe('uncertain')
  expect(h.module.state.selectedIds).toEqual([2])
  expect(h.module.state.canSave).toBe(false)
  const failed = h.module.reload(); h.reads[2].reject(new Error('核实读取失败')); await failed
  expect(h.module.state.snapshot.roleIdList).toEqual([2])
  expect(h.module.state.canSave).toBe(false)
  h.confirm(false); expect(await h.module.endAssignment()).toBe(false)
  expect(h.module.state.snapshot.roleIdList).toEqual([2])
  h.confirm(true)
  const ended = h.module.endAssignment(); await flushPromises()
  expect(h.module.state.phase).toBe('loading')
  expect(h.module.state.canSave).toBe(false)
  expect(h.confirmations.at(-1)).toContain('影响后续分配')
  h.reads[3].resolve(roles([1])); await ended
  expect(h.module.state.selectedIds).toEqual([1])
  expect(h.module.state.snapshot).toBe(null)
  expect(h.module.state.canSave).toBe(true)
  expect(h.saves).toHaveLength(1)
})

it('关闭、页面重挂载和刷新恢复提交记录，失去原响应的在途记录按待确认而不重发', async () => {
  const h = harness(); await loaded(h)
  h.module.select([2]); void h.module.save()
  await h.module.requestLeave()
  h.module = createRoleAssignment(h.options)
  await h.module.open({ id: 'A', username: '张三' })
  expect(h.module.state.phase).toBe('uncertain')
  expect(h.module.state.snapshot).toMatchObject({ target: { id: 'A' }, roleIdList: [2] })
  expect(h.module.state.canSave).toBe(false)
  expect(h.saves).toHaveLength(1)
})

it('未保存勾选不跨刷新恢复；记录的身份和目标隔离', async () => {
  const h = harness(); await loaded(h)
  h.module.select([2])
  h.module.release(); h.module = createRoleAssignment(h.options)
  await loaded(h)
  expect(h.module.state.selectedIds).toEqual([1])
  void h.module.save(); h.module.release()
  h.identity('new-entry'); await loaded(h)
  expect(h.module.state.snapshot).toBe(null)
  expect(h.module.state.canSave).toBe(true)
})

it.each(['throw', 'drop', 'corrupt'])('提交记录 %s 时先不发送保存，并保留原选择与明确错误', async kind => {
  const h = harness(); await loaded(h)
  h.module.select([2])
  h.options.records.save = (key, value) => {
    if (kind === 'throw') throw new Error('存储不可用')
    if (kind === 'corrupt') h.storage.set(key, { ...value, roleIdList: [] })
  }
  await h.module.save()
  expect(h.saves).toHaveLength(0)
  expect(h.module.state.canSave).toBe(false)
  expect(h.module.state.recordError).toBeTruthy()
  expect(h.module.state.selectedIds).toEqual([2])
})

it('单个目标的损坏或无法读取记录阻止覆盖，其他目标仍可分配', async () => {
  const h = harness(); await loaded(h)
  void h.module.save(); h.module.release()
  const key = [...h.storage.keys()][0]; h.storage.set(key, { broken: true })
  await h.module.open({ id: 'A', username: '张三' })
  expect(h.module.state.recordError).toBeTruthy()
  expect(h.module.state.canSave).toBe(false)
  await loaded(h, { id: 'B', username: '李四' })
  expect(h.module.state.canSave).toBe(true)
})


it('明确成功后认出故障独立恢复，保存结论不回退也不再次提交', async () => {
  const h = harness(); await loaded(h)
  const saved = h.module.save(); h.saves[0].resolve({ code: 200, data: null }); await saved
  expect(h.module.state.phase).toBe('closed')
  h.recognitions[0].reject(new Error('认出临时故障')); await flushPromises()
  expect(h.module.state.permissionRefresh.status).toBe('retryable')
  expect(h.module.state.permissionRefresh.error).toContain('已保存')
  const retried = h.module.retryRecognition()
  h.recognitions[1].resolve({ name: '张三', routers: [] }); await retried
  expect(h.module.state.permissionRefresh.status).toBe('idle')
  expect(h.saves).toHaveLength(1)
  expect(h.storage.size).toBe(0)
})

it('切换进入身份后旧成功不认出、不通知、不清理新展示，旧认出故障不影响新身份', async () => {
  const h = harness(); await loaded(h)
  const saved = h.module.save(); h.module.release(); h.identity('replacement')
  await loaded(h, { id: 'B', username: '李四' })
  h.saves[0].resolve({ code: 200, data: null }); await saved
  expect(h.recognitions).toHaveLength(0)
  expect(h.notices).toEqual([])
  expect(h.module.state.target.id).toBe('B')
  const next = h.module.save(); h.saves[1].resolve({ code: 200, data: null }); await next
  h.identity('third'); h.recognitions[0].reject(new Error('旧身份故障')); await flushPromises()
  await loaded(h)
  expect(h.module.state.permissionRefresh.status).toBe('idle')
  await h.module.retryRecognition()
  expect(h.recognitions).toHaveLength(1)
})

it.each([208, 209, 205])('明确业务前拒绝 %s 才解除记录并保留原选择，通用 201 仍待确认', async code => {
  const h = harness(); await loaded(h)
  h.module.select([2]); const saved = h.module.save()
  h.saves[0].reject(new BusinessRequestFailure('retryable', '明确拒绝', code)); await saved
  expect(h.module.state.phase).toBe('ready')
  expect(h.module.state.selectedIds).toEqual([2])
  expect(h.module.state.canSave).toBe(true)
  expect(h.storage.size).toBe(0)
  const second = h.module.save()
  h.saves[1].reject(new BusinessRequestFailure('retryable', '通用错误', 201)); await second
  expect(h.module.state.phase).toBe('uncertain')
  expect(h.module.state.canSave).toBe(false)
})

it('取消等待保存的离开保留冻结内容，已关闭的旧失败不污染新展示', async () => {
  const h = harness(); await loaded(h)
  const saved = h.module.save(); h.confirm(false)
  expect(await h.module.requestLeave()).toBe(false)
  expect(h.module.state.phase).toBe('saving')
  h.confirm(true); await loaded(h, { id: 'B', username: '李四' })
  h.saves[0].reject(new Error('原保存超时')); await saved
  expect(h.module.state.target.id).toBe('B')
  expect(h.module.state.error).toBe('')
  expect(h.notices).toEqual([])
  await h.module.open({ id: 'A', username: '张三' })
  expect(h.module.state.phase).toBe('uncertain')
})
