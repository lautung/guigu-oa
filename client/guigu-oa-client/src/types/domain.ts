export type EntityId = string | number

export interface ApiResponse<TData> {
  code: number
  data: TData
  message?: string
}

export interface PageResult<TRecord> {
  records?: TRecord[]
  total?: number
  pages?: number
}

export interface ApiRequestConfig {
  method: 'get' | 'post' | 'put' | 'delete'
  url: string
  data?: unknown
  params?: Record<string, string | number | boolean | null | undefined>
  /** 二进制读（公告图片）：响应体不是业务信封，由 request 模块直通 Blob。 */
  responseType?: 'blob'
}

export interface ApiClient {
  <TData = unknown>(config: ApiRequestConfig): Promise<ApiResponse<TData>>
}

export interface MenuNode {
  id?: EntityId
  parentId?: EntityId
  name?: string
  parentName?: string
  title?: string
  type?: number
  path?: string
  component?: string
  perms?: string
  icon?: string
  sortValue?: number
  status?: number
  hidden?: boolean
  select?: boolean
  isSelect?: boolean
  meta?: { title?: string }
  children?: MenuNode[]
}

/** /info 下发的后台菜单条目：path 是绝对路径，parentPath 只用于展示层级。 */
export interface BackofficeMenuEntry {
  path: string
  parentPath?: string | null
  title: string
  type: number
  hidden?: boolean
  sort?: number | null
  icon?: string | null
}

export interface SidebarItem {
  path: string
  title: string
  icon?: string | null
  children?: SidebarItem[]
}

export interface SidebarGroup {
  key: 'work' | 'config'
  title: string
  collapsed: boolean
  hint: string | null
  items: SidebarItem[]
}

export interface MenuForm extends MenuNode {
  id: EntityId | ''
  parentId: EntityId
  parentName: string
  name: string
  type: number
  path: string
  component: string
  perms: string
  icon: string
  sortValue: number
  status: number
}

export interface ProcessTypeRecord {
  id?: EntityId | ''
  name?: string
  description?: string
  createTime?: string
  updateTime?: string
  processTemplateList?: ProcessTemplateRecord[]
}

export interface ProcessTemplateRecord {
  id?: EntityId | ''
  name?: string
  iconUrl?: string
  processTypeId?: EntityId | ''
  processTypeName?: string
  formProps?: string
  formOptions?: string
  processDefinitionKey?: string
  processDefinitionPath?: string
  description?: string
  status?: number
  availableForInitiation?: boolean
  availabilityMessage?: string
  createTime?: string
  updateTime?: string
}

export interface PresentedProcessTemplate extends ProcessTemplateRecord {
  id: EntityId | ''
  name: string
  iconUrl: string
  processTypeId: EntityId | ''
  formProps: string
  formOptions: string
  processDefinitionKey: string
  processDefinitionPath: string
  description: string
  status: number
}

export interface ProcessTemplateListItem extends ProcessTemplateRecord {
  id: EntityId | ''
  statusLabel: string
  canEdit: boolean
  canDelete: boolean
  canPublish: boolean
}

export interface ProcessTemplateStartGroup {
  id?: EntityId
  name?: string
  templates: Array<Pick<ProcessTemplateRecord, 'id' | 'name' | 'iconUrl'>>
}

export interface RoleRecord {
  id?: EntityId | ''
  roleName?: string
  roleCode?: string
  dataScope?: number
  deptIds?: EntityId[]
  createTime?: string
}

export interface RoleListItem extends RoleRecord {
  id: EntityId
}

export interface RoleForm {
  id: EntityId | ''
  roleName: string
  roleCode: string
  dataScope: number
  deptIds: EntityId[]
}

export interface DeptOption {
  id: EntityId
  name: string
  parentId?: EntityId
}

/** 部门维护行（#247）：ancestors 是服务端算好的含自身包裹逗号路径，客户端只展示。 */
export interface DeptRecord {
  id?: EntityId | ''
  parentId?: EntityId
  ancestors?: string
  name?: string
  leader?: string
  sortValue?: number
  status?: number
  createTime?: string
  children?: DeptRecord[]
}

export interface DeptTreeNode extends DeptRecord {
  children: DeptTreeNode[]
}

export interface DeptForm {
  id: EntityId | ''
  parentId: EntityId
  name: string
  leader: string
  sortValue: number
  status: number
}

