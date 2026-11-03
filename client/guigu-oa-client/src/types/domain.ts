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
