import { expect, it } from 'vitest'
import { createApprovalDetail } from '@/work/approval-detail'
import { workListPerspective } from '@/work/work-list'
import { XSS_PAYLOAD } from '../support/harness'

function deferred() {
  let resolve, reject
  const promise = new Promise((yes, no) => {
    resolve = yes
    reject = no
  })
  return { promise, resolve, reject }
}
const payload = (id, extra = {}) => ({
  process: {
    id,
    title: `审批 ${id}`,
    status: 1,
    formValues: { formShowData: { 原因: '出差' } },
    description: '等待李四审批',
  },
  isApprove: true,
  processRecordList: [{ operateUser: '张三', description: '发起申请' }],
  ...extra,
})
const target = (id = 'A', taskId = 'task-A', kind = 'pending') => ({
  processId: id,
  taskId,
  perspective: workListPerspective(kind),
})
let entrySequence = 0
function harness(overrides = {}) {
  const reads = [],
    submissions = [],
    navigation = [],
    notifications = []
  const records = new Map()
  let entry = `login-${++entrySequence}`
  const adapter = {
    entryId: () => entry,
    read: (id) => {
      const request = deferred()
      reads.push({ id, ...request })
      return request.promise
    },
    submit: (request) => {
      const result = deferred()
      submissions.push({ request, ...result })
      return result.promise
    },
    loadRecords: (key) => JSON.parse(records.get(key) || '[]'),
    saveRecords: (key, value) => records.set(key, JSON.stringify(value)),
    listAttachments: async () => ({ attachments: [], canUpload: true }),
    upload: async () => ({ id: 'file-1', fileName: '说明.txt', sizeBytes: 1 }),
    download: async () => {},
    navigate: (path) => navigation.push(path),
    notify: (message) => notifications.push(message),
    ...overrides,
  }
  return {
    module: createApprovalDetail(adapter),
    adapter,
    reads,
    submissions,
    navigation,
    notifications,
    records,
    changeIdentity: () => {
      entry = `login-${++entrySequence}`
    },
  }
}

it('把表单值呈现为文本，数组按顿号连接，HTML 原样保留为字符', async () => {
  const h = harness()
  const opened = h.module.open(target())
  h.reads[0].resolve({
    process: {
      id: 'A',
      title: '审批 A',
      status: 1,
      formValues: {
        formShowData: {
          请假原因: XSS_PAYLOAD,
          同行人: ['张三', '李四'],
        },
      },
    },
    processRecordList: [],
  })
  await opened.ready
  expect(opened.state.detail.formFields).toEqual([
    { key: '请假原因', value: XSS_PAYLOAD },
    { key: '同行人', value: '张三、李四' },
  ])
})

it('读取 A 时打开 B，A 迟到的成功不改变 B 的内容和加载状态', async () => {
  const h = harness()
  const a = h.module.open(target())
  const b = h.module.open(target('B', 'task-B'))
  expect(b.state.detail).toBeNull()
  expect(b.state.canSubmit).toBe(false)
  h.reads[0].resolve(payload('A'))
  await a.ready
  expect(b.state.loading).toBe(true)
  expect(b.state.detail).toBeNull()
  h.reads[1].resolve(payload('B'))
  await b.ready
  expect(b.state.detail.title).toBe('审批 B')
  expect(b.state.canSubmit).toBe(true)
  expect(h.navigation).toEqual([])
})

it('刷新失败保留尚未更新的内容并阻止处理，重试成功恢复资格，无权读取清空内容', async () => {
  const h = harness()
  const opened = h.module.open(target())
  h.reads[0].resolve(payload('A'))
  await opened.ready
  const refresh = opened.reload()
  expect(opened.state.canSubmit).toBe(false)
  h.reads[1].reject(new Error('网络故障'))
  await refresh
  expect(opened.state.detail.title).toBe('审批 A')
  expect(opened.state.stale).toBe(true)
  expect(opened.state.error).toBe('网络故障')
  const retry = opened.reload()
  h.reads[2].resolve(payload('A'))
  await retry
  expect(opened.state.stale).toBe(false)
  expect(opened.state.canSubmit).toBe(true)
  const denied = opened.reload()
  h.reads[3].reject(Object.assign(new Error('无权读取'), { kind: 'forbidden' }))
  await denied
  expect(opened.state.detail).toBeNull()
  expect(opened.state.canSubmit).toBe(false)
  opened.back()
  expect(h.navigation).toEqual(['/work/pending'])
})

