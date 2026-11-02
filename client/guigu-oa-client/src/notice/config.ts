import type {
  ApiRequestConfig,
  AuditTagType,
  EntityId,
  NoticeAdminListItem,
  NoticeAdminRecord,
  NoticeDetailRecord,
  NoticeDetailView,
  NoticeFeedItem,
  NoticeFeedRecord,
  NoticeWritePayload,
} from '@/types/domain'

/**
 * 公告（ADR 0055）的读侧与管理侧请求形状、标签与展示投影。
 *
 * 读侧内置：登录即有，不设页面授予、不套数据范围。类型固定 1 通知 / 2 公告；
 * 状态 0 草稿 / 1 已发布 / 2 已撤回。标签由本文件一份常量渲染，不建字典。
 */

export const NOTICE_TYPES = [
  { value: 1, label: '通知' },
  { value: 2, label: '公告' },
] as const

export const NOTICE_STATUSES = [
  { value: 0, label: '草稿' },
  { value: 1, label: '已发布' },
  { value: 2, label: '已撤回' },
] as const

export const NOTICE_PERMISSIONS = {
  add: 'system:notice:add',
  edit: 'system:notice:edit',
  remove: 'system:notice:remove',
  publish: 'system:notice:publish',
} as const

export const NOTICE_PAGE_SIZE = 10

export const NOTICE_UNREAD_LIMIT = 5

/** 列表 tab：全部 / 未读。 */
export type NoticeListTab = 'all' | 'unread'

export interface NoticeFeedQuery {
  unreadOnly?: string
  [key: string]: string | number | undefined
}

export interface NoticeAdminQuery {
  title?: string
  type?: number | ''
  status?: number | ''
  [key: string]: string | number | undefined
}

export function noticeTypeLabel(value: number | null | undefined): string {
  return NOTICE_TYPES.find((option) => option.value === value)?.label || ''
}

/** 通知偏提示、公告偏正式；同一个值在列表、详情与铃铛里保持同一种视觉。 */
export function noticeTypeTagType(value: number | null | undefined): AuditTagType {
  return value === 2 ? 'warning' : 'info'
}

export function noticeStatusLabel(value: number | null | undefined): string {
  return NOTICE_STATUSES.find((option) => option.value === value)?.label || ''
}

export function noticeStatusTagType(value: number | null | undefined): AuditTagType {
  if (value === 1) return 'success'
  if (value === 2) return 'danger'
  return 'info'
}

export function noticeReadLabel(read: boolean | null | undefined): string {
  return read ? '已读' : '未读'
}

export function noticeReadTagType(read: boolean | null | undefined): AuditTagType {
  return read ? 'info' : 'danger'
}

export function presentFeedItem(record: NoticeFeedRecord = {}): NoticeFeedItem {
  const read = record.read === true
  return {
    id: record.id ?? '',
    title: record.title || '',
    type: record.type,
    typeLabel: noticeTypeLabel(record.type),
    typeTagType: noticeTypeTagType(record.type),
    publisherName: record.publisherName || '',
    publishTime: record.publishTime || '',
    read,
    readLabel: noticeReadLabel(read),
    readTagType: noticeReadTagType(read),
  }
}

export function presentDetail(record: NoticeDetailRecord = {}): NoticeDetailView {
  return {
    id: record.id ?? '',
    title: record.title || '',
    type: record.type,
    typeLabel: noticeTypeLabel(record.type),
    typeTagType: noticeTypeTagType(record.type),
    publisherId: record.publisherId ?? '',
    publisherName: record.publisherName || '',
    publishTime: record.publishTime || '',
    content: record.content || '',
    read: record.read === true,
  }
}

export function presentAdminListRecord(record: NoticeAdminRecord = {}): NoticeAdminListItem {
  return {
    id: record.id ?? '',
    title: record.title || '',
    type: record.type,
    typeLabel: noticeTypeLabel(record.type),
    status: record.status,
    statusLabel: noticeStatusLabel(record.status),
    statusTagType: noticeStatusTagType(record.status),
    publisherName: record.publisherName || '',
    publishTime: record.publishTime || '',
    createTime: record.createTime || '',
  }
}

