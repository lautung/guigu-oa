import { describe, expect, it, vi } from 'vitest'

import {
  NOTICE_DELETE_MESSAGE,
  NOTICE_LEAVE_MESSAGE,
  NOTICE_WITHDRAW_MESSAGE,
  createNoticeEditor,
  emptyNoticeForm,
  noticeWritePayload,
} from '@/notice/admin'

function record(patch = {}) {
  return {
    code: 200,
    data: {
      id: 7,
      title: '系统维护',
      type: 1,
      content: '<p>今晚维护</p>',
      status: 0,
      ...patch,
    },
  }
}

function createAdapter(overrides = {}) {
  return {
    load: vi.fn(async () => record()),
    create: vi.fn(async () => record({ id: 7, status: 0 })),
    update: vi.fn(async () => record()),
    publish: vi.fn(async () => record({ status: 1 })),
    withdraw: vi.fn(async () => record({ status: 2 })),
    remove: vi.fn(async () => ({ code: 200, data: null })),
    confirm: vi.fn(async () => true),
    notify: vi.fn(),
    navigate: vi.fn(),
    replace: vi.fn(),
    editorPath: (id) => `/system/notice/edit/${id}`,
    listPath: '/system/notice',
    ...overrides,
  }
}

describe('公告编辑状态机（ADR 0055）', () => {
  it('starts a new notice clean and becomes dirty on title/type/content changes', async () => {
    const editor = createNoticeEditor(createAdapter())
    await editor.open()

    expect(editor.state.status).toBeNull()
    expect(editor.state.canEdit).toBe(true)
    expect(editor.state.dirty).toBe(false)
    expect(editor.state.canSave).toBe(false)

    editor.updateForm({ title: '新公告' })
    expect(editor.state.dirty).toBe(true)
    expect(editor.state.canSave).toBe(true)

    editor.updateForm({ type: 2, content: '<p>正文</p>' })
    expect(editor.state.form).toEqual({ title: '新公告', type: 2, content: '<p>正文</p>' })
  })

  it('creates a draft, replaces the route with the edit path and resets dirty', async () => {
    const adapter = createAdapter()
    const editor = createNoticeEditor(adapter)
    await editor.open()
    editor.updateForm({ title: '新公告', type: 2, content: '<p>正文</p>' })

    await expect(editor.saveDraft()).resolves.toBe(true)

    expect(adapter.create).toHaveBeenCalledTimes(1)
    expect(adapter.create).toHaveBeenCalledWith({
      title: '新公告',
      type: 2,
      content: '<p>正文</p>',
    })
    expect(adapter.update).not.toHaveBeenCalled()
    expect(adapter.replace).toHaveBeenCalledWith('/system/notice/edit/7')
    expect(editor.state.id).toBe(7)
    expect(editor.state.status).toBe(0)
    expect(editor.state.dirty).toBe(false)
    expect(adapter.notify).toHaveBeenCalledWith('草稿已保存', 'success')
  })

  it('publishes a new notice by creating it first, then publishing the new id', async () => {
    const adapter = createAdapter()
    const editor = createNoticeEditor(adapter)
    await editor.open()
    editor.updateForm({ title: '新公告', content: '<p>正文</p>' })

    await expect(editor.publish()).resolves.toBe(true)

    expect(adapter.confirm).toHaveBeenCalledWith(expect.stringContaining('发布'))
    expect(adapter.create).toHaveBeenCalledTimes(1)
    expect(adapter.publish).toHaveBeenCalledWith(7)
    expect(adapter.create.mock.invocationCallOrder[0]).toBeLessThan(
      adapter.publish.mock.invocationCallOrder[0],
    )
    expect(editor.state.status).toBe(1)
    expect(editor.state.dirty).toBe(false)
    expect(adapter.replace).toHaveBeenCalledWith('/system/notice/edit/7')
  })

  it('saves and publishes an existing draft with PUT before publish', async () => {
    const adapter = createAdapter({
      load: vi.fn(async () => record({ status: 0 })),
      update: vi.fn(async () => record({ status: 0, title: '改过的标题' })),
    })
    const editor = createNoticeEditor(adapter)
    await editor.open(7)
    editor.updateForm({ title: '改过的标题' })
    expect(editor.state.dirty).toBe(true)

    await editor.publish()

    expect(adapter.update).toHaveBeenCalledWith(7, {
      title: '改过的标题',
      type: 1,
      content: '<p>今晚维护</p>',
    })
    expect(adapter.publish).toHaveBeenCalledWith(7)
    expect(editor.state.status).toBe(1)
    expect(editor.state.dirty).toBe(false)
  })

  it('keeps a published notice published when editing and saving it', async () => {
    const adapter = createAdapter({
      load: vi.fn(async () => record({ status: 1 })),
      update: vi.fn(async () => record({ status: 1, title: '已发布改标题' })),
    })
    const editor = createNoticeEditor(adapter)
    await editor.open(7)

    expect(editor.state.canEdit).toBe(true)
    expect(editor.state.canWithdraw).toBe(true)
    expect(editor.state.canPublish).toBe(false)

    editor.updateForm({ title: '已发布改标题' })
    await editor.saveDraft()

    expect(adapter.update).toHaveBeenCalledTimes(1)
    expect(adapter.publish).not.toHaveBeenCalled()
    expect(editor.state.status).toBe(1)
    expect(editor.state.dirty).toBe(false)
  })

  it('withdraws a published notice and clears dirty', async () => {
    const adapter = createAdapter({ load: vi.fn(async () => record({ status: 1 })) })
    const editor = createNoticeEditor(adapter)
    await editor.open(7)
    editor.updateForm({ title: '撤回前改标题' })

    await expect(editor.withdraw()).resolves.toBe(true)

    expect(adapter.confirm).toHaveBeenCalledWith(NOTICE_WITHDRAW_MESSAGE)
    expect(adapter.withdraw).toHaveBeenCalledWith(7)
    expect(editor.state.status).toBe(2)
    expect(editor.state.readOnly).toBe(true)
    expect(editor.state.dirty).toBe(false)
    expect(adapter.notify).toHaveBeenCalledWith('已撤回', 'success')
  })

  it('treats a withdrawn notice as read-only and only allows republish/delete', async () => {
    const adapter = createAdapter({
      load: vi.fn(async () => record({ status: 2, title: '已撤回公告' })),
      publish: vi.fn(async () => record({ status: 1, title: '已撤回公告' })),
    })
    const editor = createNoticeEditor(adapter)
    await editor.open(7)

    expect(editor.state.readOnly).toBe(true)
    expect(editor.state.canEdit).toBe(false)
    expect(editor.state.canSave).toBe(false)
    expect(editor.state.canPublish).toBe(true)
    expect(editor.state.canRemove).toBe(true)
    expect(editor.updateForm({ title: '不允许' })).toBe(false)
    expect(editor.state.form.title).toBe('已撤回公告')

    await editor.publish()

    expect(adapter.update).not.toHaveBeenCalled()
    expect(adapter.publish).toHaveBeenCalledWith(7)
    expect(editor.state.status).toBe(1)
    expect(editor.state.canEdit).toBe(true)
  })

  it('deletes after confirmation and returns to the list', async () => {
    const adapter = createAdapter()
    const editor = createNoticeEditor(adapter)
    await editor.open(7)

    await expect(editor.remove()).resolves.toBe(true)

    expect(adapter.confirm).toHaveBeenCalledWith(NOTICE_DELETE_MESSAGE)
    expect(adapter.remove).toHaveBeenCalledWith(7)
    expect(adapter.navigate).toHaveBeenCalledWith('/system/notice')
    expect(editor.state.phase).toBe('closed')
  })

  it('does not delete when the confirmation is cancelled', async () => {
    const adapter = createAdapter({ confirm: vi.fn(async () => false) })
    const editor = createNoticeEditor(adapter)
    await editor.open(7)

    await expect(editor.remove()).resolves.toBe(false)
    expect(adapter.remove).not.toHaveBeenCalled()
    expect(adapter.navigate).not.toHaveBeenCalled()
    expect(editor.state.phase).toBe('ready')
  })

  it('leaves without a prompt when clean and prompts when dirty', async () => {
    const adapter = createAdapter()
    const editor = createNoticeEditor(adapter)
    await editor.open(7)

    await expect(editor.requestLeave()).resolves.toBe(true)
    expect(adapter.confirm).not.toHaveBeenCalled()

    editor.updateForm({ title: '未保存修改' })
    adapter.confirm.mockResolvedValueOnce(false)
    await expect(editor.requestLeave()).resolves.toBe(false)
    expect(adapter.confirm).toHaveBeenCalledWith(NOTICE_LEAVE_MESSAGE)
    expect(editor.state.phase).toBe('ready')

    adapter.confirm.mockResolvedValueOnce(true)
    await expect(editor.requestLeave()).resolves.toBe(true)
    expect(editor.state.phase).toBe('closed')
  })

  it('does not intercept leaving a read-only withdrawn notice', async () => {
    const adapter = createAdapter({ load: vi.fn(async () => record({ status: 2 })) })
    const editor = createNoticeEditor(adapter)
    await editor.open(7)

    await expect(editor.requestLeave()).resolves.toBe(true)
    expect(adapter.confirm).not.toHaveBeenCalled()
  })

  it('keeps dirty and the edit state when a save fails', async () => {
    const adapter = createAdapter({
      create: vi.fn(async () => {
        throw new Error('网络中断')
      }),
    })
    const editor = createNoticeEditor(adapter)
    await editor.open()
    editor.updateForm({ title: '新公告' })

    await expect(editor.saveDraft()).resolves.toBe(false)

    expect(editor.state.phase).toBe('ready')
    expect(editor.state.error).toBe('网络中断')
    expect(editor.state.dirty).toBe(true)
    expect(adapter.replace).not.toHaveBeenCalled()
  })

  it('reports a load error when the server returns an unknown status', async () => {
    const adapter = createAdapter({ load: vi.fn(async () => record({ status: 9 })) })
    const editor = createNoticeEditor(adapter)

    await editor.open(7)

    expect(editor.state.phase).toBe('load-error')
    expect(editor.state.error).toContain('状态无法确认')
  })
})

describe('公告写载荷', () => {
  it('sends only title/type/content and keeps server image URLs verbatim', () => {
    const form = emptyNoticeForm()
    form.title = '带图片的公告'
    form.type = 2
    form.content = '<p>看图</p><p><img src="/admin/notices/images/9" alt="截图"></p>'

    expect(noticeWritePayload(form)).toEqual({
      title: '带图片的公告',
      type: 2,
      content: '<p>看图</p><p><img src="/admin/notices/images/9" alt="截图"></p>',
    })
  })

  it('saves the uploaded server URL, never a blob URL', async () => {
    const adapter = createAdapter()
    const editor = createNoticeEditor(adapter)
    await editor.open()
    editor.updateForm({
      title: '带图公告',
      content: '<p><img src="/admin/notices/images/9"></p>',
    })

    await editor.saveDraft()

    const payload = adapter.create.mock.calls[0][0]
    expect(payload.content).toContain('/admin/notices/images/9')
    expect(payload.content).not.toContain('blob:')
  })
})