it('一次处理固定任务和动作，重复或相反点击不追加提交，明确成功只导航原打开', async () => {
  const h = harness()
  const opened = h.module.open(target())
  h.reads[0].resolve(payload('A'))
  await opened.ready
  const submitted = opened.decide(1)
  await opened.decide(-1)
  expect(h.submissions.map((x) => x.request)).toEqual([
    { processId: 'A', taskId: 'task-A', status: 1 },
  ])
  expect(opened.state.canSubmit).toBe(false)
  h.submissions[0].resolve({ kind: 'success' })
  await submitted
  expect(h.navigation).toEqual(['/work/processed'])
})

it('通信故障保留原任务待确认记录，释放和刷新后恢复，不推断结果或自动重发；人工结束后须重新进入', async () => {
  const h = harness()
  const first = h.module.open(target())
  h.reads[0].resolve(payload('A'))
  await first.ready
  const sending = first.decide(-1)
  h.submissions[0].reject(new Error('断网'))
  await sending
  expect(first.state.recoveries).toHaveLength(1)
  expect(first.state.recoveries[0]).toMatchObject({ processId: 'A', taskId: 'task-A', status: -1 })
  first.release()
  const fresh = createApprovalDetail(h.adapter).open(target())
  h.reads[1].resolve(payload('A', { isApprove: false }))
  await fresh.ready
  expect(fresh.state.recoveries).toHaveLength(1)
  expect(fresh.state.canSubmit).toBe(false)
  const refresh = fresh.reload()
  h.reads[2].reject(Object.assign(new Error('无权读取'), { kind: 'forbidden' }))
  await refresh
  expect(fresh.state.recoveries).toHaveLength(1)
  expect(h.submissions).toHaveLength(1)
  fresh.endRecovery(fresh.state.recoveries[0].operationId)
  expect(fresh.state.recoveries).toEqual([])
  expect(fresh.state.canSubmit).toBe(false)
  expect(fresh.state.ended).toBe(true)
  const latest = h.module.open(target())
  h.reads[3].resolve(payload('A'))
  await latest.ready
  expect(latest.state.canSubmit).toBe(true)
})

it('整批附件补传含文件间隙阻止处理且不排队，处理在途与待确认暂停新补传而可下载', async () => {
  const uploads = [],
    downloaded = []
  const h = harness({
    upload: (processId, file) => {
      const upload = deferred()
      uploads.push({ processId, file, ...upload })
      return upload.promise
    },
    download: async (...args) => downloaded.push(args),
  })
  const opened = h.module.open(target())
  h.reads[0].resolve(payload('A'))
  await opened.ready
  await opened.reloadAttachments()
  const batch = opened.addAttachments([new File(['a'], 'a.txt'), new File(['b'], 'b.txt')])
  await opened.decide(1)
  expect(h.submissions).toHaveLength(0)
  expect(opened.state.attachmentBusy).toBe(true)
  uploads[0].resolve({ id: 1, fileName: 'a.txt', sizeBytes: 1 })
  await Promise.resolve()
  await opened.decide(-1)
  expect(h.submissions).toHaveLength(0)
  expect(opened.state.attachmentBusy).toBe(true)
  uploads[1].resolve({ id: 2, fileName: 'b.txt', sizeBytes: 1 })
  await batch
  expect(opened.state.canSubmit).toBe(true)
  expect(h.submissions).toHaveLength(0)
  const decision = opened.decide(1)
  await opened.addAttachments([new File(['c'], 'c.txt')])
  expect(uploads).toHaveLength(2)
  h.submissions[0].resolve({ kind: 'uncertain' })
  await decision
  expect(opened.state.canAddAttachments).toBe(false)
  await opened.downloadAttachment(opened.state.attachments[0])
  expect(downloaded).toEqual([['A', { id: 1, fileName: 'a.txt', sizeBytes: 1 }]])
})

