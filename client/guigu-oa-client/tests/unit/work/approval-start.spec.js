import { describe, expect, it, vi } from 'vitest'
import { createApprovalStart } from '@/work/approval-start'

function deferred() {
  let resolve, reject
  const promise = new Promise((yes, no) => {
    resolve = yes
    reject = no
  })
  return { promise, resolve, reject }
}
const template = {
  id: 11,
  processTypeId: 1,
  name: '请假申请',
  status: 1,
  formProps: JSON.stringify([
    { type: 'input', field: 'reason', title: '请假原因', $required: true },
    { type: 'input', field: 'days', title: '请假天数', props: { type: 'number' } },
  ]),
  formOptions: JSON.stringify({
    submitBtn: false,
    form: { labelPosition: 'right', labelWidth: '125px' },
  }),
}
const attachment = { id: 42, fileName: '证明.pdf', sizeBytes: 31 }
function harness(overrides = {}) {
  const values = new Map()
  let entry = 'entry-alice',
    sequence = 0
  const storage = {
    getItem: (key) => values.get(key) || null,
    setItem: (key, value) => {
      values.set(key, value)
    },
    removeItem: (key) => {
      values.delete(key)
    },
  }
  const effects = []
  const adapter = {
    entryId: () => entry,
    storage,
    id: () => `uuid-${++sequence}`,
    readTemplate: vi.fn(async () => template),
    recipients: vi.fn(async () => [{ id: 7, name: 'Carol' }]),
    listDraft: vi.fn(async () => []),
    upload: vi.fn(async (file) => ({ ...attachment, fileName: file.name })),
    remove: vi.fn(async () => {}),
    start: vi.fn(async () => ({ kind: 'started' })),
    notify: (message, kind) => effects.push(['notify', message, kind]),
    navigate: (path) => effects.push(['navigate', path]),
    ...overrides,
  }
  return {
    adapter,
    module: createApprovalStart(adapter),
    storage,
    values,
    effects,
    setEntry: (value) => {
      entry = value
    },
  }
}
async function open(h, id = 11) {
  const page = h.module.open(id)
  await page.ready
  return page
}
async function restore(h) {
  return open({ ...h, module: createApprovalStart(h.adapter) })
}

