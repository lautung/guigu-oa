import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    ElMessage: Object.assign(vi.fn(), { error: vi.fn(), success: vi.fn(), close: vi.fn() }),
    ElMessageBox: { confirm: vi.fn().mockResolvedValue('confirm'), close: vi.fn() },
  }
})

import ElementPlus from 'element-plus'
import NoticeAdminList from '@/views/config/notice/index.vue'
import { hasPermi } from '@/permission'
import { PATHS } from '@/router/paths'
import {
  createTestRouter,
  ensureDomApis,
  installHttp,
  paged,
  setupSession,
} from '../support/harness'

ensureDomApis()

const rows = [
  {
    id: 1,
    title: '系统维护',
    type: 1,
    status: 0,
    publisherName: '',
    publishTime: '',
    createTime: '2026-11-03 10:00:00',
  },
  {
    id: 2,
    title: '年会公告',
    type: 2,
    status: 1,
    publisherName: '管理员',
    publishTime: '2026-11-02 09:00:00',
    createTime: '2026-11-01 09:00:00',
  },
  {
    id: 3,
    title: '旧公告',
    type: 2,
    status: 2,
    publisherName: '管理员',
    publishTime: '2026-10-01 09:00:00',
    createTime: '2026-09-30 09:00:00',
  },
]

function mountList(permissions = []) {
  const http = installHttp(() => paged(rows))
  const { pinia } = setupSession({
    permissions,
    menus: [
      { path: '/system', title: '组织与权限', type: 0, sort: 1 },
      { path: PATHS.SYSTEM_NOTICE, parentPath: '/system', title: '通知公告', type: 1, sort: 1 },
    ],
  })
  const router = createTestRouter([
    { path: PATHS.SYSTEM_NOTICE, component: { template: '<div />' } },
    { path: PATHS.SYSTEM_NOTICE_EDIT, component: { template: '<div />' } },
    { path: '/', component: { template: '<div />' } },
  ])
  const wrapper = mount(NoticeAdminList, {
    global: { plugins: [pinia, router, ElementPlus], directives: { hasPermi } },
  })
  return { wrapper, router, http }
}

function lastParams(http) {
  return http.calls.at(-1)?.params
}

let mounted

beforeEach(() => {
  vi.clearAllMocks()
})

afterEach(() => {
  mounted?.http.restore()
  mounted = undefined
})

describe('通知公告列表筛选与分页', () => {
  it('reads the first page with the default page size and no empty filters', async () => {
    mounted = mountList()
    await flushPromises()

    expect(lastParams(mounted.http)).toEqual({ page: 1, limit: 10 })
    expect(mounted.wrapper.text()).toContain('系统维护')
    expect(mounted.wrapper.text()).toContain('草稿')
    expect(mounted.wrapper.text()).toContain('已发布')
    expect(mounted.wrapper.text()).toContain('已撤回')
  })

  it('applies title/type/status filters and keeps them when paging', async () => {
    mounted = mountList()
    await flushPromises()

    mounted.wrapper.vm.query.title = '维护'
    mounted.wrapper.vm.query.type = 0
    mounted.wrapper.vm.query.status = 0
    mounted.wrapper.vm.search()
    await flushPromises()

    expect(lastParams(mounted.http)).toEqual({
      page: 1,
      limit: 10,
      title: '维护',
      type: 0,
      status: 0,
    })

    mounted.wrapper.vm.list.handlePageChange(2)
    await flushPromises()

    expect(lastParams(mounted.http)).toEqual({
      page: 2,
      limit: 10,
      title: '维护',
      type: 0,
      status: 0,
    })
  })

  it('drops filters on reset', async () => {
    mounted = mountList()
    await flushPromises()

    mounted.wrapper.vm.query.title = '维护'
    mounted.wrapper.vm.search()
    await flushPromises()
    mounted.wrapper.vm.reset()
    await flushPromises()

    expect(lastParams(mounted.http)).toEqual({ page: 1, limit: 10 })
  })

  it('offers only the actions that match each status', async () => {
    mounted = mountList([
      'system:notice:add',
      'system:notice:edit',
      'system:notice:remove',
      'system:notice:publish',
    ])
    await flushPromises()
    const vm = mounted.wrapper.vm

    expect(vm.canEdit(rows[0])).toBe(true)
    expect(vm.canEdit(rows[1])).toBe(true)
    expect(vm.canEdit(rows[2])).toBe(false)

    expect(vm.canPublish(rows[0])).toBe(true)
    expect(vm.canPublish(rows[1])).toBe(false)
    expect(vm.canPublish(rows[2])).toBe(true)

    expect(vm.canWithdraw(rows[0])).toBe(false)
    expect(vm.canWithdraw(rows[1])).toBe(true)
    expect(vm.canWithdraw(rows[2])).toBe(false)
  })

  it('navigates to the hidden editor route for create and edit', async () => {
    mounted = mountList()
    await flushPromises()

    mounted.wrapper.vm.openCreate()
    await flushPromises()
    expect(mounted.router.currentRoute.value.path).toBe('/system/notice/edit')

    mounted.wrapper.vm.openEdit(rows[2])
    await flushPromises()
    expect(mounted.router.currentRoute.value.path).toBe('/system/notice/edit/3')
  })

  it('renders only the actions granted by the button permissions', async () => {
    mounted = mountList([
      'system:notice:add',
      'system:notice:edit',
      'system:notice:remove',
      'system:notice:publish',
    ])
    await flushPromises()
    const labels = mounted.wrapper.findAll('button').map((button) => button.text())

    expect(labels).toContain('新建')
    expect(labels).toContain('编辑')
    expect(labels).toContain('发布')
    expect(labels).toContain('再次发布')
    expect(labels).toContain('撤回')
    expect(labels).toContain('删除')
  })

  it('hides publish/withdraw/delete when only edit is granted', async () => {
    mounted = mountList(['system:notice:edit'])
    await flushPromises()
    const labels = mounted.wrapper.findAll('button').map((button) => button.text())

    expect(labels).toContain('编辑')
    expect(labels).not.toContain('新建')
    expect(labels).not.toContain('发布')
    expect(labels).not.toContain('再次发布')
    expect(labels).not.toContain('撤回')
    expect(labels).not.toContain('删除')
  })
})