it.each(['processed', 'started', 'copied', 'config', 'other-task'])(
  '从 %s 查看同审批不能结束原任务待确认记录',
  async (kind) => {
    const h = harness()
    const first = h.module.open(target())
    h.reads[0].resolve(payload('A'))
    await first.ready
    const sending = first.decide(1)
    h.submissions[0].resolve({ kind: 'uncertain' })
    await sending
    const perspective =
      kind === 'config'
        ? {
            kind: null,
            title: '',
            returnPath: '/process/processList',
            readOnly: true,
            emphasis: '',
          }
        : workListPerspective(kind === 'other-task' ? 'pending' : kind)
    const next = h.module.open({
      processId: 'A',
      taskId: kind === 'other-task' ? 'task-B' : undefined,
      perspective,
    })
    h.reads[1].resolve(payload('A'))
    await next.ready
    const record = next.state.recoveries[0]
    next.endRecovery(record.operationId)
    expect(next.state.recoveries).toHaveLength(1)
    if (kind !== 'other-task') expect(next.state.canSubmit).toBe(false)
  },
)

it('首次详情失败后重试成功也读取附件，附件失败可以单独重试', async () => {
  let reads = 0
  const h = harness({
    listAttachments: async () => {
      reads++
      if (reads === 1) throw new Error('附件服务故障')
      return { attachments: [{ id: 9, fileName: '补充.pdf', sizeBytes: 9 }], canUpload: true }
    },
  })
  const opened = h.module.open(target())
  h.reads[0].reject(new Error('详情网络故障'))
  await opened.ready
  const retry = opened.reload()
  h.reads[1].resolve(payload('A'))
  await retry
  expect(opened.state.attachmentError).toBe('附件服务故障')
  expect(opened.state.canSubmit).toBe(true)
  await opened.reloadAttachments()
  expect(opened.state.attachments[0].fileName).toBe('补充.pdf')
  expect(h.reads).toHaveLength(2)
})

it.each(['success', 'failure'])('同任务同地址重新打开，旧读取 %s 不改变新页面', async (outcome) => {
  const h = harness()
  const old = h.module.open(target())
  old.release()
  const fresh = h.module.open(target())
  h.reads[1].resolve(payload('A'))
  await fresh.ready
  if (outcome === 'success')
    h.reads[0].resolve(payload('A', { process: { id: 'A', title: '旧内容' } }))
  else h.reads[0].reject(new Error('旧错误'))
  await old.ready
  expect(fresh.state.detail.title).toBe('审批 A')
  expect(fresh.state.error).toBe('')
  expect(fresh.state.loading).toBe(false)
  expect(h.notifications).toEqual([])
})

it.each(['processed', 'started', 'copied', 'config'])(
  '五入口中的 %s 保留展示与返回，服务端可处理不能给只读入口处理按钮',
  async (kind) => {
    const h = harness()
    const perspective =
      kind === 'config'
        ? {
            kind: null,
            title: '审批实例',
            returnPath: '/process/processList',
            readOnly: true,
            emphasis: '',
          }
        : workListPerspective(kind)
    const opened = h.module.open({ processId: 'A', taskId: 'task-A', perspective })
    h.reads[0].resolve(payload('A'))
    await opened.ready
    expect(opened.state.detail.formFields).toEqual([{ key: '原因', value: '出差' }])
    expect(opened.state.detail.records[0].description).toBe('发起申请')
    expect(opened.state.detail.recordsFirst).toBe(kind === 'started')
    expect(opened.state.detail.progressLabel).toBe(kind === 'started' ? '等待李四审批' : '')
    await opened.decide(1)
    expect(h.submissions).toHaveLength(0)
    opened.back()
    expect(h.navigation).toEqual([perspective.returnPath])
  },
)

