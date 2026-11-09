import { describe, expect, it } from 'vitest'
import { flushPromises } from '@vue/test-utils'
import { createTemplateEditor, formRules } from '@/config/template-editor'
import { getDefaultPostLoginRoute } from '@/navigation/constants'
import { emptyMessageOf } from '../support/form-rules'

function deferred() {
  let resolve, reject
  const promise = new Promise((yes, no) => {
    resolve = yes
    reject = no
  })
  return { promise, resolve, reject }
}
const record = (id = 'A', extra = {}) => ({
  id,
  name: '请假审批',
  processTypeId: 3,
  status: 0,
  formProps: '[]',
  formOptions: '{}',
  processDefinitionPath: 'opaque/original.zip',
  processDefinitionKey: 'original',
  ...extra,
})
function harness() {
  const reads = [],
    uploads = [],
    saves = [],
    approverReads = [],
    approverSaves = [],
    optionReads = [],
    navigation = [],
    replacements = [],
    notices = [],
    confirmations = [],
    dismissals = [],
    lists = []
  let entryId = 'entry-1'
  let design = { rule: [], option: {} }
  let designerFailure = ''
  let confirm = true
  function openScope() {
    const context = { generation: 1, entryId }
    const scope = {
      context,
      current: () => context.entryId === entryId,
      request: (config, isRelevant) => {
        if (config.url === '/admin/process/processType/findAll')
          return Promise.resolve({ code: 200, data: [{ id: 3, name: '请假' }] })
        const pending = deferred()
        const group = config.url.includes('/get/')
          ? reads
          : config.url.endsWith('/uploadProcessDefinition')
            ? uploads
            : config.url.includes('/approverRules/')
              ? config.method === 'put'
                ? approverSaves
                : approverReads
              : config.url === '/admin/process/processTemplate/ruleTargets'
                ? optionReads
                : saves
        group.push({ config, isRelevant, scope, ...pending })
        return pending.promise
      },
    }
    return scope
  }
  const options = {
    openScope,
    entryId: () => entryId,
    dismissConfirm: () => dismissals.push('dismiss'),
    designer: async () => {
      if (designerFailure) throw new Error(designerFailure)
      return {
        setRule: (rule) => {
          design.rule = structuredClone(rule)
        },
        setOption: (option) => {
          design.option = structuredClone(option)
        },
        getRule: () => design.rule,
        getOption: () => design.option,
      }
    },
    confirm: async (message) => {
      confirmations.push(message)
      return confirm
    },
    navigate: (path) => navigation.push(path),
    replace: (path) => replacements.push(path),
    notify: (message) => notices.push(message),
    openList: (path) => lists.push(path),
  }
  return {
    module: createTemplateEditor(options),
    options,
    reads,
    uploads,
    saves,
    approverReads,
    approverSaves,
    optionReads,
    navigation,
    replacements,
    notices,
    confirmations,
    dismissals,
    lists,
    design: (value) => {
      design = value
    },
    designerFailure: (message) => {
      designerFailure = message
    },
    confirm: (value) => {
      confirm = value
    },
    identity: () => {
      entryId = 'entry-2'
    },
    entry: (value) => {
      entryId = value
    },
  }
}
const zip = (name = 'new.zip') => new File(['zip'], name, { type: 'application/zip' })
const uploaded = (name = 'new') => ({
  code: 200,
  data: { processDefinitionPath: `opaque/${name}.zip`, processDefinitionKey: name },
})

it('通过完整编辑 interface 完成三步新建，保留扩展设计并以未发布保存一次固定内容', async () => {
  const h = harness()
  const edit = h.module.open()
  await edit.ready
  edit.updateBasic({ name: '报销审批', processTypeId: 3, description: '出差费用' })
  await edit.next()
  h.design({
    rule: [{ type: 'row', children: [{ type: 'input', field: 'amount', custom: { nested: 1 } }] }],
    option: { extension: { color: 'blue' } },
  })
  await edit.next()
  const upload = edit.selectZip(zip())
  expect(edit.state.canSave).toBe(false)
  h.uploads[0].resolve(uploaded())
  await upload
  expect(edit.state.canSave).toBe(true)
  const saving = edit.save()
  await flushPromises()
  await edit.save()
  edit.updateBasic({ name: '不应提交' })
  await edit.selectZip(zip('other.zip'))
  expect(h.saves).toHaveLength(1)
  expect(h.saves[0].config.data).toMatchObject({
    name: '报销审批',
    processTypeId: 3,
    description: '出差费用',
    status: 0,
    processDefinitionPath: 'opaque/new.zip',
    processDefinitionKey: 'new',
    formProps:
      '[{"type":"row","children":[{"type":"input","field":"amount","custom":{"nested":1}}]}]',
    formOptions: '{"extension":{"color":"blue"}}',
  })
  h.saves[0].resolve({ code: 200, message: '保存成功', data: null })
  await saving
  expect(h.navigation).toEqual(['/system/processSet/processTemplate'])
  expect(h.notices).toEqual(['保存成功，审批模板仍为未发布'])
})

