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

const dictApi = vi.hoisted(() => ({
  refreshCache: vi.fn(),
  removeData: vi.fn(),
  removeType: vi.fn(),
  saveOrUpdateData: vi.fn(),
  saveOrUpdateType: vi.fn(),
}))

vi.mock('@/api/sysDict', () => dictApi)

import ElementPlus from 'element-plus'
import { hasPermi } from '@/permission'
import SysDict from '@/views/config/dict/index.vue'
import { ensureDomApis, installHttp, ok, paged, setupSession } from '../support/harness'

ensureDomApis()

const typeRow = {
  id: 11,
  dictName: '公告类型',
  dictType: 'sys_notice_type',
  status: 1,
  remark: '种子',
  createTime: '2026-11-04 10:00:00',
}

const dataRow = {
  id: 21,
  dictType: 'sys_notice_type',
  dictLabel: '通知',
  dictValue: '1',
  sortValue: 1,
  status: 1,
  remark: '',
  createTime: '2026-11-04 10:00:00',
}

function responder(config) {
  const url = config.url || ''
  if (/\/admin\/system\/dict\/type\/\d+\/\d+$/.test(url)) return paged([typeRow])
  if (/\/admin\/system\/dict\/data\/\d+\/\d+$/.test(url)) {
    return config.params?.dictType ? paged([dataRow]) : paged([])
  }
  return ok(null)
}

function typeReads() {
  return http.calls.filter((call) => /\/admin\/system\/dict\/type\/\d+\/\d+$/.test(call.url || ''))
}

function dataReads() {
  return http.calls.filter((call) => /\/admin\/system\/dict\/data\/\d+\/\d+$/.test(call.url || ''))
}

function mountPage() {
  const { pinia } = setupSession({
    permissions: ['system:dict:add', 'system:dict:edit', 'system:dict:remove'],
  })
  return mount(SysDict, {
    global: { plugins: [pinia, ElementPlus], directives: { hasPermi } },
  })
}

let http
let wrapper

beforeEach(() => {
  vi.clearAllMocks()
  messageMock.mockReset()
  confirmMock.mockReset().mockResolvedValue('confirm')
  dictApi.saveOrUpdateType.mockReset().mockResolvedValue(ok(null))
  dictApi.saveOrUpdateData.mockReset().mockResolvedValue(ok(null))
  dictApi.removeType.mockReset().mockResolvedValue(ok(null))
  dictApi.removeData.mockReset().mockResolvedValue(ok(null))
  dictApi.refreshCache.mockReset().mockResolvedValue(ok(2))
  http = installHttp(responder)
})

afterEach(() => {
  http.restore()
  wrapper?.unmount()
  wrapper = undefined
})

describe('字典管理两级页面', () => {
  it('loads the type list and keeps the data pane empty until a type is selected', async () => {
    wrapper = mountPage()
    await flushPromises()

    expect(typeReads()).toHaveLength(1)
    expect(wrapper.vm.typeRows).toEqual([
      expect.objectContaining({ id: 11, dictType: 'sys_notice_type', statusLabel: '正常' }),
    ])
    expect(dataReads()).toHaveLength(1)
    expect(dataReads()[0].params).toEqual({})
    expect(wrapper.vm.dataRows).toEqual([])
  })

  it('reads the right pane with the selected dictType', async () => {
    wrapper = mountPage()
    await flushPromises()

    wrapper.vm.selectType(typeRow)
    await flushPromises()

    expect(dataReads()).toHaveLength(2)
    expect(dataReads().at(-1).params).toEqual({ dictType: 'sys_notice_type' })
    expect(wrapper.vm.dataRows).toEqual([
      expect.objectContaining({ id: 21, dictLabel: '通知', statusLabel: '正常' }),
    ])
  })

  it('saves a data row and refreshes the right pane', async () => {
    wrapper = mountPage()
    await flushPromises()
    wrapper.vm.selectType(typeRow)
    await flushPromises()
    const before = dataReads().length

    wrapper.vm.openEditData(21)
    await flushPromises()
    wrapper.vm.dataForm.dictLabel = '通知（改）'
    await wrapper.vm.saveData()
    await flushPromises()

    expect(dictApi.saveOrUpdateData).toHaveBeenCalledWith(
      expect.objectContaining({ id: 21, dictType: 'sys_notice_type', dictLabel: '通知（改）' }),
    )
    expect(wrapper.vm.dataDialogVisible).toBe(false)
    expect(dataReads().length).toBe(before + 1)
  })

  it('saves a type row and refreshes the left pane', async () => {
    wrapper = mountPage()
    await flushPromises()
    const before = typeReads().length

    wrapper.vm.openEditType(11)
    await flushPromises()
    wrapper.vm.typeForm.dictName = '公告类型（改）'
    await wrapper.vm.saveType()
    await flushPromises()

    expect(dictApi.saveOrUpdateType).toHaveBeenCalledWith(
      expect.objectContaining({ id: 11, dictType: 'sys_notice_type', dictName: '公告类型（改）' }),
    )
    expect(wrapper.vm.typeDialogVisible).toBe(false)
    expect(typeReads().length).toBe(before + 1)
  })

  it('refreshes the server cache, then rereads the list', async () => {
    wrapper = mountPage()
    await flushPromises()
    const before = typeReads().length

    await wrapper.vm.refreshCache()
    await flushPromises()

    expect(dictApi.refreshCache).toHaveBeenCalledTimes(1)
    expect(typeReads().length).toBe(before + 1)
  })

  it('removes a data row only after confirmation and refreshes', async () => {
    wrapper = mountPage()
    await flushPromises()
    wrapper.vm.selectType(typeRow)
    await flushPromises()
    const before = dataReads().length

    await wrapper.vm.removeData(21)
    await flushPromises()

    expect(confirmMock).toHaveBeenCalledTimes(1)
    expect(dictApi.removeData).toHaveBeenCalledWith(21)
    expect(dataReads().length).toBe(before + 1)
  })
})
