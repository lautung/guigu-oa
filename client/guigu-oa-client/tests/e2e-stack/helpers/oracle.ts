import type { APIRequestContext } from '@playwright/test'

import { bpmnXml, buildZip } from './stack'

/**
 * 真实 API oracle（#121 §6）：直接调用 Compose 里的 lautung-oa-server（经 nginx 的 /dev-api），
 * 用另一个账号的视角核对 UI 操作产生的持久化结果。不读 MySQL，不打 mock。
 *
 * body.code 是权威契约（见 #120 决议）：code !== 200 一律抛 ApiFailure。
 */

export const PASSWORD = '111111'

export class ApiFailure extends Error {
  constructor(
    readonly code: number,
    message: string,
    readonly httpStatus: number,
  ) {
    super(`API code=${code} (HTTP ${httpStatus}): ${message}`)
  }
}

interface ApiResponse<T> {
  code: number
  message?: string
  data: T
}

export interface PageResult<T> {
  records: T[]
  total: number
  pages: number
}

export interface ProcessRecord {
  id: number
  taskId?: string
  title?: string
  name?: string
  processTypeName?: string
  processTemplateName?: string
  status: number
  formValues?: string
  createTime?: string
  /** 待处理三源合成标记（#288）：行来源（APPROVE/CANDIDATE/ADD_SIGN）与挂起/催办位。 */
  actionKind?: string
  suspended?: boolean
  urged?: boolean
}

export interface AttachmentRecord {
  id: number
  fileName: string
  contentType?: string
  sizeBytes?: number
}

export interface AttachmentGrant {
  attachmentId: number
  uploadUrl: string
  headers?: Record<string, string>
}

export type WorkListKind = 'pending' | 'processed' | 'started' | 'copied'

const WORK_LIST_PATHS: Record<WorkListKind, string> = {
  pending: 'findPending',
  processed: 'findProcessed',
  started: 'findStarted',
  copied: 'findCarbonCopied',
}

export class Oracle {
  private tokens = new Map<string, string>()

  constructor(private readonly request: APIRequestContext) {}

  private async token(user: string): Promise<string> {
    const cached = this.tokens.get(user)
    if (cached) return cached
    const body = await this.raw('post', '/admin/system/index/login', {
      username: user,
      password: PASSWORD,
    })
    if (body.code !== 200 || !body.data?.token) {
      throw new Error(`oracle 登录失败 ${user}: code=${body.code} ${body.message ?? ''}`)
    }
    this.tokens.set(user, body.data.token)
    return body.data.token
  }

  /** 登录失败场景专用：返回原始响应体，不缓存、不抛业务错误。 */
  async raw(
    method: 'get' | 'post' | 'put' | 'delete',
    path: string,
    data?: unknown,
    user?: string,
  ): Promise<ApiResponse<unknown>> {
    const headers: Record<string, string> = { 'Content-Type': 'application/json' }
    if (user) headers.token = await this.token(user)
    const response = await this.request.fetch(`/dev-api${path}`, {
      method,
      headers,
      data: data === undefined ? undefined : JSON.stringify(data),
    })
    let body: ApiResponse<unknown>
    try {
      body = (await response.json()) as ApiResponse<unknown>
    } catch {
      body = { code: -1, message: `非 JSON 响应 HTTP ${response.status()}`, data: null }
    }
    return body
  }

  /** 业务调用：code !== 200 抛 ApiFailure（HTTP 层失败同样归一成 ApiFailure）。 */
  async call<T>(
    user: string,
    method: 'get' | 'post' | 'put' | 'delete',
    path: string,
    data?: unknown,
  ): Promise<T> {
    const body = (await this.raw(method, path, data, user)) as ApiResponse<T>
    if (body.code !== 200) {
      throw new ApiFailure(body.code, body.message ?? '(无 message)', 200)
    }
    return body.data
  }

  /** 预期失败路径：返回业务 code，不抛。 */
  async codeOf(
    user: string,
    method: 'get' | 'post' | 'put' | 'delete',
    path: string,
    data?: unknown,
  ): Promise<number> {
    const body = await this.raw(method, path, data, user)
    return body.code
  }

  async workList(
    user: string,
    kind: WorkListKind,
    page = 1,
    limit = 50,
  ): Promise<PageResult<ProcessRecord>> {
    return this.call<PageResult<ProcessRecord>>(
      user,
      'get',
      `/admin/process/${WORK_LIST_PATHS[kind]}/${page}/${limit}`,
    )
  }