/** 岗位（#248）：职务标签，不产生授权、不参与审批分配、不套数据范围。 */
export interface PostRecord {
  id?: EntityId | ''
  postCode?: string
  name?: string
  sortValue?: number
  status?: number
  createTime?: string
}

export interface PostListItem extends PostRecord {
  id: EntityId
  statusLabel: string
}

export interface PostForm {
  id: EntityId | ''
  postCode: string
  name: string
  sortValue: number
  status: number
}

/** 父级下拉项：depth 只用于缩进展示（0＝顶级部门）。 */
export interface DeptParentOption {
  id: EntityId
  name: string
  depth: number
}

export interface UserRecord {
  id?: EntityId | ''
  username?: string
  password?: string
  name?: string
  phone?: string
  status?: number
  createTime?: string
}

export interface UserListItem extends UserRecord {
  id: EntityId
  statusEnabled: boolean
}

export interface UserForm {
  id: EntityId | ''
  username: string
  password: string
  name: string
  phone: string
  status: number
}

/** 操作日志（ADR 0054）：审计行是历史事实，读侧只映射，不 join。 */
export interface OperLogRecord {
  id?: EntityId | ''
  title?: string
  businessType?: number
  method?: string
  requestMethod?: string
  operId?: EntityId
  operName?: string
  deptId?: EntityId
  deptName?: string
  operUrl?: string
  operIp?: string
  operParam?: string
  resultCode?: number
  resultMessage?: string
  status?: number
  errorMsg?: string
  costTime?: number
  createTime?: string
}

export type AuditTagType = 'success' | 'danger' | 'warning' | 'info'

export interface OperLogListItem extends OperLogRecord {
  id: EntityId
  businessTypeLabel: string
  statusLabel: string
  statusTagType: AuditTagType
  costTimeText: string
}

/** 详情弹窗的展示投影：JSON 已经格式化，缺省值统一成空串。 */
export interface OperLogDetail {
  id: EntityId | ''
  title: string
  businessTypeLabel: string
  operName: string
  deptName: string
  operIp: string
  requestMethod: string
  operUrl: string
  method: string
  statusLabel: string
  statusTagType: AuditTagType
  resultCodeText: string
  resultMessage: string
  errorMsg: string
  costTimeText: string
  operParamText: string
  createTime: string
}

/** 登录日志（ADR 0054）：一行即全貌，没有详情端点。 */
export interface LoginLogRecord {
  id?: EntityId | ''
  username?: string
  userId?: EntityId
  deptId?: EntityId
  ip?: string
  browser?: string
  os?: string
  status?: number
  msg?: string
  createTime?: string
}

export interface LoginLogListItem extends LoginLogRecord {
  id: EntityId
  statusLabel: string
  statusTagType: AuditTagType
}

/** 在线用户（ADR 0060）：一行 = 一次登录；current 由服务端按请求者自己的会话标出。 */
export interface OnlineSessionRecord {
  sessionId?: string
  username?: string
  name?: string
  loginTime?: string
  ip?: string
  browser?: string
  os?: string
  lastActive?: string
  current?: boolean
}

export interface OnlineSessionListItem {
  sessionId: string
  username: string
  name: string
  loginTime: string
  ip: string
  browser: string
  os: string
  lastActive: string
  current: boolean
}

/** 服务监控/数据监控（ADR 0060）：字段镜像 lautung-oa-model 的 monitor VO，单位字节/百分比(0-100)/秒。 */
export interface HikariMetrics {
  activeConnections?: number | null
  idleConnections?: number | null
  totalConnections?: number | null
  threadsAwaiting?: number | null
  maxPoolSize?: number | null
  connectionTimeoutSeconds?: number | null
  totalAcquired?: number | null
  totalTimeouts?: number | null
}

export interface MemorySegmentMetrics {
  usedBytes?: number | null
  committedBytes?: number | null
  maxBytes?: number | null
}

export interface ThreadMetrics {
  threadCount?: number | null
  peakThreadCount?: number | null
  daemonThreadCount?: number | null
}

export interface JvmMetrics {
  uptimeSeconds?: number | null
  heap?: MemorySegmentMetrics | null
  nonHeap?: MemorySegmentMetrics | null
  pools?: { name?: string; usage?: MemorySegmentMetrics | null }[] | null
  threads?: ThreadMetrics | null
}

