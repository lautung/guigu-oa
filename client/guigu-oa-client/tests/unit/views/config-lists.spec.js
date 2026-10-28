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

const sysRoleApi = vi.hoisted(() => ({
  getById: vi.fn(),
  removeById: vi.fn(),
  saveOrUpdate: vi.fn(),
}))
const processTypeApi = vi.hoisted(() => ({
  getById: vi.fn(),
  removeById: vi.fn(),
  saveOrUpdate: vi.fn(),
}))
const sysMenuApi = vi.hoisted(() => ({
  findNodes: vi.fn(),
  removeById: vi.fn(),
  saveOrUpdate: vi.fn(),
}))

vi.mock('@/api/sysRole', () => sysRoleApi)
vi.mock('@/api/processType', () => processTypeApi)
vi.mock('@/api/sysMenu', () => ({
  ...sysMenuApi,
  doAssign: vi.fn(),
  getByIdForAssign: vi.fn(),
}))

import ElementPlus from 'element-plus'
import ProcessInstance from '@/views/config/processInstance/index.vue'
import ProcessTemplate from '@/views/config/processTemplate/index.vue'
import ProcessType from '@/views/config/processType/index.vue'
import SysMenu from '@/views/config/sysMenu/index.vue'
import SysRole from '@/views/config/sysRole/index.vue'
import { PATHS } from '@/router/paths'
import {
  createTestRouter,
  ensureDomApis,
  installHttp,
  ok,
  paged,
  setupSession,
} from '../support/harness'

const roleRecord = {
  id: 3,
  roleName: '审批人',
  roleCode: 'approver',
  createTime: '2026-01-01 10:00:00',
}
const typeRecord = { id: 2, name: '请假', description: '请假审批' }
const instanceRecord = {
  id: 'p-1',
  processDefinitionName: '请假审批',
  title: '张三的请假',
  status: 'RUNNING',
  createTime: '2026-01-02 10:00:00',
}
const templateRecord = {
  id: 5,
  name: '请假模板',
  processTypeId: 2,
  status: 1,
  createTime: '2026-01-03 10:00:00',
}

function listResponder(config) {
  const url = config.url || ''
  if (/\/sysRole\/\d+\/\d+$/.test(url)) return paged([roleRecord])
  if (/\/processType\/\d+\/\d+$/.test(url)) return paged([typeRecord])
  if (/\/processTemplate\/\d+\/\d+$/.test(url)) return paged([templateRecord])
  if (/^\/admin\/process\/\d+\/\d+$/.test(url)) return paged([instanceRecord])
  return ok(null)
}

ensureDomApis()

function observeMount(pinia, router) {
  return {
    global: {
      plugins: [pinia, router, ElementPlus],
      stubs: { 'form-create': true, 'fc-designer': true },
    },
  }
}

function testRouter() {
  return createTestRouter([
    { path: '/', component: { template: '<div />' } },
    { path: PATHS.SYSTEM_ASSIGN_AUTH, component: { template: '<div />' } },
    { path: PATHS.PROCESS_SET_TEMPLATE_SET, component: { template: '<div />' } },
    { path: PATHS.PROCESS_MGR_PROCESS_DETAIL, component: { template: '<div />' } },
  ])
}

let http
beforeEach(() => {
  vi.clearAllMocks()
  messageMock.mockReset()
  confirmMock.mockReset().mockResolvedValue('confirm')
  sysRoleApi.getById.mockResolvedValue(ok(roleRecord))
  sysRoleApi.removeById.mockResolvedValue(ok(null))
  sysRoleApi.saveOrUpdate.mockResolvedValue(ok(null))
  processTypeApi.getById.mockResolvedValue(ok(typeRecord))
  processTypeApi.removeById.mockResolvedValue(ok(null))
  processTypeApi.saveOrUpdate.mockResolvedValue(ok(null))
  sysMenuApi.findNodes.mockResolvedValue(
    ok([{ id: 1, name: '系统管理', type: 0, path: '/system', children: [] }]),
  )
  sysMenuApi.removeById.mockResolvedValue(ok(null))
  sysMenuApi.saveOrUpdate.mockResolvedValue(ok(null))
  http = installHttp(listResponder)
})

afterEach(() => http.restore())

