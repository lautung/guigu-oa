import { expect, type APIRequestContext } from '@playwright/test'

import { bpmnXml, buildZip, marker, runId } from '../../e2e-stack/helpers/stack'
import { apiBaseURL } from './env'

/**
 * 接口套件的共享 HTTP harness（#120 §1）：登录、带 token 调用、业务码断言与造数助手。
 *
 * `body.code` 是权威契约（#120 §4）：成功 200；业务失败 201 / 205 / 208 / 209 / 403。
 * HTTP 状态码按 #145 修复后的约定：认证失败 401，容器级 404/405；业务失败仍是 HTTP 200，
 * 状态码只在 f-http-semantics.spec.ts 里单独断言。
 */

export const PASSWORD = '111111'

export const ADMIN = 'admin'
export const PLAIN_USER = 'user'
export const ALICE = 'alice'
export const BOB = 'bob'
export const CAROL = 'carol'

export type Method = 'get' | 'post' | 'put' | 'delete'

export interface Envelope<T = unknown> {
  code: number
  message?: string
  data: T
}

export interface ApiResult<T = unknown> {
  status: number
  body: Envelope<T>
  headers: Record<string, string>
}

export interface TokenPair {
  token: string
  refreshToken: string
}

export interface PageResult<T> {
  records: T[]
  total: number
  pages?: number
  current?: number
  size?: number
}

export interface UserRecord {
  id: number
  username: string
  name?: string
  phone?: string
  status?: number
  password?: string
}

export interface RoleRecord {
  id: number
  roleName: string
  roleCode?: string
  description?: string
}

export interface MenuRecord {
  id: number
  parentId: number
  name: string
  type: number
  path: string
  component?: string
  sortValue?: number
  status?: number
  select?: boolean
  children?: MenuRecord[]
}

export interface ConfigMenu {
  path: string
  parentPath?: string | null
  title: string
  type: number
  hidden: boolean
}

export interface InfoPayload {
  name?: string
  roles?: string
  avatar?: string
  configMenus: ConfigMenu[]
  buttons: string[]
}

export interface TemplateRecord {
  id: number
  name: string
  status: number
  processTypeId: number
  processDefinitionKey?: string
  processDefinitionPath?: string
  availableForInitiation?: boolean
  iconUrl?: string
  description?: string
  formProps?: string
  formOptions?: string
}

export interface ProcessRecord {
  id: number
  taskId?: string
  userId?: number
  title?: string
  status?: number
  formValues?: string
  processTemplateName?: string
  currentAuditor?: string
  createTime?: string
}

export interface AttachmentRecord {
  id: number
  fileName: string
  contentType?: string
  sizeBytes?: number
}

export interface UploadGrant {
  attachmentId: number
  uploadUrl: string
  headers?: Record<string, string>
  expiresAt?: string
}

export interface ProcessAttachmentList {
  attachments: AttachmentRecord[]
  canUpload: boolean
}

export interface RecipientRecord {
  id: number
  name: string
}

export interface StartableGroup {
  id: number
  name: string
  processTemplateList: TemplateRecord[]
}

let sequence = 0

/**
 * 造数用唯一名：`username` / `role_name` 列只有 VARCHAR(20)，所以压缩到 ≤16 字符。
 * 同一轮共用 runId，跨轮不撞。
 */
export function unique(tag: string): string {
  sequence += 1
  const prefix = tag.replace(/[^a-zA-Z0-9]/g, '').slice(0, 8)
  const suffix = `${runId().slice(-4)}${sequence.toString(36)}${Math.random()
    .toString(36)
    .slice(2, 4)}`
  return `${prefix}${suffix}`
}

export function expectCode(result: Envelope, expected: number, context = ''): void {
  expect(result.code, `${context} code=${result.code} message=${result.message ?? ''}`).toBe(
    expected,
  )
}

/**
 * 负例专用：多条分支共用 201 时，只断言 code 会被更早的拒绝遮蔽。
 * 传稳定的消息片段，确保走到的确实是命名分支。
 */
export function expectCodeAndMessage(
  result: Envelope,
  expected: number,
  message: string,
  context = '',
): void {
  expectCode(result, expected, context)
  expect(result.message, `${context} message=${result.message ?? ''}`).toContain(message)
}

export class Api {
  private readonly tokens = new Map<string, string>()

  constructor(private readonly request: APIRequestContext) {}

