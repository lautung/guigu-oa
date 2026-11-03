import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'

import AppShell from '@/shell/AppShell.vue'
import AppSidebar from '@/shell/AppSidebar.vue'
import AppTopBar from '@/shell/AppTopBar.vue'
import NoticeBell from '@/shell/NoticeBell.vue'
import ShellTabs from '@/shell/ShellTabs.vue'
import {
  elementStubs,
  fakeRecognition,
  installHttp,
  mountOptions,
  ok,
  setupSession,
} from '../support/harness'

const SYSTEM_MENUS = [
  { path: '/system', title: '系统管理', type: 0, sort: 1 },
  { path: '/system/sysUser', parentPath: '/system', title: '用户管理', type: 1, sort: 1 },
  { path: '/system/log', parentPath: '/system', title: '日志管理', type: 0, sort: 2 },
  { path: '/system/log/operLog', parentPath: '/system/log', title: '操作日志', type: 1, sort: 1 },
]

const MONITOR_MENUS = [
  { path: '/monitor', title: '系统监控', type: 0, sort: 4 },
  { path: '/monitor/online', parentPath: '/monitor', title: '在线用户', type: 1, sort: 1 },
]

const page = (name) => ({ template: `<div class="page-${name}">${name}</div>` })

function shellRouter(initial = '/work/pending') {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/workbench', component: page('workbench'), meta: { title: '工作台' } },
      { path: '/work/pending', component: page('pending'), meta: { title: '待处理' } },
      { path: '/work/pending/:id/:taskId', component: page('detail'), meta: { title: '审批详情' } },
      { path: '/work/processed', component: page('processed'), meta: { title: '已处理' } },
      { path: '/work/start', component: page('start'), meta: { title: '发起审批' } },
      { path: '/notice', component: page('notice'), meta: { title: '全部公告' } },
      { path: '/system/sysUser', component: page('sysUser'), meta: { title: '用户管理' } },
      { path: '/system/log/operLog', component: page('operLog'), meta: { title: '操作日志' } },
      { path: '/monitor', component: page('monitor'), meta: { title: '系统监控' } },
      { path: '/monitor/online', component: page('online'), meta: { title: '在线用户' } },
      { path: '/tool', component: page('tool'), meta: { title: '系统工具' } },
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

function setViewport(width) {
  Object.defineProperty(window, 'innerWidth', { value: width, writable: true, configurable: true })
}

const sidebarModule = {
  key: 'system',
  title: '系统管理',
  icon: 'Setting',
  items: [{ path: '/system/sysUser', title: '用户管理', icon: 'Document' }],
  groups: [
    {
      title: '日志管理',
      icon: 'Folder',
      items: [{ path: '/system/log/operLog', title: '操作日志', icon: 'Document' }],
    },
  ],
  firstPath: '/system/sysUser',
  emptyHint: '',
}

describe('AppShell', () => {
  beforeEach(() => {
    localStorage.clear()
    document.documentElement.classList.remove('dark')
    setViewport(1280)
  })

  it('renders one shell with top modules, sidebar, tabs and breadcrumb', async () => {
    const { pinia } = setupSession({ name: 'Alice' })
    const router = shellRouter()
    await router.isReady()
    const wrapper = mount(AppShell, shellOptions(pinia, router))

    expect(wrapper.findAll('.top-nav-item').map((item) => item.text())).toEqual([
      '工作台',
      '办事',
      '系统管理',
      '系统监控',
      '系统工具',
    ])
    expect(wrapper.find('.app-sidebar').text()).toContain('待处理')
    expect(wrapper.findAll('.shell-tab').map((tab) => tab.text())).toContain('待处理')
    expect(
      wrapper
        .findAll('.shell-tab')
        .find((tab) => tab.classes().includes('active'))
        .text(),
    ).toBe('待处理')
    expect(wrapper.findAll('.el-breadcrumb-item-stub').map((item) => item.text())).toEqual([
      '首页',
      '办事',
      '待处理',
    ])
  })

  it('keeps tabs across modules and returns to the most recent tab of a module', async () => {
    const { pinia } = setupSession({ name: 'Alice', menus: SYSTEM_MENUS })
    const router = shellRouter()
    await router.isReady()
    const wrapper = mount(AppShell, shellOptions(pinia, router))

    await router.push('/system/sysUser')
    await flushPromises()
    expect(wrapper.findAll('.top-nav-item').map((item) => item.text())).toEqual([
      '工作台',
      '办事',
      '系统管理',
      '系统监控',
      '系统工具',
    ])
    expect(wrapper.findAll('.shell-tab').map((tab) => tab.text())).toEqual([
      '工作台',
      '待处理',
      '用户管理',
    ])

    const work = wrapper.findAll('.top-nav-item').find((item) => item.text() === '办事')
    await work.trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/work/pending')

    const system = wrapper.findAll('.top-nav-item').find((item) => item.text() === '系统管理')
    await system.trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/system/sysUser')
    expect(wrapper.findAll('.shell-tab').map((tab) => tab.text())).toEqual([
      '工作台',
      '待处理',
      '用户管理',
    ])
  })

  it('renders the breadcrumb from the menu chain and falls details back to their list', async () => {
    const { pinia } = setupSession({ name: 'Alice', menus: SYSTEM_MENUS })
    const router = shellRouter('/system/log/operLog')
    await router.isReady()
    const wrapper = mount(AppShell, shellOptions(pinia, router))

    expect(wrapper.findAll('.el-breadcrumb-item-stub').map((item) => item.text())).toEqual([
      '首页',
      '系统管理',
      '日志管理',
      '操作日志',
    ])

    await router.push('/work/pending/9/task-1')
    await flushPromises()
    expect(wrapper.findAll('.el-breadcrumb-item-stub').map((item) => item.text())).toEqual([
      '首页',
      '办事',
      '待处理',
    ])
  })

  it('links breadcrumb ancestors to their list pages and leaves directories unlinked', async () => {
    const { pinia } = setupSession({ name: 'Alice', menus: SYSTEM_MENUS })
    const router = shellRouter('/work/pending/9/task-1')
    await router.isReady()
    const wrapper = mount(AppShell, shellOptions(pinia, router))

    const links = wrapper.findAll('.shell-breadcrumb-link')
    expect(links.map((link) => link.text())).toEqual(['首页', '待处理'])

    await links[1].trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/work/pending')

    await router.push('/system/log/operLog')
    await flushPromises()
    expect(wrapper.findAll('.shell-breadcrumb-link').map((link) => link.text())).toEqual(['首页'])
    expect(wrapper.find('.shell-breadcrumb').text()).toContain('日志管理')
  })

  it('closes the active tab to its neighbour and closes all back to workbench', async () => {
    const { pinia } = setupSession({ name: 'Alice' })
    const router = shellRouter()
    await router.isReady()
    const wrapper = mount(AppShell, shellOptions(pinia, router))
    await router.push('/work/processed')
    await flushPromises()

    const pendingTab = wrapper.findAll('.shell-tab').find((tab) => tab.text().includes('待处理'))
    await pendingTab.find('.tab-close').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/work/processed')
    expect(wrapper.findAll('.shell-tab').map((tab) => tab.text())).toEqual(['工作台', '已处理'])

    const tabs = wrapper.findComponent(ShellTabs)
    tabs.vm.onCommand('all')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/workbench')
    expect(wrapper.findAll('.shell-tab').map((tab) => tab.text())).toEqual(['工作台'])
  })

  it('applies stored theme and density through the config provider', async () => {
    localStorage.setItem('guigu_oa_shell_theme', 'dark')
    localStorage.setItem('guigu_oa_shell_density', 'large')
    const { pinia } = setupSession({ name: 'Alice' })
    const router = shellRouter()
    await router.isReady()
    const wrapper = mount(AppShell, shellOptions(pinia, router))

    expect(document.documentElement.classList.contains('dark')).toBe(true)
    expect(wrapper.find('.el-config-provider-stub').attributes('data-size')).toBe('large')
  })

  it('keeps 系统监控 disabled and on the current page without monitor grants', async () => {
    const { pinia } = setupSession({ name: 'Alice' })
    const router = shellRouter()
    await router.isReady()
    const wrapper = mount(AppShell, shellOptions(pinia, router))

    const monitor = wrapper.findAll('.top-nav-item').find((item) => item.text() === '系统监控')
    await monitor.trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.path).toBe('/work/pending')
    expect(monitor.attributes('disabled')).toBeDefined()
  })

  it('switches to 在线用户 when monitor pages are granted', async () => {
    const { pinia } = setupSession({ name: 'Alice', menus: MONITOR_MENUS })
    const router = shellRouter()
    await router.isReady()
    const wrapper = mount(AppShell, shellOptions(pinia, router))

    const monitor = wrapper.findAll('.top-nav-item').find((item) => item.text() === '系统监控')
    await monitor.trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.path).toBe('/monitor/online')
    expect(wrapper.findAll('.shell-tab').map((tab) => tab.text())).toContain('在线用户')
  })

  it('turns the sidebar into a drawer under 900px and shows the desktop hint on config pages', async () => {
    setViewport(390)
    const { pinia } = setupSession({ name: 'Alice', menus: SYSTEM_MENUS })
    const router = shellRouter('/system/sysUser')
    await router.isReady()
    const wrapper = mount(AppShell, shellOptions(pinia, router))

    expect(wrapper.find('.shell-tabs').exists()).toBe(false)
    expect(wrapper.find('.desktop-hint').text()).toBe('建议桌面')
    expect(wrapper.find('.app-sidebar').classes()).not.toContain('open')

    await wrapper.find('.menu-toggle').trigger('click')
    expect(wrapper.find('.app-sidebar').classes()).toContain('open')
    expect(wrapper.find('.shell-backdrop').exists()).toBe(true)

    await wrapper.find('.shell-backdrop').trigger('click')
    expect(wrapper.find('.app-sidebar').classes()).not.toContain('open')
  })

  it('keeps config pages open when the viewport shrinks instead of redirecting', async () => {
    const { pinia } = setupSession({ name: 'Alice', menus: SYSTEM_MENUS })
    const router = shellRouter('/system/sysUser')
    await router.isReady()
    mount(AppShell, shellOptions(pinia, router))

    setViewport(390)
    window.dispatchEvent(new Event('resize'))
    await flushPromises()

    expect(router.currentRoute.value.path).toBe('/system/sysUser')
  })
})