describe('SysRole list page', () => {
  it('loads rows, searches, and opens the create dialog', async () => {
    const { pinia } = setupSession()
    const router = testRouter()
    const wrapper = mount(SysRole, observeMount(pinia, router))
    await flushPromises()

    expect(wrapper.vm.rows).toEqual([expect.objectContaining({ roleName: '审批人' })])

    wrapper.vm.roleName = '审'
    wrapper.vm.search()
    await flushPromises()
    expect(http.calls.at(-1).params).toEqual({ roleName: '审' })

    wrapper.vm.reset()
    await flushPromises()
    expect(wrapper.vm.roleName).toBe('')

    wrapper.vm.openCreate()
    expect(wrapper.vm.dialogVisible).toBe(true)
    expect(wrapper.vm.form.id).toBe('')
  })

  it('edits, saves, deletes, and navigates to menu authorization', async () => {
    const { pinia } = setupSession()
    const router = testRouter()
    const wrapper = mount(SysRole, observeMount(pinia, router))
    await flushPromises()

    await wrapper.vm.openEdit(3)
    expect(wrapper.vm.form.roleName).toBe('审批人')
    expect(wrapper.vm.dialogVisible).toBe(true)

    wrapper.vm.form = { ...wrapper.vm.form, roleName: '新角色' }
    await wrapper.vm.save()
    expect(sysRoleApi.saveOrUpdate).toHaveBeenCalledWith(
      expect.objectContaining({ roleName: '新角色' }),
    )
    expect(wrapper.vm.dialogVisible).toBe(false)

    await wrapper.vm.remove(3)
    expect(confirmMock).toHaveBeenCalled()
    expect(sysRoleApi.removeById).toHaveBeenCalledWith(3)

    wrapper.vm.openAssign(3, '审批人')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe(PATHS.SYSTEM_ASSIGN_AUTH)
  })

  it('keeps the dialog open when a refused edit is not loaded', async () => {
    const { pinia } = setupSession()
    const wrapper = mount(SysRole, observeMount(pinia, testRouter()))
    await flushPromises()

    const reject = vi.fn()
    sysRoleApi.getById.mockImplementation(async () => {
      reject()
      throw new Error('已拒绝')
    })
    await wrapper.vm.openEdit(3)
    expect(reject).toHaveBeenCalled()
    expect(wrapper.vm.dialogVisible).toBe(false)
  })
})

describe('ProcessType list page', () => {
  it('loads, edits, saves, and removes a 审批类型', async () => {
    const { pinia } = setupSession()
    const wrapper = mount(ProcessType, observeMount(pinia, testRouter()))
    await flushPromises()

    expect(wrapper.vm.rows[0]).toMatchObject({ name: '请假' })

    wrapper.vm.openCreate()
    expect(wrapper.vm.dialogVisible).toBe(true)

    await wrapper.vm.openEdit(2)
    expect(wrapper.vm.form.name).toBe('请假')

    await wrapper.vm.save()
    expect(processTypeApi.saveOrUpdate).toHaveBeenCalled()
    expect(wrapper.vm.dialogVisible).toBe(false)

    await wrapper.vm.remove(2)
    expect(processTypeApi.removeById).toHaveBeenCalledWith(2)
  })

  it('does not delete when the confirmation is dismissed', async () => {
    confirmMock.mockRejectedValue(new Error('cancel'))
    const { pinia } = setupSession()
    const wrapper = mount(ProcessType, observeMount(pinia, testRouter()))
    await flushPromises()

    await wrapper.vm.remove(2)
    expect(processTypeApi.removeById).not.toHaveBeenCalled()
  })
})

describe('ProcessInstance list page', () => {
  it('builds the query with and without a create-time range', async () => {
    const { pinia } = setupSession()
    const wrapper = mount(ProcessInstance, observeMount(pinia, testRouter()))
    await flushPromises()

    expect(wrapper.vm.rows).toHaveLength(1)
    expect(wrapper.vm.currentQuery()).toMatchObject({ createTimeBegin: '', createTimeEnd: '' })

    wrapper.vm.createTimes = ['2026-01-01', '2026-01-31']
    wrapper.vm.query.keyword = '请假'
    wrapper.vm.search()
    await flushPromises()
    expect(http.calls.at(-1).params).toMatchObject({
      keyword: '请假',
      createTimeBegin: '2026-01-01',
      createTimeEnd: '2026-01-31',
    })

    wrapper.vm.reset()
    await flushPromises()
    expect(wrapper.vm.createTimes).toEqual([])
  })

  it('opens the detail route', async () => {
    const { pinia } = setupSession()
    const router = testRouter()
    const wrapper = mount(ProcessInstance, observeMount(pinia, router))
    await flushPromises()

    wrapper.vm.openDetail('/processMgr/process/p-1')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/processMgr/process/p-1')
  })
})