  private url(path: string): string {
    return `${apiBaseURL}${path.startsWith('/') ? path : `/${path}`}`
  }

  async raw<T = unknown>(
    method: Method,
    path: string,
    options: { token?: string; data?: unknown; headers?: Record<string, string> } = {},
  ): Promise<ApiResult<T>> {
    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
      ...options.headers,
    }
    if (options.token) headers.token = options.token
    const response = await this.request.fetch(this.url(path), {
      method,
      headers,
      data: options.data === undefined ? undefined : JSON.stringify(options.data),
    })
    const status = response.status()
    let body: Envelope<T>
    try {
      body = (await response.json()) as Envelope<T>
    } catch {
      body = { code: -1, message: `非 JSON 响应 HTTP ${status}`, data: null as unknown as T }
    }
    return { status, body, headers: response.headers() }
  }

  /** 匿名调用：登录、刷新、登出与错误形状用。 */
  async anonymous<T = unknown>(
    method: Method,
    path: string,
    data?: unknown,
  ): Promise<ApiResult<T>> {
    return this.raw<T>(method, path, { data })
  }

  async login(username: string, password = PASSWORD): Promise<Envelope<TokenPair>> {
    const { body } = await this.raw<TokenPair>('post', '/admin/system/index/login', {
      data: { username, password },
    })
    return body
  }

  async token(username: string): Promise<string> {
    const cached = this.tokens.get(username)
    if (cached) return cached
    const body = await this.login(username)
    if (body.code !== 200 || !body.data?.token) {
      throw new Error(`登录失败 ${username}: code=${body.code} ${body.message ?? ''}`)
    }
    this.tokens.set(username, body.data.token)
    return body.data.token
  }

  forget(username: string): void {
    this.tokens.delete(username)
  }

  async call<T>(user: string, method: Method, path: string, data?: unknown): Promise<T> {
    const result = await this.raw<T>(method, path, { token: await this.token(user), data })
    if (result.body.code !== 200) {
      throw new Error(
        `${method.toUpperCase()} ${path} → code=${result.body.code} HTTP ${result.status}: ${
          result.body.message ?? ''
        }`,
      )
    }
    return result.body.data
  }

  async code(user: string, method: Method, path: string, data?: unknown): Promise<number> {
    return (await this.raw(method, path, { token: await this.token(user), data })).body.code
  }

  async envelope<T = unknown>(
    user: string,
    method: Method,
    path: string,
    data?: unknown,
  ): Promise<ApiResult<T>> {
    return this.raw<T>(method, path, { token: await this.token(user), data })
  }

  /** 流程 zip 上传（multipart，字段名 `file`）。 */
  async uploadProcessZip(
    user: string,
    file: { name: string; buffer: Buffer },
  ): Promise<ApiResult<{ processDefinitionPath: string; processDefinitionKey: string }>> {
    const response = await this.request.post(
      this.url('/admin/process/processTemplate/uploadProcessDefinition'),
      {
        headers: { token: await this.token(user) },
        multipart: {
          file: { name: file.name, mimeType: 'application/zip', buffer: file.buffer },
        },
      },
    )
    const status = response.status()
    let body: Envelope<{ processDefinitionPath: string; processDefinitionKey: string }>
    try {
      body = (await response.json()) as Envelope<{
        processDefinitionPath: string
        processDefinitionKey: string
      }>
    } catch {
      body = {
        code: -1,
        message: `非 JSON 响应 HTTP ${status}`,
        data: null as unknown as { processDefinitionPath: string; processDefinitionKey: string },
      }
    }
    return { status, body, headers: response.headers() }
  }

  /** 直传对象存储（预签名 PUT），用于附件正例。 */
  async putObject(url: string, headers: Record<string, string> | undefined, body: Buffer) {
    const response = await this.request.fetch(url, {
      method: 'put',
      headers: headers ?? {},
      data: body,
    })
    if (!response.ok()) {
      throw new Error(`对象存储 PUT HTTP ${response.status()}`)
    }
  }
}

// ---------------------------------------------------------------------------
// 造数助手（#120 §5：可回滚对象用 API 造数并在 finally 自清理）
// ---------------------------------------------------------------------------

