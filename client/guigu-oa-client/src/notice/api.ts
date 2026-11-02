import request, { requestBinary } from '@/request/request'
import {
  adminDetailRequest,
  adminListRequest,
  createRequest,
  detailRequest,
  feedRequest,
  imageRequest,
  markReadRequest,
  presentAdminQuery,
  publishRequest,
  removeRequest,
  unreadCountRequest,
  unreadRequest,
  updateRequest,
  uploadImageRequest,
  withdrawRequest,
  type NoticeAdminQuery,
  type NoticeFeedQuery,
} from '@/notice/config'
import type {
  ApiResponse,
  EntityId,
  NoticeAdminRecord,
  NoticeDetailRecord,
  NoticeFeedRecord,
  NoticeImageResult,
  NoticeWritePayload,
  PageResult,
} from '@/types/domain'

/** 公告读侧（ADR 0055）：列表永不返回正文。 */
export function fetchFeed(
  page: number,
  limit: number,
  query: NoticeFeedQuery = {},
): Promise<ApiResponse<PageResult<NoticeFeedRecord>>> {
  return request<PageResult<NoticeFeedRecord>>(feedRequest(page, limit, query))
}

export function fetchUnread(): Promise<ApiResponse<NoticeFeedRecord[]>> {
  return request<NoticeFeedRecord[]>(unreadRequest())
}

export function fetchUnreadCount(): Promise<ApiResponse<number>> {
  return request<number>(unreadCountRequest())
}

/** 撤回/删除/不存在时 `data` 为空，页面按不存在处理。 */
export function fetchNoticeDetail(id: EntityId): Promise<ApiResponse<NoticeDetailRecord | null>> {
  return request<NoticeDetailRecord | null>(detailRequest(id))
}

/** 打开详情成功后显式写回执；幂等，撤回/删除返回 201「公告不存在」。 */
export function markNoticeRead(id: EntityId): Promise<ApiResponse<void>> {
  return request<void>(markReadRequest(id))
}

/** 正文图片：二进制直通，认证头由 request transport 附带（同源 `<img>` 拿不到 header）。 */
export function fetchNoticeImage(id: EntityId): Promise<Blob> {
  // 背景取图失败不弹全局提示；水合器保留原 src，正文其余部分照常阅读。
  return requestBinary(imageRequest(id), { notifyNormalErrors: false })
}

/** 管理侧（#220 发布页复用）：页面 `/system/notice`，按钮点见 `NOTICE_PERMISSIONS`。 */
export function findNoticePage(
  page: number,
  limit: number,
  query: NoticeAdminQuery = {},
): Promise<ApiResponse<PageResult<NoticeAdminRecord>>> {
  return request<PageResult<NoticeAdminRecord>>(
    adminListRequest(page, limit, presentAdminQuery(query)),
  )
}

export function getNotice(id: EntityId): Promise<ApiResponse<NoticeAdminRecord>> {
  return request<NoticeAdminRecord>(adminDetailRequest(id))
}

export function createNotice(payload: NoticeWritePayload): Promise<ApiResponse<NoticeAdminRecord>> {
  return request<NoticeAdminRecord>(createRequest(payload))
}

export function updateNotice(
  id: EntityId,
  payload: NoticeWritePayload,
): Promise<ApiResponse<NoticeAdminRecord>> {
  return request<NoticeAdminRecord>(updateRequest(id, payload))
}

export function removeNotice(id: EntityId): Promise<ApiResponse<void>> {
  return request<void>(removeRequest(id))
}

export function publishNotice(id: EntityId): Promise<ApiResponse<NoticeAdminRecord>> {
  return request<NoticeAdminRecord>(publishRequest(id))
}

export function withdrawNotice(id: EntityId): Promise<ApiResponse<NoticeAdminRecord>> {
  return request<NoticeAdminRecord>(withdrawRequest(id))
}

export function uploadNoticeImage(file: File): Promise<ApiResponse<NoticeImageResult>> {
  return request<NoticeImageResult>(uploadImageRequest(file))
}