  /**
   * 在某人的某种列表里按请假原因里的标记找审批。
   * helper 不依赖列表排序（已发起最新在前，见 #179），全页遍历对任何顺序都成立；
   * 每页 100 条，封顶 20 页。
   */
  async findMarked(
    user: string,
    kind: WorkListKind,
    marker: string,
  ): Promise<ProcessRecord | undefined> {
    return (await this.findMarkedAll(user, kind, marker))[0]
  }

  async findMarkedAll(user: string, kind: WorkListKind, marker: string): Promise<ProcessRecord[]> {
    const found: ProcessRecord[] = []
    for (let page = 1; page <= 20; page++) {
      const result = await this.workList(user, kind, page, 100)
      found.push(...result.records.filter((record) => (record.formValues ?? '').includes(marker)))
      if (page * 100 >= result.total) break
    }
    return found
  }

  async show(user: string, processId: number) {
    return this.call<{
      process: ProcessRecord
      processRecordList: unknown[]
      isApprove: boolean
    }>(user, 'get', `/admin/process/show/${processId}`)
  }

  async attachments(user: string, processId: number) {
    return this.call<{ attachments: AttachmentRecord[]; canUpload: boolean }>(
      user,
      'get',
      `/admin/attachments/processes/${processId}`,
    )
  }

  async downloadGrant(user: string, processId: number, attachmentId: number) {
    return this.call<{ downloadUrl: string }>(
      user,
      'post',
      `/admin/attachments/processes/${processId}/${attachmentId}/download-url`,
    )
  }

  async download(user: string, processId: number, attachmentId: number): Promise<Buffer> {
    const grant = await this.downloadGrant(user, processId, attachmentId)
    const response = await this.request.get(grant.downloadUrl)
    if (!response.ok()) throw new Error(`附件下载 HTTP ${response.status()}`)
    return Buffer.from(await response.body())
  }

  /** 按客户端真实时序上传附件：登记 → PUT 对象存储 → complete。返回值带 draftId，发起时随 startUp 传回。 */
  async upload(
    user: string,
    file: { fileName: string; contentType: string; body: Buffer },
  ): Promise<AttachmentRecord & { draftId: string }> {
    const draftId = crypto.randomUUID()
    const grant = await this.call<AttachmentGrant>(user, 'post', '/admin/attachments/uploads', {
      draftId,
      fileName: file.fileName,
      contentType: file.contentType,
      sizeBytes: file.body.length,
    })
    const put = await this.request.fetch(grant.uploadUrl, {
      method: 'put',
      headers: grant.headers ?? {},
      data: file.body,
    })
    if (!put.ok()) throw new Error(`对象存储 PUT HTTP ${put.status()}`)
    const record = await this.call<AttachmentRecord>(
      user,
      'post',
      `/admin/attachments/${grant.attachmentId}/complete`,
    )
    return { ...record, draftId }
  }

  /** 起草一条标记审批（发起人是 user），返回数据库里的审批 id。 */
  async startApproval(
    user: string,
    template: { id: number; processTypeId: number },
    marker: string,
    options: { days?: number; attachmentIds?: number[]; attachmentDraftId?: string } = {},
  ): Promise<number> {
    await this.call(user, 'post', '/admin/process/startUp', {
      processTemplateId: template.id,
      processTypeId: template.processTypeId,
      formValues: JSON.stringify({
        formData: { reason: marker, days: options.days ?? 1 },
        formShowData: { 请假原因: marker, 请假天数: options.days ?? 1 },
      }),
      attachmentIds: options.attachmentIds ?? [],
      ...(options.attachmentDraftId ? { attachmentDraftId: options.attachmentDraftId } : {}),
    })
    const record = await poll(20_000, 500, async () => this.findMarked(user, 'started', marker))
    if (!record) throw new Error(`startUp 后已在已发起列表找不到标记 ${marker}`)
    return record.id
  }

  async approve(user: string, processId: number, status: 1 | -1): Promise<void> {
    const pending = await this.workList(user, 'pending', 1, 100)
    const record = pending.records.find((item) => item.id === processId)
    if (!record?.taskId) throw new Error(`待处理里找不到审批 ${processId}（或没有 taskId）`)
    await this.call(user, 'post', '/admin/process/approve', {
      processId,
      taskId: record.taskId,
      status,
    })
  }

