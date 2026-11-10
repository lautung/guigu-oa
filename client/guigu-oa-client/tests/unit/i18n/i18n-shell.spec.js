import { afterEach, describe, expect, it } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'

import AppShell from '@/shell/AppShell.vue'
import AppTopBar from '@/shell/AppTopBar.vue'
import NoticeBell from '@/shell/NoticeBell.vue'
import { buildShellNav } from '@/shell/shell-nav'
import { DEFAULT_LOCALE, LOCALE_STORAGE_KEY, currentLocale, setLocale } from '@/i18n'
import { elementStubs, installHttp, mountOptions, ok, setupSession } from '../support/harness'

const page = (name) => ({ template: `<div class="page-${name}">${name}</div>` })

function shellRouter(initial = '/work/pending') {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/workbench', component: page('workbench'), meta: { title: '工作台' } },
      { path: '/work/pending', component: page('pending'), meta: { title: '待处理' } },
      { path: '/work/start', component: page('start'), meta: { title: '发起审批' } },
      { path: '/system/sysUser', component: page('sysUser'), meta: { title: '用户管理' } },
    ],
  })
  router.push(initial)
  return router
}

function shellOptions(pinia, router) {
  const options = mountOptions(pinia, router)
  return {
    global: {
      ...options.global,
      stubs: { ...elementStubs, NoticeBell: true },
    },
  }
}

describe('外壳 locale 切换', () => {
  afterEach(() => {
    setLocale(DEFAULT_LOCALE)
    localStorage.clear()
  })

  it('renders the shell chrome in English after switching locale', async () => {
    setLocale('en')
    const { pinia } = setupSession({ name: 'Alice' })
    const router = shellRouter()
    await router.isReady()
    const wrapper = mount(AppShell, shellOptions(pinia, router))

    expect(wrapper.findAll('.top-nav-item').map((item) => item.text())).toEqual([
      'Workbench',
      'Work',
      'Business',
      'System',
      'Monitor',
      'Tools',
    ])
    expect(wrapper.find('.shell-breadcrumb').text()).toContain('Home')
    expect(wrapper.find('.shell-breadcrumb').text()).toContain('Work')
    expect(wrapper.findAll('.shell-tab').map((tab) => tab.text())).toEqual(['Workbench', '待处理'])
    expect(wrapper.find('.user-menu-profile').text()).toBe('Profile')
    expect(wrapper.find('.lock-btn').text()).toBe('Lock screen')
    expect(wrapper.find('.logout-btn').text()).toBe('Sign out')
    expect(wrapper.findAll('.appearance-label')[0].text()).toBe('Theme')
    expect(wrapper.find('.menu-search-trigger').attributes('title')).toBe('Search menu')
    expect(wrapper.find('.menu-search-input').attributes('placeholder')).toBe(
      'Search pages, menus or breadcrumbs',
    )
  })

  it('switches a mounted shell without remounting', async () => {
    const { pinia } = setupSession({ name: 'Alice' })
    const router = shellRouter()
    await router.isReady()
    const wrapper = mount(AppShell, shellOptions(pinia, router))

    expect(wrapper.findAll('.top-nav-item').map((item) => item.text())).toEqual([
      '工作台',
      '办事',
      '业务',
      '系统管理',
      '系统监控',
      '系统工具',
    ])

    setLocale('en')
    await flushPromises()

    expect(wrapper.findAll('.top-nav-item').map((item) => item.text())).toEqual([
      'Workbench',
      'Work',
      'Business',
      'System',
      'Monitor',
      'Tools',
    ])
    expect(wrapper.find('.shell-breadcrumb').text()).toContain('Home')
  })

  it('persists the top-bar language choice', async () => {
    const { pinia } = setupSession({ name: 'Alice' })
    const router = shellRouter()
    await router.isReady()
    const wrapper = mount(AppTopBar, {
      props: {
        modules: buildShellNav([]),
        activeModule: 'work',
        narrow: false,
        desktopHint: false,
      },
      global: {
        ...mountOptions(pinia, router).global,
        stubs: { ...elementStubs, NoticeBell: true },
      },
    })

    expect(wrapper.find('.locale-switch').exists()).toBe(true)
    wrapper.vm.switchLocale('en')
    await flushPromises()

    expect(currentLocale()).toBe('en')
    expect(localStorage.getItem(LOCALE_STORAGE_KEY)).toBe('en')
  })
})

describe('外壳组件英文文案', () => {
  let http

  afterEach(() => {
    http?.restore()
    http = undefined
    setLocale(DEFAULT_LOCALE)
  })

  it('shows the notice bell chrome and empty state in English', async () => {
    setLocale('en')
    http = installHttp((config) => {
      if (config.url.endsWith('/admin/notices/unread-count')) return ok(0)
      if (config.url.endsWith('/admin/notices/unread')) return ok([])
      return ok(null)
    })
    const { pinia } = setupSession({ name: 'Alice' })
    const wrapper = mount(NoticeBell, mountOptions(pinia, shellRouter()))
    await flushPromises()

    expect(wrapper.find('.notice-bell').attributes('aria-label')).toBe('Notices')
    expect(wrapper.find('.notice-empty').text()).toBe('No unread notices')
    expect(wrapper.find('.notice-all').text()).toBe('View all')
  })
})
