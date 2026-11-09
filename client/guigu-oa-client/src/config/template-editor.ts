import { markRaw, reactive, readonly } from 'vue'
import { classifySubmissionFailure } from '@/request/submission-outcome'
import { getDefaultPostLoginRoute } from '@/navigation/constants'
import { processTemplateListPath } from '@/router/paths'
import type { RequestScope } from '@/request/request-scope'
import type {
  ApiRequestConfig,
  EntityId,
  PresentedProcessTemplate,
  ProcessTypeRecord,
  ProcessTemplateRecord,
} from '@/types/domain'
import { requiredRule, type RequiredRule } from './form-rules'
import { emptyForm, zipUploadError } from './process-template'

const API = '/admin/process/processTemplate'
const LIST = processTemplateListPath()
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
interface EditorOptions {
  openScope: () => RequestScope
  entryId(): string
  designer(): Promise<Designer>
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

/** 节点审批人来源（ADR 0069）：BPMN 写死不是规则行——没有规则的节点沿用 zip 里的 assignee。 */
export const APPROVER_SOURCE_OPTIONS = [
  { value: 'BPMN_ASSIGNEE', label: 'BPMN 写死' },
  { value: 'DEPT_LEADER', label: '部门负责人' },
  { value: 'ROLE', label: '角色' },
  { value: 'POST', label: '岗位' },
] as const

export type ApproverSourceChoice = (typeof APPROVER_SOURCE_OPTIONS)[number]['value'] | ''
type RuleSourceType = 'DEPT_LEADER' | 'ROLE' | 'POST'

/** 配置页一个节点的审批人选择行：key 是 BPMN userTask id，来源与目标由管理员编辑。 */
export interface ApproverRow {
  key: string
  name: string
  assignee: string
  sourceType: ApproverSourceChoice
  targetId: EntityId | ''
}

/** 角色/岗位目标选项（id + 展示名）。 */
export interface ApproverTargetOption {
  id: EntityId
  name: string
}

interface ApproverNodeRef {
  key: string
  name: string
  assignee: string
}

interface ApproverRuleLine {
  taskDefinitionKey: string
  sourceType: RuleSourceType
  targetId: EntityId | ''
}

function errorMessage(error: unknown, fallback: string) {
  return error instanceof Error ? error.message : fallback
}
function validDesign(rule: unknown, option: unknown): Design {
  if (!Array.isArray(rule) || !option || typeof option !== 'object' || Array.isArray(option))
    throw new Error('表单设计内容损坏或不兼容，请重试读取或返回')
  return { rule, option: option as Record<string, unknown> }
}

function isRuleSource(value: unknown): value is RuleSourceType {
  return value === 'DEPT_LEADER' || value === 'ROLE' || value === 'POST'
}

function targetIdOf(value: unknown): EntityId | '' {
  if (typeof value === 'number' && Number.isFinite(value)) return value
  if (typeof value === 'string' && /^\d+$/.test(value.trim())) return value.trim()
  return ''
}

/** 一次读取的形状校验（GET approverRules）：不认识的数据当读取失败，不静默丢配置。 */
function parseApproverConfig(data: unknown): {
  nodes: ApproverNodeRef[]
  rules: ApproverRuleLine[]
} {
  const fail = () => new Error('审批人配置读取结果不完整，请重试')
  if (!data || typeof data !== 'object' || Array.isArray(data)) throw fail()
  const { userTasks, rules } = data as { userTasks?: unknown; rules?: unknown }
  if (!Array.isArray(userTasks) || !Array.isArray(rules)) throw fail()
  const nodes = userTasks.map((task): ApproverNodeRef => {
    if (!task || typeof task !== 'object') throw fail()
    const key = (task as { key?: unknown }).key
    if (typeof key !== 'string' || !key.trim()) throw fail()
    const name = (task as { name?: unknown }).name
    const assignee = (task as { assignee?: unknown }).assignee
    return {
      key,
      name: typeof name === 'string' ? name : '',
      assignee: typeof assignee === 'string' ? assignee : '',
    }
  })
  const lines = rules.map((rule): ApproverRuleLine => {
    if (!rule || typeof rule !== 'object') throw fail()
    const taskDefinitionKey = (rule as { taskDefinitionKey?: unknown }).taskDefinitionKey
    const sourceType = (rule as { sourceType?: unknown }).sourceType
    if (typeof taskDefinitionKey !== 'string' || !taskDefinitionKey.trim()) throw fail()
    if (!isRuleSource(sourceType)) throw fail()
    const targetId =
      sourceType === 'DEPT_LEADER' ? '' : targetIdOf((rule as { targetId?: unknown }).targetId)
    if (sourceType !== 'DEPT_LEADER' && targetId === '') throw fail()
    return { taskDefinitionKey, sourceType, targetId }
  })
  return { nodes, rules: lines }
}

/** 上传响应里的节点清单是已采用 zip 的权威解析结果；形状不齐时按「解析不出」处理。 */
function parseUploadedUserTasks(value: unknown): ApproverNodeRef[] {
  if (!Array.isArray(value)) return []
  return value.flatMap((task): ApproverNodeRef[] => {
    if (!task || typeof task !== 'object') return []
    const key = (task as { key?: unknown }).key
    if (typeof key !== 'string' || !key.trim()) return []
    const name = (task as { name?: unknown }).name
    const assignee = (task as { assignee?: unknown }).assignee
    return [
      {
        key,
        name: typeof name === 'string' ? name : '',
        assignee: typeof assignee === 'string' ? assignee : '',
      },
    ]
  })
}

/** 目标选项投影：角色读 roleName、岗位读 name，只留有 id 的行。 */
function targetOptions(records: unknown, nameField: 'roleName' | 'name'): ApproverTargetOption[] {
  if (!Array.isArray(records)) return []
  return records.flatMap((record): ApproverTargetOption[] => {
    if (!record || typeof record !== 'object') return []
    const id = (record as { id?: unknown }).id
    if (id == null || id === '') return []
    const name = (record as Record<string, unknown>)[nameField]
    return [{ id: id as EntityId, name: typeof name === 'string' ? name : '' }]
  })
}

/**
 * 节点行装配：已编辑选择优先（换 zip 后同 key 保留），其次落库规则，
 * 都没有时回落 BPMN 写死；无 assignee 且无规则的节点是「未配置」，发布会被拒绝。
 */
function approverRows(
  nodes: ApproverNodeRef[],
  previous: ApproverRow[],
  rules: ApproverRuleLine[],
): ApproverRow[] {
  const choiceByKey = new Map<
    string,
    { sourceType: ApproverSourceChoice; targetId: EntityId | '' }
  >()
  for (const row of previous)
    choiceByKey.set(row.key, { sourceType: row.sourceType, targetId: row.targetId })
  for (const rule of rules)
    if (!choiceByKey.has(rule.taskDefinitionKey))
      choiceByKey.set(rule.taskDefinitionKey, {
        sourceType: rule.sourceType,
        targetId: rule.targetId,
      })
  return nodes.map((node) => {
    const choice = choiceByKey.get(node.key)
    const sourceType = choice?.sourceType || (node.assignee ? 'BPMN_ASSIGNEE' : '')
    return {
      key: node.key,
      name: node.name || node.key,
      assignee: node.assignee,
      sourceType,
      targetId:
        choice && (choice.sourceType === 'ROLE' || choice.sourceType === 'POST')
          ? choice.targetId
          : '',
    }
  })
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
    const scope = options.openScope()
    let released = false
    let leftIntentionally = false
    const current = () => !released && scope.current()
    const request = <TData = unknown>(config: ApiRequestConfig, isRelevant?: () => boolean) =>
      scope.request<TData>(config, isRelevant ? () => current() && isRelevant() : () => current())
    const state = reactive({
      phase: 'loading' as Phase,
      step: 0,
      form: emptyForm(),
      types: [] as ProcessTypeRecord[],
      error: '',
      uploadError: '',
      zipStatus: 'empty' as 'empty' | 'uploading' | 'ready' | 'error',
      zip: null as ZipReference | null,
      approvers: [] as ApproverRow[],
      approverStatus: 'idle' as 'idle' | 'loading' | 'ready' | 'error',
      approverError: '',
      approverSaving: false,
      approverSaveError: '',
      approverDirty: false,
      approverRoles: [] as ApproverTargetOption[],
      approverPosts: [] as ApproverTargetOption[],
      approverOptionsError: '',
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
      /** 已采用 zip 但解析不出任何 BPMN 节点：可保存，发布会被拒绝（明示不可发布）。 */
      get zipUnparsable(): boolean {
        return (
          state.zipStatus === 'ready' &&
          state.approverStatus === 'ready' &&
          state.approvers.length === 0
        )
      },
      /** 落库规则引用了当前节点清单里已不存在的节点：保存审批人配置后清除。 */
      get staleApproverKeys(): string[] {
        if (state.zipStatus !== 'ready') return []
        const keys = new Set(state.approvers.map((row) => row.key))
        return savedRuleKeys.filter((key) => !keys.has(key))
      },
      get canSaveApprovers(): boolean {
        return (
          state.canEdit &&
          id != null &&
          state.approverStatus === 'ready' &&
          !state.approverSaving &&
          state.approvers.length > 0 &&
          state.approvers.every(
            (row) =>
              (row.sourceType !== 'ROLE' && row.sourceType !== 'POST') || row.targetId !== '',
          )
        )
      },
    })
    let baselineDesign = ''
    let previousZip: ZipReference | null = null
    let selectedFile: File | null = null
    let uploadNumber = 0
    let confirming = false
    // 落库规则的节点 key（DB 真相）：换 zip 后用来给出「陈旧规则」警告，保存成功后更新。
    let savedRuleKeys: string[] = []
    function release() {
      released = true
      leftIntentionally = false
      state.phase = 'closed'
      state.form = emptyForm()
      state.snapshot = null
      state.zip = null
      state.approvers = []
      state.approverStatus = 'idle'
      state.approverError = ''
      state.approverSaving = false
      state.approverSaveError = ''
      state.approverDirty = false
      state.approverRoles = []
      state.approverPosts = []
      state.approverOptionsError = ''
      selectedFile = null
      previousZip = null
      savedRuleKeys = []
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
        if (id != null) void loadApprovers()
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
          userTasks?: unknown
        }>(
          {
            method: 'post',
            url: `${API}/uploadProcessDefinition`,
            data,
          },
          () => number === uploadNumber,
        )
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
        // 上传响应的 userTasks 是已采用 zip 的权威节点清单：作废在途配置读取，
        // 同 key 的选择保留（previous 行已含落库规则的回显）。
        approverReadNumber++
        state.approvers = approverRows(
          parseUploadedUserTasks(response.data?.userTasks),
          state.approvers,
          [],
        )
        state.approverStatus = 'ready'
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

    // 审批人配置（ADR 0069，#286）：读取、编辑与整体替换保存。行与选择都在 state.approvers，
    // 换 zip 的节点清单替换行（同 key 保留选择），保存是把当前配置整体 PUT 给服务端。
    // 角色/岗位目标选项与节点读取互不阻塞：选项失败只降级目标下拉，不挡配置回显。
    let approverReadNumber = 0
    async function loadTargetOptions(number: number) {
      // 目标选项走 process 模块的专用只读端点（启用角色 + 启用岗位，#286 审查轮），
      // 不再借道 /admin/system/sysRole/findAll 与 /admin/system/sysUser/posts。
      try {
        const response = await request<{ roles: unknown; posts: unknown }>({
          method: 'get',
          url: `${API}/ruleTargets`,
        })
        if (!current() || number !== approverReadNumber) return
        state.approverRoles = targetOptions(response.data?.roles, 'name')
        state.approverPosts = targetOptions(response.data?.posts, 'name')
        state.approverOptionsError = ''
      } catch {
        if (!current() || number !== approverReadNumber) return
        state.approverRoles = []
        state.approverPosts = []
        state.approverOptionsError = '角色或岗位选项读取失败，目标下拉可能为空'
      }
    }
    async function loadApprovers() {
      if (id == null || !current() || state.leaving) return
      const number = ++approverReadNumber
      state.approverStatus = 'loading'
      state.approverError = ''
      void loadTargetOptions(number)
      try {
        const config = await request<unknown>({ method: 'get', url: `${API}/approverRules/${id}` })
        if (!current() || number !== approverReadNumber) return
        if (config.code !== 200) throw new Error(config.message || '审批人配置读取失败，请重试')
        const parsed = parseApproverConfig(config.data)
        state.approvers = approverRows(parsed.nodes, [], parsed.rules)
        savedRuleKeys = parsed.rules.map((rule) => rule.taskDefinitionKey)
        state.approverStatus = 'ready'
      } catch (error: unknown) {
        if (!current() || number !== approverReadNumber) return
        state.approverStatus = 'error'
        state.approverError = errorMessage(error, '审批人配置读取失败，请重试')
      }
    }
    function retryApprovers() {
      if (!current() || state.approverStatus !== 'error') return
      return loadApprovers()
    }
    function updateApprover(
      key: string,
      change: Partial<Pick<ApproverRow, 'sourceType' | 'targetId'>>,
    ) {
      if (!state.canEdit || id == null || state.approverStatus !== 'ready') return
      const row = state.approvers.find((item) => item.key === key)
      if (!row) return
      if (change.sourceType != null && change.sourceType !== row.sourceType) {
        row.sourceType = change.sourceType
        // 部门负责人按发起人所在部门解析，服务端拒绝指定目标（ApproverRuleRequest）。
        if (change.sourceType !== 'ROLE' && change.sourceType !== 'POST') row.targetId = ''
      }
      if (change.targetId != null) row.targetId = change.targetId
      state.approverDirty = true
    }
    async function saveApprovers() {
      if (!state.canSaveApprovers) return
      state.approverSaving = true
      state.approverSaveError = ''
      try {
        const lines = state.approvers
          .filter((row): row is ApproverRow & { sourceType: RuleSourceType } =>
            isRuleSource(row.sourceType),
          )
          .map((row) =>
            row.sourceType === 'DEPT_LEADER'
              ? { taskDefinitionKey: row.key, sourceType: row.sourceType }
              : { taskDefinitionKey: row.key, sourceType: row.sourceType, targetId: row.targetId },
          )
        const response = await request({
          method: 'put',
          url: `${API}/approverRules/${id}`,
          data: lines,
        })
        if (!current()) return
        if (response.code !== 200) throw new Error(response.message || '无法确认审批人配置保存结果')
        savedRuleKeys = lines.map((line) => line.taskDefinitionKey)
        state.approverDirty = false
        options.notify('审批人配置已保存', 'success')
      } catch (error: unknown) {
        if (!current()) return
        state.approverSaveError = errorMessage(error, '审批人配置保存失败，请重试')
      } finally {
        if (current()) state.approverSaving = false
      }
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
      if (!current()) {
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
              : state.dirty || state.approverDirty
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
        const liveEntry = options.entryId()
        release()
        if (liveEntry) options.replace(getDefaultPostLoginRoute())
      }
    }
    async function back() {
      if (!current()) return
      if (
        (await requestLeave()) &&
        leftIntentionally &&
        releaseCurrent === release &&
        scope.current()
      )
        options.navigate(LIST)
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
      updateApprover,
      saveApprovers,
      retryApprovers,
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
/** 基本设置两步的必填与文案镜像 ProcessTemplateSaveRequest(ADR 0034);拦截门仍在 canSave。 */
export function formRules(): Record<'name' | 'processTypeId', RequiredRule[]> {
  return {
    name: [requiredRule('审批模板名称不能为空')],
    processTypeId: [requiredRule('审批类型不能为空')],
  }
}

export type TemplateEditorOpening = ReturnType<ReturnType<typeof createTemplateEditor>['open']>