it('读取目标未发布模板才允许编辑，三步往返和合法空设计不丢失原内容', async () => {
  const h = harness()
  const edit = h.module.open('A')
  expect(edit.state.canEdit).toBe(false)
  await flushPromises()
  expect(h.reads).toHaveLength(1)
  h.reads[0].resolve({ code: 200, data: record() })
  await edit.ready
  expect(edit.state.form.name).toBe('请假审批')
  expect(edit.state.zip).toEqual({
    name: 'opaque/original.zip',
    path: 'opaque/original.zip',
    key: 'original',
  })
  await edit.next()
  h.design({ rule: [], option: {} })
  edit.previous()
  edit.updateBasic({ description: '保留描述' })
  await edit.next()
  await edit.next()
  const saving = edit.save()
  await flushPromises()
  expect(h.saves[0].config).toMatchObject({
    method: 'put',
    data: { id: 'A', formProps: '[]', formOptions: '{}', description: '保留描述' },
  })
  h.saves[0].resolve({ code: 200, data: null })
  await saving
})

it('设计器不可读取时明确暂停保存，重试读取当前设计后保留修改', async () => {
  const h = harness()
  const edit = h.module.open()
  await edit.ready
  edit.updateBasic({ name: '有设计的审批', processTypeId: 3 })
  await edit.next()
  h.design({ rule: [{ type: 'input', field: 'reason', title: '原因' }], option: {} })
  h.designerFailure('设计器暂不可用')
  await edit.next()
  expect(edit.state.phase).toBe('design-error')
  expect(edit.state.canSave).toBe(false)
  expect(h.saves).toHaveLength(0)
  h.designerFailure('')
  await edit.retryDesigner()
  await edit.next()
  expect(edit.state.step).toBe(2)
  expect(edit.state.form.formProps).toBe('[{"type":"input","field":"reason","title":"原因"}]')
})

it.each([
  ['空记录', null],
  ['错误名称类型', record('A', { name: 123 })],
  ['错误设计类型', record('A', { formProps: false })],
  ['错误类型 ID', record('A', { processTypeId: { id: 3 } })],
  ['未知发布状态', record('A', { status: undefined })],
  ['错误目标', record('B')],
  ['损坏 JSON', record('A', { formProps: '{broken' })],
  ['不兼容规则', record('A', { formProps: '{}' })],
  ['不兼容选项', record('A', { formOptions: '[]' })],
])('%s 明确阻止编辑保存，不降级为新建，并允许重新读取原目标', async (_, data) => {
  const h = harness()
  const edit = h.module.open('A')
  await flushPromises()
  h.reads[0].resolve({ code: 200, data })
  await edit.ready
  expect(edit.state.phase).toBe('load-error')
  expect(edit.state.error).not.toBe('')
  edit.updateBasic({ name: '意外新建' })
  await edit.save()
  expect(h.saves).toHaveLength(0)
  const retry = edit.reload()
  await flushPromises()
  expect(h.reads[1].config.url).toBe('/admin/process/processTemplate/get/A')
  h.reads[1].resolve({ code: 200, data: record() })
  await retry
  expect(edit.state.phase).toBe('ready')
  expect(edit.state.form.name).toBe('请假审批')
})

it('已发布模板保持只读且可返回，读取网络错误可以重试', async () => {
  const h = harness()
  const edit = h.module.open('A')
  await flushPromises()
  h.reads[0].reject(new Error('读取断网'))
  await edit.ready
  expect(edit.state.error).toBe('读取断网')
  const retry = edit.reload()
  await flushPromises()
  h.reads[1].resolve({ code: 200, data: record('A', { status: 1 }) })
  await retry
  expect(edit.state.phase).toBe('readonly')
  expect(edit.state.canEdit).toBe(false)
  await edit.selectZip(zip())
  await edit.save()
  expect(h.uploads).toHaveLength(0)
  expect(h.saves).toHaveLength(0)
})

it('上传失败不静默保存旧引用；B 成功、C 失败后只能明确恢复 B，移除也须明确恢复', async () => {
  const h = harness()
  const edit = h.module.open('A')
  await flushPromises()
  h.reads[0].resolve({ code: 200, data: record() })
  await edit.ready
  edit.updateBasic({ description: '保留修改' })
  const b = edit.selectZip(zip('B.zip'))
  await edit.save()
  expect(h.saves).toHaveLength(0)
  h.uploads[0].resolve(uploaded('B'))
  await b
  const c = edit.selectZip(zip('C.zip'))
  h.uploads[1].reject(new Error('上传断网'))
  await c
  expect(edit.state.form.description).toBe('保留修改')
  expect(edit.state.zipStatus).toBe('error')
  expect(edit.state.canSave).toBe(false)
  expect(edit.state.canRestoreZip).toBe(true)
  edit.restoreZip()
  expect(edit.state.zip).toEqual({ name: 'B.zip', path: 'opaque/B.zip', key: 'B' })
  expect(edit.state.canSave).toBe(true)
  edit.removeZip()
  expect(edit.state.canSave).toBe(false)
  edit.restoreZip()
  expect(edit.state.zip.key).toBe('B')
})

