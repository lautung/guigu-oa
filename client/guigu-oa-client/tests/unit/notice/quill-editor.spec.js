import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'

import QuillEditor from '@/notice/QuillEditor.vue'

/** 编辑器图片：原生 `<img>` 带不上 token，必须换成认证取回的 blob；保存仍写服务端 URL。 */
function mountEditor(overrides = {}) {
  const created = []
  const revoked = []
  const loadImage =
    overrides.loadImage || vi.fn(async () => new Blob(['png'], { type: 'image/png' }))
  const wrapper = mount(QuillEditor, {
    attachTo: document.body,
    props: {
      modelValue: '',
      loadImage,
      createObjectUrl: () => {
        const url = `blob:editor-${created.length + 1}`
        created.push(url)
        return url
      },
      revokeObjectUrl: (url) => revoked.push(url),
    },
  })
  return { wrapper, loadImage, created, revoked }
}

function images(wrapper) {
  return wrapper.findAll('.ql-editor img')
}

describe('公告编辑器图片水合', () => {
  it('插入后把服务端地址水合成 blob，保留 data-notice-src', async () => {
    const { wrapper, loadImage } = mountEditor()

    wrapper.vm.insertImage('/admin/notices/images/9')

    await vi.waitFor(() => {
      expect(images(wrapper)).toHaveLength(1)
      expect(images(wrapper)[0].attributes('src')).toMatch(/^blob:editor-/)
    })
    expect(loadImage).toHaveBeenCalledWith(9)
    expect(images(wrapper)[0].attributes('data-notice-src')).toBe('/admin/notices/images/9')

    wrapper.unmount()
  })

  it('emitted HTML 只含服务端地址，不泄漏 blob', async () => {
    const { wrapper } = mountEditor()

    wrapper.vm.insertImage('/admin/notices/images/9')
    await vi.waitFor(() => expect(images(wrapper)[0].attributes('src')).toMatch(/^blob:editor-/))

    const emitted = wrapper.emitted('update:modelValue') || []
    expect(emitted.length).toBeGreaterThan(0)
    for (const [html] of emitted) {
      expect(html).toContain('/admin/notices/images/9')
      expect(html).not.toContain('blob:')
      expect(html).not.toContain('data-notice-src')
    }

    wrapper.unmount()
  })

  it('重新应用内容与卸载时撤销对象地址', async () => {
    const { wrapper, revoked } = mountEditor()

    wrapper.vm.insertImage('/admin/notices/images/9')
    await vi.waitFor(() => expect(images(wrapper)[0].attributes('src')).toMatch(/^blob:editor-/))
    const first = images(wrapper)[0].attributes('src')

    await wrapper.setProps({
      modelValue: '<p>换图</p><img src="/admin/notices/images/10">',
    })
    await vi.waitFor(() => {
      const current = images(wrapper)[0]
      expect(current.attributes('src')).toMatch(/^blob:editor-/)
      expect(current.attributes('src')).not.toBe(first)
    })
    expect(revoked).toEqual([first])
    const second = images(wrapper)[0].attributes('src')

    wrapper.unmount()
    expect(revoked).toEqual([first, second])
  })

  it('取回失败时保留服务端地址，不产生对象地址', async () => {
    const loadImage = vi.fn(async () => {
      throw new Error('401')
    })
    const { wrapper } = mountEditor({ loadImage })

    wrapper.vm.insertImage('/admin/notices/images/6')
    await vi.waitFor(() => expect(loadImage).toHaveBeenCalledWith(6))
    await new Promise((resolve) => setTimeout(resolve, 0))

    expect(images(wrapper)).toHaveLength(1)
    expect(images(wrapper)[0].attributes('src')).toBe('/admin/notices/images/6')
    expect(images(wrapper)[0].attributes('data-notice-src')).toBeUndefined()
    const emitted = wrapper.emitted('update:modelValue') || []
    expect(emitted.length).toBeGreaterThan(0)
    expect(emitted.every(([html]) => html.includes('/admin/notices/images/6'))).toBe(true)

    wrapper.unmount()
  })
})
