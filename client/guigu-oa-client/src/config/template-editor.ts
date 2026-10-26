import { markRaw, reactive, readonly } from 'vue'
import { classifySubmissionFailure } from '@/submission-outcome'
import { getDefaultPostLoginRoute } from '@/navigation/constants'
import type { Recognition } from '@/recognition'
import type {
  ApiClient,
  EntityId,
  PresentedProcessTemplate,
  ProcessTypeRecord,
  ProcessTemplateRecord,
} from '@/types/domain'
import { emptyForm, zipUploadError } from './process-template'

const API = '/admin/process/processTemplate'
const LIST = '/processSet/processTemplate'
interface Design {
  rule: unknown[]
  option: Record<string, unknown>
}
interface Designer {
  setRule(rule: unknown[]): void
  setOption(option: Record<string, unknown>): void
  getRule(): unknown
  getOption(): unknown
}
type Occupancy = { action: 'allow' } | { action: 'redirect'; to: string }
interface EditorOptions {
  recognition: Pick<Recognition, 'capture'>
  request: ApiClient
  designer(): Promise<Designer>
  occupancy(): Occupancy
  confirm(message: string, kind: 'leave' | 'end-editing'): Promise<boolean>
  dismissConfirm(): void
  notify(message: string, level: 'success' | 'warning'): void
  navigate(path: string): void
  replace(path: string): void
  openList(path: string): void
}
interface ZipReference {
  name: string
  path: string
  key: string
}
type Phase =
  | 'loading'
  | 'load-error'
  | 'ready'
  | 'design-error'
  | 'readonly'
  | 'saving'
  | 'uncertain'
  | 'closed'
function errorMessage(error: unknown, fallback: string) {
  return error instanceof Error ? error.message : fallback
}
function validDesign(rule: unknown, option: unknown): Design {
  if (!Array.isArray(rule) || !option || typeof option !== 'object' || Array.isArray(option))
    throw new Error('表单设计内容损坏或不兼容，请重试读取或返回')
  return { rule, option: option as Record<string, unknown> }
}

function loadedForm(record: ProcessTemplateRecord, id: EntityId): PresentedProcessTemplate {
  const form = emptyForm()
  const textFields = [
    'name',
    'iconUrl',
    'description',
    'formProps',
    'formOptions',
    'processDefinitionPath',
    'processDefinitionKey',
  ] as const
  for (const field of textFields) {
    const value = record[field]
    if (value != null && typeof value !== 'string')
      throw new Error('审批模板内容格式不兼容，请重试读取或返回')
    form[field] = value ?? ''
  }
  if (record.processTypeId != null && !['string', 'number'].includes(typeof record.processTypeId))
    throw new Error('审批类型无法确认，请重试读取或返回')
  form.processTypeId = record.processTypeId ?? ''
  form.id = id
  form.status = record.status!
  return form
}