it('新建无旧包时失败和不完整上传响应都不能保存，重试成功才恢复资格', async () => {
  const h = harness()
  const edit = h.module.open()
  await edit.ready
  edit.updateBasic({ name: '新建审批', processTypeId: 3 })
  const first = edit.selectZip(zip())
  h.uploads[0].resolve({ code: 200, data: { processDefinitionPath: 'opaque/missing-key.zip' } })
  await first
  expect(edit.state.canSave).toBe(false)
  expect(edit.state.canRestoreZip).toBe(false)
  expect(edit.state.uploadError).toContain('不完整')
  const retry = edit.retryUpload()
  h.uploads[1].resolve(uploaded())
  await retry
  expect(edit.state.canSave).toBe(true)
})

it.each(['success', 'failure'])(
  '先选 A 再选 B，A 的迟到 %s 不改变当前 B 上传状态和引用',
  async (outcome) => {
    const h = harness()
    const edit = h.module.open()
    await edit.ready
    const a = edit.selectZip(zip('A.zip'))
    const b = edit.selectZip(zip('B.zip'))
    if (outcome === 'success') h.uploads[0].resolve(uploaded('A'))
    else h.uploads[0].reject(new Error('旧错误'))
    await a
    expect(edit.state.zipStatus).toBe('uploading')
    expect(edit.state.uploadError).toBe('')
    h.uploads[1].resolve(uploaded('B'))
    await b
    expect(edit.state.zip.key).toBe('B')
    expect(h.notices).toEqual([])
  },
)

it('保存超时保留固定快照且不能再保存，另开列表保留当前编辑，结束确认说明风险并仅清理本地内容', async () => {
  const h = harness()
  const edit = h.module.open('A')
  await flushPromises()
  h.reads[0].resolve({ code: 200, data: record() })
  await edit.ready
  const saving = edit.save()
  await flushPromises()
  h.saves[0].reject(new Error('请求超时'))
  await saving
  expect(edit.state.phase).toBe('uncertain')
  expect(edit.state.snapshot).toMatchObject({
    id: 'A',
    name: '请假审批',
    processDefinitionPath: 'opaque/original.zip',
  })
  edit.updateBasic({ name: '重复覆盖' })
  await edit.save()
  await edit.selectZip(zip())
  expect(h.saves).toHaveLength(1)
  expect(h.uploads).toHaveLength(0)
  edit.openList()
  expect(h.lists).toEqual(['/system/processSet/processTemplate'])
  expect(edit.state.phase).toBe('uncertain')
  h.confirm(false)
  await edit.endEditing()
  expect(edit.state.snapshot.name).toBe('请假审批')
  expect(h.confirmations[0]).toContain('仍可能生效')
  expect(h.confirmations[0]).toContain('重复创建或覆盖')
  h.confirm(true)
  await edit.endEditing()
  expect(edit.state.snapshot).toBeNull()
  expect(edit.state.phase).toBe('closed')
  expect(h.navigation).toEqual(['/system/processSet/processTemplate'])
  expect(h.notices).toEqual([])
})

it('普通离开先确认，取消保留修改；保存中确认离开不撤销原请求，迟到结果没有提示导航', async () => {
  const h = harness()
  const edit = h.module.open('A')
  await flushPromises()
  h.reads[0].resolve({ code: 200, data: record() })
  await edit.ready
  edit.updateBasic({ description: '尚未保存' })
  h.confirm(false)
  expect(await edit.requestLeave()).toBe(false)
  expect(edit.state.form.description).toBe('尚未保存')
  expect(edit.state.canEdit).toBe(true)
  const saving = edit.save()
  await flushPromises()
  h.confirm(true)
  expect(await edit.requestLeave()).toBe(true)
  expect(h.confirmations[1]).toContain('离开不撤销')
  h.saves[0].resolve({ code: 200, data: null })
  await saving
  expect(h.navigation).toEqual([])
  expect(h.notices).toEqual([])
})

it('仅设计器中的尚未保存修改也需要离开确认；dispose 只清理本地内容', async () => {
  const h = harness()
  const edit = h.module.open()
  await edit.ready
  h.design({ rule: [{ type: 'input', field: 'new' }], option: {} })
  h.confirm(false)
  expect(await edit.requestLeave()).toBe(false)
  expect(h.confirmations).toHaveLength(1)
  edit.dispose()
  expect(edit.state.canEdit).toBe(false)
  expect(edit.state.form.formProps).toBe('')
})

it('身份替换抢在进行中的确认之前，关闭确认且迟到结果不再导航', async () => {
  const h = harness()
  const confirming = deferred()
  h.options.confirm = (message) => {
    h.confirmations.push(message)
    return confirming.promise
  }
  const edit = h.module.open('A')
  await flushPromises()
  h.reads[0].resolve({ code: 200, data: record() })
  await edit.ready
  edit.updateBasic({ description: '尚未保存' })
  const leaving = edit.back()
  await flushPromises()
  expect(h.confirmations).toHaveLength(1)
  h.identity()
  await edit.environmentChanged()
  expect(h.dismissals).toEqual(['dismiss'])
  expect(edit.state.phase).toBe('closed')
  expect(h.replacements).toEqual([getDefaultPostLoginRoute()])
  confirming.resolve(false)
  await leaving
  expect(h.replacements).toEqual([getDefaultPostLoginRoute()])
  expect(h.navigation).toEqual([])
})

