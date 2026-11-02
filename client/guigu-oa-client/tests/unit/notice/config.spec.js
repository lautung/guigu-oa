import { describe, expect, it } from 'vitest'

import {
  createRequest,
  detailRequest,
  emptyAdminQuery,
  feedQueryForTab,
  feedRequest,
  imageRequest,
  markReadRequest,
  noticeReadLabel,
  noticeStatusLabel,
  noticeStatusTagType,
  noticeTypeLabel,
  noticeTypeTagType,
  presentAdminListRecord,
  presentAdminQuery,
  presentDetail,
  presentFeedItem,
  publishRequest,
  removeRequest,
  unreadCountRequest,
  unreadRequest,
  updateRequest,
  uploadImageRequest,
  withdrawRequest,
} from '@/notice/config'

describe('公告标签与展示投影', () => {
  it('renders the fixed type and status enums from one constant', () => {
    expect(noticeTypeLabel(1)).toBe('通知')
    expect(noticeTypeLabel(2)).toBe('公告')
    expect(noticeTypeLabel(undefined)).toBe('')
    expect(noticeTypeTagType(2)).toBe('warning')
    expect(noticeTypeTagType(1)).toBe('info')
    expect(noticeStatusLabel(0)).toBe('草稿')
    expect(noticeStatusLabel(1)).toBe('已发布')
    expect(noticeStatusLabel(2)).toBe('已撤回')
    expect(noticeStatusTagType(1)).toBe('success')
    expect(noticeStatusTagType(2)).toBe('danger')
    expect(noticeStatusTagType(0)).toBe('info')
    expect(noticeReadLabel(true)).toBe('已读')
    expect(noticeReadLabel(false)).toBe('未读')
  })

  it('maps feed rows with read markers and empty defaults', () => {
    const item = presentFeedItem({
      id: 7,
      title: '系统维护',
      type: 2,
      publisherName: '管理员',
      publishTime: '2026-11-03 10:00:00',
    })

    expect(item).toMatchObject({
      id: 7,
      title: '系统维护',
      typeLabel: '公告',
      typeTagType: 'warning',
      publisherName: '管理员',
      publishTime: '2026-11-03 10:00:00',
      read: false,
      readLabel: '未读',
      readTagType: 'danger',
    })
    expect(presentFeedItem()).toMatchObject({ id: '', title: '', read: false })
  })

  it('keeps detail content verbatim for the single renderer', () => {
    const detail = presentDetail({
      id: 3,
      title: '标题',
      type: 1,
      content: '<p><strong>x</strong></p>',
      read: true,
    })

    expect(detail.content).toBe('<p><strong>x</strong></p>')
    expect(detail.typeLabel).toBe('通知')
    expect(detail.read).toBe(true)
    expect(presentDetail().content).toBe('')
  })

  it('projects admin rows without leaking content into the list', () => {
    const row = presentAdminListRecord({
      id: 9,
      title: '草稿',
      type: 1,
      status: 0,
      content: '<p>不应出现在列表</p>',
    })

    expect(row).toMatchObject({ id: 9, title: '草稿', statusLabel: '草稿', statusTagType: 'info' })
    expect(row.content).toBeUndefined()
  })
})

describe('公告请求形状', () => {
  it('maps the feed tab to unreadOnly only for the unread tab', () => {
    expect(feedQueryForTab('all')).toEqual({})
    expect(feedQueryForTab('unread')).toEqual({ unreadOnly: 'true' })

    expect(feedRequest(1, 10)).toEqual({
      method: 'get',
      url: '/admin/notices/feed',
      params: { page: 1, limit: 10 },
    })
    expect(feedRequest(2, 20, feedQueryForTab('unread'))).toEqual({
      method: 'get',
      url: '/admin/notices/feed',
      params: { page: 2, limit: 20, unreadOnly: true },
    })
  })

  it('declares the read-side endpoints from ADR 0055', () => {
    expect(unreadRequest()).toEqual({ method: 'get', url: '/admin/notices/unread' })
    expect(unreadCountRequest()).toEqual({ method: 'get', url: '/admin/notices/unread-count' })
    expect(detailRequest(5)).toEqual({ method: 'get', url: '/admin/notices/5' })
    expect(markReadRequest(5)).toEqual({ method: 'post', url: '/admin/notices/5/read' })
    expect(imageRequest(8)).toEqual({
      method: 'get',
      url: '/admin/notices/images/8',
      responseType: 'blob',
    })
  })

  it('declares the admin endpoints #220 reuses', () => {
    expect(createRequest({ title: 't', type: 1, content: '<p>x</p>' })).toEqual({
      method: 'post',
      url: '/admin/system/notices',
      data: { title: 't', type: 1, content: '<p>x</p>' },
    })
    expect(updateRequest(4, { title: 't', type: 2, content: '' })).toEqual({
      method: 'put',
      url: '/admin/system/notices/4',
      data: { title: 't', type: 2, content: '' },
    })
    expect(removeRequest(4)).toEqual({ method: 'delete', url: '/admin/system/notices/4' })
    expect(publishRequest(4)).toEqual({ method: 'post', url: '/admin/system/notices/4/publish' })
    expect(withdrawRequest(4)).toEqual({ method: 'post', url: '/admin/system/notices/4/withdraw' })
  })

  it('uploads images as multipart with the file field', () => {
    const file = new File(['png'], 'shot.png', { type: 'image/png' })
    const request = uploadImageRequest(file)

    expect(request.method).toBe('post')
    expect(request.url).toBe('/admin/system/notices/images')
    expect(request.data).toBeInstanceOf(FormData)
    expect(request.data.get('file')).toBe(file)
  })
})

describe('管理侧筛选', () => {
  it('drops empty filters but keeps zero', () => {
    expect(presentAdminQuery(emptyAdminQuery())).toEqual({})
    expect(presentAdminQuery({ title: '维', type: 0, status: 0 })).toEqual({
      title: '维',
      type: 0,
      status: 0,
    })
    expect(presentAdminQuery({ title: '', type: '', status: 2 })).toEqual({ status: 2 })
  })
})