describe('ShellTabs', () => {
  const tabs = [
    { path: '/workbench', title: '工作台' },
    { path: '/work/pending', title: '待处理' },
  ]

  it('marks the active tab, activates on click and closes on the close icon', async () => {
    const wrapper = mount(ShellTabs, {
      props: { tabs, activePath: '/work/pending' },
      global: { stubs: elementStubs },
    })

    const active = wrapper.findAll('.shell-tab').find((tab) => tab.classes().includes('active'))
    expect(active.text()).toBe('待处理')
    expect(wrapper.findAll('.shell-tab')[0].find('.tab-close').exists()).toBe(false)

    await wrapper.findAll('.shell-tab')[0].trigger('click')
    expect(wrapper.emitted('activate')[0]).toEqual(['/workbench'])

    await wrapper.findAll('.shell-tab')[1].find('.tab-close').trigger('click')
    expect(wrapper.emitted('close')[0]).toEqual(['/work/pending'])
  })

  it('closes through the middle mouse button and emits dropdown commands', async () => {
    const wrapper = mount(ShellTabs, {
      props: { tabs, activePath: '/work/pending' },
      global: { stubs: elementStubs },
    })

    await wrapper.findAll('.shell-tab')[1].trigger('mousedown', { button: 1 })
    expect(wrapper.emitted('close')[0]).toEqual(['/work/pending'])

    wrapper.vm.onCommand('others')
    expect(wrapper.emitted('command')[0]).toEqual(['others'])
  })
})