it('身份被替换时释放并落到默认落点；进入结果清空时只释放不导航', async () => {
  const first = harness()
  const edit = first.module.open('A')
  await flushPromises()
  first.reads[0].resolve({ code: 200, data: record() })
  await edit.ready
  edit.updateBasic({ description: '尚未保存' })
  first.identity()
  await edit.environmentChanged()
  expect(edit.state.phase).toBe('closed')
  expect(first.confirmations).toEqual([])
  expect(first.replacements).toEqual([getDefaultPostLoginRoute()])

  const second = harness()
  const other = second.module.open('A')
  await flushPromises()
  second.reads[0].resolve({ code: 200, data: record() })
  await other.ready
  other.updateBasic({ description: '尚未保存' })
  second.entry('')
  await other.environmentChanged()
  expect(other.state.phase).toBe('closed')
  expect(second.replacements).toEqual([])
})

it('身份未变时环境变化不打断编辑', async () => {
  const h = harness()
  const edit = h.module.open('A')
  await flushPromises()
  h.reads[0].resolve({ code: 200, data: record() })
  await edit.ready
  edit.updateBasic({ description: '尚未保存' })
  await edit.environmentChanged()
  expect(edit.state.phase).toBe('ready')
  expect(edit.state.form.description).toBe('尚未保存')
  expect(h.confirmations).toEqual([])
  expect(h.replacements).toEqual([])
  expect(h.dismissals).toEqual([])
})

it('保存中身份被替换时释放不确认，迟到成功不提示也不抢导航', async () => {
  const h = harness()
  const edit = h.module.open('A')
  await flushPromises()
  h.reads[0].resolve({ code: 200, data: record() })
  await edit.ready
  const saving = edit.save()
  await flushPromises()
  h.identity()
  await edit.environmentChanged()
  expect(edit.state.phase).toBe('closed')
  expect(h.confirmations).toEqual([])
  h.saves[0].resolve({ code: 200, data: null })
  await saving
  expect(h.notices).toEqual([])
  expect(h.navigation).toEqual([])
  expect(h.replacements).toEqual([getDefaultPostLoginRoute()])
})

it.each([208, 209])('明确拒绝 %s 恢复正常重试；重发后断网仍待确认', async (code) => {
  const { BusinessRequestFailure } = await import('@/request/request-failure')
  const h = harness()
  const edit = h.module.open('A')
  await flushPromises()
  h.reads[0].resolve({ code: 200, data: record() })
  await edit.ready
  const first = edit.save()
  await flushPromises()
  h.saves[0].reject(new BusinessRequestFailure('retryable', '认证恢复暂不可用，尚未提交', code))
  await first
  expect(edit.state.phase).toBe('ready')
  expect(edit.state.canSave).toBe(true)
  expect(edit.state.snapshot).toBeNull()
  const retry = edit.save()
  await flushPromises()
  h.saves[1].reject(new Error('重发后断网'))
  await retry
  expect(edit.state.phase).toBe('uncertain')
  expect(edit.state.canSave).toBe(false)
})

it.each(['success', 'failure'])(
  '切换模板和同地址重开后旧读取 %s 不改变当前内容、提示或加载状态',
  async (outcome) => {
    const h = harness()
    const old = h.module.open('A')
    await flushPromises()
    const next = h.module.open('A')
    await flushPromises()
    if (outcome === 'success')
      h.reads[0].resolve({ code: 200, data: record('A', { name: '旧内容' }) })
    else h.reads[0].reject(new Error('旧错误'))
    await old.ready
    expect(next.state.phase).toBe('loading')
    expect(next.state.form.name).toBe('')
    h.reads[1].resolve({ code: 200, data: record('A', { name: '本次内容' }) })
    await next.ready
    expect(next.state.form.name).toBe('本次内容')
    expect(next.state.error).toBe('')
    expect(h.notices).toEqual([])
  },
)

it.each(['success', 'failure'])(
  '换身份后旧上传 %s 不改变新编辑，原上传请求带原归属且凭据由请求层逐次提供',
  async (outcome) => {
    const h = harness()
    const old = h.module.open()
    await old.ready
    const upload = old.selectZip(zip())
    h.identity()
    const next = h.module.open()
    await next.ready
    if (outcome === 'success') h.uploads[0].resolve(uploaded())
    else h.uploads[0].reject(new Error('旧身份错误'))
    await upload
    expect(h.uploads[0].scope.context.entryId).toBe('entry-1')
    expect(h.uploads[0].isRelevant()).toBe(false)
    expect(h.uploads[0].config.headers).toBeUndefined()
    expect(h.uploads[0].config.data.get('file').name).toBe('new.zip')
    expect(next.state.zipStatus).toBe('empty')
    expect(next.state.uploadError).toBe('')
    expect(h.notices).toEqual([])
  },
)