/** tab 过滤的唯一 owner：未读 tab 才带 `unreadOnly=true`，全部 tab 不带参数。 */
export function feedQueryForTab(tab: NoticeListTab): NoticeFeedQuery {
  return tab === 'unread' ? { unreadOnly: 'true' } : {}
}

/** 管理侧筛选：空字段不进查询串；0 是合法的类型/状态，不能被当成空值丢掉。 */
export function presentAdminQuery(query: NoticeAdminQuery = {}): Record<string, string | number> {
  const params: Record<string, string | number> = {}
  if (query.title) params.title = query.title
  if (query.type !== '' && query.type != null) params.type = query.type
  if (query.status !== '' && query.status != null) params.status = query.status
  return params
}

export function emptyAdminQuery(): NoticeAdminQuery {
  return { title: '', type: '', status: '' }
}

const NOTICE_READ_API = '/admin/notices'
const NOTICE_ADMIN_API = '/admin/system/notices'

export function feedRequest(
  page: number,
  limit: number,
  query: NoticeFeedQuery = {},
): ApiRequestConfig {
  const params: Record<string, string | number | boolean> = { page, limit }
  if (query.unreadOnly === 'true') params.unreadOnly = true
  return { method: 'get', url: `${NOTICE_READ_API}/feed`, params }
}

export function unreadRequest(): ApiRequestConfig {
  return { method: 'get', url: `${NOTICE_READ_API}/unread` }
}

export function unreadCountRequest(): ApiRequestConfig {
  return { method: 'get', url: `${NOTICE_READ_API}/unread-count` }
}

export function detailRequest(id: EntityId): ApiRequestConfig {
  return { method: 'get', url: `${NOTICE_READ_API}/${id}` }
}

export function markReadRequest(id: EntityId): ApiRequestConfig {
  return { method: 'post', url: `${NOTICE_READ_API}/${id}/read` }
}

/** 正文图片：二进制直通，服务端带存储 content-type 与 nosniff。 */
export function imageRequest(id: EntityId): ApiRequestConfig & { responseType: 'blob' } {
  return { method: 'get', url: `${NOTICE_READ_API}/images/${id}`, responseType: 'blob' }
}

export function adminListRequest(
  page: number,
  limit: number,
  params: Record<string, string | number> = {},
): ApiRequestConfig {
  return { method: 'get', url: NOTICE_ADMIN_API, params: { page, limit, ...params } }
}

export function adminDetailRequest(id: EntityId): ApiRequestConfig {
  return { method: 'get', url: `${NOTICE_ADMIN_API}/${id}` }
}

export function createRequest(payload: NoticeWritePayload): ApiRequestConfig {
  return { method: 'post', url: NOTICE_ADMIN_API, data: { ...payload } }
}

export function updateRequest(id: EntityId, payload: NoticeWritePayload): ApiRequestConfig {
  return { method: 'put', url: `${NOTICE_ADMIN_API}/${id}`, data: { ...payload } }
}

export function removeRequest(id: EntityId): ApiRequestConfig {
  return { method: 'delete', url: `${NOTICE_ADMIN_API}/${id}` }
}

export function publishRequest(id: EntityId): ApiRequestConfig {
  return { method: 'post', url: `${NOTICE_ADMIN_API}/${id}/publish` }
}

export function withdrawRequest(id: EntityId): ApiRequestConfig {
  return { method: 'post', url: `${NOTICE_ADMIN_API}/${id}/withdraw` }
}

/** 上传走服务端转存（multipart 字段 `file`），不经浏览器直传、不加桶策略。 */
export function uploadImageRequest(file: File): ApiRequestConfig {
  const data = new FormData()
  data.append('file', file)
  return { method: 'post', url: `${NOTICE_ADMIN_API}/images`, data }
}
