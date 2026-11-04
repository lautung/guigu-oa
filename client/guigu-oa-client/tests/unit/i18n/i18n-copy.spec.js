import { afterEach, describe, expect, it } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent, computed, nextTick } from 'vue'
import ElementPlus, { ElConfigProvider } from 'element-plus'
import { createMemoryHistory, createRouter } from 'vue-router'

import ListLoadError from '@/components/ListLoadError.vue'
import ListPagination from '@/components/ListPagination.vue'
import ModulePlaceholder from '@/shell/ModulePlaceholder.vue'
import LockScreen from '@/shell/LockScreen.vue'
import LoginPage from '@/views/login/index.vue'
import ProfilePage from '@/views/profile/index.vue'
import { useLockScreenStore } from '@/shell/lock-screen'
import { DEFAULT_LOCALE, elementLocaleFor, setLocale } from '@/i18n'
import {
  PermissionRequestFailure,
  businessFailure,
  transportFailure,
} from '@/request/request-failure'
import { validateAvatarFile, validatePassword, validateProfile } from '@/profile/config'
import { ensureDomApis, installHttp, mountOptions, ok, setupSession } from '../support/harness'

ensureDomApis()

const alertStub = {
  name: 'ElAlert',
  props: ['title'],
  template: '<section class="alert-stub" :data-title="title"><slot /></section>',
}
const buttonStub = { name: 'ElButton', template: '<button><slot /></button>' }
const emptyStub = {
  name: 'ElEmpty',
  props: ['description'],
  template: '<div class="empty-stub">{{ description }}<slot /></div>',
}

afterEach(() => {
  setLocale(DEFAULT_LOCALE)
  localStorage.clear()
})

describe('通用组件英文文案', () => {
  it('shows the load error title and retry in English', () => {
    setLocale('en')
    const wrapper = mount(ListLoadError, {
      props: { status: 'failed' },
      global: { stubs: { 'el-alert': alertStub, 'el-button': buttonStub } },
    })

    expect(wrapper.findComponent({ name: 'ElAlert' }).props('title')).toBe('Load failed')
    expect(wrapper.find('button').text()).toBe('Retry')
  })

  it('shows the module placeholder empty state in English', async () => {
    setLocale('en')
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/system', component: { template: '<div />' }, meta: { title: '系统管理' } }],
    })
    await router.push('/system')
    await router.isReady()
    const wrapper = mount(ModulePlaceholder, {
      global: { plugins: [router], stubs: { 'el-empty': emptyStub } },
    })

    expect(wrapper.find('.empty-stub').text()).toContain('No pages available in 系统管理')
    expect(wrapper.text()).toContain('Pages for this module will be provided in a later version.')
  })

  it('renders Element Plus pagination text in the active locale', async () => {
    const Host = defineComponent({
      components: { ElConfigProvider, ListPagination },
      setup() {
        return { locale: computed(() => elementLocaleFor()) }
      },
      template: `
        <el-config-provider :locale="locale">
          <list-pagination :page="1" :size="10" :total="42" />
        </el-config-provider>
      `,
    })
    const wrapper = mount(Host, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    expect(wrapper.text()).toContain('共 42 条')

    setLocale('en')
    await nextTick()
    expect(wrapper.text()).toContain('Total 42')
  })
})

describe('request 层英文文案', () => {
  it('falls back to English defaults instead of Chinese', () => {
    setLocale('en')

    expect(new PermissionRequestFailure().message).toBe('No permission')
    expect(businessFailure({ code: 500, message: '' }).message).toBe('Request failed')
    expect(transportFailure('boom').message).toBe('Request failed')
  })
})

describe('个人中心英文文案', () => {
  it('validates profile fields with English messages', () => {
    setLocale('en')

    expect(validateProfile({ name: '', phone: '' })).toBe('Name is required')
    expect(validateProfile({ name: 'a'.repeat(21), phone: '' })).toBe('Name is too long')
    expect(validateProfile({ name: 'Alice', phone: '1'.repeat(21) })).toBe(
      'Phone number is too long',
    )
    expect(validatePassword({ oldPassword: '', newPassword: '' }, '')).toBe(
      'Old password is required',
    )
    expect(validatePassword({ oldPassword: 'old', newPassword: '123' }, '123')).toBe(
      'Password must be 6 to 100 characters long',
    )
    expect(validatePassword({ oldPassword: 'old', newPassword: '123456' }, '654321')).toBe(
      'The two passwords do not match',
    )
    expect(validateAvatarFile(new File(['x'], 'a.gif', { type: 'image/gif' }))).toBe(
      'Unsupported avatar type',
    )
    expect(
      validateAvatarFile(new File([new Uint8Array(2 * 1024 * 1024 + 1)], 'a.png', {
        type: 'image/png',
      })),
    ).toBe('Avatar exceeds the 2 MB limit')
  })

  it('renders the profile page in English', async () => {
    setLocale('en')
    const http = installHttp((config) => {
      if (config.url === '/admin/system/profile') {
        return ok({ username: 'alice', name: 'Alice', phone: '', avatar: '' })
      }
      return ok(null)
    })
    try {
      const { pinia } = setupSession({ name: 'Alice' })
      const wrapper = mount(ProfilePage, { global: { plugins: [pinia, ElementPlus] } })
      await flushPromises()

      expect(wrapper.find('.page-title').text()).toBe('Profile')
      expect(wrapper.text()).toContain('Avatar')
      expect(wrapper.text()).toContain('Basic information')
      expect(wrapper.text()).toContain('Change password')
      expect(wrapper.text()).toContain('Save profile')
    } finally {
      http.restore()
    }
  })
})

describe('锁屏英文文案', () => {
  it('renders the lock screen in English', () => {
    setLocale('en')
    const { pinia } = setupSession({ name: 'Alice' })
    useLockScreenStore(pinia).lock()
    const wrapper = mount(LockScreen, mountOptions(pinia))

    expect(wrapper.find('.lock-screen').attributes('aria-label')).toBe('Lock screen')
    expect(wrapper.find('.lock-title').text()).toBe('Screen locked')
    expect(wrapper.find('.lock-password').attributes('placeholder')).toBe('Enter your password')
    expect(wrapper.find('.lock-submit').text()).toBe('Unlock')
  })
})

describe('登录页英文文案', () => {
  it('renders placeholders and the submit button in English', async () => {
    setLocale('en')
    const http = installHttp(() => ok({ enabled: false }))
    try {
      const { pinia } = setupSession({ token: '' })
      const router = createRouter({
        history: createMemoryHistory(),
        routes: [
          { path: '/login', component: { template: '<div />' } },
          { path: '/register', component: { template: '<div />' } },
        ],
      })
      await router.push('/login')
      await router.isReady()
      const wrapper = mount(LoginPage, { global: { plugins: [pinia, router, ElementPlus] } })
      await flushPromises()

      expect(wrapper.find('input[placeholder="Username"]').exists()).toBe(true)
      expect(wrapper.find('input[placeholder="Password"]').exists()).toBe(true)
      expect(wrapper.find('.login-btn').text()).toBe('Sign in')
    } finally {
      http.restore()
    }
  })
})