it('上传被新选择替代后其旧认证错误也无呈现资格', async () => {
  const h = harness()
  const edit = h.module.open()
  await edit.ready
  const old = edit.selectZip(zip('old.zip'))
  const fresh = edit.selectZip(zip('fresh.zip'))
  expect(h.uploads[0].isRelevant()).toBe(false)
  expect(h.uploads[1].isRelevant()).toBe(true)
  h.uploads[0].reject(new Error('旧认证错误'))
  h.uploads[1].resolve(uploaded('fresh'))
  await Promise.all([old, fresh])
  expect(edit.state.zip.key).toBe('fresh')
})

it('保存与待确认不能被重读或设计重试解锁；提交只包含现有写入字段', async () => {
  const h = harness()
  const edit = h.module.open('A')
  await flushPromises()
  h.reads[0].resolve({
    code: 200,
    data: record('A', {
      createTime: '2026-10-19',
      processTypeName: '请假',
      availableForInitiation: true,
    }),
  })
  await edit.ready
  const sending = edit.save()
  await flushPromises()
  void edit.reload()
  void edit.retryDesigner()
  expect(edit.state.phase).toBe('saving')
  expect(h.reads).toHaveLength(1)
  expect(h.saves[0].config.data).not.toHaveProperty('createTime')
  expect(h.saves[0].config.data).not.toHaveProperty('processTypeName')
  expect(h.saves[0].config.data).not.toHaveProperty('availableForInitiation')
  h.saves[0].reject(new Error('断网'))
  await sending
  void edit.reload()
  void edit.retryDesigner()
  expect(edit.state.phase).toBe('uncertain')
  expect(h.reads).toHaveLength(1)
})

async function transportHarness(respond) {
  const { default: axios } = await import('axios')
  const { createRecognition } = await import('@/recognition')
  const { createRequestScope } = await import('@/request/request-scope')
  const { createRequestClient } = await import('@/request/request')
  const fixture = harness()
  const calls = [],
    globalNotices = [],
    authNavigation = []
  let tokens = { token: 'access-1', refreshToken: 'refresh-1' }
  let entry = 'login-1'
  const recognition = createRecognition({
    credentials: () => tokens,
    entryId: () => entry,
    writeTokens: (next) => {
      tokens = next
    },
    clearUser: () => {
      entry = ''
      tokens = { token: '' }
    },
    hasUser: () => true,
    writeUser: () => {},
    recovery: () => ({ status: 'idle', target: '' }),
    showRecovery: () => {},
    loadUser: async () => ({ name: 'Alice' }),
    currentPath: () => '/system/processSet/templateSet?id=A',
    goTo: (path) => authNavigation.push(path),
    goToLogin: (path) => authNavigation.push(path),
    notify: (message) => globalNotices.push(message),
  })
  const original = axios.defaults.adapter
  axios.defaults.adapter = async (config) => {
    calls.push({ ...config, dispatchedToken: config.headers.get('token') })
    const data = config.url.endsWith('/findAll')
      ? { code: 200, data: [{ id: 3, name: '请假' }] }
      : config.url.includes('/get/')
        ? { code: 200, data: record() }
        : config.url.includes('/approverRules/') && config.method === 'get'
          ? { code: 200, data: { userTasks: [], rules: [] } }
          : config.url === '/admin/process/processTemplate/ruleTargets'
            ? { code: 200, data: { roles: [], posts: [] } }
            : await respond(config, calls)
    return { data, config, status: 200, statusText: 'OK', headers: {} }
  }
  const client = createRequestClient(recognition)
  const module = createTemplateEditor({
    ...fixture.options,
    openScope: () => createRequestScope(recognition, client, { notifyNormalErrors: false }),
    entryId: () => recognition.capture().entryId,
  })
  return {
    ...fixture,
    module,
    calls,
    globalNotices,
    authNavigation,
    restore: () => {
      axios.defaults.adapter = original
    },
    rotate: () => {
      tokens = { token: 'rotated', refreshToken: 'refresh-1' }
    },
  }
}

it('上传和保存通过正式编辑 interface 复用逐次凭据及一次认证恢复，上传不创建或发布模板', async () => {
  const h = await transportHarness(async (config, calls) => {
    if (config.url.endsWith('/refresh'))
      return { code: 200, data: { token: 'refreshed', refreshToken: 'refresh-2' } }
    if (config.url.endsWith('/uploadProcessDefinition')) {
      return calls.filter((call) => call.url.endsWith('/uploadProcessDefinition')).length === 1
        ? { code: 208, message: '已过期' }
        : uploaded('B')
    }
    return { code: 200, data: null }
  })
  try {
    const edit = h.module.open('A')
    await edit.ready
    h.rotate()
    await edit.selectZip(zip('B.zip'))
    expect(
      h.calls
        .filter((call) => call.url.endsWith('/uploadProcessDefinition'))
        .map((call) => call.dispatchedToken),
    ).toEqual(['rotated', 'refreshed'])
    expect(h.calls.filter((call) => call.url.endsWith('/refresh'))).toHaveLength(1)
    expect(
      h.calls.filter(
        (call) =>
          call.url.endsWith('/save') ||
          call.url.endsWith('/update') ||
          call.url.includes('/publish/'),
      ),
    ).toHaveLength(0)
    await edit.save()
    expect(h.calls.find((call) => call.url.endsWith('/update')).dispatchedToken).toBe('refreshed')
    expect(h.globalNotices).toEqual([])
  } finally {
    h.restore()
  }
})

