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

const operLogApi = vi.hoisted(() => ({
  clean: vi.fn(),
  getById: vi.fn(),
  removeByIds: vi.fn(),
}))
const loginLogApi = vi.hoisted(() => ({
  clean: vi.fn(),
  removeByIds: vi.fn(),
  unlock: vi.fn(),
}))

vi.mock('@/api/oper-log', () => operLogApi)
vi.mock('@/api/login-log', () => loginLogApi)

import ElementPlus from 'element-plus'
import LoginLogPage from '@/views/config/loginLog/index.vue'
import OperLogPage from '@/views/config/operLog/index.vue'
import { hasPermi } from '@/permission'
import { ensureDomApis, installHttp, ok, paged, setupSession } from '../support/harness'

const operRecord = {
  id: 1,
  title: '用户管理·新增用户',
  businessType: 1,
  method: 'SysUserController#save',
  requestMethod: 'POST',
  operName: 'admin',
  deptName: '研发部',
  operUrl: '/admin/system/sysUser/save',
  operIp: '127.0.0.1',
  operParam: '{"username":"alice"}',
  resultCode: 200,
  resultMessage: '操作成功',
  status: 0,
  errorMsg: '',
  costTime: 12,
  createTime: '2026-01-01 10:00:00',
}

const loginRecord = {
  id: 7,
  username: 'alice',
  ip: '127.0.0.1',
  browser: 'Chrome',
  os: 'macOS',
  status: 2,
  msg: '账号锁定',
  createTime: '2026-01-02 10:00:00',
}

const ALL_OPER_PERMISSIONS = ['system:operLog:remove', 'system:operLog:clean']
const ALL_LOGIN_PERMISSIONS = [
  'system:loginLog:remove',
  'system:loginLog:clean',
  'system:loginLog:unlock',
]

let operRows = []
let loginRows = []
let http

function responder(config) {
  const url = config.url || ''
  if (/^\/admin\/system\/sysOperLog\/\d+\/\d+$/.test(url)) return paged(operRows)
  if (/^\/admin\/system\/sysLoginLog\/\d+\/\d+$/.test(url)) return paged(loginRows)
  return ok(null)
}

function realOptions(pinia) {
  return {
    global: {
      plugins: [pinia, ElementPlus],
      stubs: { 'form-create': true, 'fc-designer': true },
      directives: { hasPermi },
    },
  }
}

function buttonTexts(wrapper) {
  return wrapper.findAll('button').map((node) => node.text())
}

function button(wrapper, text) {
  return wrapper.findAll('button').find((node) => node.text() === text)
}

ensureDomApis()

beforeEach(() => {
  vi.clearAllMocks()
  messageMock.mockReset()
  confirmMock.mockReset().mockResolvedValue('confirm')
  operLogApi.clean.mockResolvedValue(ok(null))
  operLogApi.getById.mockResolvedValue(ok(operRecord))
  operLogApi.removeByIds.mockResolvedValue(ok(null))
  loginLogApi.clean.mockResolvedValue(ok(null))
  loginLogApi.removeByIds.mockResolvedValue(ok(null))
  loginLogApi.unlock.mockResolvedValue(ok(null))
  operRows = [{ ...operRecord }]
  loginRows = [{ ...loginRecord }]
  http = installHttp(responder)
})

afterEach(() => http.restore())