it.each(['release', 'identity', 'reopen'])(
  '处理中 %s 后迟到成功不提示不导航；可靠结论只结束原操作记录',
  async (mode) => {
    const h = harness()
    const old = h.module.open(target())
    h.reads[0].resolve(payload('A'))
    await old.ready
    const sending = old.decide(1)
    let fresh
    if (mode === 'release') old.release()
    if (mode === 'identity') h.changeIdentity()
    if (mode === 'reopen') {
      fresh = h.module.open(target('B', 'task-B'))
      h.reads[1].resolve(payload('B'))
      await fresh.ready
    }
    h.submissions[0].resolve({ kind: 'success' })
    await sending
    expect(h.navigation).toEqual([])
    expect(h.notifications).toEqual([])
    if (fresh) expect(fresh.state.detail.title).toBe('审批 B')
  },
)

it.each(['unreadable', 'corrupt', 'unwritable'])(
  '恢复记录 %s 时明确故障并阻止新提交',
  async (mode) => {
    const h = harness({
      ...(mode === 'unreadable'
        ? {
            loadRecords: () => {
              throw new Error('无法读取记录')
            },
          }
        : {}),
      ...(mode === 'corrupt' ? { loadRecords: () => [{ taskId: '丢失动作' }] } : {}),
      ...(mode === 'unwritable'
        ? {
            saveRecords: () => {
              throw new Error('无法保存记录')
            },
          }
        : {}),
    })
    const opened = h.module.open(target())
    h.reads[0].resolve(payload('A'))
    await opened.ready
    await opened.decide(1)
    expect(h.submissions).toHaveLength(0)
    expect(opened.state.recordError).not.toBe('')
    expect(opened.state.canSubmit).toBe(false)
  },
)

it('新登录不继承原进入结果记录；另一任务不解除原记录', async () => {
  const h = harness()
  const old = h.module.open(target())
  h.reads[0].resolve(payload('A'))
  await old.ready
  const sending = old.decide(1)
  h.submissions[0].resolve({ kind: 'uncertain' })
  await sending
  const otherTask = h.module.open(target('A', 'task-B'))
  h.reads[1].resolve(payload('A'))
  await otherTask.ready
  expect(otherTask.state.recoveries[0].taskId).toBe('task-A')
  h.changeIdentity()
  const fresh = h.module.open(target())
  h.reads[2].resolve(payload('A'))
  await fresh.ready
  expect(fresh.state.recoveries).toEqual([])
  expect(fresh.state.canSubmit).toBe(true)
})

it('旧附件读取和补传结果不写入新审批或新身份，批次停止追加后续文件', async () => {
  const upload = deferred(),
    fileRead = deferred()
  let reads = 0,
    uploadCount = 0
  const h = harness({
    listAttachments: () => {
      reads++
      return reads === 2 ? fileRead.promise : Promise.resolve({ attachments: [], canUpload: true })
    },
    upload: () => {
      uploadCount++
      return upload.promise
    },
  })
  const a = h.module.open(target())
  h.reads[0].resolve(payload('A'))
  await a.ready
  const loading = a.reloadAttachments()
  const b = h.module.open(target('B', 'task-B'))
  h.reads[1].resolve(payload('B'))
  await b.ready
  fileRead.resolve({ attachments: [{ id: 1, fileName: '旧文件', sizeBytes: 1 }], canUpload: true })
  await loading
  expect(b.state.attachments).toEqual([])
  const uploading = b.addAttachments([new File(['1'], '1.txt'), new File(['2'], '2.txt')])
  h.changeIdentity()
  upload.resolve({ id: 2, fileName: '另一身份文件', sizeBytes: 1 })
  await uploading
  expect(b.state.attachments).toEqual([])
  expect(uploadCount).toBe(1)
})

it('提交前刚写入记录却无法恢复读取时暂停业务请求', async () => {
  let written = false
  const h = harness({
    loadRecords: () => {
      if (written) throw new Error('恢复读取失败')
      return []
    },
    saveRecords: () => {
      written = true
    },
  })
  const opened = h.module.open(target())
  h.reads[0].resolve(payload('A'))
  await opened.ready
  const sending = opened.decide(1)
  if (h.submissions[0]) h.submissions[0].resolve({ kind: 'uncertain' })
  await sending
  expect(h.submissions).toHaveLength(0)
  expect(opened.state.recordError).toBe('恢复读取失败')
})