/** One opening owns the entire edit; HTTP and designer cooperation stay behind this interface. */
export function createTemplateEditor(options: EditorOptions) {
  let releaseCurrent = () => {}
  function open(id?: EntityId) {
    releaseCurrent()
    const context = options.recognition.capture()
    let released = false
    let leftIntentionally = false
    const current = () => {
      const now = options.recognition.capture()
      return (
        !released &&
        !!context.entryId &&
        now.entryId === context.entryId &&
        now.generation === context.generation
      )
    }
    const request: ApiClient = (config) =>
      options.request({
        ...config,
        _recognition: context,
        _localErrors: true,
        _isRelevant: () => current() && config._isRelevant?.() !== false,
      })
    const state = reactive({
      phase: 'loading' as Phase,
      step: 0,
      form: emptyForm(),
      types: [] as ProcessTypeRecord[],
      error: '',
      uploadError: '',
      zipStatus: 'empty' as 'empty' | 'uploading' | 'ready' | 'error',
      zip: null as ZipReference | null,
      dirty: false,
      capturing: false,
      leaving: false,
      snapshot: null as PresentedProcessTemplate | null,
      get inspectionDesign(): Design | null {
        if (!state.snapshot) return null
        // form-create initializes its own rule data; every renderer gets a copy of the fixed JSON.
        const design = validDesign(
          JSON.parse(state.snapshot.formProps),
          JSON.parse(state.snapshot.formOptions),
        )
        const form = design.option.form
        return markRaw({
          rule: design.rule,
          option: {
            ...design.option,
            form: { ...(form && typeof form === 'object' ? form : {}), disabled: true },
            submitBtn: false,
            resetBtn: false,
          },
        })
      },
      get canEdit(): boolean {
        return current() && state.phase === 'ready' && !state.capturing && !state.leaving
      },
      get canNavigateSteps(): boolean {
        return (
          state.canEdit ||
          (current() &&
            !!state.snapshot &&
            ['saving', 'uncertain'].includes(state.phase) &&
            !state.leaving)
        )
      },
      get canSave(): boolean {
        return (
          state.canEdit &&
          !!state.form.name.trim() &&
          !!state.form.processTypeId &&
          state.zipStatus === 'ready' &&
          !!state.zip?.path &&
          !!state.zip?.key
        )
      },
      get canRestoreZip(): boolean {
        return state.canEdit && !!previousZip && state.zipStatus !== 'ready'
      },
    })
    let baselineDesign = ''
    let previousZip: ZipReference | null = null
    let selectedFile: File | null = null
    let uploadNumber = 0
    let confirming = false
    function release() {
      released = true
      leftIntentionally = false
      state.phase = 'closed'
      state.form = emptyForm()
      state.snapshot = null
      state.zip = null
      selectedFile = null
      previousZip = null
    }
    releaseCurrent = release
    async function designer() {
      const instance = await options.designer()
      return current() ? instance : null
    }
    async function captureDesign() {
      const instance = await designer()
      if (!instance) return false
      const design = validDesign(instance.getRule(), instance.getOption())
      if (JSON.stringify(design) !== baselineDesign) state.dirty = true
      state.form.formProps = JSON.stringify(design.rule)
      state.form.formOptions = JSON.stringify(design.option)
      return true
    }
    let readNumber = 0
    async function reload() {
      if (!current() || !['loading', 'load-error'].includes(state.phase) || state.leaving) return
      const number = ++readNumber
      state.phase = 'loading'
      state.error = ''
      try {
        const types = await request<ProcessTypeRecord[]>({
          method: 'get',
          url: '/admin/process/processType/findAll',
        })
        if (!current() || number !== readNumber) return
        state.types = Array.isArray(types.data) ? types.data : []
        if (id != null) {
          const response = await request<ProcessTemplateRecord>({
            method: 'get',
            url: `${API}/get/${id}`,
          })
          if (!current() || number !== readNumber) return
          if (response.code !== 200 || !response.data || String(response.data.id) !== String(id))
            throw new Error('审批模板不存在或读取结果与目标不一致，请重试或返回')
          if (response.data.status !== 0 && response.data.status !== 1)
            throw new Error('审批模板发布状态无法确认，请重试或返回')
          state.form = loadedForm(response.data, id)
          if (state.form.status === 1) {
            state.phase = 'readonly'
            state.error = '已发布的审批模板只读，请在列表中选择“查看”'
            options.notify(state.error, 'warning')
            options.navigate(LIST)
            return
          }
        }
        const design = validDesign(
          state.form.formProps ? JSON.parse(state.form.formProps) : [],
          state.form.formOptions ? JSON.parse(state.form.formOptions) : {},
        )
        const instance = await designer()
        if (!instance || number !== readNumber) return
        instance.setRule(design.rule)
        instance.setOption(design.option)
        baselineDesign = JSON.stringify(validDesign(instance.getRule(), instance.getOption()))
        if (state.form.processDefinitionPath && state.form.processDefinitionKey) {
          state.zip = {
            name: state.form.processDefinitionPath,
            path: state.form.processDefinitionPath,
            key: state.form.processDefinitionKey,
          }
          previousZip = { ...state.zip }
          state.zipStatus = 'ready'
        }
        state.phase = 'ready'
      } catch (error: unknown) {
        if (!current() || number !== readNumber) return
        state.phase = 'load-error'
        state.error = errorMessage(error, '审批模板读取失败，请重试')
      }
    }
    function updateBasic(
      change: Partial<
        Pick<PresentedProcessTemplate, 'name' | 'processTypeId' | 'iconUrl' | 'description'>
      >,
    ) {
      if (!state.canEdit) return
      Object.assign(state.form, change)
      state.dirty = true
    }
    async function next() {
      if (!state.canNavigateSteps) return
      if (!state.canEdit) {
        if (state.step < 2) state.step++
        return
      }
      if (state.step === 0) {
        if (!state.form.processTypeId || !state.form.name.trim()) {
          state.error = '请填写审批类型和名称'
          return
        }
        state.error = ''
        state.step = 1
      } else if (state.step === 1) {
        state.capturing = true
        try {
          if (await captureDesign()) state.step = 2
        } catch (error: unknown) {
          if (current()) {
            state.phase = 'design-error'
            state.error = errorMessage(error, '设计器无法读取表单设计，请重试或返回')
          }
        } finally {
          if (current()) state.capturing = false
        }
      } else await save()
    }
    function previous() {
      if (state.canNavigateSteps && state.step > 0) state.step--
    }
    async function selectZip(file: File) {
      if (!state.canEdit) return
      selectedFile = file
      state.dirty = true
      const number = ++uploadNumber
      state.zip = null
      const invalid = zipUploadError(file)
      state.uploadError = invalid || ''
      state.zipStatus = invalid ? 'error' : 'uploading'
      if (invalid) return
      try {
        const data = new FormData()
        data.append('file', file)
        const response = await request<{
          processDefinitionPath?: string
          processDefinitionKey?: string
        }>({
          method: 'post',
          url: `${API}/uploadProcessDefinition`,
          data,
          _isRelevant: () => number === uploadNumber,
        })
        if (!current() || number !== uploadNumber) return
        const { processDefinitionPath: path, processDefinitionKey: key } = response.data || {}
        if (
          response.code !== 200 ||
          typeof path !== 'string' ||
          !path.trim() ||
          typeof key !== 'string' ||
          !key.trim()
        )
          throw new Error('上传结果不完整，请重试或明确恢复旧 zip')
        state.zip = { name: file.name, path, key }
        previousZip = { ...state.zip }
        state.zipStatus = 'ready'
      } catch (error: unknown) {
        if (!current() || number !== uploadNumber) return
        state.zipStatus = 'error'
        state.uploadError = errorMessage(error, '流程 zip 上传失败，请重试')
      }
    }
    async function retryUpload() {
      if (selectedFile) await selectZip(selectedFile)
    }
    function removeZip() {
      if (!state.canEdit) return
      uploadNumber++
      state.zip = null
      state.zipStatus = 'empty'
      state.uploadError = ''
      selectedFile = null
      state.dirty = true
    }
    function restoreZip() {
      if (!state.canRestoreZip || !previousZip) return
      uploadNumber++
      state.zip = { ...previousZip }
      state.zipStatus = 'ready'
      state.uploadError = ''
      selectedFile = null
    }
    async function save() {
      if (!state.canSave) return
      state.phase = 'saving'
      state.error = ''
      try {
        if (!(await captureDesign())) return
      } catch (error: unknown) {
        if (current()) {
          state.phase = 'design-error'
          state.error = errorMessage(error, '设计器无法读取表单设计，请重试或返回')
        }
        return
      }
      if (!current() || !state.zip) return
      const { name, iconUrl, processTypeId, formProps, formOptions, description } = state.form
      const snapshot = {
        id: id ?? '',
        name,
        iconUrl,
        processTypeId,
        formProps,
        formOptions,
        description,
        status: 0,
        processDefinitionPath: state.zip.path,
        processDefinitionKey: state.zip.key,
      }
      if (!id) delete (snapshot as Partial<PresentedProcessTemplate>).id
      state.snapshot = { ...snapshot }
      try {
        const response = await request({
          method: id ? 'put' : 'post',
          url: `${API}/${id ? 'update' : 'save'}`,
          data: Object.freeze(snapshot),
        })
        if (!current()) return
        if (response.code !== 200) throw new Error(response.message || '无法确认模板保存结果')
        options.notify('保存成功，审批模板仍为未发布', 'success')
        release()
        options.navigate(LIST)
      } catch (error: unknown) {
        if (!current()) return
        const refused = classifySubmissionFailure(error, 'save-template') === 'refused'
        state.phase = refused ? 'ready' : 'uncertain'
        if (refused) state.snapshot = null
        state.error = errorMessage(error, '无法确认模板保存结果')
      }
    }
    function dismissPendingConfirm() {
      if (state.leaving || confirming) options.dismissConfirm()
    }
    async function requestLeave() {
      const occupancy = options.occupancy()
      if (!current() || occupancy.action === 'redirect') {
        dismissPendingConfirm()
        release()
        return true
      }
      if (state.leaving) return false
      state.leaving = true
      try {
        if (state.phase === 'ready') {
          try {
            await captureDesign()
          } catch {
            state.dirty = true
          }
        }
        if (!current()) return true
        const message =
          state.phase === 'saving'
            ? '审批模板正在保存。离开不撤销已提交的保存，原请求仍可能生效，确认离开？'
            : state.phase === 'uncertain'
              ? '原保存仍可能生效，再次新建或修改可能重复创建或覆盖。离开只清理本地内容，不代表保存失败，确认离开？'
              : state.dirty
                ? '当前有未保存修改，离开将丢弃本次编辑，确认离开？'
                : ''
        if (message && !(await options.confirm(message, 'leave'))) return !current()
        if (current()) {
          release()
          leftIntentionally = true
        }
        return true
      } finally {
        state.leaving = false
      }
    }
    async function environmentChanged() {
      if (released) return
      if (!current()) {
        dismissPendingConfirm()
        const now = options.recognition.capture()
        release()
        if (now.entryId) options.replace(getDefaultPostLoginRoute())
        return
      }
      const occupancy = options.occupancy()
      if (occupancy.action === 'redirect') {
        dismissPendingConfirm()
        release()
        options.replace(occupancy.to)
      }
    }
    async function back() {
      if (!current()) return
      if ((await requestLeave()) && leftIntentionally && releaseCurrent === release) {
        const now = options.recognition.capture()
        if (now.entryId === context.entryId && now.generation === context.generation)
          options.navigate(LIST)
      }
    }
    function openList() {
      if (current() && state.phase === 'uncertain') options.openList(LIST)
    }
    async function endEditing() {
      if (!current() || state.phase !== 'uncertain' || confirming) return false
      confirming = true
      try {
        const accepted = await options.confirm(
          '原保存仍可能生效，再次新建或修改可能重复创建或覆盖。结束本次编辑只清理本地内容，不代表保存失败。',
          'end-editing',
        )
        if (!current() || !accepted) return false
        release()
        options.navigate(LIST)
        return true
      } finally {
        confirming = false
      }
    }
    async function retryDesigner() {
      if (!current() || state.phase !== 'design-error' || state.capturing) return
      state.capturing = true
      try {
        if (await captureDesign()) {
          state.phase = 'ready'
          state.error = ''
        }
      } catch (error: unknown) {
        if (current()) state.error = errorMessage(error, '设计器无法读取表单设计，请重试或返回')
      } finally {
        if (current()) state.capturing = false
      }
    }
    const ready = reload()
    return {
      state: readonly(state),
      ready,
      updateBasic,
      next,
      previous,
      selectZip,
      retryUpload,
      removeZip,
      restoreZip,
      save,
      reload,
      retryDesigner,
      openList,
      endEditing,
      requestLeave,
      environmentChanged,
      back,
      dispose: release,
    }
  }
  return { open }
}
export type TemplateEditorOpening = ReturnType<ReturnType<typeof createTemplateEditor>['open']>