it('明确认证拒绝后重发保存再断网，仍保留待确认快照且不第三次自动提交', async () => {
  const { AxiosError } = await import('axios')
  const h = await transportHarness(async (config, calls) => {
    if (config.url.endsWith('/refresh'))
      return { code: 200, data: { token: 'refreshed', refreshToken: 'refresh-2' } }
    if (calls.filter((call) => call.url.endsWith('/update')).length === 1)
      return { code: 208, message: '已过期' }
    throw new AxiosError('重发后断网', 'ERR_NETWORK', config)
  })
  try {
    const edit = h.module.open('A')
    await edit.ready
    await edit.save()
    expect(edit.state.phase).toBe('uncertain')
    expect(edit.state.snapshot.name).toBe('请假审批')
    await edit.save()
    expect(h.calls.filter((call) => call.url.endsWith('/update'))).toHaveLength(2)
    expect(h.globalNotices).toEqual([])
  } finally {
    h.restore()
  }
})

it.each([201, 403])('保存明确拒绝 %s 可立即重试且无全局普通错误', async (code) => {
  const h = await transportHarness(async () => ({ code, message: '本次保存未获接受' }))
  try {
    const edit = h.module.open('A')
    await edit.ready
    await edit.save()
    expect(edit.state.phase).toBe('ready')
    expect(edit.state.canSave).toBe(true)
    expect(edit.state.snapshot).toBeNull()
    expect(edit.state.error).toBe('本次保存未获接受')
    expect(h.calls.filter((call) => call.url.endsWith('/update'))).toHaveLength(1)
    expect(h.globalNotices).toEqual([])
  } finally {
    h.restore()
  }
})

it('保存被 205 明确拒绝时可立即重试，并把没有操作权限提示一次', async () => {
  const h = await transportHarness(async () => ({ code: 205, message: '没有操作权限' }))
  try {
    const edit = h.module.open('A')
    await edit.ready
    await edit.save()
    expect(edit.state.phase).toBe('ready')
    expect(edit.state.canSave).toBe(true)
    expect(edit.state.snapshot).toBeNull()
    expect(edit.state.error).toBe('没有操作权限')
    expect(h.calls.filter((call) => call.url.endsWith('/update'))).toHaveLength(1)
    expect(h.globalNotices).toEqual(['没有操作权限'])
  } finally {
    h.restore()
  }
})

it.each([599, 'malformed'])(
  '保存无法证明未写入的响应 %s 保留待确认且无全局普通错误',
  async (code) => {
    const h = await transportHarness(async () =>
      code === 'malformed' ? '<html>bad response</html>' : { code, message: '无法确认写入结果' },
    )
    try {
      const edit = h.module.open('A')
      await edit.ready
      await edit.save()
      expect(edit.state.phase).toBe('uncertain')
      expect(edit.state.canEdit).toBe(false)
      expect(h.calls.filter((call) => call.url.endsWith('/update'))).toHaveLength(1)
      expect(h.globalNotices).toEqual([])
    } finally {
      h.restore()
    }
  },
)

it.each(['force', 'reopen', 'identity'])(
  '普通返回确认中 %s 后结束旧确认不能抢导航',
  async (change) => {
    const h = harness()
    const confirming = deferred()
    h.options.confirm = () => confirming.promise
    const edit = h.module.open()
    await edit.ready
    edit.updateBasic({ name: '未保存', processTypeId: 3 })
    const leaving = edit.back()
    await flushPromises()
    if (change === 'force') edit.dispose()
    if (change === 'reopen') {
      const next = h.module.open()
      await next.ready
    }
    if (change === 'identity') h.identity()
    confirming.resolve(false)
    await leaving
    expect(h.navigation).toEqual([])
  },
)

it.each(['force', 'reopen', 'identity'])(
  '结束待确认的确认中 %s 后迟到确认不能导航',
  async (change) => {
    const h = harness()
    const confirming = deferred()
    h.options.confirm = () => confirming.promise
    const edit = h.module.open('A')
    await flushPromises()
    h.reads[0].resolve({ code: 200, data: record() })
    await edit.ready
    const saving = edit.save()
    await flushPromises()
    h.saves[0].reject(new Error('断网'))
    await saving
    const ending = edit.endEditing()
    if (change === 'force') edit.dispose()
    if (change === 'reopen') {
      const next = h.module.open()
      await next.ready
    }
    if (change === 'identity') h.identity()
    confirming.resolve(true)
    await ending
    expect(h.navigation).toEqual([])
  },
)