it.each([
  ['task-B', 'pending'],
  ['task-A', 'processed'],
  ['task-A', 'started'],
  ['task-A', 'copied'],
])('同审批读取中切换任务 %s 和入口 %s，旧失败不改变当前打开', async (taskId, kind) => {
  const h = harness()
  const old = h.module.open(target())
  const fresh = h.module.open(target('A', taskId, kind))
  h.reads[1].resolve(payload('A'))
  await fresh.ready
  h.reads[0].reject(new Error('先前打开故障'))
  await old.ready
  expect(fresh.state.error).toBe('')
  expect(fresh.state.detail.perspective.kind).toBe(kind)
  expect(fresh.state.canSubmit).toBe(kind === 'pending')
})

it.each(['same-factory', 'new-factory'])(
  '补传在途跨 %s 重开同审批保持互斥，已有附件可读取下载，完成后不自动提交',
  async (mode) => {
    const inFlight = deferred()
    let uploads = 0
    const downloads = []
    const h = harness({
      upload: () => {
        uploads++
        return inFlight.promise
      },
      listAttachments: async () => ({
        attachments: [{ id: 7, fileName: '原附件.txt', sizeBytes: 1 }],
        canUpload: true,
      }),
      download: async (...args) => downloads.push(args),
    })
    const first = h.module.open(target())
    h.reads[0].resolve(payload('A'))
    await first.ready
    const batch = first.addAttachments([new File(['1'], '1.txt'), new File(['2'], '2.txt')])
    first.release()
    const factory = mode === 'same-factory' ? h.module : createApprovalDetail(h.adapter)
    const reopened = factory.open(target())
    h.reads[1].resolve(payload('A'))
    await reopened.ready
    expect(reopened.state.attachmentBusy).toBe(true)
    expect(reopened.state.canSubmit).toBe(false)
    expect(reopened.state.attachments.map((file) => file.fileName)).toEqual(['原附件.txt'])
    await reopened.downloadAttachment(reopened.state.attachments[0])
    expect(downloads).toHaveLength(1)
    await reopened.decide(1)
    expect(h.submissions).toHaveLength(0)
    inFlight.resolve({ id: 8, fileName: '旧打开补传结果.txt', sizeBytes: 1 })
    await batch
    expect(uploads).toBe(1)
    expect(reopened.state.attachmentBusy).toBe(false)
    expect(reopened.state.canSubmit).toBe(true)
    expect(reopened.state.attachments.map((file) => file.fileName)).toEqual(['原附件.txt'])
    expect(h.submissions).toHaveLength(0)
    reopened.release()
  },
)

it.each(['other-approval', 'new-entry'])(
  '旧补传不阻塞 %s，旧失败不提示新页面且互斥最终释放',
  async (mode) => {
    const upload = deferred()
    const h = harness({ upload: () => upload.promise })
    const old = h.module.open(target())
    h.reads[0].resolve(payload('A'))
    await old.ready
    const batch = old.addAttachments([new File(['1'], '1.txt')])
    old.release()
    if (mode === 'new-entry') h.changeIdentity()
    const processId = mode === 'other-approval' ? 'B' : 'A'
    const next = createApprovalDetail(h.adapter).open(target(processId, 'new-task'))
    h.reads[1].resolve(payload(processId))
    await next.ready
    expect(next.state.attachmentBusy).toBe(false)
    expect(next.state.canSubmit).toBe(true)
    upload.reject(new Error('旧上传登记失败'))
    await batch
    expect(next.state.attachmentError).toBe('')
    expect(next.state.canSubmit).toBe(true)
    next.release()
    if (mode === 'other-approval') {
      const original = createApprovalDetail(h.adapter).open(target())
      h.reads[2].resolve(payload('A'))
      await original.ready
      expect(original.state.attachmentBusy).toBe(false)
      expect(original.state.canSubmit).toBe(true)
      original.release()
    }
  },
)