describe('操作日志列表页', () => {
  it('loads rows, projects the filter, paginates, and refreshes after a batch delete', async () => {
    const { pinia } = setupSession({ permissions: ALL_OPER_PERMISSIONS })
    const wrapper = mount(OperLogPage, realOptions(pinia))
    await flushPromises()

    expect(wrapper.vm.rows).toEqual([
      expect.objectContaining({
        title: '用户管理·新增用户',
        businessTypeLabel: '新增',
        statusLabel: '成功',
      }),
    ])
    expect(http.calls.at(-1).url).toBe('/admin/system/sysOperLog/1/10')

    wrapper.vm.query.operId = 301
    wrapper.vm.query.operName = 'admin'
    wrapper.vm.query.businessType = 0
    wrapper.vm.query.status = 0
    wrapper.vm.query.createTimes = ['2026-01-01 00:00:00', '2026-01-31 23:59:59']
    wrapper.vm.search()
    await flushPromises()
    expect(http.calls.at(-1).params).toEqual({
      operId: 301,
      operName: 'admin',
      businessType: 0,
      status: 0,
      createTimeBegin: '2026-01-01 00:00:00',
      createTimeEnd: '2026-01-31 23:59:59',
    })

    wrapper.vm.list.handlePageChange(2)
    await flushPromises()
    expect(http.calls.at(-1).url).toBe('/admin/system/sysOperLog/2/10')

    operLogApi.removeByIds.mockImplementation(async () => {
      operRows = []
      return ok(null)
    })
    wrapper.vm.onSelectionChange([{ id: 1 }, { id: 2 }])
    const before = http.calls.length
    await wrapper.vm.removeSelected()
    await flushPromises()

    expect(confirmMock).toHaveBeenCalledTimes(1)
    expect(operLogApi.removeByIds).toHaveBeenCalledWith([1, 2])
    expect(http.calls.length).toBe(before + 1)
    expect(wrapper.vm.rows).toEqual([])
    expect(wrapper.vm.selectedIds).toEqual([])
  })

  it('requires the clean confirmation and refreshes the empty list afterwards', async () => {
    const { pinia } = setupSession({ permissions: ALL_OPER_PERMISSIONS })
    const wrapper = mount(OperLogPage, realOptions(pinia))
    await flushPromises()

    confirmMock.mockRejectedValueOnce(new Error('cancel'))
    await wrapper.vm.cleanAll()
    expect(operLogApi.clean).not.toHaveBeenCalled()

    operLogApi.clean.mockImplementation(async () => {
      operRows = []
      return ok(null)
    })
    const before = http.calls.length
    await wrapper.vm.cleanAll()
    await flushPromises()

    expect(confirmMock).toHaveBeenCalledTimes(2)
    expect(operLogApi.clean).toHaveBeenCalledTimes(1)
    expect(http.calls.length).toBe(before + 1)
    expect(wrapper.vm.rows).toEqual([])
  })

  it('opens the detail dialog with pretty-printed parameters', async () => {
    const { pinia } = setupSession({ permissions: ALL_OPER_PERMISSIONS })
    const wrapper = mount(OperLogPage, realOptions(pinia))
    await flushPromises()

    await wrapper.vm.openDetail(1)
    await flushPromises()

    expect(operLogApi.getById).toHaveBeenCalledWith(1)
    expect(wrapper.vm.detailVisible).toBe(true)
    expect(wrapper.vm.detail).toMatchObject({
      businessTypeLabel: '新增',
      resultCodeText: '200',
      resultMessage: '操作成功',
      costTimeText: '12 ms',
      requestMethod: 'POST',
      operUrl: '/admin/system/sysUser/save',
      operIp: '127.0.0.1',
      operParamText: '{\n  "username": "alice"\n}',
    })
    expect(wrapper.text()).toContain('"username": "alice"')
    expect(wrapper.text()).toContain('12 ms')
  })

  it('removes the write buttons from the DOM when the permissions are absent', async () => {
    const { pinia } = setupSession({ permissions: [] })
    const wrapper = mount(OperLogPage, realOptions(pinia))
    await flushPromises()

    expect(buttonTexts(wrapper)).not.toContain('批量删除')
    expect(buttonTexts(wrapper)).not.toContain('清空')
  })

  it('offers the write buttons and disables the batch action without a selection', async () => {
    const { pinia } = setupSession({ permissions: ALL_OPER_PERMISSIONS })
    const wrapper = mount(OperLogPage, realOptions(pinia))
    await flushPromises()

    expect(buttonTexts(wrapper)).toContain('批量删除')
    expect(buttonTexts(wrapper)).toContain('清空')
    expect(button(wrapper, '批量删除').attributes('disabled')).toBeDefined()
  })
})

