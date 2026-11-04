import { afterEach, describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'

import App from '@/App.vue'
import { setLocale } from '@/i18n'
import { elementStubs, setupSession } from '../support/harness'

afterEach(() => setLocale('zh-CN', null))

describe('根级 Element Plus locale', () => {
  it('wraps login/register routes (the whole router-view) in the locale provider', () => {
    setLocale('en', null)
    const { pinia } = setupSession()
    const wrapper = mount(App, {
      global: {
        plugins: [pinia],
        stubs: {
          ...elementStubs,
          'router-view': { template: '<div class="route-view" />' },
        },
      },
    })

    const provider = wrapper.find('.el-config-provider-stub')
    expect(provider.attributes('data-locale')).toBe('en')
    expect(provider.find('.route-view').exists()).toBe(true)
  })

  it('follows a locale switch without remounting the root', async () => {
    setLocale('zh-CN', null)
    const { pinia } = setupSession()
    const wrapper = mount(App, {
      global: {
        plugins: [pinia],
        stubs: {
          ...elementStubs,
          'router-view': { template: '<div class="route-view" />' },
        },
      },
    })

    expect(wrapper.find('.el-config-provider-stub').attributes('data-locale')).toBe('zh-cn')
    setLocale('en', null)
    await wrapper.vm.$nextTick()
    expect(wrapper.find('.el-config-provider-stub').attributes('data-locale')).toBe('en')
  })
})
