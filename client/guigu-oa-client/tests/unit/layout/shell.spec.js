import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'

import AppSidebar from '@/layout/components/AppSidebar.vue'
import SidebarNode from '@/layout/components/SidebarNode.vue'
import TopBar from '@/layout/components/TopBar.vue'
import { fakeRecognition, mountOptions, setupSession } from '../support/harness'

function router() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/', component: { template: '<div />' } }],
  })
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
  it('shows the default avatar and logs out through the recognition module', async () => {
    const recognition = fakeRecognition({ exit: vi.fn() })
    const { pinia } = setupSession({ name: 'Alice', recognition })
    const wrapper = mount(TopBar, mountOptions(pinia, router()))

    expect(wrapper.find('.username').text()).toBe('Alice')
    expect(wrapper.find('.avatar').attributes('src')).toContain('dingtalk')

    await wrapper.vm.handleLogout()
    expect(recognition.exit).toHaveBeenCalledTimes(1)
  })

  it('prefers the stored avatar when present', () => {
    const { pinia } = setupSession({ name: 'Bob', avatar: 'https://example.test/bob.png' })
    const wrapper = mount(TopBar, mountOptions(pinia, router()))
    expect(wrapper.find('.avatar').attributes('src')).toBe('https://example.test/bob.png')
  })
})
