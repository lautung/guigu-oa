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

const sysPostApi = vi.hoisted(() => ({
  removeById: vi.fn(),
  saveOrUpdate: vi.fn(),
}))

vi.mock('@/api/sysPost', () => sysPostApi)

import ElementPlus from 'element-plus'
import SysPost from '@/views/config/sysPost/index.vue'
import { ensureDomApis, installHttp, ok, paged, setupSession } from '../support/harness'

ensureDomApis()

const postRecord = {
  id: 4,
  postCode: 't248_clerk',
  name: '文员',
  sortValue: 1,
  status: 1,
  createTime: '2026-11-04 10:00:00',
}

const disabledPost = {
  id: 5,
  postCode: 't248_auditor',
  name: '审核员',
  sortValue: 2,
  status: 0,
  createTime: '2026-11-04 10:01:00',
}

function responder(config) {
  if (/\/sysPost\/\d+\/\d+$/.test(config.url || '')) return paged([postRecord, disabledPost])
  return ok(null)
}

function realOptions(pinia) {
  return {
    global: {
      plugins: [pinia, ElementPlus],
    },
  }
}

let http
beforeEach(() => {
  vi.clearAllMocks()
  messageMock.mockReset()
  confirmMock.mockReset().mockResolvedValue('confirm')
  sysPostApi.saveOrUpdate.mockReset().mockResolvedValue(ok(null))
  sysPostApi.removeById.mockReset().mockResolvedValue(ok(null))
  http = installHttp(responder)
})

afterEach(() => http.restore())

describe('SysPost list page', () => {
  it('loads rows with codes, names, sort values, and status labels', async () => {
    const { pinia } = setupSession()
    const wrapper = mount(SysPost, realOptions(pinia))
    await flushPromises()

    expect(http.calls.at(-1).url).toBe('/admin/system/sysPost/1/10')
    expect(wrapper.vm.rows).toEqual([
      expect.objectContaining({ postCode: 't248_clerk', name: '文员', statusLabel: '正常' }),
      expect.objectContaining({ postCode: 't248_auditor', name: '审核员', statusLabel: '停用' }),
    ])

    wrapper.vm.name = '文员'
    wrapper.vm.search()
    await flushPromises()
    expect(http.calls.at(-1).params).toEqual({ name: '文员' })

    wrapper.vm.reset()
    await flushPromises()
    expect(wrapper.vm.name).toBe('')
    expect(http.calls.at(-1).params).toEqual({})
  })

  it('opens the create dialog and blocks it with the local required messages', async () => {
    const { pinia } = setupSession()
    const wrapper = mount(SysPost, realOptions(pinia))
    await flushPromises()

    wrapper.vm.openCreate()
    await flushPromises()
    expect(wrapper.vm.dialogVisible).toBe(true)
    expect(wrapper.vm.dialogTitle).toBe('新建岗位')
    expect(wrapper.vm.form).toEqual({ id: '', postCode: '', name: '', sortValue: 0, status: 1 })

    await wrapper.vm.save()
    // Element Plus 用 100ms debounce 决定错误是否上屏，等过 debounce 再断言。
    await new Promise((resolve) => setTimeout(resolve, 150))
    await flushPromises()
    expect(sysPostApi.saveOrUpdate).not.toHaveBeenCalled()
    expect(wrapper.vm.dialogVisible).toBe(true)
    expect(wrapper.findAll('.el-form-item__error').map((node) => node.text())).toEqual([
      '岗位名称不能为空',
      '岗位编码不能为空',
    ])
  })

  it('edits a row, saves it, and closes the dialog', async () => {
    const { pinia } = setupSession()
    const wrapper = mount(SysPost, realOptions(pinia))
    await flushPromises()

    wrapper.vm.openEdit(4)
    await flushPromises()
    expect(wrapper.vm.form).toEqual({
      id: 4,
      postCode: 't248_clerk',
      name: '文员',
      sortValue: 1,
      status: 1,
    })
    expect(wrapper.vm.dialogTitle).toBe('编辑岗位')

    wrapper.vm.form = { ...wrapper.vm.form, name: '高级文员', status: 0 }
    await wrapper.vm.save()
    expect(sysPostApi.saveOrUpdate).toHaveBeenCalledWith(
      expect.objectContaining({ id: 4, name: '高级文员', status: 0 }),
    )
    expect(wrapper.vm.dialogVisible).toBe(false)
  })

  it('confirms before deleting and refreshes the list', async () => {
    const { pinia } = setupSession()
    const wrapper = mount(SysPost, realOptions(pinia))
    await flushPromises()
    const listCalls = http.calls.filter((call) =>
      /\/sysPost\/\d+\/\d+$/.test(call.url || ''),
    ).length

    await wrapper.vm.remove(4)
    expect(confirmMock).toHaveBeenCalledWith(
      '此操作将永久删除该岗位, 是否继续?',
      '提示',
      expect.objectContaining({ type: 'warning' }),
    )
    expect(sysPostApi.removeById).toHaveBeenCalledWith(4)
    expect(http.calls.filter((call) => /\/sysPost\/\d+\/\d+$/.test(call.url || '')).length).toBe(
      listCalls + 1,
    )
  })

  it('does not delete when the confirmation is dismissed', async () => {
    const { pinia } = setupSession()
    const wrapper = mount(SysPost, realOptions(pinia))
    await flushPromises()

    confirmMock.mockRejectedValueOnce('cancel')
    await wrapper.vm.remove(4)
    expect(sysPostApi.removeById).not.toHaveBeenCalled()
  })
})