it('保存中与待确认可往返核对完整提交快照，查看不读取设计器也不解锁任何写入', async () => {
  const h = harness()
  const edit = h.module.open('A')
  await flushPromises()
  h.reads[0].resolve({ code: 200, data: record() })
  await edit.ready
  edit.updateBasic({ name: '本次提交名称', processTypeId: 3, description: '本次提交说明' })
  await edit.next()
  h.design({
    rule: [{ type: 'input', field: 'reason', title: '核对原因' }],
    option: { form: { labelWidth: '140px' }, custom: { enabled: true } },
  })
  await edit.next()
  const saving = edit.save()
  await flushPromises()
  const snapshot = JSON.parse(JSON.stringify(edit.state.snapshot))
  h.designerFailure('查看期间不应再次读取设计器')
  expect(edit.state.canNavigateSteps).toBe(true)
  edit.previous()
  expect(edit.state.step).toBe(1)
  expect(edit.state.inspectionDesign).toEqual({
    rule: [{ type: 'input', field: 'reason', title: '核对原因' }],
    option: {
      form: { labelWidth: '140px', disabled: true },
      custom: { enabled: true },
      submitBtn: false,
      resetBtn: false,
    },
  })
  const rendererCopy = edit.state.inspectionDesign
  rendererCopy.rule[0].title = '渲染器内部改动'
  expect(rendererCopy.rule[0].title).toBe('渲染器内部改动')
  expect(edit.state.inspectionDesign.rule[0].title).toBe('核对原因')
  edit.previous()
  expect(edit.state.step).toBe(0)
  expect(edit.state.form).toMatchObject({
    name: '本次提交名称',
    processTypeId: 3,
    description: '本次提交说明',
  })
  edit.updateBasic({ name: '不能覆盖快照' })
  await edit.selectZip(zip('cannot-change.zip'))
  edit.removeZip()
  edit.restoreZip()
  await edit.next()
  await edit.next()
  await edit.next()
  expect(edit.state.step).toBe(2)
  expect(h.uploads).toHaveLength(0)
  expect(h.saves).toHaveLength(1)
  expect(edit.state.snapshot).toEqual(snapshot)
  h.saves[0].reject(new Error('断网'))
  await saving
  edit.previous()
  expect(edit.state.step).toBe(1)
  expect(edit.state.phase).toBe('uncertain')
  expect(edit.state.canEdit).toBe(false)
  expect(edit.state.canSave).toBe(false)
  expect(edit.state.snapshot).toEqual(snapshot)
  expect(edit.state.inspectionDesign.rule[0].title).toBe('核对原因')
})

const approverConfig = (overrides = {}) => ({
  code: 200,
  data: {
    userTasks: [
      { key: 'approve', name: '部门审批', assignee: 'lisi' },
      { key: 'manager', name: '经理审批', assignee: '' },
    ],
    rules: [{ id: 1, taskDefinitionKey: 'approve', sourceType: 'ROLE', targetId: 7 }],
    ...overrides,
  },
})

it('编辑既有模板时一次读取节点清单与当前规则，未配置节点回落 BPMN 写死，配置读取不阻塞模板编辑', async () => {
  const h = harness()
  const edit = h.module.open('A')
  await flushPromises()
  h.reads[0].resolve({ code: 200, data: record() })
  await edit.ready
  expect(edit.state.phase).toBe('ready')
  expect(edit.state.approverStatus).toBe('loading')
  h.approverReads[0].resolve(approverConfig())
  await flushPromises()
  expect(edit.state.approverStatus).toBe('ready')
  expect(edit.state.approvers).toEqual([
    { key: 'approve', name: '部门审批', assignee: 'lisi', sourceType: 'ROLE', targetId: 7 },
    { key: 'manager', name: '经理审批', assignee: '', sourceType: '', targetId: '' },
  ])
  // 配置读取失败不阻塞模板本身的编辑（phase 仍是 ready）。
  expect(edit.state.phase).toBe('ready')
})

it('节点或规则数据损坏时明确报错可重试，规则目标选项读取失败只降级目标选择', async () => {
  const h = harness()
  const edit = h.module.open('A')
  await flushPromises()
  h.reads[0].resolve({ code: 200, data: record() })
  await edit.ready
  // 目标选项走 process 专用只读端点 ruleTargets（#286）：一次请求带角色与岗位两组。
  h.optionReads[0].resolve({
    code: 200,
    data: { roles: [{ id: 5, name: '审批主管' }], posts: [{ id: 2, name: '经理' }] },
  })
  h.approverReads[0].resolve({ code: 200, data: { userTasks: 'broken', rules: [] } })
  await flushPromises()
  expect(edit.state.approverStatus).toBe('error')
  expect(edit.state.approverError).not.toBe('')
  expect(edit.state.approverRoles).toEqual([{ id: 5, name: '审批主管' }])
  expect(edit.state.approverPosts).toEqual([{ id: 2, name: '经理' }])
  expect(h.optionReads[0].config.url).toBe('/admin/process/processTemplate/ruleTargets')

  // 选项读取失败只降级目标下拉，不挡配置读取的可重试。
  const retry = edit.retryApprovers()
  h.optionReads[1].reject(new Error('选项读取失败'))
  h.approverReads[1].resolve(approverConfig())
  await retry
  await flushPromises()
  expect(edit.state.approverStatus).toBe('ready')
  expect(edit.state.approvers).toEqual([
    { key: 'approve', name: '部门审批', assignee: 'lisi', sourceType: 'ROLE', targetId: 7 },
    { key: 'manager', name: '经理审批', assignee: '', sourceType: '', targetId: '' },
  ])
  expect(edit.state.approverRoles).toEqual([])
  expect(edit.state.approverPosts).toEqual([])
  expect(edit.state.approverOptionsError).not.toBe('')
  expect(h.approverReads[1].config.url).toBe('/admin/process/processTemplate/approverRules/A')
})

