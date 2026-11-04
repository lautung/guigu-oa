import { afterEach, describe, expect, it } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'

import MenuSearch from '@/shell/MenuSearch.vue'
import { buildMenuSearch, searchMenus } from '@/shell/menu-search'
import { mountOptions, setupSession } from '../support/harness'

const SYSTEM_MENUS = [
  { path: '/system', title: '系统管理', type: 0, sort: 1 },
  { path: '/system/sysUser', parentPath: '/system', title: '用户管理', type: 1, sort: 1 },
  { path: '/system/sysRole', parentPath: '/system', title: '角色管理', type: 1, sort: 2 },
  { path: '/system/log', parentPath: '/system', title: '日志管理', type: 0, sort: 2 },
  { path: '/system/log/operLog', parentPath: '/system/log', title: '操作日志', type: 1, sort: 1 },
  { path: '/system/processSet', parentPath: '/system', title: '审批设置', type: 0, sort: 3 },
  {
    path: '/system/processSet/processTemplate',
    parentPath: '/system/processSet',
    title: '审批模板',
    type: 1,
    sort: 1,
  },
  {
    path: '/system/processSet/templateSet',
    parentPath: '/system/processSet/processTemplate',
    title: '审批模板设置',
    type: 1,
    hidden: true,
    sort: 2,
  },
]

const SEARCH_ENTRIES = [
  { title: '用户管理', path: '/system/sysUser', trail: ['首页', '系统管理', '用户管理'] },
  {
    title: '审批模板',
    path: '/system/processSet/processTemplate',
    trail: ['首页', '系统管理', '审批设置', '审批模板'],
  },
  { title: '发起审批', path: '/work/start', trail: ['首页', '办事', '发起审批'] },
  { title: '系统监控', path: '/monitor', trail: ['首页', '系统监控'] },
  { title: 'User Admin', path: '/system/sysUser', trail: ['首页', '系统管理', 'User Admin'] },
]

describe('菜单搜索索引', () => {
  it('flattens authorized menu pages and built-in work items with their trail', () => {
    const entries = buildMenuSearch(SYSTEM_MENUS)

    expect(entries.map((entry) => entry.path)).toEqual([
      '/workbench',
      '/notice',
      '/work/start',
      '/work/pending',
      '/work/processed',
      '/work/started',
      '/work/copied',
      '/system/sysUser',
      '/system/sysRole',
      '/system/log/operLog',
      '/system/processSet/processTemplate',
    ])
    expect(entries.find((entry) => entry.path === '/system/log/operLog').trail).toEqual([
      '首页',
      '系统管理',
      '日志管理',
      '操作日志',
    ])
  })

  it('never indexes hidden or unauthorized pages', () => {
    const paths = buildMenuSearch(SYSTEM_MENUS).map((entry) => entry.path)

    expect(paths).not.toContain('/system/processSet/templateSet')
    expect(paths).not.toContain('/system/sysMenu')
    expect(paths).not.toContain('/system/assignAuth')
  })
})

describe('菜单搜索匹配', () => {
  it('matches title or breadcrumb without case sensitivity', () => {
    expect(searchMenus(SEARCH_ENTRIES, 'user').map((entry) => entry.title)).toEqual(['User Admin'])
    expect(searchMenus(SEARCH_ENTRIES, '日志')).toEqual([])
    expect(searchMenus(SEARCH_ENTRIES, '系统管理').map((entry) => entry.title)).toEqual([
      '用户管理',
      '审批模板',
      'User Admin',
    ])
  })

  it('ranks title prefix, then title contains, then breadcrumb-only matches', () => {
    expect(searchMenus(SEARCH_ENTRIES, '审批').map((entry) => entry.title)).toEqual([
      '审批模板',
      '发起审批',
    ])
    expect(searchMenus(SEARCH_ENTRIES, '管理').map((entry) => entry.title)).toEqual([
      '用户管理',
      '审批模板',
      'User Admin',
    ])
  })

  it('keeps navigation order for equal ranks and returns nothing for empty queries', () => {
    expect(searchMenus(SEARCH_ENTRIES, '系统').map((entry) => entry.title)).toEqual([
      '系统监控',
      '用户管理',
      '审批模板',
      'User Admin',
    ])
    expect(searchMenus(SEARCH_ENTRIES, '')).toEqual([])
    expect(searchMenus(SEARCH_ENTRIES, '   ')).toEqual([])
    expect(searchMenus(SEARCH_ENTRIES, '不存在')).toEqual([])
  })
})

describe('MenuSearch', () => {
  function searchRouter() {
    return createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/', component: { template: '<div />' } },
        { path: '/system/sysUser', component: { template: '<div />' } },
        { path: '/system/sysRole', component: { template: '<div />' } },
        { path: '/system/log/operLog', component: { template: '<div />' } },
        { path: '/system/processSet/processTemplate', component: { template: '<div />' } },
        { path: '/work/start', component: { template: '<div />' } },
      ],
    })
  }

  afterEach(() => {
    localStorage.clear()
  })

  it('filters as you type, shows the trail and opens the clicked page', async () => {
    const { pinia } = setupSession({ name: 'Alice', menus: SYSTEM_MENUS })
    const router = searchRouter()
    const wrapper = mount(MenuSearch, mountOptions(pinia, router))

    await wrapper.find('.menu-search-input').setValue('操作')
    const items = wrapper.findAll('.menu-search-item')
    expect(items).toHaveLength(1)
    expect(items[0].text()).toContain('操作日志')
    expect(items[0].text()).toContain('系统管理 / 日志管理')

    await items[0].trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/system/log/operLog')
  })

  it('moves the active result with arrow keys and opens it on enter', async () => {
    const { pinia } = setupSession({ name: 'Alice', menus: SYSTEM_MENUS })
    const router = searchRouter()
    const wrapper = mount(MenuSearch, mountOptions(pinia, router))
    const input = wrapper.find('.menu-search-input')

    await input.setValue('系统')
    expect(wrapper.findAll('.menu-search-item')[0].classes()).toContain('active')

    await input.trigger('keydown', { key: 'ArrowDown' })
    expect(wrapper.findAll('.menu-search-item')[1].classes()).toContain('active')

    await input.trigger('keydown', { key: 'Enter' })
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/system/sysRole')
  })

  it('opens the first result on enter and shows empty states', async () => {
    const { pinia } = setupSession({ name: 'Alice', menus: SYSTEM_MENUS })
    const router = searchRouter()
    const wrapper = mount(MenuSearch, mountOptions(pinia, router))
    const input = wrapper.find('.menu-search-input')

    expect(wrapper.find('.menu-search-hint').exists()).toBe(true)

    await input.setValue('操作')
    await input.trigger('keydown', { key: 'Enter' })
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/system/log/operLog')

    await input.setValue('不存在的页面')
    expect(wrapper.findAll('.menu-search-item')).toHaveLength(0)
    expect(wrapper.find('.menu-search-empty').text()).toBe('没有匹配的页面')
  })
})
