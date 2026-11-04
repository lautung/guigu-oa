import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

import ElementPlus from 'element-plus'

import ApiDocPage from '@/views/tool/api-doc/index.vue'
import { ensureDomApis } from '../support/harness'

ensureDomApis()

function mountPage() {
  return mount(ApiDocPage, { global: { plugins: [ElementPlus] } })
}

let openSpy

beforeEach(() => {
  openSpy = vi.spyOn(window, 'open').mockImplementation(() => null)
})

afterEach(() => {
  vi.unstubAllGlobals()
  openSpy.mockRestore()
})

describe('接口文档页', () => {
  it('offers the Swagger UI button when the document endpoint answers 200', async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true })
    vi.stubGlobal('fetch', fetchMock)

    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('.open-swagger-button').text()).toBe('打开 Swagger UI')
    expect(wrapper.text()).toContain('/v3/api-docs')
    expect(wrapper.text()).toContain('/swagger-ui.html')
    expect(wrapper.text()).not.toContain('未启用')

    await wrapper.find('.open-swagger-button').trigger('click')
    expect(openSpy).toHaveBeenCalledWith('/swagger-ui.html', '_blank', 'noopener')
  })

  it('shows the 未启用 empty state on 404 without a dead link', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false }))

    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.text()).toContain('当前环境未启用接口文档，仅开发环境提供')
    expect(wrapper.find('.open-swagger-button').exists()).toBe(false)
  })
})
