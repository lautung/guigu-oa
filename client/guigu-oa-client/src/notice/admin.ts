import { reactive, readonly } from 'vue'

import type { ApiResponse, EntityId, NoticeAdminRecord, NoticeWritePayload } from '@/types/domain'

/**
 * 公告发布管理页的状态机（ADR 0055）：草稿(0) → 已发布(1) → 已撤回(2)。
 *
 * - 新建：保存草稿走 POST；发布走 POST 再 POST /{id}/publish，创建成功后把地址
 *   换成编辑路径，刷新仍带着 id。
 * - 草稿：保存草稿走 PUT；发布走 PUT 再 publish。
 * - 已发布：保存走 PUT（保留同一 id，不回滚已读回执）；撤回走 withdraw。
 * - 已撤回：只读，只能再次发布或删除；服务端拒绝编辑，页面也不发 PUT。
 * - 删除：二次确认后回到列表。
 *
 * dirty 只比较标题/类型/正文与最近一次载入或保存的快照；已撤回只读不拦离开，
 * 不做 localStorage 自动存档。
 */

export type NoticeEditorPhase = 'loading' | 'ready' | 'saving' | 'load-error' | 'closed'

export interface NoticeEditorForm {
  title: string
  type: number
  content: string
}

export interface NoticeEditorAdapter {
  load(id: EntityId): Promise<ApiResponse<NoticeAdminRecord>>
  create(payload: NoticeWritePayload): Promise<ApiResponse<NoticeAdminRecord>>
  update(id: EntityId, payload: NoticeWritePayload): Promise<ApiResponse<NoticeAdminRecord>>
  publish(id: EntityId): Promise<ApiResponse<NoticeAdminRecord>>
  withdraw(id: EntityId): Promise<ApiResponse<NoticeAdminRecord>>
  remove(id: EntityId): Promise<ApiResponse<void>>
  confirm(message: string): Promise<boolean>
  notify(message: string, kind: 'success' | 'warning'): void
  navigate(path: string): void
  replace(path: string): void
  editorPath(id: EntityId): string
  listPath: string
}

const NOTICE_STATUS_DRAFT = 0
const NOTICE_STATUS_PUBLISHED = 1
const NOTICE_STATUS_WITHDRAWN = 2

export const NOTICE_LEAVE_MESSAGE = '当前有未保存修改，离开将丢弃本次编辑，确认离开？'
export const NOTICE_DELETE_MESSAGE = '删除后不可恢复，确认删除该公告？'
export const NOTICE_WITHDRAW_MESSAGE = '撤回后公告将从全员列表消失，确认撤回？'
export const NOTICE_PUBLISH_MESSAGE = '发布后公告将对全员可见，确认发布？'

export function emptyNoticeForm(): NoticeEditorForm {
  return { title: '', type: 1, content: '' }
}

/** 写请求只带 DTO 的三个字段（ADR 0034）：id 在路径，status 由服务端状态机拥有。 */
export function noticeWritePayload(form: NoticeEditorForm): NoticeWritePayload {
  return { title: form.title, type: form.type, content: form.content }
}

function validStatus(status: unknown): number | null {
  return status === NOTICE_STATUS_DRAFT ||
    status === NOTICE_STATUS_PUBLISHED ||
    status === NOTICE_STATUS_WITHDRAWN
    ? status
    : null
}

function validType(type: unknown): number {
  return type === 2 ? 2 : 1
}

function errorMessage(error: unknown, fallback: string): string {
  if (error && typeof error === 'object' && 'message' in error) {
    const message = (error as { message?: unknown }).message
    if (typeof message === 'string' && message) return message
  }
  return fallback
}

function copyForm(form: NoticeEditorForm): NoticeEditorForm {
  return { title: form.title, type: form.type, content: form.content }
}

