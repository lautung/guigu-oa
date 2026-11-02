import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'

import NoticeContent from '@/notice/NoticeContent.vue'

describe('公告正文唯一渲染面', () => {
  it('按服务端消毒后的 HTML 原样渲染', () => {
    const wrapper = mount(NoticeContent, {
      props: {
        html: '<h2>标题</h2><p>正文 <strong>粗</strong></p><p><a href="https://example.com">链接</a></p>',
        loadImage: vi.fn(),
      },
    })

    expect(wrapper.find('.notice-content').html()).toContain('<h2>标题</h2>')
    expect(wrapper.find('.notice-content strong').text()).toBe('粗')
    expect(wrapper.find('.notice-content a').attributes('href')).toBe('https://example.com')
  })

  it('通过认证取回把公告图片换成 blob 地址，其他图片不动', async () => {
    const created = []
    const revoked = []
    const loadImage = vi.fn(async () => new Blob(['png'], { type: 'image/png' }))
    const wrapper = mount(NoticeContent, {
      props: {
        html: '<p><img src="/admin/notices/images/2"><img src="/avatar.svg"></p>',
        loadImage,
        createObjectUrl: () => {
          const url = `blob:notice-${created.length + 1}`
          created.push(url)
          return url
        },
        revokeObjectUrl: (url) => revoked.push(url),
      },
    })

    await vi.waitFor(() => {
      expect(wrapper.find('img[src="/admin/notices/images/2"]').exists()).toBe(false)
    })

    expect(loadImage).toHaveBeenCalledWith(2)
    expect(wrapper.find('img[src="blob:notice-1"]').exists()).toBe(true)
    expect(wrapper.find('img[src="/avatar.svg"]').exists()).toBe(true)

    wrapper.unmount()
    expect(revoked).toEqual(['blob:notice-1'])
  })

  it('正文变化时撤销上一批对象地址并重新水合', async () => {
    const revoked = []
    let next = 0
    const wrapper = mount(NoticeContent, {
      props: {
        html: '<img src="/admin/notices/images/7">',
        loadImage: vi.fn(async () => new Blob(['png'], { type: 'image/png' })),
        createObjectUrl: () => `blob:next-${++next}`,
        revokeObjectUrl: (url) => revoked.push(url),
      },
    })

    await vi.waitFor(() => expect(wrapper.find('img[src="blob:next-1"]').exists()).toBe(true))

    await wrapper.setProps({ html: '<img src="/admin/notices/images/8">' })
    await vi.waitFor(() => expect(wrapper.find('img[src="blob:next-2"]').exists()).toBe(true))
    expect(revoked).toEqual(['blob:next-1'])
  })
})
