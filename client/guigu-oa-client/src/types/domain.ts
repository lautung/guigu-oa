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

export interface SidebarItem {
  path: string
  title: string
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
  createTime?: string
}

export interface RoleListItem extends RoleRecord {
  id: EntityId
}

export interface RoleForm {
  id: EntityId | ''
  roleName: string
  roleCode: string
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
  value: unknown
}

export interface PresentedWorkListRecord {
  id?: EntityId
  taskId?: EntityId
  title?: string
  name?: string
  processTypeName?: string
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
  routers?: MenuNode[]
}

export interface FormRule {
  field: string
  title: string
}

export interface FormDesignerOption {
  [key: string]: unknown
  form?: Record<string, unknown>
  submitBtn?: boolean
}

export interface FormDesignerState {
  rule: FormRule[]
  option: FormDesignerOption
}

export interface Profile {
  name: string
  phone: string
}