it('保存走整体替换 PUT approverRules：部门负责人不带目标，角色岗位必须选目标，保存成功清脏', async () => {
  const h = harness()
  const edit = h.module.open('A')
  await flushPromises()
  h.reads[0].resolve({ code: 200, data: record() })
  await edit.ready
  h.approverReads[0].resolve(approverConfig())
  await flushPromises()

  edit.updateApprover('manager', { sourceType: 'ROLE' })
  expect(edit.state.approverDirty).toBe(true)
  expect(edit.state.canSaveApprovers).toBe(false)
  await edit.saveApprovers()
  expect(h.approverSaves).toHaveLength(0)

  edit.updateApprover('manager', { targetId: 7 })
  edit.updateApprover('approve', { sourceType: 'DEPT_LEADER' })
  expect(edit.state.canSaveApprovers).toBe(true)
  const saving = edit.saveApprovers()
  h.approverSaves[0].resolve({ code: 200, data: null })
  await saving
  expect(h.approverSaves[0].config).toMatchObject({
    method: 'put',
    url: '/admin/process/processTemplate/approverRules/A',
    data: [
      { taskDefinitionKey: 'approve', sourceType: 'DEPT_LEADER' },
      { taskDefinitionKey: 'manager', sourceType: 'ROLE', targetId: 7 },
    ],
  })
  expect(edit.state.approverDirty).toBe(false)
  expect(edit.state.approverSaveError).toBe('')
  expect(h.notices).toContain('审批人配置已保存')
})

it('保存失败文案上屏可重试；来源切回 BPMN 写死的节点不再进载荷', async () => {
  const h = harness()
  const edit = h.module.open('A')
  await flushPromises()
  h.reads[0].resolve({ code: 200, data: record() })
  await edit.ready
  h.approverReads[0].resolve(approverConfig())
  await flushPromises()
  const saving = edit.saveApprovers()
  h.approverSaves[0].reject(new Error('断网'))
  await saving
  expect(edit.state.approverSaveError).toBe('断网')
  expect(edit.state.approverSaving).toBe(false)

  edit.updateApprover('approve', { sourceType: 'BPMN_ASSIGNEE' })
  edit.updateApprover('manager', { sourceType: 'ROLE', targetId: 7 })
  const retry = edit.saveApprovers()
  h.approverSaves[1].resolve({ code: 200, data: null })
  await retry
  expect(h.approverSaves[1].config.data).toEqual([
    { taskDefinitionKey: 'manager', sourceType: 'ROLE', targetId: 7 },
  ])
})

it('新建模板没有 id：审批人配置不可编辑不可保存，提交保存不受影响', async () => {
  const h = harness()
  const edit = h.module.open()
  await edit.ready
  expect(edit.state.approverStatus).toBe('idle')
  edit.updateApprover('approve', { sourceType: 'DEPT_LEADER' })
  expect(edit.state.approvers).toEqual([])
  expect(edit.state.approverDirty).toBe(false)
  expect(edit.state.canSaveApprovers).toBe(false)
  await edit.saveApprovers()
  expect(h.approverSaves).toHaveLength(0)
})

it('上传新 zip 后按其节点清单重建行且同 key 选择保留，陈旧规则有警告；解析不出 BPMN 明示不可发布', async () => {
  const h = harness()
  const edit = h.module.open('A')
  await flushPromises()
  h.reads[0].resolve({ code: 200, data: record() })
  await edit.ready
  h.approverReads[0].resolve(approverConfig())
  await flushPromises()
  expect(edit.state.zipUnparsable).toBe(false)

  const upload = edit.selectZip(zip())
  h.uploads[0].resolve({
    code: 200,
    data: {
      processDefinitionPath: 'opaque/new.zip',
      processDefinitionKey: 'new',
      userTasks: [{ key: 'manager', name: '经理审批', assignee: '' }],
    },
  })
  await upload
  expect(edit.state.approvers).toEqual([
    { key: 'manager', name: '经理审批', assignee: '', sourceType: '', targetId: '' },
  ])
  expect(edit.state.staleApproverKeys).toEqual(['approve'])

  const other = edit.selectZip(zip('other.zip'))
  h.uploads[1].resolve({
    code: 200,
    data: {
      processDefinitionPath: 'opaque/other.zip',
      processDefinitionKey: 'other',
      userTasks: [],
    },
  })
  await other
  expect(edit.state.zipUnparsable).toBe(true)
  expect(edit.state.canSaveApprovers).toBe(false)
})

describe('template formRules', () => {
  it('mirrors the server 必填 messages for the basic step', () => {
    const rules = formRules()
    expect(Object.keys(rules).sort()).toEqual(['name', 'processTypeId'])
    expect(emptyMessageOf(rules.name[0])).toBe('审批模板名称不能为空')
    expect(emptyMessageOf(rules.processTypeId[0])).toBe('审批类型不能为空')
  })
})