  async userId(admin: string, username: string): Promise<number> {
    const result = await this.call<PageResult<{ id: number; username: string }>>(
      admin,
      'get',
      `/admin/system/sysUser/1/50?keyword=${encodeURIComponent(username)}`,
    )
    const found = result.records.find((item) => item.username === username)
    if (!found) throw new Error(`找不到用户 ${username}`)
    return found.id
  }

  async setUserStatus(admin: string, userId: number, status: 0 | 1): Promise<void> {
    await this.call(admin, 'put', `/admin/system/sysUser/updateStatus/${userId}/${status}`)
  }

  async assignRoles(admin: string, userId: number, roleIdList: number[]): Promise<void> {
    // doAssign 允许 201（提交成功、缓存失效待确认，ADR 0015）。
    const code = await this.codeOf(admin, 'post', '/admin/system/sysRole/doAssign', {
      userId,
      roleIdList,
    })
    if (code !== 200 && code !== 201) throw new ApiFailure(code, 'doAssign 失败', 200)
    this.tokens.delete('user')
  }

  async processTypeId(admin: string, name: string): Promise<number> {
    const types = await this.call<{ id: number; name: string }[]>(
      admin,
      'get',
      '/admin/process/processType/findAll',
    )
    const found = types.find((item) => item.name === name)
    if (!found) throw new Error(`找不到审批类型 ${name}`)
    return found.id
  }

  async getTemplate(admin: string, id: number) {
    return this.call<Record<string, unknown>>(
      admin,
      'get',
      `/admin/process/processTemplate/get/${id}`,
    )
  }

  async createUnpublishedTemplate(
    admin: string,
    fields: { name: string; processTypeId: number; description: string },
  ): Promise<number> {
    await this.call(admin, 'post', '/admin/process/processTemplate/save', {
      name: fields.name,
      processTypeId: fields.processTypeId,
      iconUrl: '',
      description: fields.description,
      formProps: JSON.stringify([
        { type: 'input', field: 'reason', title: '请假原因', $required: true },
        {
          type: 'input',
          field: 'days',
          title: '请假天数',
          $required: true,
          props: { type: 'number' },
        },
      ]),
      formOptions: JSON.stringify({ submitBtn: true, form: { labelPosition: 'top' } }),
    })
    const result = await this.call<PageResult<{ id: number; name: string }>>(
      admin,
      'get',
      '/admin/process/processTemplate/1/50',
    )
    const found = result.records.find((item) => item.name === fields.name)
    if (!found) throw new Error(`创建后列表里找不到模板 ${fields.name}`)
    return found.id
  }

  // ---------------------------------------------------------------------
  // W1 动作与动态审批人（#290）：转办/加签/撤回/催办直发 API 做对照，
  // 以及「模板 + 审批人规则 + 部门负责人」的 API 造数链（ADR 0069）。
  // ---------------------------------------------------------------------

  /** 上传流程 zip（multipart，字段名 file），返回路径 / key / BPMN userTasks。 */
  async uploadProcessZip(
    admin: string,
    file: { name: string; buffer: Buffer },
  ): Promise<{
    processDefinitionPath: string
    processDefinitionKey: string
    userTasks: { key: string; name?: string; assignee?: string }[]
  }> {
    const response = await this.request.fetch(
      '/dev-api/admin/process/processTemplate/uploadProcessDefinition',
      {
        method: 'post',
        headers: { token: await this.token(admin) },
        multipart: {
          file: { name: file.name, mimeType: 'application/zip', buffer: file.buffer },
        },
      },
    )
    const body = (await response.json()) as {
      code: number
      message?: string
      data?: { processDefinitionPath: string; processDefinitionKey: string; userTasks: [] }
    }
    if (body.code !== 200 || !body.data) {
      throw new Error(`上传流程 zip 失败 code=${body.code} ${body.message ?? ''}`)
    }
    return body.data
  }

  /** 造一条带流程 zip 的已发布模板，发布前整体替换审批人规则（#285 链路）。 */
  async publishTemplateWithRules(
    admin: string,
    name: string,
    processKey: string,
    rules: { taskDefinitionKey: string; sourceType: string; targetId?: number }[],
  ): Promise<number> {
    const typeId = await this.processTypeId(admin, '演示审批')
    const id = await this.createUnpublishedTemplate(admin, {
      name,
      processTypeId: typeId,
      description: '栈测试动态审批人用',
    })
    const zip = buildZip([{ name: 'process.bpmn20.xml', data: bpmnXml(processKey) }])
    const uploaded = await this.uploadProcessZip(admin, { name: `${processKey}.zip`, buffer: zip })
    await this.call(admin, 'put', '/admin/process/processTemplate/update', {
      ...(await this.getTemplate(admin, id)),
      processDefinitionPath: uploaded.processDefinitionPath,
      processDefinitionKey: uploaded.processDefinitionKey,
    })
    if (rules.length > 0) {
      await this.call(admin, 'put', `/admin/process/processTemplate/approverRules/${id}`, rules)
    }
    await this.call(admin, 'post', `/admin/process/processTemplate/publish/${id}`)
    return id
  }