export interface ServerMetrics {
  cpu?: {
    usagePercent?: number | null
    systemLoad?: number | null
    availableProcessors?: number | null
  } | null
  memory?: {
    totalBytes?: number | null
    freeBytes?: number | null
    usagePercent?: number | null
  } | null
  jvm?: JvmMetrics | null
  disks?:
    | {
        mountPoint?: string | null
        totalBytes?: number | null
        usableBytes?: number | null
        usedBytes?: number | null
        usagePercent?: number | null
      }[]
    | null
  hikari?: HikariMetrics | null
}

export interface SlowSqlEntry {
  timestamp?: number | null
  statementId?: string | null
  elapsedMs?: number | null
  rows?: number | null
  sql?: string | null
}

export interface MonitorDataSnapshot {
  hikari?: HikariMetrics | null
  slowSql?: {
    enabled?: boolean | null
    thresholdMs?: number | null
    entries?: SlowSqlEntry[] | null
  } | null
}

/** 缓存监控/缓存列表（ADR 0060，#237）：镜像 CacheMonitorVo 的五段解析字段。 */
export interface CacheMonitorSnapshot {
  server?: {
    redisVersion?: string | null
    uptimeSeconds?: number | null
  } | null
  memory?: {
    usedMemoryBytes?: number | null
    usedMemoryHuman?: string | null
    /** 0 表示未设上限（Redis 原值）。 */
    maxMemoryBytes?: number | null
  } | null
  clients?: {
    connectedClients?: number | null
  } | null
  stats?: {
    keyspaceHits?: number | null
    keyspaceMisses?: number | null
    expiredKeys?: number | null
  } | null
  keyspace?:
    | {
        db?: string | null
        keys?: number | null
        expires?: number | null
        avgTtlMs?: number | null
      }[]
    | null
}

export interface CacheKeyRecord {
  key?: string | null
  /** 秒；-1 无过期（服务端把扫描间隙消失的 -2 也归 -1）。 */
  ttl?: number | null
}

export interface CacheKeyPage {
  keys?: CacheKeyRecord[] | null
  /** 0 表示扫描结束。 */
  nextCursor?: number | null
}

/**
 * 定时任务行（ADR 0060，#238）：镜像 `MonitorJobVo`。`available` 是读侧判存活，
 * false 表示 bean 已不存在（「异常」行）；`last*` 是最近一次执行摘要，没跑过为空。
 */
export interface MonitorJobRecord {
  id?: EntityId
  name?: string | null
  beanName?: string | null
  cron?: string | null
  /** 0 停用 / 1 启用。 */
  enabled?: number | null
  /** 0 禁止并发（默认）/ 1 允许并发。 */
  concurrency?: number | null
  remark?: string | null
  available?: boolean
  lastTriggerType?: number | null
  lastStatus?: number | null
  /** dev profile 序列化成 `yyyy-MM-dd HH:mm:ss` 字符串；兜底 epoch 毫秒。 */
  lastStartedAt?: string | number | null
  lastDurationMs?: number | null
  lastMessage?: string | null
}

/**
 * 定时任务执行记录（ADR 0060，#238）：镜像 `SysJobLog`。trigger 1 自动 / 2 手动；
 * status 1 成功 / 2 失败 / 3 跳过；保留策略每任务最近 20 条在服务端写入路径执行。
 */
export interface MonitorJobLogRecord {
  id?: EntityId
  jobId?: EntityId
  jobName?: string | null
  triggerType?: number | null
  status?: number | null
  message?: string | null
  startedAt?: string | number | null
  finishedAt?: string | number | null
  durationMs?: number | null
}

export interface ProcessInstanceRecord {
  id?: EntityId
  processCode?: string
  title?: string
  name?: string
  processTypeName?: string
  processTemplateName?: string
  description?: string
  status?: number
  createTime?: string
  /** 公共字段口径（ADR 0031）；抄送行自 #181 起补上。 */
  updateTime?: string
  formValues?: string | ProcessFormValues
  taskId?: EntityId
}

export interface ProcessInstanceListItem extends ProcessInstanceRecord {
  id: EntityId
  statusLabel: string
  detailPath: string
}

export type FormDataRecord = Record<string, unknown>