describe('发起审批 interface', () => {
  it.each(['暂不可发起，需核实原审批内容', '审批步骤原版本无法确认，请联系配置人员'])(
    'blocks unavailable published templates before loading CC or attachments: %s',
    async (message) => {
      const h = harness({
        readTemplate: vi.fn(async () => ({
          ...template,
          availableForInitiation: false,
          availabilityMessage: message,
        })),
      })
      const page = await open(h)
      expect(page.view.value.template).toBeNull()
      expect(page.view.value.error).toBe(message)
      await page.submit({ reason: '不能发起' })
      expect(h.adapter.recipients).not.toHaveBeenCalled()
      expect(h.adapter.listDraft).not.toHaveBeenCalled()
      expect(h.adapter.start).not.toHaveBeenCalled()
    },
  )
  it('submits a fixed form/CC/attachment snapshot and locks all mutations and duplicate clicks', async () => {
    const flight = deferred(),
      h = harness({
        listDraft: vi.fn(async () => [attachment]),
        start: vi.fn(() => flight.promise),
      })
    const page = await open(h)
    expect(page.view.value.template.rule).toMatchObject([
      { field: 'reason', $required: true },
      { field: 'days', props: { type: 'number' } },
    ])
    expect(page.view.value.template.option).toMatchObject({
      submitBtn: true,
      form: { labelPosition: 'top', labelWidth: '125px' },
    })
    const form = { reason: '事假', days: 3, nested: { value: 'before' } }
    page.edit(form, [7])
    const pending = page.submit(form)
    expect(page.view.value.locked).toBe(true)
    form.nested.value = 'after'
    page.edit({ reason: 'changed' }, [8])
    await page.submit({ reason: '重复点击' })
    await page.remove(42)
    await page.add([new File(['x'], 'new.txt')])
    expect(h.adapter.start).toHaveBeenCalledTimes(1)
    const sent = h.adapter.start.mock.calls[0][0]
    expect(JSON.parse(sent.formValues)).toEqual({
      formData: { reason: '事假', days: 3, nested: { value: 'before' } },
      formShowData: { 请假原因: '事假', 请假天数: 3 },
    })
    expect(sent).toMatchObject({
      processTemplateId: 11,
      processTypeId: 1,
      attachmentIds: [42],
      ccUserIds: [7],
    })
    expect(sent.attachmentDraftId).toBeTruthy()
    expect(sent.idempotencyKey).toBeTruthy()
    expect(h.adapter.remove).not.toHaveBeenCalled()
    expect(h.adapter.upload).not.toHaveBeenCalled()
    flight.resolve({ kind: 'started' })
    await pending
    expect(h.effects).toEqual([
      ['notify', '发起成功', 'success'],
      ['navigate', '/work/started'],
    ])
  })
  it('keeps one submission key per attempt and mints a new one only after restart', async () => {
    const h = harness({
        start: vi
          .fn()
          .mockResolvedValueOnce({ kind: 'unconfirmed' })
          .mockResolvedValue({ kind: 'started' }),
      }),
      page = await open(h)
    await page.submit({ reason: 'original' })
    await page.prepareStartAgain(true)
    await page.submit({ reason: 'again' })
    const [first, second] = h.adapter.start.mock.calls.map((call) => call[0])
    expect(first.idempotencyKey).toBeTruthy()
    expect(second.idempotencyKey).toBeTruthy()
    expect(second.idempotencyKey).not.toBe(first.idempotencyKey)
  })
  it('blocks pending reads and never queues a submission', async () => {
    const read = deferred(),
      h = harness({ listDraft: vi.fn(() => read.promise) }),
      page = h.module.open(11)
    await vi.waitFor(() => expect(page.view.value.reading).toBe(true))
    await page.submit({ reason: '读取中' })
    expect(h.adapter.start).not.toHaveBeenCalled()
    read.resolve([])
    await page.ready
    expect(h.adapter.start).not.toHaveBeenCalled()
    await page.submit({ reason: '读取后再点击' })
    expect(h.adapter.start).toHaveBeenCalledTimes(1)
  })
  it('blocks while deleting, then submits the updated attachment list only on another click', async () => {
    const removal = deferred(),
      h = harness({
        listDraft: vi.fn(async () => [attachment]),
        remove: vi.fn(() => removal.promise),
      }),
      page = await open(h)
    const removing = page.remove(42)
    await page.submit({ reason: '删除中' })
    expect(h.adapter.start).not.toHaveBeenCalled()
    removal.resolve()
    await removing
    expect(h.adapter.start).not.toHaveBeenCalled()
    await page.submit({ reason: '再点击' })
    expect(h.adapter.start.mock.calls[0][0].attachmentIds).toEqual([])
  })
  it('keeps the entire file batch busy between transfers', async () => {
    const a = deferred(),
      b = deferred(),
      h = harness({
        upload: vi
          .fn()
          .mockImplementationOnce(() => a.promise)
          .mockImplementationOnce(() => b.promise),
      }),
      page = await open(h)
    const uploads = page.add([new File(['a'], 'a.txt'), new File(['b'], 'b.txt')])
    expect(page.view.value.uploading).toBe(2)
    await page.submit({ reason: '上传中' })
    a.resolve({ id: 1, fileName: 'a.txt', sizeBytes: 1 })
    await vi.waitFor(() => expect(h.adapter.upload).toHaveBeenCalledTimes(2))
    expect(page.view.value.uploading).toBe(1)
    await page.submit({ reason: '第二个在途' })
    b.resolve({ id: 2, fileName: 'b.txt', sizeBytes: 1 })
    await uploads
    expect(h.adapter.start).not.toHaveBeenCalled()
    await page.submit({ reason: '再点击' })
    expect(h.adapter.start.mock.calls[0][0].attachmentIds).toEqual([1, 2])
  })
  it('retains a definitive rejection and accepts a modified retry', async () => {
    const h = harness({
        listDraft: vi.fn(async () => [attachment]),
        start: vi
          .fn()
          .mockResolvedValueOnce({ kind: 'rejected', message: '不能发起' })
          .mockResolvedValue({ kind: 'started' }),
      }),
      page = await open(h)
    page.edit({ reason: '原内容' }, [7])
    await page.submit({ reason: '原内容' })
    expect(page.view.value).toMatchObject({
      locked: false,
      attachments: [{ id: 42 }],
    })
    expect(page.draft).toMatchObject({
      formData: { reason: '原内容' },
      ccUserIds: [7],
    })
    await page.submit({ reason: '修改后' })
    expect(JSON.parse(h.adapter.start.mock.calls[1][0].formValues).formData.reason).toBe('修改后')
  })
  it('restores unknown results and requires explicit reconciliation before another manual submission', async () => {
    const h = harness({
        listDraft: vi.fn().mockResolvedValueOnce([attachment]).mockResolvedValue([]),
        start: vi
          .fn()
          .mockResolvedValueOnce({ kind: 'unconfirmed' })
          .mockResolvedValue({ kind: 'started' }),
      }),
      page = await open(h)
    page.edit({ reason: '原请求' }, [7])
    await page.submit({ reason: '原请求' })
    page.release()
    const restored = await restore(h)
    expect(restored.view.value).toMatchObject({
      phase: 'unconfirmed',
      locked: true,
    })
    expect(restored.draft).toMatchObject({
      formData: { reason: '原请求' },
      ccUserIds: [7],
    })
    await restored.submit({ reason: '不能直接重发' })
    await restored.prepareStartAgain(false)
    expect(h.adapter.start).toHaveBeenCalledTimes(1)
    await restored.prepareStartAgain(true)
    expect(restored.view.value.attachments).toMatchObject([{ id: 42, availability: 'unavailable' }])
    await restored.submit({ reason: '附件未处理' })
    expect(h.adapter.start).toHaveBeenCalledTimes(1)
    restored.omitUnavailable(42)
    expect(h.adapter.remove).not.toHaveBeenCalled()
    await restored.submit({ reason: '明确不再附加后再点' })
    expect(h.adapter.start.mock.calls[1][0].attachmentIds).toEqual([])
  })
  it('finishes a user-confirmed found approval without resending or deleting server files', async () => {
    const h = harness({ start: vi.fn(async () => ({ kind: 'unconfirmed' })) }),
      page = await open(h)
    await page.submit({ reason: '已找到' })
    page.finishFoundApproval()
    expect(h.adapter.start).toHaveBeenCalledTimes(1)
    expect(h.adapter.remove).not.toHaveBeenCalled()
    expect(h.effects).toContainEqual(['navigate', '/work/started'])
    expect((await restore(h)).draft.formData).toEqual({})
  })
  it('restores editable content, CC and registered attachments after route disposal', async () => {
    const h = harness({ listDraft: vi.fn(async () => [attachment]) }),
      page = await open(h)
    page.edit({ reason: '保留', days: 2 }, [7])
    page.release()
    const next = await open(h)
    expect(next.view.value).toMatchObject({
      attachments: [{ id: 42 }],
      locked: false,
    })
    expect(next.draft).toMatchObject({
      formData: { reason: '保留', days: 2 },
      ccUserIds: [7],
    })
  })
  it('restores interrupted requests as unconfirmed without replaying POST', async () => {
    const flight = deferred(),
      h = harness({ start: vi.fn(() => flight.promise) }),
      page = await open(h)
    page.edit({ reason: '刷新', days: 2 }, [7])
    const pending = page.submit({ reason: '刷新', days: 2 })
    page.release()
    const next = await restore(h)
    expect(next.view.value).toMatchObject({ phase: 'unconfirmed' })
    expect(next.draft).toMatchObject({
      formData: { reason: '刷新', days: 2 },
      ccUserIds: [7],
    })
    await next.submit({ reason: '刷新后' })
    expect(h.adapter.start).toHaveBeenCalledTimes(1)
    h.effects.length = 0
    flight.resolve({ kind: 'started' })
    await pending
    expect(h.effects).toEqual([])
  })
  it.each(['started', 'rejected', 'unconfirmed'])(
    'does not present old-page late %s effects',
    async (kind) => {
      const flight = deferred(),
        h = harness({ start: vi.fn(() => flight.promise) }),
        page = await open(h)
      const pending = page.submit({ reason: '旧页' })
      page.release()
      const next = await open(h)
      flight.resolve({ kind, message: '迟到错误' })
      await pending
      expect(next.draft.formData).toEqual({ reason: '旧页' })
      expect(h.effects).toEqual([])
    },
  )
  it('does not let an old success clear a successor record for the same template', async () => {
    const flight = deferred(),
      h = harness({ start: vi.fn(() => flight.promise) }),
      page = await open(h)
    const pending = page.submit({ reason: 'old' })
    page.release()
    const next = await restore(h)
    await next.prepareStartAgain(true)
    next.edit({ reason: 'new' }, [7])
    flight.resolve({ kind: 'started' })
    await pending
    const restored = await restore(h)
    expect(restored.view.value).toMatchObject({ phase: 'editing' })
    expect(restored.draft).toMatchObject({
      formData: { reason: 'new' },
      ccUserIds: [7],
    })
    expect(h.effects).toEqual([])
  })
  it('isolates saved content by template and entry result', async () => {
    const h = harness(),
      page = await open(h)
    page.edit({ reason: 'Alice' }, [7])
    page.release()
    expect((await open(h, 12)).draft.formData).toEqual({})
    h.setEntry('entry-bob')
    expect((await open(h)).draft.formData).toEqual({})
    h.setEntry('entry-alice')
    expect((await open(h)).draft.formData).toEqual({ reason: 'Alice' })
  })
  it('does not complete a transfer or present errors under a different entry', async () => {
    const transfer = deferred(),
      h = harness({
        upload: vi.fn(async (file, id, assertCurrent) => {
          await transfer.promise
          assertCurrent()
          return attachment
        }),
      }),
      page = await open(h)
    const uploading = page.add([new File(['a'], 'a.txt')])
    h.setEntry('entry-bob')
    const bob = await open(h)
    transfer.resolve()
    await uploading
    expect(bob.view.value.attachments).toEqual([])
    expect(h.effects).toEqual([])
  })
  it('retains interrupted filenames after refresh instead of silently submitting without them', async () => {
    const transfer = deferred(),
      h = harness({ upload: vi.fn(() => transfer.promise) }),
      page = await open(h)
    const uploading = page.add([new File(['x'], 'proof.txt')])
    page.release()
    const next = await restore(h)
    expect(next.view.value.interrupted).toMatchObject([{ name: 'proof.txt' }])
    await next.submit({ reason: '不能遗漏' })
    expect(h.adapter.start).not.toHaveBeenCalled()
    next.omitInterrupted(next.view.value.interrupted[0].id)
    await next.submit({ reason: '明确忽略后' })
    expect(h.adapter.start).toHaveBeenCalledTimes(1)
    transfer.reject(new Error('old upload'))
    await uploading
  })
  it('does not send when submission recovery storage is unavailable', async () => {
    const h = harness(),
      page = await open(h)
    h.storage.setItem = () => {
      throw new Error('quota')
    }
    await page.submit({ reason: '不能保存' })
    expect(h.adapter.start).not.toHaveBeenCalled()
    expect(page.view.value.phase).toBe('editing')
  })
  it('keeps failed attachment reads closed until an explicit successful reload', async () => {
    const h = harness({
        listDraft: vi.fn().mockRejectedValueOnce(new Error('读失败')).mockResolvedValue([]),
      }),
      page = await open(h)
    expect(page.view.value.attachmentsReady).toBe(false)
    await page.submit({ reason: '未就绪' })
    expect(h.adapter.start).not.toHaveBeenCalled()
    await page.reload()
    await page.submit({ reason: '重试后' })
    expect(h.adapter.start).toHaveBeenCalledTimes(1)
  })
  it('refuses unpublished templates without starting', async () => {
    const h = harness({ readTemplate: vi.fn(async () => ({ ...template, status: 0 })) }),
      page = await open(h)
    await page.submit({ reason: '未发布' })
    expect(page.view.value.template).toBeNull()
    expect(h.adapter.start).not.toHaveBeenCalled()
  })
  it('does not submit stale saved form values when the current values cannot be serialized', async () => {
    const h = harness(),
      page = await open(h)
    page.edit({ reason: 'old' }, [])
    const invalid = { reason: 'new' }
    invalid.circular = invalid
    expect(page.edit(invalid, [])).toBe(false)
    await page.submit(invalid)
    expect(h.adapter.start).not.toHaveBeenCalled()
    await page.submit({ reason: 'corrected' })
    expect(JSON.parse(h.adapter.start.mock.calls[0][0].formValues).formData.reason).toBe(
      'corrected',
    )
  })
  it('consumes the template-only legacy draft once before isolating subsequent entry results', async () => {
    const h = harness()
    h.storage.setItem('oa-attachment-draft:11', 'legacy-draft')
    const alice = await open(h)
    expect(h.adapter.listDraft).toHaveBeenLastCalledWith('legacy-draft')
    expect(h.storage.getItem('oa-attachment-draft:11')).toBeNull()
    alice.release()
    h.setEntry('entry-bob')
    await open(h)
    expect(h.adapter.listDraft.mock.calls.at(-1)[0]).not.toBe('legacy-draft')
  })
  it('keeps a failed legacy consumption isolated and blocks sending until recovery succeeds', async () => {
    const h = harness()
    h.storage.setItem('oa-attachment-draft:11', 'legacy-draft')
    const remove = h.storage.removeItem
    let failing = true
    h.storage.removeItem = (key) => {
      if (failing && key === 'oa-attachment-draft:11') throw new Error('cannot consume legacy')
      remove(key)
    }
    const alice = await open(h)
    await alice.submit({ reason: 'blocked' })
    expect(h.adapter.start).not.toHaveBeenCalled()
    expect(alice.view.value.error).toContain('cannot consume legacy')
    alice.release()
    h.setEntry('entry-bob')
    await open(h)
    expect(h.adapter.listDraft.mock.calls.at(-1)[0]).not.toBe('legacy-draft')
    h.setEntry('entry-alice')
    const recovered = await restore(h)
    await recovered.submit({ reason: 'still blocked after refresh' })
    expect(h.adapter.start).not.toHaveBeenCalled()
    failing = false
    await recovered.reload()
    expect(h.storage.getItem('oa-attachment-draft:11')).toBeNull()
    await recovered.submit({ reason: 'migration recovered' })
    expect(h.adapter.start).toHaveBeenCalledTimes(1)
    expect(h.adapter.start.mock.calls[0][0].attachmentDraftId).toBe('legacy-draft')
  })
  it('replaces an unavailable reference only after its new file is registered', async () => {
    const transfer = deferred(),
      h = harness({
        listDraft: vi.fn().mockResolvedValueOnce([attachment]).mockResolvedValue([]),
        start: vi
          .fn()
          .mockResolvedValueOnce({ kind: 'unconfirmed' })
          .mockResolvedValue({ kind: 'started' }),
        upload: vi.fn(() => transfer.promise),
      }),
      page = await open(h)
    await page.submit({ reason: 'original' })
    await page.prepareStartAgain(true)
    const replacing = page.add([new File(['new'], 'new.pdf')], { kind: 'unavailable', id: 42 })
    expect(page.view.value.attachments).toMatchObject([{ id: 42, availability: 'unavailable' }])
    await page.submit({ reason: 'still uploading' })
    expect(h.adapter.start).toHaveBeenCalledTimes(1)
    transfer.resolve({ id: 43, fileName: 'new.pdf', sizeBytes: 3 })
    await replacing
    expect(page.view.value.attachments).toMatchObject([{ id: 43, availability: 'pending' }])
    expect(page.view.value.interrupted).toEqual([])
    await page.submit({ reason: 'replacement ready' })
    expect(h.adapter.start.mock.calls[1][0].attachmentIds).toEqual([43])
    expect(h.adapter.remove).not.toHaveBeenCalled()
  })
  it('preserves a failed replacement across refresh and retries it without omitting the old reference', async () => {
    const h = harness({
        listDraft: vi.fn().mockResolvedValueOnce([attachment]).mockResolvedValue([]),
        start: vi
          .fn()
          .mockResolvedValueOnce({ kind: 'unconfirmed' })
          .mockResolvedValue({ kind: 'started' }),
        upload: vi
          .fn()
          .mockRejectedValueOnce(new Error('failed'))
          .mockResolvedValue({ id: 43, fileName: 'retry.pdf', sizeBytes: 1 }),
      }),
      page = await open(h)
    await page.submit({ reason: 'original' })
    await page.prepareStartAgain(true)
    await page.add([new File(['x'], 'failed.pdf')], { kind: 'unavailable', id: 42 })
    expect(page.view.value.attachments).toMatchObject([{ id: 42, availability: 'unavailable' }])
    page.release()
    const next = await restore(h)
    await next.add([new File(['x'], 'retry.pdf')], {
      kind: 'interrupted',
      id: next.view.value.interrupted[0].id,
    })
    expect(next.view.value.interrupted).toEqual([])
    expect(next.view.value.attachments).toMatchObject([{ id: 43, availability: 'pending' }])
    await next.submit({ reason: 'replacement ready' })
    expect(h.adapter.start.mock.calls[1][0].attachmentIds).toEqual([43])
  })
  it('reselects an interrupted upload without leaving an extra omission step', async () => {
    const h = harness({
        upload: vi.fn().mockRejectedValueOnce(new Error('failed')).mockResolvedValue(attachment),
      }),
      page = await open(h)
    await page.add([new File(['x'], 'failed.pdf')])
    const id = page.view.value.interrupted[0].id
    await page.add([new File(['x'], 'retry.pdf')], { kind: 'interrupted', id })
    expect(page.view.value.interrupted).toEqual([])
    await page.submit({ reason: 'ready' })
    expect(h.adapter.start.mock.calls[0][0].attachmentIds).toEqual([42])
  })
  it('requires an attachment reread after an uncertain delete outcome', async () => {
    const h = harness({
        listDraft: vi.fn().mockResolvedValueOnce([attachment]).mockResolvedValue([]),
        remove: vi.fn().mockRejectedValue(new Error('response lost')),
      }),
      page = await open(h)
    await page.remove(42)
    expect(page.view.value.attachmentsReady).toBe(false)
    await page.submit({ reason: 'cannot send stale IDs' })
    expect(h.adapter.start).not.toHaveBeenCalled()
    await page.reload()
    expect(page.view.value.attachments).toMatchObject([{ id: 42, availability: 'unavailable' }])
    page.omitUnavailable(42)
    await page.submit({ reason: 'checked' })
    expect(h.adapter.start.mock.calls[0][0].attachmentIds).toEqual([])
  })
  it('does not replace an unconfirmed attempt while its pending references are being read', async () => {
    const read = deferred(),
      h = harness({
        listDraft: vi
          .fn()
          .mockResolvedValueOnce([attachment])
          .mockImplementationOnce(() => read.promise)
          .mockResolvedValue([]),
        start: vi.fn(async () => ({ kind: 'unconfirmed' })),
      }),
      page = await open(h)
    await page.submit({ reason: 'original' })
    const reading = page.reload()
    await vi.waitFor(() => expect(page.view.value.reading).toBe(true))
    await page.prepareStartAgain(true)
    expect(page.view.value.phase).toBe('unconfirmed')
    read.resolve([])
    await reading
    await page.prepareStartAgain(true)
    expect(page.view.value.phase).toBe('editing')
    expect(h.adapter.start).toHaveBeenCalledTimes(1)
  })
  it('treats the module draft as the single owner and persists deep changes', async () => {
    const h = harness()
    const page = await open(h)
    page.draft.formData.reason = '草稿'
    page.draft.formData.nested = { value: 'deep' }
    page.draft.ccUserIds.push(7)
    expect(JSON.parse([...h.values.values()][0]).formData).toEqual({
      reason: '草稿',
      nested: { value: 'deep' },
    })
    expect(JSON.parse([...h.values.values()][0]).ccUserIds).toEqual([7])
    expect((await restore(h)).draft).toMatchObject({
      formData: { reason: '草稿', nested: { value: 'deep' } },
      ccUserIds: [7],
    })
  })
  it('reflows draft changes while sending or unconfirmed, keeping the submitted content', async () => {
    const flight = deferred(),
      h = harness({ start: vi.fn(() => flight.promise) }),
      page = await open(h)
    page.draft.formData.reason = '已提交'
    const pending = page.submit({ reason: '已提交' })
    expect(page.view.value.phase).toBe('sending')
    page.draft.formData.reason = '保存中改动'
    page.draft.ccUserIds.push(7)
    expect(page.draft.formData.reason).toBe('已提交')
    expect(page.draft.ccUserIds).toEqual([])
    flight.resolve({ kind: 'unconfirmed' })
    await pending
    expect(page.view.value.phase).toBe('unconfirmed')
    page.draft.formData.reason = '待确认改动'
    expect(page.draft.formData.reason).toBe('已提交')
    expect(JSON.parse(h.adapter.start.mock.calls[0][0].formValues).formData.reason).toBe('已提交')
  })
  it('keeps the typed draft and surfaces the error when the record cannot be persisted', async () => {
    const h = harness(),
      page = await open(h)
    h.storage.setItem = () => {
      throw new Error('存储不可用')
    }
    page.draft.formData.reason = '保留输入'
    expect(page.draft.formData.reason).toBe('保留输入')
    expect(page.view.value.error).toBe('存储不可用')
    await page.submit({ reason: '提交被挡住' })
    expect(h.adapter.start).not.toHaveBeenCalled()
  })
  it('keeps the draft in step with programmatic edits', async () => {
    const h = harness(),
      page = await open(h)
    expect(page.edit({ reason: '程序化' }, [7])).toBe(true)
    expect(page.draft).toMatchObject({ formData: { reason: '程序化' }, ccUserIds: [7] })
  })
  it('stops accepting draft changes after the opening is released', async () => {
    const h = harness(),
      page = await open(h)
    page.edit({ reason: '已接受' }, [])
    page.release()
    page.draft.formData.reason = '释放后'
    expect((await restore(h)).draft.formData).toEqual({ reason: '已接受' })
  })
})
