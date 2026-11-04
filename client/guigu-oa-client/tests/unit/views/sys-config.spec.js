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

const sysConfigApi = vi.hoisted(() => ({
  refreshCache: vi.fn(),
  removeById: vi.fn(),
  saveOrUpdate: vi.fn(),
}))

vi.mock('@/api/sysConfig', () => sysConfigApi)

import ElementPlus from 'element-plus'
import { hasPermi } from '@/permission'
import SysConfig from '@/views/config/config/index.vue'
import { ensureDomApis, installHttp, ok, paged, setupSession } from '../support/harness'

ensureDomApis()

const builtinRow = {
  id: 1,
  configKey: 'sys.account.registerUser',
  configValue: 'false',
  configName: '是否开放注册',
  valueType: 'boolean',
  builtin: 1,
  remark: '关闭时注册页不可用',
  createTime: '2026-11-04 10:00:00',
}

const customRow = {
  id: 2,
  configKey: 't252_custom',
  configValue: 'hello',
  configName: '自定义参数',
  valueType: 'string',
  builtin: 0,
  createTime: '2026-11-04 10:01:00',
}

function responder(config) {
  if (/\/admin\/system\/config\/\d+\/\d+$/.test(config.url || '')) {
    return paged([builtinRow, customRow])
  }
  return ok(null)
}

function mountPage() {
  const { pinia } = setupSession({
    permissions: ['system:config:add', 'system:config:edit', 'system:config:remove'],
  })
  return mount(SysConfig, {
    global: { plugins: [pinia, ElementPlus], directives: { hasPermi } },
  })
}

function listReads() {
  return http.calls.filter((call) => /\/admin\/system\/config\/\d+\/\d+$/.test(call.url || ''))
}

let http
let wrapper
beforeEach(() => {
  vi.clearAllMocks()
  messageMock.mockReset()
  confirmMock.mockReset().mockResolvedValue('confirm')
  sysConfigApi.saveOrUpdate.mockReset().mockResolvedValue(ok(null))
  sysConfigApi.removeById.mockReset().mockResolvedValue(ok(null))
  sysConfigApi.refreshCache.mockReset().mockResolvedValue(ok(1))
  http = installHttp(responder)
})

afterEach(() => {
  http.restore()
  wrapper?.unmount()
  wrapper = undefined
})

describe('SysConfig list page', () => {
  it('loads rows with keys, values, types, and builtin tags', async () => {
    wrapper = mountPage()
    await flushPromises()

    expect(listReads()).toHaveLength(1)
    expect(wrapper.vm.rows).toEqual([
      expect.objectContaining({
        configKey: 'sys.account.registerUser',
        builtinLabel: '内置',
        valueTypeLabel: '布尔',
      }),
      expect.objectContaining({
        configKey: 't252_custom',
        builtinLabel: '自定义',
        valueTypeLabel: '字符串',
      }),
    ])
  })

  it('keeps builtin keys read-only and sends only the value on save', async () => {
    wrapper = mountPage()
    await flushPromises()
    const readsBefore = listReads().length

    wrapper.vm.openEdit(1)
    await flushPromises()
    expect(wrapper.vm.form).toEqual({
      id: 1,
      configKey: 'sys.account.registerUser',
      configValue: 'false',
      configName: '是否开放注册',
      valueType: 'boolean',
      builtin: 1,
      remark: '关闭时注册页不可用',
    })
    expect(wrapper.find('[data-test="config-key"]').attributes('disabled')).toBeDefined()
    expect(wrapper.find('[data-test="config-name"]').attributes('disabled')).toBeDefined()
    expect(wrapper.find('[data-test="config-value"]').attributes('disabled')).toBeUndefined()

    wrapper.vm.form.configValue = 'true'
    await wrapper.vm.save()
    await flushPromises()

    expect(sysConfigApi.saveOrUpdate).toHaveBeenCalledWith(
      expect.objectContaining({ id: 1, configValue: 'true', builtin: 1 }),
    )
    expect(wrapper.vm.dialogVisible).toBe(false)
    expect(listReads().length).toBe(readsBefore + 1)
  })

  it('blocks an empty key and name before the request', async () => {
    wrapper = mountPage()
    await flushPromises()

    wrapper.vm.openCreate()
    await flushPromises()
    await wrapper.vm.save()
    expect(sysConfigApi.saveOrUpdate).not.toHaveBeenCalled()

    await new Promise((resolve) => setTimeout(resolve, 160))
    await flushPromises()
    expect(wrapper.vm.dialogVisible).toBe(true)
    expect(wrapper.findAll('.el-form-item__error').map((node) => node.text())).toEqual([
      '参数名称不能为空',
      '参数键不能为空',
    ])
  })

  it('saves an edited custom 参数 and refreshes the list', async () => {
    wrapper = mountPage()
    await flushPromises()
    const readsBefore = listReads().length

    wrapper.vm.openEdit(2)
    await flushPromises()
    wrapper.vm.form.configValue = 'v2'
    wrapper.vm.form.valueType = 'number'
    await wrapper.vm.save()
    await flushPromises()

    expect(sysConfigApi.saveOrUpdate).toHaveBeenCalledWith(
      expect.objectContaining({
        id: 2,
        configKey: 't252_custom',
        configValue: 'v2',
        valueType: 'number',
      }),
    )
    expect(listReads().length).toBe(readsBefore + 1)
  })

  it('manually refreshes the cache and reloads the list', async () => {
    wrapper = mountPage()
    await flushPromises()
    const readsBefore = listReads().length

    await wrapper.vm.refreshCache()
    await flushPromises()

    expect(sysConfigApi.refreshCache).toHaveBeenCalledTimes(1)
    expect(listReads().length).toBe(readsBefore + 1)
  })

  it('does not offer delete on builtin rows and confirms before deleting a custom row', async () => {
    wrapper = mountPage()
    await flushPromises()

    const builtinTableRow = wrapper
      .findAll('.el-table__row')
      .find((node) => node.text().includes('sys.account.registerUser'))
    expect(builtinTableRow.findAll('button').some((node) => node.text() === '删除')).toBe(false)

    const readsBefore = listReads().length
    await wrapper.vm.remove(2)
    expect(confirmMock).toHaveBeenCalledWith(
      '此操作将永久删除该参数, 是否继续?',
      '提示',
      expect.objectContaining({ type: 'warning' }),
    )
    expect(sysConfigApi.removeById).toHaveBeenCalledWith(2)
    expect(listReads().length).toBe(readsBefore + 1)
  })
})
