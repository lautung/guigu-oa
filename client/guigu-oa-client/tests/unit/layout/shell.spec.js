import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'

import AppSidebar from '@/layout/components/AppSidebar.vue'
import SidebarNode from '@/layout/components/SidebarNode.vue'
import TopBar from '@/layout/components/TopBar.vue'
import { fakeRecognition, installHttp, mountOptions, ok, setupSession } from '../support/harness'

function router() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/', component: { template: '<div />' } }],
  })
}

function noticeRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: { template: '<div />' } },
      { path: '/notice', name: 'NoticeList', component: { template: '<div />' } },
      { path: '/notice/:id', name: 'NoticeDetail', component: { template: '<div />' } },
    ],
  })
}

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

function setViewport(width) {
  Object.defineProperty(window, 'innerWidth', { value: width, writable: true, configurable: true })
}

const configMenu = { path: '/system/sysUser', title: '用户管理', type: 1 }

describe('AppSidebar', () => {
  beforeEach(() => setViewport(1280))

  it('renders the work group plus granted config menus on desktop', () => {
    const { pinia } = setupSession({ menus: [configMenu] })
    const wrapper = mount(AppSidebar, mountOptions(pinia, router()))

    const titles = wrapper.findAll('.group-title span:first-child').map((node) => node.text())
    expect(titles).toEqual(['办事', '配置'])
    expect(wrapper.find('.group-hint').exists()).toBe(false)
    expect(wrapper.text()).toContain('用户管理')
  })

  it('only collapses the config group on mobile', async () => {
    const { pinia } = setupSession({ menus: [configMenu] })
    const wrapper = mount(AppSidebar, mountOptions(pinia, router()))

    wrapper.vm.toggleGroup('config')
    expect(wrapper.find('.group-hint').exists()).toBe(false)

    setViewport(390)
    window.dispatchEvent(new Event('resize'))
    await wrapper.vm.$nextTick()

    expect(wrapper.find('.group-hint').text()).toBe('请在电脑浏览器使用')
    wrapper.vm.toggleGroup('config')
    await wrapper.vm.$nextTick()
    expect(wrapper.find('.group-hint').exists()).toBe(false)
  })

  it('removes the resize listener when unmounted', () => {
    const { pinia } = setupSession()
    const remove = vi.spyOn(window, 'removeEventListener')
    const wrapper = mount(AppSidebar, mountOptions(pinia, router()))
    wrapper.unmount()
    expect(remove).toHaveBeenCalledWith('resize', expect.any(Function))
  })
})

describe('SidebarNode', () => {
  it('marks an ancestor as active while rendering nested children', () => {
    const routerInstance = router()
    const wrapper = mount(SidebarNode, {
      props: {
        item: {
          path: '/system',
          title: '系统管理',
          children: [{ path: '/system/sysUser', title: '用户管理' }],
        },
        activePath: '/system/sysUser',
      },
      global: mountOptions(undefined, routerInstance).global,
    })
    expect(wrapper.find('.menu-link').classes()).toContain('active')
    expect(wrapper.findAll('.menu-link')).toHaveLength(2)
    expect(wrapper.findAll('.submenu-list')).toHaveLength(1)
  })

  it('keeps unrelated paths inactive', () => {
    const routerInstance = router()
    const wrapper = mount(SidebarNode, {
      props: { item: { path: '/system', title: '系统管理' }, activePath: '/work/pending' },
      global: mountOptions(undefined, routerInstance).global,
    })
    expect(wrapper.find('.menu-link').classes()).not.toContain('active')
  })
})

describe('TopBar', () => {
  let http

  afterEach(() => {
    http?.restore()
    http = undefined
  })

  it('shows the default avatar and logs out through the recognition module', async () => {
    const recognition = fakeRecognition({ exit: vi.fn() })
    http = installHttp(noticeResponder({ count: 0, rows: [] }))
    const { pinia } = setupSession({ name: 'Alice', recognition })
    const wrapper = mount(TopBar, mountOptions(pinia, router()))

    expect(wrapper.find('.username').text()).toBe('Alice')
    expect(wrapper.find('.avatar').attributes('src')).toBe('/avatar.svg')

    await wrapper.vm.handleLogout()
    expect(recognition.exit).toHaveBeenCalledTimes(1)
  })

  it('prefers the stored avatar when present', () => {
    http = installHttp(noticeResponder({ count: 0, rows: [] }))
    const { pinia } = setupSession({ name: 'Bob', avatar: 'https://example.test/bob.png' })
    const wrapper = mount(TopBar, mountOptions(pinia, router()))
    expect(wrapper.find('.avatar').attributes('src')).toBe('https://example.test/bob.png')
  })

  it('shows the unread badge and the latest five unread notices', async () => {
    const state = {
      count: 3,
      rows: [1, 2, 3, 4, 5, 6].map(unreadRow),
    }
    http = installHttp(noticeResponder(state))
    const { pinia } = setupSession({ name: 'Alice' })
    const routerInstance = noticeRouter()
    const wrapper = mount(TopBar, mountOptions(pinia, routerInstance))
    await flushPromises()

    const badge = wrapper.find('.el-badge-stub')
    expect(badge.attributes('data-value')).toBe('3')
    expect(badge.attributes('data-hidden')).toBe('false')
    expect(wrapper.findAll('.notice-item')).toHaveLength(5)
    expect(wrapper.find('.notice-item-title').text()).toBe('公告 1')

    await wrapper.findAll('.notice-item')[1].trigger('click')
    await flushPromises()
    expect(routerInstance.currentRoute.value.path).toBe('/notice/2')

    await wrapper.find('.notice-all').trigger('click')
    await flushPromises()
    expect(routerInstance.currentRoute.value.path).toBe('/notice')
  })

  it('caps the badge at 99+', async () => {
    http = installHttp(noticeResponder({ count: 120, rows: [] }))
    const { pinia } = setupSession({ name: 'Alice' })
    const wrapper = mount(TopBar, mountOptions(pinia, router()))
    await flushPromises()

    expect(wrapper.find('.el-badge-stub').attributes('data-value')).toBe('99+')
  })

  it('hides the badge when there is nothing unread', async () => {
    http = installHttp(noticeResponder({ count: 0, rows: [] }))
    const { pinia } = setupSession({ name: 'Alice' })
    const wrapper = mount(TopBar, mountOptions(pinia, router()))
    await flushPromises()

    expect(wrapper.find('.el-badge-stub').attributes('data-hidden')).toBe('true')
    expect(wrapper.find('.notice-empty').text()).toBe('暂无未读公告')
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
    const session = setupSession({ name: 'Alice' })
    session.recognition.exit = vi.fn(() => {
      session.user.token = ''
    })
    const wrapper = mount(TopBar, mountOptions(session.pinia, router()))
    await flushPromises()
    expect(countCalls).toBe(1)

    await wrapper.vm.handleLogout()
    await flushPromises()
    expect(wrapper.find('.el-badge-stub').attributes('data-hidden')).toBe('true')

    window.dispatchEvent(new Event('focus'))
    await flushPromises()
    expect(countCalls).toBe(1)
  })
})