export async function createUser(api: Api, username: string): Promise<UserRecord> {
  await api.call(ADMIN, 'post', '/admin/system/sysUser/save', {
    username,
    name: username,
    password: PASSWORD,
    phone: '',
    status: 1,
  })
  const page = await api.call<PageResult<UserRecord>>(
    ADMIN,
    'get',
    `/admin/system/sysUser/1/50?keyword=${encodeURIComponent(username)}`,
  )
  const found = page.records.find((record) => record.username === username)
  if (!found) throw new Error(`创建后列表里找不到用户 ${username}`)
  return found
}

export async function deleteUser(api: Api, userId: number): Promise<void> {
  await api.call(ADMIN, 'delete', `/admin/system/sysUser/remove/${userId}`)
}

export async function createRole(api: Api, roleName: string): Promise<RoleRecord> {
  await api.call(ADMIN, 'post', '/admin/system/sysRole/save', {
    roleName,
    roleCode: unique('code'),
    description: '接口测试临时角色',
  })
  const page = await api.call<PageResult<RoleRecord>>(
    ADMIN,
    'get',
    `/admin/system/sysRole/1/50?roleName=${encodeURIComponent(roleName)}`,
  )
  const found = page.records.find((record) => record.roleName === roleName)
  if (!found) throw new Error(`创建后列表里找不到角色 ${roleName}`)
  return found
}

/** 清理要幂等：角色已删时 remove 返回 201（「失败」），清理不应因此翻红。 */
export async function deleteRole(api: Api, roleId: number): Promise<void> {
  const code = await api.code(ADMIN, 'delete', `/admin/system/sysRole/remove/${roleId}`)
  if (code !== 200 && code !== 201) throw new Error(`删除角色 ${roleId} 失败 code=${code}`)
}

/** doAssign 可能因提交后的缓存失效返回 201（ADR 0015 / submission-outcome.ts），两种都算提交成功。 */
export async function assignRoles(api: Api, userId: number, roleIdList: number[]): Promise<void> {
  const code = await api.code(ADMIN, 'post', '/admin/system/sysRole/doAssign', {
    userId,
    roleIdList,
  })
  if (code !== 200 && code !== 201) throw new Error(`sysRole/doAssign 失败 code=${code}`)
}

export async function assignMenus(api: Api, roleId: number, menuIdList: number[]): Promise<void> {
  await api.call(ADMIN, 'post', '/admin/system/sysMenu/doAssign', { roleId, menuIdList })
}

export async function processTypeId(api: Api, name: string): Promise<number> {
  const types = await api.call<{ id: number; name: string }[]>(
    ADMIN,
    'get',
    '/admin/process/processType/findAll',
  )
  const found = types.find((item) => item.name === name)
  if (!found) throw new Error(`找不到审批类型 ${name}`)
  return found.id
}

export async function createUnpublishedTemplate(api: Api, name: string): Promise<number> {
  const typeId = await processTypeId(api, '演示审批')
  await api.call(ADMIN, 'post', '/admin/process/processTemplate/save', {
    name,
    iconUrl: '',
    processTypeId: typeId,
    description: '接口测试临时模板',
    formProps: JSON.stringify([
      { type: 'input', field: 'reason', title: '请假原因', $required: true },
      { type: 'input', field: 'days', title: '请假天数', $required: true },
    ]),
    formOptions: JSON.stringify({ submitBtn: true, form: { labelPosition: 'top' } }),
  })
  const page = await api.call<PageResult<TemplateRecord>>(
    ADMIN,
    'get',
    '/admin/process/processTemplate/1/500',
  )
  const found = page.records.find((record) => record.name === name)
  if (!found) throw new Error(`创建后列表里找不到模板 ${name}`)
  return found.id
}

export async function getTemplate(api: Api, id: number): Promise<TemplateRecord> {
  return api.call<TemplateRecord>(ADMIN, 'get', `/admin/process/processTemplate/get/${id}`)
}

/**
 * 造一条已发布模板：上传 zip（文件名 = BPMN process id）→ update 绑定 → publish。
 * 已发布模板不可删除（ADR 0005），每轮留下一条 runId 命名的残留（#120 §5 快照组）。
 */
