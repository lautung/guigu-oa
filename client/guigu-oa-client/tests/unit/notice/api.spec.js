import { beforeEach, describe, expect, it, vi } from 'vitest'

const { requestMock, binaryMock } = vi.hoisted(() => ({
  requestMock: vi.fn(),
  binaryMock: vi.fn(),
}))

vi.mock('@/request/request', () => ({ default: requestMock, requestBinary: binaryMock }))

import {
  createNotice,
  fetchFeed,
  fetchNoticeDetail,
  fetchNoticeImage,
  fetchUnread,
  fetchUnreadCount,
  findNoticePage,
  getNotice,
  markNoticeRead,
  publishNotice,
  removeNotice,
  updateNotice,
  uploadNoticeImage,
  withdrawNotice,
} from '@/notice/api'
import {
  adminDetailRequest,
  adminListRequest,
  createRequest,
  detailRequest,
  feedRequest,
  imageRequest,
  markReadRequest,
  publishRequest,
  removeRequest,
  unreadCountRequest,
  unreadRequest,
  updateRequest,
  uploadImageRequest,
  withdrawRequest,
} from '@/notice/config'

describe('公告 api 包装', () => {
  beforeEach(() => {
    requestMock.mockReset()
    requestMock.mockImplementation(async (config) => ({ code: 200, data: config }))
    binaryMock.mockReset()
  })

  it('routes read-side calls through the configured client', async () => {
    await fetchFeed(1, 10)
    await fetchUnread()
    await fetchUnreadCount()
    await fetchNoticeDetail(3)
    await markNoticeRead(3)

    expect(requestMock.mock.calls.map(([config]) => config)).toEqual([
      feedRequest(1, 10),
      unreadRequest(),
      unreadCountRequest(),
      detailRequest(3),
      markReadRequest(3),
    ])
  })

  it('routes images through the binary client', async () => {
    const blob = new Blob(['x'], { type: 'image/png' })
    binaryMock.mockResolvedValue(blob)

    await expect(fetchNoticeImage(6)).resolves.toBe(blob)
    expect(binaryMock).toHaveBeenCalledWith(imageRequest(6), { notifyNormalErrors: false })
  })

  it('routes admin calls through the configured client for #220', async () => {
    const payload = { title: 't', type: 1, content: '<p>x</p>' }
    const file = new File(['x'], 'a.png', { type: 'image/png' })

    await findNoticePage(1, 10, { status: 0 })
    await getNotice(4)
    await createNotice(payload)
    await updateNotice(4, payload)
    await removeNotice(4)
    await publishNotice(4)
    await withdrawNotice(4)
    await uploadNoticeImage(file)

    expect(requestMock.mock.calls.map(([config]) => config)).toEqual([
      adminListRequest(1, 10, { status: 0 }),
      adminDetailRequest(4),
      createRequest(payload),
      updateRequest(4, payload),
      removeRequest(4),
      publishRequest(4),
      withdrawRequest(4),
      uploadImageRequest(file),
    ])
  })
})