describe('AppSidebar', () => {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/system/sysUser', component: { template: '<div />' } },
      { path: '/system/log/operLog', component: { template: '<div />' } },
      { path: '/system/assignAuth', component: { template: '<div />' } },
    ],
  })

  it('renders flat items and nested group headings with the active item marked', () => {
    const wrapper = mount(AppSidebar, {
      props: {
        module: sidebarModule,
        activePath: '/system/sysUser',
        collapsed: false,
        narrow: false,
        drawerOpen: false,
      },
      global: mountOptions(undefined, router).global,
    })

    expect(wrapper.find('.side-label').text()).toBe('系统管理')
    expect(wrapper.findAll('.side-item').map((item) => item.text())).toEqual([
      '用户管理',
      '操作日志',
    ])
    expect(wrapper.find('.side-item').classes()).toContain('active')
    expect(wrapper.find('.side-group-title').text()).toContain('日志管理')
  })

  it('emits collapse and navigate, and turns group headings into links when collapsed', async () => {
    const wrapper = mount(AppSidebar, {
      props: {
        module: sidebarModule,
        activePath: '/system/sysUser',
        collapsed: true,
        narrow: false,
        drawerOpen: false,
      },
      global: mountOptions(undefined, router).global,
    })

    expect(wrapper.find('.app-sidebar').classes()).toContain('collapsed')
    expect(wrapper.find('.side-group-title').attributes('href')).toBe('/system/log/operLog')

    await wrapper.find('.collapse-btn').trigger('click')
    expect(wrapper.emitted('toggle-collapse')).toHaveLength(1)

    await wrapper.findAll('.side-item')[0].trigger('click')
    expect(wrapper.emitted('navigate')).toHaveLength(1)
  })

  it('uses the drawer variant on narrow viewports', () => {
    const wrapper = mount(AppSidebar, {
      props: {
        module: sidebarModule,
        activePath: '/system/sysUser',
        collapsed: true,
        narrow: true,
        drawerOpen: true,
      },
      global: mountOptions(undefined, router).global,
    })

    expect(wrapper.find('.app-sidebar').classes()).toContain('open')
    expect(wrapper.find('.app-sidebar').classes()).not.toContain('collapsed')
    expect(wrapper.find('.collapse-btn').exists()).toBe(false)
  })

  it('shows the module empty hint when it has no pages', () => {
    const wrapper = mount(AppSidebar, {
      props: {
        module: {
          ...sidebarModule,
          items: [],
          groups: [],
          firstPath: '',
          emptyHint: '暂无可用页面',
        },
        activePath: '/system/sysUser',
        collapsed: false,
        narrow: false,
        drawerOpen: false,
      },
      global: mountOptions(undefined, router).global,
    })

    expect(wrapper.find('.side-empty').text()).toBe('暂无可用页面')
  })
})