describe('登录日志列表页', () => {
  it('loads rows, projects the filter, and paginates', async () => {
    const { pinia } = setupSession({ permissions: ALL_LOGIN_PERMISSIONS })
    const wrapper = mount(LoginLogPage, realOptions(pinia))
    await flushPromises()

    expect(wrapper.vm.rows).toEqual([
      expect.objectContaining({
        username: 'alice',
        statusLabel: '锁定',
        statusTagType: 'warning',
      }),
    ])
    expect(http.calls.at(-1).url).toBe('/admin/system/sysLoginLog/1/10')

    wrapper.vm.query.username = 'alice'
    wrapper.vm.query.ip = '127.0.0.1'
    wrapper.vm.query.status = 2
    wrapper.vm.query.createTimes = ['2026-01-01 00:00:00', '2026-01-31 23:59:59']
    wrapper.vm.search()
    await flushPromises()
    expect(http.calls.at(-1).params).toEqual({
      username: 'alice',
      ip: '127.0.0.1',
      status: 2,
      createTimeBegin: '2026-01-01 00:00:00',
      createTimeEnd: '2026-01-31 23:59:59',
    })

    wrapper.vm.list.handleSizeChange(20)
    await flushPromises()
    expect(http.calls.at(-1).url).toBe('/admin/system/sysLoginLog/1/20')
  })

  it('unlocks the account after confirmation and shows the new unlock row', async () => {
    const { pinia } = setupSession({ permissions: ALL_LOGIN_PERMISSIONS })
    const wrapper = mount(LoginLogPage, realOptions(pinia))
    await flushPromises()

    loginLogApi.unlock.mockImplementation(async (username) => {
      loginRows = [
        { id: 8, username, status: 3, msg: '手动解锁', createTime: '2026-01-03 10:00:00' },
        ...loginRows,
      ]
      return ok(null)
    })

    await wrapper.vm.unlockRow(wrapper.vm.rows[0])
    await flushPromises()

    expect(confirmMock).toHaveBeenCalledTimes(1)
    expect(loginLogApi.unlock).toHaveBeenCalledWith('alice')
    expect(wrapper.vm.rows[0]).toMatchObject({ statusLabel: '解锁', msg: '手动解锁' })
  })

  it('does not unlock or clean when the confirmation is dismissed', async () => {
    const { pinia } = setupSession({ permissions: ALL_LOGIN_PERMISSIONS })
    const wrapper = mount(LoginLogPage, realOptions(pinia))
    await flushPromises()

    confirmMock.mockRejectedValue(new Error('cancel'))
    await wrapper.vm.unlockRow(wrapper.vm.rows[0])
    await wrapper.vm.cleanAll()

    expect(loginLogApi.unlock).not.toHaveBeenCalled()
    expect(loginLogApi.clean).not.toHaveBeenCalled()
  })

  it('cleans the list after confirmation and refreshes it', async () => {
    const { pinia } = setupSession({ permissions: ALL_LOGIN_PERMISSIONS })
    const wrapper = mount(LoginLogPage, realOptions(pinia))
    await flushPromises()

    loginLogApi.clean.mockImplementation(async () => {
      loginRows = []
      return ok(null)
    })
    const before = http.calls.length
    await wrapper.vm.cleanAll()
    await flushPromises()

    expect(confirmMock).toHaveBeenCalledTimes(1)
    expect(loginLogApi.clean).toHaveBeenCalledTimes(1)
    expect(http.calls.length).toBe(before + 1)
    expect(wrapper.vm.rows).toEqual([])
  })

  it('removes the write and unlock actions from the DOM when the permissions are absent', async () => {
    const { pinia } = setupSession({ permissions: [] })
    const wrapper = mount(LoginLogPage, realOptions(pinia))
    await flushPromises()

    expect(buttonTexts(wrapper)).not.toContain('批量删除')
    expect(buttonTexts(wrapper)).not.toContain('清空')
    expect(buttonTexts(wrapper)).not.toContain('解锁')
  })

  it('offers the write and unlock actions when the permissions are granted', async () => {
    const { pinia } = setupSession({ permissions: ALL_LOGIN_PERMISSIONS })
    const wrapper = mount(LoginLogPage, realOptions(pinia))
    await flushPromises()

    expect(buttonTexts(wrapper)).toContain('批量删除')
    expect(buttonTexts(wrapper)).toContain('清空')
    expect(buttonTexts(wrapper)).toContain('解锁')
  })
})
