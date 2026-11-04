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

const sysDeptApi = vi.hoisted(() => ({
  findTree: vi.fn(),
  removeById: vi.fn(),
  saveOrUpdate: vi.fn(),
}))

vi.mock('@/api/sysDept', () => sysDeptApi)

import ElementPlus from 'element-plus'
import { hasPermi } from '@/permission'
import SysDept from '@/views/config/sysDept/index.vue'
import { createTestRouter, ensureDomApis, installHttp, ok, setupSession } from '../support/harness'

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

function mountPage() {
  const { pinia } = setupSession({
    permissions: ['system:dept:add', 'system:dept:edit', 'system:dept:remove'],
  })
  const router = createTestRouter([{ path: '/', component: { template: '<div />' } }])
  return mount(SysDept, {
    global: { plugins: [pinia, router, ElementPlus], directives: { hasPermi } },
  })
}

let wrapper
let http
beforeEach(() => {
  vi.clearAllMocks()
  messageMock.mockReset()
  confirmMock.mockReset().mockResolvedValue('confirm')
  sysDeptApi.findTree.mockResolvedValue(ok(TREE))
  sysDeptApi.saveOrUpdate.mockResolvedValue(ok(null))
  sysDeptApi.removeById.mockResolvedValue(ok(null))
  http = installHttp((config) => {
    if ((config.url || '').endsWith('/admin/system/sysDept/findTree')) return ok(TREE)
    return ok(null)
  })
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
    expect(sysDeptApi.saveOrUpdate).not.toHaveBeenCalled()

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

    expect(sysDeptApi.saveOrUpdate).toHaveBeenCalledWith(
      expect.objectContaining({ id: 2, parentId: 1, name: '研发部', sortValue: 2, status: 0 }),
    )
    expect(wrapper.vm.dialogVisible).toBe(false)
    expect(treeReads()).toHaveLength(2)
    expect(messageMock).toHaveBeenCalledWith(expect.objectContaining({ message: '操作成功' }))
  })

  it('confirms deletion, refreshes after it, and surfaces the delete-protection message', async () => {
    wrapper = mountPage()
    await flushPromises()

    await wrapper.vm.remove(2)
    expect(confirmMock).toHaveBeenCalled()
    expect(sysDeptApi.removeById).toHaveBeenCalledWith(2)
    expect(treeReads()).toHaveLength(2)

    sysDeptApi.removeById.mockRejectedValueOnce(new Error('该部门存在用户，不允许删除'))
    await wrapper.vm.remove(2)
    expect(messageMock).toHaveBeenCalledWith(
      expect.objectContaining({ message: '该部门存在用户，不允许删除', type: 'error' }),
    )
    expect(treeReads()).toHaveLength(2)
  })
})