export function createNoticeEditor(adapter: NoticeEditorAdapter) {
  const form = reactive<NoticeEditorForm>(emptyNoticeForm())
  const state = reactive({
    phase: 'loading' as NoticeEditorPhase,
    id: '' as EntityId | '',
    status: null as number | null,
    form,
    snapshot: null as NoticeEditorForm | null,
    error: '',
    get dirty(): boolean {
      if (!state.snapshot) return false
      return (
        state.form.title !== state.snapshot.title ||
        state.form.type !== state.snapshot.type ||
        state.form.content !== state.snapshot.content
      )
    },
    /** 新建、草稿与已发布可编辑；已撤回只读。 */
    get canEdit(): boolean {
      return (
        state.phase === 'ready' &&
        (state.status === null ||
          state.status === NOTICE_STATUS_DRAFT ||
          state.status === NOTICE_STATUS_PUBLISHED)
      )
    },
    get readOnly(): boolean {
      return state.status === NOTICE_STATUS_WITHDRAWN
    },
    get canSave(): boolean {
      return (
        state.canEdit &&
        !!state.form.title.trim() &&
        (state.form.type === 1 || state.form.type === 2)
      )
    },
    /** 发布：新建、草稿与已撤回可用；已发布只提供保存与撤回。 */
    get canPublish(): boolean {
      return (
        state.phase === 'ready' &&
        (state.status === null ||
          state.status === NOTICE_STATUS_DRAFT ||
          state.status === NOTICE_STATUS_WITHDRAWN) &&
        !!state.form.title.trim() &&
        (state.form.type === 1 || state.form.type === 2)
      )
    },
    get canWithdraw(): boolean {
      return state.phase === 'ready' && state.status === NOTICE_STATUS_PUBLISHED
    },
    get canRemove(): boolean {
      return state.phase === 'ready' && state.id !== ''
    },
  })

  let generation = 0
  const current = (number: number) => number === generation && state.phase !== 'closed'

  function reset(id: EntityId | '') {
    state.id = id
    state.status = null
    state.snapshot = null
    state.error = ''
    Object.assign(state.form, emptyNoticeForm())
  }

  async function open(id?: EntityId): Promise<void> {
    const number = ++generation
    reset(id ?? '')
    state.phase = 'loading'
    if (id == null) {
      state.snapshot = copyForm(state.form)
      state.phase = 'ready'
      return
    }
    try {
      const response = await adapter.load(id)
      if (!current(number)) return
      const record = response.data
      if (!record || record.id == null || String(record.id) !== String(id)) {
        throw new Error('公告不存在或读取结果与目标不一致，请重试或返回')
      }
      const status = validStatus(record.status)
      if (status == null) throw new Error('公告状态无法确认，请重试或返回')
      state.status = status
      state.form.title = record.title || ''
      state.form.type = validType(record.type)
      state.form.content = record.content || ''
      state.snapshot = copyForm(state.form)
      state.phase = 'ready'
    } catch (error: unknown) {
      if (!current(number)) return
      state.phase = 'load-error'
      state.error = errorMessage(error, '公告读取失败，请重试')
    }
  }

  function updateForm(change: Partial<NoticeEditorForm>): boolean {
    if (!state.canEdit) return false
    if (change.title != null) state.form.title = change.title
    if (change.type != null) state.form.type = validType(change.type)
    if (change.content != null) state.form.content = change.content
    return true
  }

  function settle(record: NoticeAdminRecord | null | undefined, fallbackStatus: number) {
    const status = validStatus(record?.status)
    state.status = status == null ? fallbackStatus : status
    state.snapshot = copyForm(state.form)
  }

  /** 保存草稿：新建 POST，已有草稿/已发布 PUT（已发布保持发布状态）。 */
  async function saveDraft(): Promise<boolean> {
    if (!state.canSave) return false
    state.phase = 'saving'
    state.error = ''
    try {
      const payload = noticeWritePayload(state.form)
      if (state.id === '') {
        const response = await adapter.create(payload)
        const record = response.data
        if (!record || record.id == null) throw new Error('公告创建结果不完整，请重试')
        state.id = record.id
        settle(record, NOTICE_STATUS_DRAFT)
        adapter.replace(adapter.editorPath(state.id))
      } else {
        const response = await adapter.update(state.id, payload)
        settle(response.data, state.status ?? NOTICE_STATUS_DRAFT)
      }
      state.phase = 'ready'
      adapter.notify('草稿已保存', 'success')
      return true
    } catch (error: unknown) {
      state.phase = 'ready'
      state.error = errorMessage(error, '公告保存失败，请重试')
      return false
    }
  }

  /** 发布：新建/草稿先落库再 publish；已撤回只 publish（服务端拒绝编辑）。 */
  async function publish(): Promise<boolean> {
    if (!state.canPublish) return false
    if (!(await adapter.confirm(NOTICE_PUBLISH_MESSAGE))) return false
    state.phase = 'saving'
    state.error = ''
    try {
      if (state.status === null) {
        const created = await adapter.create(noticeWritePayload(state.form))
        const record = created.data
        if (!record || record.id == null) throw new Error('公告创建结果不完整，请重试')
        state.id = record.id
        state.status = NOTICE_STATUS_DRAFT
        state.snapshot = copyForm(state.form)
        adapter.replace(adapter.editorPath(state.id))
      } else if (state.status === NOTICE_STATUS_DRAFT) {
        const updated = await adapter.update(state.id, noticeWritePayload(state.form))
        settle(updated.data, NOTICE_STATUS_DRAFT)
      }
      const published = await adapter.publish(state.id)
      settle(published.data, NOTICE_STATUS_PUBLISHED)
      state.phase = 'ready'
      adapter.notify('发布成功', 'success')
      return true
    } catch (error: unknown) {
      state.phase = 'ready'
      state.error = errorMessage(error, '公告发布失败，请重试')
      return false
    }
  }

  async function withdraw(): Promise<boolean> {
    if (!state.canWithdraw) return false
    if (!(await adapter.confirm(NOTICE_WITHDRAW_MESSAGE))) return false
    state.phase = 'saving'
    state.error = ''
    try {
      const response = await adapter.withdraw(state.id)
      settle(response.data, NOTICE_STATUS_WITHDRAWN)
      state.phase = 'ready'
      adapter.notify('已撤回', 'success')
      return true
    } catch (error: unknown) {
      state.phase = 'ready'
      state.error = errorMessage(error, '公告撤回失败，请重试')
      return false
    }
  }

  async function remove(): Promise<boolean> {
    if (!state.canRemove) return false
    if (!(await adapter.confirm(NOTICE_DELETE_MESSAGE))) return false
    state.phase = 'saving'
    state.error = ''
    try {
      await adapter.remove(state.id)
      state.phase = 'closed'
      state.snapshot = copyForm(state.form)
      adapter.notify('删除成功', 'success')
      adapter.navigate(adapter.listPath)
      return true
    } catch (error: unknown) {
      state.phase = 'ready'
      state.error = errorMessage(error, '公告删除失败，请重试')
      return false
    }
  }

  /** 路由离开/返回：只有可编辑且 dirty 才拦；已撤回只读不拦。 */
  async function requestLeave(): Promise<boolean> {
    if (state.phase === 'closed') return true
    if (!state.canEdit || !state.dirty) return true
    if (!(await adapter.confirm(NOTICE_LEAVE_MESSAGE))) return false
    state.phase = 'closed'
    return true
  }

  function dispose(): void {
    generation += 1
    state.phase = 'closed'
  }

  return {
    state: readonly(state),
    open,
    updateForm,
    saveDraft,
    publish,
    withdraw,
    remove,
    requestLeave,
    dispose,
  }
}
