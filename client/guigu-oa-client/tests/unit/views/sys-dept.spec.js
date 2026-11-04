import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

const { messageMock, confirmMock } = vi.hoisted(() => ({
  messageMock: Object.assign(vi.fn(), { error: vi.fn(), success: vi.fn(), close: vi.fn() }),
  confirmMock: vi.fn(),
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    ElMessage: messageMock,
    ElMessageBox: { confirm: confirmMock, close: vi.fn() },
  }
})

import ElementPlus from 'element-plus'
import { hasPermi } from '@/permission'
import SysDept from '@/views/config/sysDept/index.vue'
import {
  createTestRouter,
  ensureDomApis,
  fakeRecognition,
  installHttp,
  ok,
  setupSession,
} from '../support/harness'

ensureDomApis()

const TREE = [
  {
    id: 1,
    parentId: 0,
    ancestors: ',0,1,',
    name: '总部',
    leader: '张三',
    sortValue: 1,
    status: 1,
    createTime: '2026-01-01 10:00:00',
    children: [
      {
        id: 2,
        parentId: 1,
        ancestors: ',0,1,2,',
        name: '研发部',
        leader: '',
        sortValue: 2,
        status: 0,
        children: [{ id: 3, parentId: 2, ancestors: ',0,1,2,3,', name: '平台组' }],
      },
    ],
  },
]

let notifications
let writeResponder

function mountPage() {
  notifications = []
  const recognition = fakeRecognition({
    reject: (_context, error, notifyNormal) => {
      if (notifyNormal) notifications.push(error.message)
      throw error
    },
  })
  const { pinia } = setupSession({
    recognition,
    permissions: ['system:dept:add', 'system:dept:edit', 'system:dept:remove'],
  })
  const router = createTestRouter([{ path: '/', component: { template: '<div />' } }])
  return mount(SysDept, {
    global: { plugins: [pinia, router, ElementPlus], directives: { hasPermi } },
  })
}

function responder(config) {
  if ((config.url || '').endsWith('/admin/system/sysDept/findTree')) return ok(TREE)
  const override = writeResponder?.(config)
  return override || ok(null)
}

let wrapper
let http
beforeEach(() => {
  vi.clearAllMocks()
  messageMock.mockReset()
  confirmMock.mockReset().mockResolvedValue('confirm')
  writeResponder = undefined
  http = installHttp(responder)
})

afterEach(() => {
  http.restore()
  wrapper?.unmount()
  wrapper = undefined
})

function treeReads() {
  return http.calls.filter((config) =>
    (config.url || '').endsWith('/admin/system/sysDept/findTree'),
  )
}

function writes(method) {
  return http.calls.filter(
    (config) => config.method === method && (config.url || '').startsWith('/admin/system/sysDept/'),
  )
}

describe('SysDept tree page', () => {
  it('loads the tree once and presents nested children', async () => {
    wrapper = mountPage()
    await flushPromises()

    expect(treeReads()).toHaveLength(1)
    expect(wrapper.vm.rows).toHaveLength(1)
    expect(wrapper.vm.rows[0]).toMatchObject({ id: 1, name: '总部', ancestors: ',0,1,' })
    expect(wrapper.vm.rows[0].children[0]).toMatchObject({
      id: 2,
      name: '研发部',
      ancestors: ',0,1,2,',
      status: 0,
    })
    expect(wrapper.vm.rows[0].children[0].children[0]).toMatchObject({ id: 3, name: '平台组' })
  })

  it('opens 新建 under a row and edits with parent options excluding its own subtree', async () => {
    wrapper = mountPage()
    await flushPromises()

    wrapper.vm.openCreate(wrapper.vm.rows[0].children[0])
    expect(wrapper.vm.form).toMatchObject({ id: '', parentId: 2, status: 1 })
    expect(wrapper.vm.parentChoices.map((option) => option.id)).toEqual([0, 1, 2, 3])

    wrapper.vm.openEdit(wrapper.vm.rows[0].children[0])
    expect(wrapper.vm.form).toMatchObject({
      id: 2,
      parentId: 1,
      name: '研发部',
      sortValue: 2,
      status: 0,
    })
    expect(wrapper.vm.parentChoices.map((option) => option.id)).toEqual([0, 1])
  })

  it('blocks an empty department name before the request', async () => {
    wrapper = mountPage()
    await flushPromises()

    wrapper.vm.openCreate()
    await flushPromises()
    await wrapper.vm.save()
    expect(writes('post')).toHaveLength(0)

    await new Promise((resolve) => setTimeout(resolve, 160))
    await flushPromises()
    expect(wrapper.vm.dialogVisible).toBe(true)
    expect(wrapper.findAll('.el-form-item__error').map((node) => node.text())).toEqual([
      '部门名称不能为空',
    ])
  })

  it('saves an edited department and refreshes the tree', async () => {
    wrapper = mountPage()
    await flushPromises()

    wrapper.vm.openEdit(wrapper.vm.rows[0].children[0])
    await flushPromises()
    await wrapper.vm.save()
    await flushPromises()

    expect(JSON.parse(writes('put')[0].data)).toEqual(
      expect.objectContaining({ id: 2, parentId: 1, name: '研发部', sortValue: 2, status: 0 }),
    )
    expect(wrapper.vm.dialogVisible).toBe(false)
    expect(treeReads()).toHaveLength(2)
    expect(messageMock).toHaveBeenCalledWith(expect.objectContaining({ message: '操作成功' }))
  })

  it('confirms deletion, refreshes after it, and surfaces the delete-protection message once', async () => {
    wrapper = mountPage()
    await flushPromises()

    await wrapper.vm.remove(2)
    expect(confirmMock).toHaveBeenCalled()
    expect(writes('delete')).toHaveLength(1)
    expect(treeReads()).toHaveLength(2)

    writeResponder = (config) =>
      (config.url || '').includes('/remove/2')
        ? { code: 201, message: '该部门存在用户，不允许删除' }
        : undefined
    await wrapper.vm.remove(2)
    expect(notifications).toEqual(['该部门存在用户，不允许删除'])
    expect(messageMock.error).not.toHaveBeenCalled()
    expect(treeReads()).toHaveLength(2)
  })

  it('surfaces a failed save once and keeps the dialog open', async () => {
    wrapper = mountPage()
    await flushPromises()

    writeResponder = (config) =>
      config.method === 'put' ? { code: 201, message: '部门名称已存在' } : undefined
    wrapper.vm.openEdit(wrapper.vm.rows[0].children[0])
    await flushPromises()
    await wrapper.vm.save()
    await flushPromises()

    expect(notifications).toEqual(['部门名称已存在'])
    expect(messageMock.error).not.toHaveBeenCalled()
    expect(wrapper.vm.dialogVisible).toBe(true)
    expect(treeReads()).toHaveLength(1)
  })
})