describe('AppTopBar', () => {
  let http

  afterEach(() => {
    http?.restore()
    http = undefined
  })

  it('renders modules, active pill, desktop hint and user name', async () => {
    http = installHttp(() => ok(null))
    const recognition = fakeRecognition({ exit: vi.fn() })
    const session = setupSession({ name: 'Alice', recognition })
    const router = shellRouter()
    const wrapper = mount(AppTopBar, {
      props: {
        modules: [
          {
            key: 'workbench',
            title: '工作台',
            icon: 'HomeFilled',
            items: [],
            groups: [],
            firstPath: '/workbench',
          },
          {
            key: 'work',
            title: '办事',
            icon: 'Tickets',
            items: [],
            groups: [],
            firstPath: '/work/start',
          },
        ],
        activeModule: 'work',
        narrow: true,
        desktopHint: true,
      },
      global: {
        ...mountOptions(session.pinia, router).global,
        stubs: { ...elementStubs, NoticeBell: true },
      },
    })

    expect(wrapper.find('.top-nav-item.active').text()).toBe('办事')
    expect(wrapper.find('.desktop-hint').text()).toBe('建议桌面')
    expect(wrapper.find('.username').text()).toBe('Alice')
    expect(wrapper.find('.user-trigger').exists()).toBe(true)
    expect(wrapper.find('.menu-search-trigger').exists()).toBe(true)
    expect(wrapper.find('.fullscreen-btn').exists()).toBe(false)
    expect(wrapper.find('.logout-btn').text()).toBe('退出登录')

    await wrapper.find('.logout-btn').trigger('click')
    await flushPromises()
    expect(recognition.exit).toHaveBeenCalled()
  })

  it('disables a module without a landing page', async () => {
    const { pinia } = setupSession({ name: 'Alice' })
    const router = shellRouter()
    const wrapper = mount(AppTopBar, {
      props: {
        modules: [
          {
            key: 'system',
            title: '系统管理',
            icon: 'Setting',
            items: [],
            groups: [],
            firstPath: '',
            emptyHint: '暂无可用页面',
          },
        ],
        activeModule: 'work',
        narrow: false,
        desktopHint: false,
      },
      global: {
        ...mountOptions(pinia, router).global,
        stubs: { ...elementStubs, NoticeBell: true },
      },
    })

    const item = wrapper.find('.top-nav-item')
    expect(item.attributes('disabled')).toBeDefined()
    expect(item.attributes('aria-disabled')).toBe('true')
    await item.trigger('click')
    expect(wrapper.emitted('switch-module')).toBeUndefined()
  })

  it('emits switch-module and toggle-menu', async () => {
    const { pinia } = setupSession({ name: 'Alice' })
    const router = shellRouter()
    const wrapper = mount(AppTopBar, {
      props: {
        modules: [
          {
            key: 'work',
            title: '办事',
            icon: 'Tickets',
            items: [],
            groups: [],
            firstPath: '/work/start',
          },
        ],
        activeModule: 'work',
        narrow: true,
        desktopHint: false,
      },
      global: {
        ...mountOptions(pinia, router).global,
        stubs: { ...elementStubs, NoticeBell: true },
      },
    })

    await wrapper.find('.top-nav-item').trigger('click')
    expect(wrapper.emitted('switch-module')[0]).toEqual(['work'])

    await wrapper.find('.menu-toggle').trigger('click')
    expect(wrapper.emitted('toggle-menu')).toHaveLength(1)
  })
})