describe('ProcessTemplate list page', () => {
  it('navigates to the editor, previews a template, and publishes', async () => {
    const { pinia } = setupSession()
    const router = testRouter()
    const wrapper = mount(ProcessTemplate, observeMount(pinia, router))
    await flushPromises()

    expect(wrapper.vm.rows[0]).toMatchObject({ name: '请假模板', canPublish: false })

    wrapper.vm.openCreate()
    await flushPromises()
    expect(router.currentRoute.value.path).toBe(PATHS.PROCESS_SET_TEMPLATE_SET)

    wrapper.vm.openEdit(undefined)
    expect(router.currentRoute.value.path).toBe(PATHS.PROCESS_SET_TEMPLATE_SET)

    wrapper.vm.show({ ...templateRecord, status: 0, formOptions: '{}' })
    expect(wrapper.vm.previewVisible).toBe(true)
    expect(wrapper.vm.preview.id).toBe(5)

    wrapper.vm.publishTemplate({ ...templateRecord, status: 0 })
    await flushPromises()
    expect(http.calls.some((call) => String(call.url).includes('/publish/5'))).toBe(true)
  })

  it('removes an unpublished template after confirmation', async () => {
    const { pinia } = setupSession()
    const wrapper = mount(ProcessTemplate, observeMount(pinia, testRouter()))
    await flushPromises()

    await wrapper.vm.remove({ ...templateRecord, status: 0 })
    expect(http.calls.some((call) => String(call.url).includes('/remove/5'))).toBe(true)
  })
})

describe('SysMenu list page', () => {
  it('loads the menu tree and creates child nodes', async () => {
    const { pinia } = setupSession()
    const wrapper = mount(SysMenu, observeMount(pinia, testRouter()))
    await flushPromises()

    expect(sysMenuApi.findNodes).toHaveBeenCalledTimes(1)
    expect(wrapper.vm.treeStatus).toBe('ready')
    expect(wrapper.vm.menuTree).toHaveLength(1)

    wrapper.vm.openCreate()
    expect(wrapper.vm.typeLocked).toBe(false)

    wrapper.vm.openCreate({ id: 1, name: '系统管理', type: 0 })
    expect(wrapper.vm.typeLocked).toBe(true)
    expect(wrapper.vm.form.parentId).toBe(1)

    wrapper.vm.openEdit({ id: 1, name: '系统管理', type: 0 })
    expect(wrapper.vm.typeLocked).toBe(true)
    expect(wrapper.vm.form.id).toBe(1)
  })

  it('saves, deletes, and reloads the tree', async () => {
    const { pinia } = setupSession()
    const wrapper = mount(SysMenu, observeMount(pinia, testRouter()))
    await flushPromises()

    wrapper.vm.openEdit({ id: 1, name: '系统管理', type: 0 })
    await wrapper.vm.save()
    expect(sysMenuApi.saveOrUpdate).toHaveBeenCalled()
    expect(messageMock).toHaveBeenCalledWith({
      message: '操作成功',
      type: 'success',
      duration: 3000,
    })
    expect(wrapper.vm.dialogVisible).toBe(false)

    await wrapper.vm.remove(1)
    expect(sysMenuApi.removeById).toHaveBeenCalledWith(1)
    expect(sysMenuApi.findNodes).toHaveBeenCalledTimes(3)
  })

  it('reports a failed tree read and recovers on retry', async () => {
    sysMenuApi.findNodes.mockRejectedValueOnce(new Error('服务不可用'))
    const { pinia } = setupSession()
    const wrapper = mount(SysMenu, observeMount(pinia, testRouter()))
    await flushPromises()

    expect(wrapper.vm.treeStatus).toBe('failed')
    expect(wrapper.vm.treeError).toBe('服务不可用')

    wrapper.vm.fetchTree()
    await flushPromises()
    expect(wrapper.vm.treeStatus).toBe('ready')
  })

  it('keeps the dialog open and stops saving when the write fails', async () => {
    sysMenuApi.saveOrUpdate.mockRejectedValueOnce(new Error('拒绝'))
    const { pinia } = setupSession()
    const wrapper = mount(SysMenu, observeMount(pinia, testRouter()))
    await flushPromises()

    wrapper.vm.openEdit({ id: 1, name: '系统管理', type: 0 })
    await wrapper.vm.save()
    expect(wrapper.vm.saving).toBe(false)
    expect(wrapper.vm.dialogVisible).toBe(true)

    confirmMock.mockRejectedValueOnce(new Error('cancel'))
    await wrapper.vm.remove(1)
    expect(sysMenuApi.removeById).not.toHaveBeenCalled()
  })
})
