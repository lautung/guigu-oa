import { computed, onScopeDispose, ref, shallowRef, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/store/modules/user'
import { getEntryId } from '@/utils/auth'
import request from '@/utils/request'
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
    formData: {},
    ccUserIds: [],
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
          await request<ProcessTemplateRecord>({
            method: 'get',
            url: `/admin/process/processTemplate/get/${id}`,
            normalErrorNotification: 'caller',
          })
        ).data,
      recipients: async () =>
        (
          await request<CarbonCopyRecipient[]>({
            method: 'get',
            url: '/admin/process/carbonCopyRecipients',
            normalErrorNotification: 'caller',
          })
        ).data,
      listDraft: async (id) =>
        (
          await request<AttachmentRecord[]>({
            method: 'get',
            url: `/admin/attachments/drafts/${id}`,
            normalErrorNotification: 'caller',
          })
        ).data,
      remove: async (id) => {
        await request({
          method: 'delete',
          url: `/admin/attachments/pending/${id}`,
          normalErrorNotification: 'caller',
        })
      },
      upload: (file, draftId, assertCurrent) =>
        uploadAttachment(file, {
          draftId,
          assertCurrent,
          createUpload: (data) =>
            request<AttachmentUploadGrant>({
              method: 'post',
              url: '/admin/attachments/uploads',
              data,
              normalErrorNotification: 'caller',
            }),
          completeUpload: (id) =>
            request<AttachmentRecord>({
              method: 'post',
              url: `/admin/attachments/${id}/complete`,
              normalErrorNotification: 'caller',
            }),
          put: (url, options) => fetch(url, options),
        }),
      start: async (snapshot) => {
        try {
          await request<void>({
            method: 'post',
            url: '/admin/process/startUp',
            data: snapshot,
            normalErrorNotification: 'caller',
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
  const formData = ref<FormDataRecord>({})
  const ccUserIds = ref<EntityId[]>([])
  const rule = shallowRef<FormRule[]>([])
  let syncing = false
  let rulesJson = ''
  const view = computed(() => page.value?.view.value || emptyView())
  function sync() {
    syncing = true
    const state = view.value
    if (JSON.stringify(formData.value) !== JSON.stringify(state.formData))
      formData.value = clone(state.formData)
    if (JSON.stringify(ccUserIds.value) !== JSON.stringify(state.ccUserIds))
      ccUserIds.value = clone([...state.ccUserIds])
    const nextRules = JSON.stringify(state.template?.rule || [])
    if (nextRules !== rulesJson) {
      rulesJson = nextRules
      rule.value = JSON.parse(nextRules) as FormRule[]
    }
    syncing = false
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
  watch(view, sync, { immediate: true, flush: 'sync' })
  watch(
    [formData, ccUserIds],
    () => {
      if (syncing) return
      page.value?.edit(formData.value, ccUserIds.value)
      if (view.value.locked) sync()
    },
    { deep: true, flush: 'sync' },
  )
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