describe('NoticeBell', () => {
  let http

  afterEach(() => {
    http?.restore()
    http = undefined
  })

  const unreadRow = (id) => ({
    id,
    title: `公告 ${id}`,
    type: 1,
    publisherName: '管理员',
    publishTime: '2026-11-03 10:00:00',
    read: false,
  })

  function noticeResponder(state) {
    return (config) => {
      if (config.url.endsWith('/admin/notices/unread-count')) return ok(state.count)
      if (config.url.endsWith('/admin/notices/unread')) return ok(state.rows)
      return ok(null)
    }
  }

  it('shows the unread badge and the latest five unread notices', async () => {
    http = installHttp(noticeResponder({ count: 3, rows: [1, 2, 3, 4, 5, 6].map(unreadRow) }))
    const { pinia } = setupSession({ name: 'Alice' })
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/', component: { template: '<div />' } },
        { path: '/notice', component: { template: '<div />' } },
        { path: '/notice/:id', component: { template: '<div />' } },
      ],
    })
    const wrapper = mount(NoticeBell, mountOptions(pinia, router))
    await flushPromises()

    const badge = wrapper.find('.el-badge-stub')
    expect(badge.attributes('data-value')).toBe('3')
    expect(badge.attributes('data-hidden')).toBe('false')
    expect(wrapper.findAll('.notice-item')).toHaveLength(5)

    await wrapper.findAll('.notice-item')[1].trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/notice/2')

    await wrapper.find('.notice-all').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/notice')
  })

  it('caps the badge at 99+ and hides it when nothing is unread', async () => {
    http = installHttp(noticeResponder({ count: 120, rows: [] }))
    const { pinia } = setupSession({ name: 'Alice' })
    const wrapper = mount(NoticeBell, mountOptions(pinia, shellRouter()))
    await flushPromises()
    expect(wrapper.find('.el-badge-stub').attributes('data-value')).toBe('99+')

    http.restore()
    http = installHttp(noticeResponder({ count: 0, rows: [] }))
    const secondSession = setupSession({ name: 'Alice' })
    const second = mount(NoticeBell, mountOptions(secondSession.pinia, shellRouter()))
    await flushPromises()
    expect(second.find('.el-badge-stub').attributes('data-hidden')).toBe('true')
    expect(second.find('.notice-empty').text()).toBe('暂无未读公告')
  })

  it('stops polling after logout and clears the badge', async () => {
    let countCalls = 0
    http = installHttp((config) => {
      if (config.url.endsWith('/admin/notices/unread-count')) {
        countCalls += 1
        return ok(2)
      }
      if (config.url.endsWith('/admin/notices/unread')) return ok([unreadRow(1)])
      return ok(null)
    })
    const recognition = fakeRecognition({
      exit: vi.fn(() => {
        session.user.token = ''
      }),
    })
    const session = setupSession({ name: 'Alice', recognition })
    const wrapper = mount(NoticeBell, mountOptions(session.pinia, shellRouter()))
    await flushPromises()
    expect(countCalls).toBe(1)

    await session.user.logout()
    await flushPromises()
    expect(wrapper.find('.el-badge-stub').attributes('data-hidden')).toBe('true')

    window.dispatchEvent(new Event('focus'))
    await flushPromises()
    expect(countCalls).toBe(1)
  })
})