export interface ProcessFormValues {
  formData?: FormDataRecord
  formShowData?: FormDataRecord
}

export interface WorkListRecord extends ProcessInstanceRecord {
  taskId?: EntityId
}

export interface WorkSummaryField {
  key: string
  value: string
}

export interface PresentedWorkListRecord {
  id?: EntityId
  taskId?: EntityId
  title?: string
  name?: string
  processTypeName?: string
  processTemplateName?: string
  createTime?: string
  status?: number
  statusLabel: string
  statusTagType: 'warning' | 'success' | 'danger' | 'info'
  summaryFields: WorkSummaryField[]
  detailPath: string
  kind: 'pending' | 'processed' | 'started' | 'copied'
  relationLabel: string
  progressLabel: string
  rowKey: string
}

export interface WorkRecordEntry {
  operateUser?: string
  description?: string
  createTime?: string
}

export interface ProcessDetailPayload {
  process?: ProcessInstanceRecord
  processRecordList?: WorkRecordEntry[]
  isApprove?: boolean
}

export interface AttachmentRecord {
  id: EntityId
  fileName: string
  sizeBytes: number
  contentType?: string
  downloadUrl?: string
}

export interface ProcessAttachmentList {
  attachments: AttachmentRecord[]
  canUpload: boolean
}

export interface AttachmentUploadGrant {
  attachmentId: EntityId
  uploadUrl: string
  headers: Record<string, string>
}

export interface AttachmentUploadRequest {
  draftId: string
  processId?: EntityId
  fileName: string
  contentType: string
  sizeBytes: number
}

export interface StartProcessRequest {
  processTemplateId: EntityId
  processTypeId: EntityId
  formValues: string
  /** 同一次提交的重复送达用同一个值；重新发起换新的值。 */
  idempotencyKey?: string
  attachmentDraftId?: string
  attachmentIds?: EntityId[]
  ccUserIds?: EntityId[]
}

export interface LoginCredentials {
  username: string
  password: string
}

export interface LoginTokens {
  token: string
  refreshToken?: string
}

export interface UserInfo {
  name: string
  avatar?: string
  roles?: string[]
  permissions?: string[]
  menus?: BackofficeMenuEntry[]
}

export interface FormRule {
  field: string
  title: string
}

export interface FormDesignerOption {
  [key: string]: unknown
  form?: Record<string, unknown>
  submitBtn?: boolean | { disabled?: boolean; loading?: boolean }
}

export interface FormDesignerState {
  rule: FormRule[]
  option: FormDesignerOption
}

export interface Profile {
  name: string
  phone: string
}

/** 公告（ADR 0055）：type 1 通知 / 2 公告；status 0 草稿 / 1 已发布 / 2 已撤回。 */
export interface NoticeFeedRecord {
  id?: EntityId | ''
  title?: string
  type?: number
  publisherName?: string
  publishTime?: string
  read?: boolean
}

export interface NoticeFeedItem extends NoticeFeedRecord {
  id: EntityId
  title: string
  typeLabel: string
  typeTagType: AuditTagType
  publisherName: string
  publishTime: string
  read: boolean
  readLabel: string
  readTagType: AuditTagType
}

export interface NoticeDetailRecord extends NoticeFeedRecord {
  publisherId?: EntityId
  content?: string
}

/** 详情页的展示投影：缺省值统一成空串，正文原样交给唯一渲染面。 */
export interface NoticeDetailView {
  id: EntityId
  title: string
  type?: number
  typeLabel: string
  typeTagType: AuditTagType
  publisherId: EntityId | ''
  publisherName: string
  publishTime: string
  content: string
  read: boolean
}

/** 管理侧（#220）：列表行不含正文，详情含草稿正文。 */
export interface NoticeAdminRecord {
  id?: EntityId | ''
  title?: string
  type?: number
  status?: number
  publisherId?: EntityId
  publisherName?: string
  publishTime?: string
  createTime?: string
  updateTime?: string
  content?: string
}

export interface NoticeAdminListItem extends NoticeAdminRecord {
  id: EntityId
  title: string
  typeLabel: string
  statusLabel: string
  statusTagType: AuditTagType
}

export interface NoticeWritePayload {
  title: string
  type: number
  content: string
}

export interface NoticeImageResult {
  id: EntityId
  url: string
}
