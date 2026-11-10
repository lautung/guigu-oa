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

/** 角色成员/候选行（#256）：角色页双列表只用这几个字段。 */
export interface RoleUserRecord {
  id?: EntityId
  username?: string
  name?: string
  phone?: string
  assigned?: boolean
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

/** 部门维护行（#247/#286）：ancestors 是服务端算好的含自身包裹逗号路径，负责人是启用用户的引用。 */
export interface DeptRecord {
  id?: EntityId | ''
  parentId?: EntityId
  ancestors?: string
  name?: string
  leaderUserId?: EntityId | null
  sortValue?: number
  status?: number
  createTime?: string
  children?: DeptRecord[]
}

export interface DeptTreeNode extends DeptRecord {
  children: DeptTreeNode[]
}

/** 用户页的只读部门节点（#249）：左树 prepend「全部」（id=0），表单树按 status 置 disabled。 */
export interface DeptSelectNode extends DeptTreeNode {
  disabled?: boolean
}

export interface DeptForm {
  id: EntityId | ''
  parentId: EntityId
  name: string
  leaderUserId: EntityId | ''
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

/** 参数（#252）：运行时可改的键值；builtin=1 由迁移种入，只可改 value。 */
export interface ParamRecord {
  id?: EntityId | ''
  configKey?: string
  configValue?: string
  configName?: string
  valueType?: string
  builtin?: number
  remark?: string
  createTime?: string
}

export interface ParamListItem extends ParamRecord {
  id: EntityId
  builtin: number
  builtinLabel: string
  valueTypeLabel: string
}

export interface ParamForm {
  id: EntityId | ''
  configKey: string
  configValue: string
  configName: string
  valueType: string
  builtin: number
  remark: string
}

/** 父级下拉项：depth 只用于缩进展示（0＝顶级部门）。 */
export interface DeptParentOption {
  id: EntityId
  name: string
  depth: number
}

/** 启用用户选项（#286，ADR 0069）：部门负责人选择器只可选启用用户，label 供下拉展示。 */
export interface UserOption {
  id: EntityId
  username: string
  name: string
  label: string
}

export interface UserRecord {
  id?: EntityId | ''
  username?: string
  password?: string
  name?: string
  phone?: string
  status?: number
  deptId?: EntityId
  postIds?: EntityId[]
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
  deptId: EntityId | ''
  postIds: EntityId[]
}

/** 用户表单的岗位选项（#249）：只读、只含启用岗位。 */
export interface PostOption {
  id: EntityId
  name: string
}

/** 用户导入的部分成功汇总（#259）：失败行不写，行号从 Excel 表头算第 1 行。 */
export interface UserImportRowFailure {
  rowNumber: number
  username?: string
  reason?: string
}

export interface UserImportResult {
  total?: number
  successCount?: number
  failureCount?: number
  failures?: UserImportRowFailure[]
}

export interface UserImportResultView {
  total: number
  successCount: number
  failureCount: number
  failures: Array<{ rowNumber: number; username: string; reason: string }>
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
  /** 事件类型：login / register（#253）。 */
  event?: string
  msg?: string
  createTime?: string
}

export interface LoginLogListItem extends LoginLogRecord {
  id: EntityId
  statusTagType: AuditTagType
  eventLabel: string
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

export interface SqlStatementStat {
  statementId?: string | null
  count?: number | null
  avgElapsedMs?: number | null
  maxElapsedMs?: number | null
  lastTimestamp?: number | null
}

export interface PoolHistoryPoint {
  timestamp?: number | null
  activeConnections?: number | null
  idleConnections?: number | null
  totalConnections?: number | null
  threadsAwaiting?: number | null
}

export interface MonitorDataSnapshot {
  hikari?: HikariMetrics | null
  slowSql?: {
    enabled?: boolean | null
    thresholdMs?: number | null
    entries?: SlowSqlEntry[] | null
  } | null
  /** 按 statementId 聚合的内存环统计（#258）：只覆盖慢 SQL，不是全量 SQL。 */
  sqlStatistics?: {
    enabled?: boolean | null
    note?: string | null
    statements?: SqlStatementStat[] | null
  } | null
  /** 连接池历史（#258）：进程内采样，时间升序，最多 120 点。 */
  poolHistory?: {
    capacity?: number | null
    intervalMs?: number | null
    points?: PoolHistoryPoint[] | null
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
  /** 待处理行状态（#288，ADR 0068）：APPROVE 通过/拒绝、CANDIDATE 候选人之一、ADD_SIGN 加签处理。 */
  actionKind?: string
  /** 前加签挂起标记：true 表示等待加签人处理、不能动作。 */
  suspended?: boolean
  /** 催办标记：最近 24 小时内该审批存在催办记录。 */
  urged?: boolean
}

/** 详情头部与待处理行共用的行状态（#288）：服务端按查看者合成，无待处理行时为 null。 */
export interface PendingRowState {
  actionKind?: string
  suspended?: boolean
  urged?: boolean
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
  /** 待处理行状态呈现（#288）：挂起/候选人行的行内文案；空串表示无特殊状态。 */
  pendingRowLabel?: string
  /** 催办标记（#288）：行内「已催办」小标。 */
  urged?: boolean
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
  /** 查看者的待处理行状态（#288）：挂起/候选人/催办标记在详情头部呈现；无待处理行时为 null。 */
  pendingRow?: PendingRowState | null
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

/** 匿名注册（#253）：手机可选；confirm 由服务端与 password 比对后丢弃。 */
export interface RegisterPayload {
  username: string
  password: string
  confirm: string
  name: string
  phone?: string
}

/** 注册开关探测的匿名响应。 */
export interface RegisterSwitchRecord {
  enabled?: boolean
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

/** 个人中心（#254）：服务端资料形状；avatar 是已认证同源二进制端点路径或空串。 */
export interface ProfileRecord {
  username?: string
  name?: string
  phone?: string
  avatar?: string
}

/** 个人中心表单投影：用户名只读，头像路径与 blob URL 分开存。 */
export interface ProfileView {
  username: string
  name: string
  phone: string
  avatarPath: string
}

export interface ProfileWritePayload {
  name: string
  phone: string
}

export interface ProfilePasswordPayload {
  oldPassword: string
  newPassword: string
}

export interface ProfileAvatarResult {
  url?: string
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

/** 字典（#251，ADR 0062）：运行时可编辑的枚举；业务只存值，标签按 `dictType` 渲染。 */
export interface DictOption {
  dictLabel: string
  dictValue: string
  sortValue?: number
}

export interface DictTypeRecord {
  id?: EntityId | ''
  dictName?: string
  dictType?: string
  status?: number
  remark?: string
  createTime?: string
}

export interface DictTypeListItem extends DictTypeRecord {
  id: EntityId
  statusLabel: string
}

export interface DictTypeForm {
  id: EntityId | ''
  dictName: string
  dictType: string
  status: number
  remark: string
}

export interface DictDataRecord {
  id?: EntityId | ''
  dictType?: string
  dictLabel?: string
  dictValue?: string
  sortValue?: number
  status?: number
  remark?: string
  createTime?: string
}

export interface DictDataListItem extends DictDataRecord {
  id: EntityId
  statusLabel: string
}

export interface DictDataForm {
  id: EntityId | ''
  dictType: string
  dictLabel: string
  dictValue: string
  sortValue: number
  status: number
  remark: string
}

export interface AttendanceShiftRecord {
  id?: EntityId | ''
  name?: string
  startTime?: string
  endTime?: string
  lunchStart?: string | null
  lunchEnd?: string | null
  graceMinutes?: number
  remark?: string
  createTime?: string
}

export interface AttendanceShiftListItem extends AttendanceShiftRecord {
  id: EntityId
}

export interface AttendanceShiftForm {
  id: EntityId | ''
  name: string
  startTime: string
  endTime: string
  lunchStart: string
  lunchEnd: string
  graceMinutes: number
  remark: string
}

export interface AttendanceGroupRecord {
  id?: EntityId | ''
  name?: string
  shiftId?: EntityId
  shiftName?: string
  workdays?: string
  memberCount?: number
  remark?: string
  createTime?: string
}

export interface AttendanceGroupListItem extends AttendanceGroupRecord {
  id: EntityId
}

export interface AttendanceMemberRecord {
  id?: EntityId
  userId?: EntityId
  username?: string
  name?: string
}

export interface AttendanceGroupForm {
  id: EntityId | ''
  name: string
  shiftId: EntityId | ''
  workdays: number[]
  memberUserIds: EntityId[]
  remark: string
}

export interface AttendanceHolidayRecord {
  id?: EntityId | ''
  holidayDate?: string
  name?: string
  dayType?: number
  createTime?: string
}

export interface AttendanceHolidayListItem extends AttendanceHolidayRecord {
  id: EntityId
}

export interface AttendanceHolidayForm {
  id: EntityId | ''
  holidayDate: string
  name: string
  dayType: number
}

/** 今日打卡状态（#279）：班次摘要、日类型与两张卡的有效时刻（服务器时间）。 */
export interface AttendanceShiftSummaryRecord {
  id?: EntityId
  name?: string
  startTime?: string
  endTime?: string
  lunchStart?: string | null
  lunchEnd?: string | null
  graceMinutes?: number
}

export interface AttendanceTodayRecord {
  date?: string
  weekday?: number
  dayType?: number
  workday?: boolean
  hasGroup?: boolean
  groupId?: EntityId
  groupName?: string
  shift?: AttendanceShiftSummaryRecord | null
  clockInTime?: string | null
  clockOutTime?: string | null
}

export interface AttendanceShiftSummaryView {
  name: string
  timeRange: string
  lunchText: string
  graceMinutes: number
}

export interface AttendanceTodayView {
  date: string
  weekdayLabel: string
  dayType: number
  dayTypeLabel: string
  workday: boolean
  hasGroup: boolean
  groupName: string
  shift: AttendanceShiftSummaryView | null
  clockInTime: string
  clockOutTime: string
}

/** 后台打卡记录行（#279）：姓名/部门/补卡操作人由服务端 join。 */
export interface AttendancePunchRecord {
  id?: EntityId | ''
  userId?: EntityId
  username?: string
  name?: string
  deptId?: EntityId
  deptName?: string
  punchDate?: string
  slot?: number
  punchTime?: string
  source?: number
  operatorName?: string
}

export interface AttendancePunchListItem extends AttendancePunchRecord {
  id: EntityId
  slotLabel: string
  sourceLabel: string
}

export interface AttendancePunchQuery {
  name?: string
  beginDate?: string
  endDate?: string
  source?: number | ''
}

/** 逐日推导结果（#281，ADR 0073）：九枚举状态 + 槽位有效时刻。 */
export interface AttendanceDayStatusRecord {
  date?: string
  weekday?: number
  dayType?: number
  workday?: boolean
  hasGroup?: boolean
  groupId?: EntityId
  groupName?: string
  shift?: AttendanceShiftSummaryRecord | null
  status?: number | null
  leaveTypeName?: string
  pending?: boolean
  clockInTime?: string | null
  clockOutTime?: string | null
}

export interface AttendanceDayStatusView {
  date: string
  weekdayLabel: string
  dayType: number
  dayTypeLabel: string
  workday: boolean
  status: number | null
  statusLabel: string
  leaveTypeName: string
  pending: boolean
  clockInText: string
  clockOutText: string
}

export interface AttendanceMonthRecord {
  month?: string
  days?: AttendanceDayStatusRecord[]
}

export interface AttendanceMonthView {
  month: string
  days: AttendanceDayStatusView[]
}

/** 补卡页成员候选（#281）：数据范围由服务端过滤。 */
export interface AttendanceRepairUserRecord {
  id?: EntityId
  username?: string
  name?: string
  deptId?: EntityId
  deptName?: string
}

export interface AttendanceRepairUserOption {
  id: EntityId
  username: string
  name: string
  deptName: string
  label: string
}

export interface AttendanceRepairForm {
  userId: EntityId | ''
  repairDate: string
  clockInTime: string
  clockOutTime: string
}

/** 假期类型（#280，ADR 0073）：独立配置，被流水引用后只停用不删。 */
export interface AttendanceLeaveTypeRecord {
  id?: EntityId | ''
  name?: string
  balanceRequired?: number
  overtimeCredit?: number
  status?: number
  sortNo?: number
  remark?: string
  createTime?: string
}

export interface AttendanceLeaveTypeListItem extends AttendanceLeaveTypeRecord {
  id: EntityId
}

export interface AttendanceLeaveTypeForm {
  id: EntityId | ''
  name: string
  balanceRequired: number
  overtimeCredit: number
  status: number
  sortNo: number
  remark: string
}

/** 发放流水行（#280）：姓名/部门/类型/操作人由服务端 join。 */
export interface AttendanceLedgerRecord {
  id?: EntityId | ''
  userId?: EntityId
  username?: string
  name?: string
  deptId?: EntityId
  deptName?: string
  leaveTypeId?: EntityId
  leaveTypeName?: string
  entryType?: number
  changeHours?: number
  businessType?: string
  businessId?: EntityId
  operatorUserId?: EntityId
  operatorName?: string
  remark?: string
  createTime?: string
}

export interface AttendanceLedgerListItem extends AttendanceLedgerRecord {
  id: EntityId
  entryTypeLabel: string
  changeHoursText: string
  negative: boolean
}

export interface AttendanceLedgerQuery {
  leaveTypeId?: EntityId | ''
  beginDate?: string
  endDate?: string
}

export interface AttendanceLedgerGrantForm {
  leaveTypeId: EntityId | ''
  userIds: EntityId[]
  changeHours: number
  remark: string
}

/** 本人某类型余额（#280）：可用 = 发放 + 入账 − 扣减 − 在途。 */
export interface AttendanceBalanceRecord {
  leaveTypeId?: EntityId
  leaveTypeName?: string
  status?: number
  grantedHours?: number
  creditedHours?: number
  deductedHours?: number
  pendingHours?: number
  availableHours?: number
}

export interface AttendanceBalanceView {
  leaveTypeId: EntityId
  leaveTypeName: string
  status: number
  availableHours: number
  availableHoursText: string
  pendingHours: number
  pendingText: string
  negative: boolean
}

// ---------------- 我的申请（请假 / 加班，#282） ----------------

/** 最近一条审批摘要（ADR 0067 读模型）：单据详情只拼摘要，不嵌完整审批载荷。 */
export interface AttendanceApplyApprovalRecord {
  processId?: EntityId
  status?: number
  currentApprover?: string | null
  description?: string | null
  startTime?: string | null
  endTime?: string | null
}

export interface AttendanceLeaveApplicationRecord {
  id?: EntityId | ''
  leaveTypeId?: EntityId
  leaveTypeName?: string
  startDate?: string
  endDate?: string
  days?: number
  hours?: number
  reason?: string
  status?: number
  submitTime?: string | null
  finishTime?: string | null
  createTime?: string
  approval?: AttendanceApplyApprovalRecord | null
}

export interface AttendanceOvertimeApplicationRecord {
  id?: EntityId | ''
  overtimeDate?: string
  startTime?: string
  endTime?: string
  hours?: number
  reason?: string
  status?: number
  submitTime?: string | null
  finishTime?: string | null
  createTime?: string
  approval?: AttendanceApplyApprovalRecord | null
}

export interface AttendanceMyLeaveTypeRecord {
  id?: EntityId
  name?: string
  balanceRequired?: number
  availableHours?: number | null
}

export interface AttendanceMyLeaveTypeView {
  id: EntityId
  name: string
  balanceRequired: number
  availableHoursText: string
}

export interface AttendanceLeaveApplyForm {
  id: EntityId | ''
  leaveTypeId: EntityId | ''
  startDate: string
  endDate: string
  reason: string
}

export interface AttendanceOvertimeApplyForm {
  id: EntityId | ''
  overtimeDate: string
  startTime: string
  endTime: string
  reason: string
}

/** 两页签共用的行投影：状态文案/颜色与可编辑可提交判定只在一处。 */
export interface AttendanceApplyListItem {
  id: EntityId
  kind: 'leave' | 'overtime'
  typeName: string
  dateText: string
  durationText: string
  reason: string
  status: number
  statusLabel: string
  statusTagType: 'success' | 'warning' | 'danger' | 'info' | 'primary'
  submitTimeText: string
  finishTimeText: string
  editable: boolean
  submittable: boolean
  approval: AttendanceApplyApprovalRecord | null
  leave?: AttendanceLeaveApplicationRecord
  overtime?: AttendanceOvertimeApplicationRecord
}