export async function publishTemplate(api: Api, name: string): Promise<number> {
  const id = await createUnpublishedTemplate(api, name)
  const processKey = `api${runId()}${Math.random().toString(36).slice(2, 6)}`
  const zip = buildZip([{ name: 'process.bpmn20.xml', data: bpmnXml(processKey) }])
  const upload = await api.uploadProcessZip(ADMIN, { name: `${processKey}.zip`, buffer: zip })
  if (upload.body.code !== 200 || !upload.body.data) {
    throw new Error(`上传流程 zip 失败 code=${upload.body.code} ${upload.body.message ?? ''}`)
  }
  const template = await getTemplate(api, id)
  await api.call(ADMIN, 'put', '/admin/process/processTemplate/update', {
    ...template,
    processDefinitionPath: upload.body.data.processDefinitionPath,
    processDefinitionKey: upload.body.data.processDefinitionKey,
  })
  await api.call(ADMIN, 'post', `/admin/process/processTemplate/publish/${id}`)
  return id
}

export async function demoTemplate(
  api: Api,
): Promise<{ id: number; processTypeId: number; name: string }> {
  const groups = await api.call<StartableGroup[]>(ALICE, 'get', '/admin/process/findStartable')
  for (const group of groups) {
    for (const template of group.processTemplateList ?? []) {
      if (template.name === '演示请假申请' && template.availableForInitiation !== false) {
        return { id: template.id, processTypeId: group.id, name: template.name }
      }
    }
  }
  throw new Error('findStartable 里没有可发起的「演示请假申请」：确认 Compose 栈已按 #128 发布')
}

export async function poll<T>(
  timeoutMs: number,
  intervalMs: number,
  attempt: () => Promise<T | undefined>,
): Promise<T | undefined> {
  const deadline = Date.now() + timeoutMs
  for (;;) {
    const value = await attempt()
    if (value !== undefined && value !== null) return value
    if (Date.now() > deadline) return undefined
    await new Promise((resolve) => setTimeout(resolve, intervalMs))
  }
}

export async function findMarked(
  api: Api,
  user: string,
  kind: 'pending' | 'processed' | 'started' | 'copied',
  text: string,
): Promise<ProcessRecord | undefined> {
  const paths = {
    pending: 'findPending',
    processed: 'findProcessed',
    started: 'findStarted',
    copied: 'findCarbonCopied',
  } as const
  for (let page = 1; page <= 20; page += 1) {
    const result = await api.call<PageResult<ProcessRecord>>(
      user,
      'get',
      `/admin/process/${paths[kind]}/${page}/100`,
    )
    const found = result.records.find((record) => (record.formValues ?? '').includes(text))
    if (found) return found
    if (page * 100 >= result.total) break
  }
  return undefined
}

export interface StartOptions {
  attachmentIds?: number[]
  attachmentDraftId?: string
  ccUserIds?: number[]
  templateId?: number
  processTypeId?: number
  idempotencyKey?: string
}

/** 以某人身份发起一条带唯一标记的审批，返回已发起列表里的记录（含 id）。 */
export async function startApproval(
  api: Api,
  user: string,
  options: StartOptions = {},
): Promise<ProcessRecord> {
  const template =
    options.templateId === undefined || options.processTypeId === undefined
      ? await demoTemplate(api)
      : { id: options.templateId, processTypeId: options.processTypeId, name: '' }
  // 每次调用必须有独立标记：同一轮里发起多条审批时 marker('接口') 会完全相同。
  const text = `${marker('接口')}${Math.random().toString(36).slice(2, 8)}`
  await api.call(user, 'post', '/admin/process/startUp', {
    processTemplateId: template.id,
    processTypeId: template.processTypeId,
    formValues: JSON.stringify({
      formData: { reason: text, days: 1 },
      formShowData: { 请假原因: text, 请假天数: 1 },
    }),
    attachmentIds: options.attachmentIds ?? [],
    ccUserIds: options.ccUserIds ?? [],
    ...(options.attachmentDraftId ? { attachmentDraftId: options.attachmentDraftId } : {}),
    ...(options.idempotencyKey ? { idempotencyKey: options.idempotencyKey } : {}),
  })
  const record = await poll(20_000, 300, () => findMarked(api, user, 'started', text))
  if (!record) throw new Error(`startUp 后已在已发起列表找不到标记 ${text}`)
  return record
}

export function flattenMenus(nodes: MenuRecord[]): MenuRecord[] {
  const all: MenuRecord[] = []
  const walk = (list: MenuRecord[]): void => {
    for (const node of list) {
      all.push(node)
      if (node.children?.length) walk(node.children)
    }
  }
  walk(nodes)
  return all
}
