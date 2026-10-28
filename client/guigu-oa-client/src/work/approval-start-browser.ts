import { computed, onScopeDispose, shallowRef, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/store/modules/user'
import { getEntryId } from '@/utils/auth'
import { openRequestScope } from '@/request-scope'
import { classifySubmissionFailure } from '@/submission-outcome'
import type { Recognition } from '@/recognition'
import { ROUTES } from '@/navigation/constants'
import { uploadAttachment } from './attachments'
import {
  createApprovalStart,
  type ApprovalStartPage,
  type ApprovalStartView,
} from './approval-start'
import type { CarbonCopyRecipient } from '@/api/carbon-copy'
import type {
  AttachmentRecord,
  AttachmentUploadGrant,
  EntityId,
  FormDataRecord,
  FormDesignerOption,
  FormRule,
  ProcessTemplateRecord,
} from '@/types/domain'

const modules = new WeakMap<Recognition, ReturnType<typeof createApprovalStart>>()
const clone = <T>(value: T): T => JSON.parse(JSON.stringify(value)) as T
const EMPTY_FORM: FormDataRecord = {}
const EMPTY_CC: EntityId[] = []
function uuid(): string {
  if (typeof crypto.randomUUID === 'function') return crypto.randomUUID()
  const bytes = crypto.getRandomValues(new Uint8Array(16))
  bytes[6] = ((bytes[6] || 0) & 15) | 64
  bytes[8] = ((bytes[8] || 0) & 63) | 128
  const hex = Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('')
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
}
function emptyView(): ApprovalStartView {
  return {
    template: null,
    recipients: [],
    attachments: [],
    interrupted: [],
    loading: true,
    reading: false,
    uploading: 0,
    deleting: [],
    attachmentsReady: false,
    locked: true,
    attachmentLocked: true,
    phase: 'editing',
    error: '',
  }
}

export function useApprovalStart(templateId: () => EntityId | undefined) {
  const router = useRouter()
  const recognition = useUserStore().recognition
  if (!recognition) throw new Error('认出 module 尚未接入')
  let module = modules.get(recognition)
  if (!module) {
    const openScope = () => openRequestScope(recognition, { notifyNormalErrors: false })
    module = createApprovalStart({
      entryId: () => getEntryId() || '',
      storage: {
        getItem: (key) => sessionStorage.getItem(key),
        setItem: (key, value) => sessionStorage.setItem(key, value),
        removeItem: (key) => sessionStorage.removeItem(key),
      },
      id: uuid,
      readTemplate: async (id) =>
        (
          await openScope().request<ProcessTemplateRecord>({
            method: 'get',
            // 办事发起页读工作侧端点（不要求配置权限）；未发布模板由服务端在这里拦下。
            url: `/admin/process/getProcessTemplate/${id}`,
          })
        ).data,
      recipients: async () =>
        (
          await openScope().request<CarbonCopyRecipient[]>({
            method: 'get',
            url: '/admin/process/carbonCopyRecipients',
          })
        ).data,
      listDraft: async (id) =>
        (
          await openScope().request<AttachmentRecord[]>({
            method: 'get',
            url: `/admin/attachments/drafts/${id}`,
          })
        ).data,
      remove: async (id) => {
        await openScope().request({
          method: 'delete',
          url: `/admin/attachments/pending/${id}`,
        })
      },
      upload: (file, draftId, assertCurrent) => {
        const scope = openScope()
        return uploadAttachment(file, {
          draftId,
          assertCurrent,
          createUpload: (data) =>
            scope.request<AttachmentUploadGrant>({
              method: 'post',
              url: '/admin/attachments/uploads',
              data,
            }),
          completeUpload: (id) =>
            scope.request<AttachmentRecord>({
              method: 'post',
              url: `/admin/attachments/${id}/complete`,
            }),
          put: (url, options) => fetch(url, options),
        })
      },
      start: async (snapshot) => {
        try {
          await openScope().request<void>({
            method: 'post',
            url: '/admin/process/startUp',
            data: snapshot,
          })
          return { kind: 'started' }
        } catch (error: unknown) {
          if (classifySubmissionFailure(error, 'start-approval') === 'refused')
            return {
              kind: 'rejected',
              message: error instanceof Error ? error.message : '发起审批未获接受',
            }
          return { kind: 'unconfirmed' }
        }
      },
      notify: (message, type) => {
        ElMessage({ message, type, duration: 5000 })
      },
      navigate: (path) => {
        void router.push(path)
      },
    })
    modules.set(recognition, module)
  }
  const starts = module
  const page = shallowRef<ApprovalStartPage>()
  const formData = computed<FormDataRecord>({
    get: () => page.value?.draft.formData ?? EMPTY_FORM,
    set: (value) => {
      if (page.value) page.value.draft.formData = value
    },
  })
  const ccUserIds = computed<EntityId[]>({
    get: () => page.value?.draft.ccUserIds ?? EMPTY_CC,
    set: (value) => {
      if (page.value) page.value.draft.ccUserIds = value
    },
  })
  const rule = shallowRef<FormRule[]>([])
  let rulesJson = ''
  const view = computed(() => page.value?.view.value || emptyView())
  function syncRule() {
    const nextRules = JSON.stringify(view.value.template?.rule || [])
    if (nextRules !== rulesJson) {
      rulesJson = nextRules
      rule.value = JSON.parse(nextRules) as FormRule[]
    }
  }
  watch(
    templateId,
    (id) => {
      page.value?.release()
      if (id == null) {
        page.value = undefined
        void router.replace(ROUTES.START)
        return
      }
      page.value = starts.open(id)
    },
    { immediate: true },
  )
  watch(view, syncRule, { immediate: true, flush: 'sync' })
  onScopeDispose(() => page.value?.release())
  const option = computed<FormDesignerOption>(() => ({
    ...clone(view.value.template?.option || {}),
    form: { ...clone(view.value.template?.option.form || {}), disabled: view.value.locked },
    submitBtn: { disabled: view.value.locked, loading: view.value.phase === 'sending' },
  }))
  return {
    view,
    formData,
    ccUserIds,
    rule,
    option,
    submit: (values: FormDataRecord) => page.value?.submit(values),
    reload: () => page.value?.reload(),
    add: (files: readonly File[]) => page.value?.add(files),
    replaceUnavailable: (id: EntityId, files: readonly File[]) =>
      page.value?.add(files, { kind: 'unavailable', id }),
    retryInterrupted: (id: string, files: readonly File[]) =>
      page.value?.add(files, { kind: 'interrupted', id }),
    remove: (id: EntityId) => page.value?.remove(id),
    omitUnavailable: (id: EntityId) => page.value?.omitUnavailable(id),
    omitInterrupted: (id: string) => page.value?.omitInterrupted(id),
    finishFoundApproval: () => page.value?.finishFoundApproval(),
    prepareStartAgain: async () => {
      const target = page.value
      try {
        await ElMessageBox.confirm(
          '原发起可能已经成功。重新发起可能产生重复审批；请先核实已发起，确认仍要继续。',
          '确认重新发起',
          {
            confirmButtonText: '接受可能重复，继续',
            cancelButtonText: '返回核实',
            type: 'warning',
          },
        )
        if (target === page.value) await target?.prepareStartAgain(true)
      } catch {
        /* The pending confirmation stays intact. */
      }
    },
  }
}
export type ApprovalStartFacade = ReturnType<typeof useApprovalStart>