  /** 造一个顶层部门并返回 id（名称带 runId 标记，跨轮不撞；负责人只认启用用户）。 */
  async createDeptWithLeader(admin: string, name: string, leaderUserId: number): Promise<number> {
    await this.call(admin, 'post', '/admin/system/sysDept/save', {
      parentId: 0,
      name,
      leaderUserId,
      status: 1,
      sortValue: 0,
    })
    const tree = await this.call<
      { id: number; name: string; children?: { id: number; name: string }[] }[]
    >(admin, 'get', '/admin/system/sysDept/findTree')
    const found: { id: number }[] = []
    const walk = (nodes: { id: number; name: string; children?: unknown[] }[]): void => {
      for (const node of nodes) {
        if (node.name === name) found.push(node)
        if (Array.isArray(node.children)) walk(node.children as typeof nodes)
      }
    }
    walk(tree)
    if (found.length === 0) throw new Error(`创建后部门树里找不到 ${name}`)
    return found[0].id
  }

  /** 把用户划入部门（update 只送 id + deptId：其余字段为空即不覆盖，ADR 0034）。 */
  async setUserDept(admin: string, userId: number, deptId: number): Promise<void> {
    await this.call(admin, 'put', '/admin/system/sysUser/update', { id: userId, deptId })
  }

  /** 待处理列表里按审批 id 找行（带 taskId 与三源标记），找不到返回 undefined。 */
  async pendingRow(user: string, processId: number): Promise<ProcessRecord | undefined> {
    for (let page = 1; page <= 20; page++) {
      const result = await this.workList(user, 'pending', page, 100)
      const found = result.records.find((record) => record.id === processId)
      if (found) return found
      if (page * 100 >= result.total) break
    }
    return undefined
  }

  async transfer(user: string, processId: number, targetUserId: number): Promise<void> {
    await this.call(user, 'post', `/admin/process/transfer/${processId}`, { targetUserId })
  }

  async addSign(
    user: string,
    processId: number,
    signType: 1 | 2,
    assigneeIds: number[],
  ): Promise<void> {
    await this.call(user, 'post', `/admin/process/addSign/${processId}`, { signType, assigneeIds })
  }

  async withdraw(user: string, processId: number): Promise<void> {
    await this.call(user, 'post', `/admin/process/withdraw/${processId}`)
  }

  async urge(user: string, processId: number): Promise<void> {
    await this.call(user, 'post', `/admin/process/urge/${processId}`)
  }
}

export async function poll<T>(
  timeoutMs: number,
  intervalMs: number,
  attempt: () => Promise<T | undefined>,
): Promise<T | undefined> {
  const deadline = Date.now() + timeoutMs
  for (;;) {
    try {
      const value = await attempt()
      if (value !== undefined && value !== null) return value
    } catch {
      // 轮询中途的服务抖动不算失败，超时才算。
    }
    if (Date.now() > deadline) return undefined
    await new Promise((resolve) => setTimeout(resolve, intervalMs))
  }
}

/** Compose 演示模板：manual seeding 后才存在（compose-demo.md「首次启动后」）。 */
export async function demoTemplate(oracle: Oracle) {
  const groups = await oracle.call<
    {
      id: number
      name: string
      processTemplateList: { id: number; name: string; availableForInitiation?: boolean }[]
    }[]
  >('alice', 'get', '/admin/process/findStartable')
  for (const group of groups) {
    for (const template of group.processTemplateList ?? []) {
      if (template.name === '演示请假申请') {
        if (!template.availableForInitiation) {
          throw new Error(
            '演示请假申请不可发起：确认 compose-demo.md「首次启动后」的发布步骤已执行',
          )
        }
        return { id: template.id, processTypeId: group.id, name: template.name }
      }
    }
  }
  throw new Error('findStartable 里没有演示请假申请：确认演示模板已发布')
}
