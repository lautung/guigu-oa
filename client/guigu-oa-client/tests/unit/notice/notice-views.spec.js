import { afterEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

const { markReadMock, refreshUnreadMock, fetchImageMock } = vi.hoisted(() => ({
  markReadMock: vi.fn(async () => ({ code: 200, data: null })),
  refreshUnreadMock: vi.fn(),
  fetchImageMock: vi.fn(async () => new Blob(['png'], { type: 'image/png' })),
}))

vi.mock('@/notice/api', () => ({
  markNoticeRead: markReadMock,
  fetchNoticeImage: fetchImageMock,
}))

vi.mock('@/notice/unread-browser', () => ({
  refreshNoticeUnread: refreshUnreadMock,
  useNoticeUnread: vi.fn(),
}))

import NoticeDetailPage from '@/views/notice/detail.vue'
import NoticeListPage from '@/views/notice/index.vue'
import {
  createTestRouter,
  installHttp,
  mountOptions,
  ok,
  paged,
  setupSession,
} from '../support/harness'

const feedRow = (id, read = false) => ({
  id,
  title: `公告 ${id}`,
  type: id % 2 === 0 ? 2 : 1,
  publisherName: '管理员',
  publishTime: '2026-11-03 10:00:00',
  read,
})

let http

afterEach(() => {
  http?.restore()
  http = undefined
  markReadMock.mockClear()
  refreshUnreadMock.mockClear()
})

function noticeRouter() {
  return createTestRouter([
    { path: '/', component: { template: '<div />' } },
    { path: '/notice', name: 'NoticeList', component: { template: '<div />' } },
    { path: '/notice/:id', name: 'NoticeDetail', component: { template: '<div />' } },
  ])
}

describe('全部公告列表', () => {
  it('按 publish_time DESC 的行投影展示类型、读态与分页', async () => {
    http = installHttp((config) => {
      if (config.url === '/admin/notices/feed') {
        return paged(
          [
            { ...feedRow(2, false), publishTime: '2026-11-03 12:00:00' },
            { ...feedRow(1, true), publishTime: '2026-11-02 12:00:00' },
          ],
          2,
        )
      }
      return ok(null)
    })
    const { pinia } = setupSession({ name: 'Alice' })
    const wrapper = mount(NoticeListPage, mountOptions(pinia, noticeRouter()))
    await wrapper.vm.list.ready
    await flushPromises()

    expect(wrapper.vm.view.status).toBe('ready')
    expect(wrapper.vm.rows.map((row) => row.title)).toEqual(['公告 2', '公告 1'])
    expect(wrapper.vm.rows[0]).toMatchObject({
      type: 2,
      readLabel: '未读',
      readTagType: 'danger',
    })
    expect(wrapper.vm.rows[1]).toMatchObject({ type: 1, readLabel: '已读' })
    expect(wrapper.vm.view.total).toBe(2)
  })

  it('未读 tab 只带 unreadOnly=true，切回全部不带过滤', async () => {
    http = installHttp((config) => {
      if (config.url === '/admin/notices/feed') return paged([feedRow(1)])
      return ok(null)
    })
    const { pinia } = setupSession({ name: 'Alice' })
    const wrapper = mount(NoticeListPage, mountOptions(pinia, noticeRouter()))
    await wrapper.vm.list.ready
    await flushPromises()

    expect(http.calls[0].params).toEqual({ page: 1, limit: 10 })

    wrapper.vm.changeTab('unread')
    await flushPromises()
    expect(http.calls.at(-1).params).toEqual({ page: 1, limit: 10, unreadOnly: true })

    wrapper.vm.changeTab('all')
    await flushPromises()
    expect(http.calls.at(-1).params).toEqual({ page: 1, limit: 10 })
  })

  it('点击标题进入详情', async () => {
    http = installHttp((config) =>
      config.url === '/admin/notices/feed' ? paged([feedRow(8)]) : ok(null),
    )
    const { pinia } = setupSession({ name: 'Alice' })
    const router = noticeRouter()
    const wrapper = mount(NoticeListPage, mountOptions(pinia, router))
    await wrapper.vm.list.ready
    await flushPromises()

    wrapper.vm.openDetail(wrapper.vm.rows[0])
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/notice/8')
  })
})

describe('公告详情', () => {
  async function mountDetail(id, responder) {
    http = installHttp(responder)
    const { pinia } = setupSession({ name: 'Alice' })
    const router = noticeRouter()
    await router.push(`/notice/${id}`)
    await router.isReady()
    const wrapper = mount(NoticeDetailPage, mountOptions(pinia, router))
    await flushPromises()
    return { wrapper, router }
  }

  it('加载成功后渲染正文并只写一次已读回执', async () => {
    const { wrapper } = await mountDetail(5, (config) => {
      if (config.url === '/admin/notices/5') {
        return ok({
          id: 5,
          title: '系统维护公告',
          type: 2,
          content: '<p>今晚 <strong>22:00</strong> 维护</p>',
          publisherName: '管理员',
          publishTime: '2026-11-03 10:00:00',
          read: false,
        })
      }
      return ok(null)
    })

    expect(wrapper.vm.status).toBe('ready')
    expect(wrapper.vm.detail.title).toBe('系统维护公告')
    expect(wrapper.find('.notice-content').html()).toContain('<strong>22:00</strong>')
    expect(markReadMock).toHaveBeenCalledTimes(1)
    expect(markReadMock).toHaveBeenCalledWith('5')
    expect(refreshUnreadMock).toHaveBeenCalledTimes(1)

    await wrapper.vm.load()
    await flushPromises()
    expect(markReadMock).toHaveBeenCalledTimes(1)
  })

  it('已读公告照常渲染，切换 id 后各写一次', async () => {
    const { wrapper, router } = await mountDetail(5, (config) => {
      const id = Number(config.url.split('/').at(-1))
      return /^\/admin\/notices\/\d+$/.test(config.url)
        ? ok({
            id,
            title: `公告 ${id}`,
            type: 1,
            content: '<p>正文</p>',
            publisherName: '管理员',
            publishTime: '2026-11-03 10:00:00',
            read: true,
          })
        : ok(null)
    })

    expect(wrapper.vm.detail.read).toBe(true)
    expect(wrapper.find('.notice-content').text()).toBe('正文')
    expect(markReadMock).toHaveBeenCalledWith('5')

    await router.push('/notice/6')
    await flushPromises()

    expect(wrapper.vm.detail.title).toBe('公告 6')
    expect(markReadMock).toHaveBeenCalledWith('6')
    expect(markReadMock).toHaveBeenCalledTimes(2)
  })

  it('撤回/删除/不存在按不存在视图处理，不写回执', async () => {
    const { wrapper } = await mountDetail(9, () => ok(null))

    expect(wrapper.vm.status).toBe('missing')
    expect(wrapper.find('.notice-missing').exists()).toBe(true)
    expect(wrapper.find('.notice-content').exists()).toBe(false)
    expect(markReadMock).not.toHaveBeenCalled()
    expect(wrapper.vm.error).toBe('')
  })

  it('读取失败保留可重试的错误视图', async () => {
    const { wrapper } = await mountDetail(9, () => ({ code: 500, message: '服务器错误' }))

    expect(wrapper.vm.status).toBe('failed')
    expect(wrapper.vm.error).toBe('服务器错误')
    expect(markReadMock).not.toHaveBeenCalled()
  })

  it('已读回执失败不打断详情，并允许下次重写', async () => {
    markReadMock.mockRejectedValueOnce(new Error('network'))
    const { wrapper } = await mountDetail(5, (config) =>
      config.url === '/admin/notices/5'
        ? ok({
            id: 5,
            title: '公告',
            type: 1,
            content: '<p>正文</p>',
            publisherName: '管理员',
            publishTime: '2026-11-03 10:00:00',
            read: false,
          })
        : ok(null),
    )

    expect(wrapper.vm.status).toBe('ready')
    expect(refreshUnreadMock).not.toHaveBeenCalled()

    await wrapper.vm.load()
    await flushPromises()
    expect(markReadMock).toHaveBeenCalledTimes(2)
  })
})
