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

const sysUserApi = vi.hoisted(() => ({
  downloadImportTemplate: vi.fn(),
  exportUsers: vi.fn(),
  findDeptTree: vi.fn(),
  findPostOptions: vi.fn(),
  getById: vi.fn(),
  importUsers: vi.fn(),
  removeById: vi.fn(),
  resetPassword: vi.fn(),
  saveOrUpdate: vi.fn(),
  updateStatus: vi.fn(),
}))
const sysMenuApi = vi.hoisted(() => ({
  getByIdForAssign: vi.fn(),
  doAssign: vi.fn(),
}))

vi.mock('@/api/sysUser', () => sysUserApi)
vi.mock('@/api/sysMenu', () => ({
  ...sysMenuApi,
  findNodes: vi.fn(),
  removeById: vi.fn(),
  saveOrUpdate: vi.fn(),
}))

import ElementPlus from 'element-plus'
import AssignAuth from '@/views/config/sysRole/assignAuth.vue'
import SysUser from '@/views/config/sysUser/index.vue'
import AssignRolesDialog from '@/views/config/sysUser/AssignRolesDialog.vue'
import { hasPermi } from '@/permission'
import { PATHS } from '@/router/paths'
import {
  createTestRouter,
  elementStubs,
  ensureDomApis,
  fakeRecognition,
  installHttp,
  mountOptions,
  ok,
  paged,
  setupSession,
} from '../support/harness'

const userRecord = {
  id: 3,
  username: 'bob',
  name: '鲍勃',
  phone: '13800000000',
  status: 1,
  deptId: 11,
  postIds: [301],
  createTime: '2026-01-01 10:00:00',
}

const deptTree = [
  {
    id: 10,
    parentId: 0,
    name: '研发部',
    status: 1,
    children: [{ id: 11, parentId: 10, name: '前端组', status: 1, children: [] }],
  },
]

const postOptions = [
  { id: 301, name: '经理', status: 1 },
  { id: 302, name: '专员', status: 1 },
]

const roleAssignment = {
  allRolesList: [
    { id: 1, roleName: '审批人' },
    { id: 2, roleName: '办事员' },
  ],
  assginRoleList: [{ id: 1 }],
}

const menuTree = [
  {
    id: 1,
    name: '系统管理',
    type: 0,
    path: '/system',
    children: [
      {
        id: 2,
        name: '用户管理',
        type: 1,
        path: '/system/sysUser',
        select: true,
        children: [
          {
            id: 21,
            name: '新增用户',
            type: 2,
            perms: 'system:user:add',
            select: true,
            children: [],
          },
        ],
      },
      { id: 9, name: '待办', type: 1, path: '/work/pending' },
    ],
  },
]

function responder(config) {
  const url = config.url || ''
  if (/\/sysUser\/depts$/.test(url)) return ok(deptTree)
  if (/\/sysUser\/posts$/.test(url)) return ok(postOptions)
  if (/\/sysUser\/\d+\/\d+$/.test(url)) return paged([userRecord])
  if (/\/sysRole\/toAssign\/\d+$/.test(url)) return ok(roleAssignment)
  if (/\/sysRole\/doAssign$/.test(url)) return ok(null)
  return ok(null)
}

ensureDomApis()

function realOptions(pinia, router) {
  return {
    global: {
      plugins: [pinia, router, ElementPlus],
      directives: { hasPermi },
      stubs: { 'form-create': true, 'fc-designer': true },
    },
  }
}

function testRouter() {
  return createTestRouter([
    { path: '/', component: { template: '<div />' } },
    { path: PATHS.SYSTEM_SYS_USER, component: { template: '<div />' } },
    { path: PATHS.SYSTEM_ASSIGN_AUTH, component: { template: '<div />' } },
  ])
}

let http
beforeEach(() => {
  vi.clearAllMocks()
  messageMock.mockReset()
  confirmMock.mockReset().mockResolvedValue('confirm')
  sysUserApi.findDeptTree.mockResolvedValue(ok(deptTree))
  sysUserApi.findPostOptions.mockResolvedValue(ok(postOptions))
  sysUserApi.getById.mockResolvedValue(ok(userRecord))
  sysUserApi.removeById.mockResolvedValue(ok(null))
  sysUserApi.resetPassword.mockResolvedValue(ok(null))
  sysUserApi.saveOrUpdate.mockResolvedValue(ok(null))
  sysUserApi.updateStatus.mockResolvedValue(ok(null))
  sysUserApi.exportUsers.mockResolvedValue(new Blob(['xlsx']))
  sysUserApi.downloadImportTemplate.mockResolvedValue(new Blob(['xlsx']))
  sysUserApi.importUsers.mockResolvedValue(
    ok({
      total: 3,
      successCount: 2,
      failureCount: 1,
      failures: [{ rowNumber: 4, username: 'bob', reason: '用户名已存在' }],
    }),
  )
  sysMenuApi.getByIdForAssign.mockResolvedValue(ok(menuTree))
  sysMenuApi.doAssign.mockResolvedValue(ok(null))
  http = installHttp(responder)
})

afterEach(() => http.restore())

describe('SysUser list page', () => {
  it('loads rows and runs the list actions', async () => {
    const { pinia } = setupSession()
    const wrapper = mount(SysUser, realOptions(pinia, testRouter()))
    await flushPromises()

    expect(wrapper.vm.rows).toEqual([expect.objectContaining({ username: 'bob', name: '鲍勃' })])
    expect(wrapper.vm.isPending(3)).toBe(false)
    expect(wrapper.vm.emptyText).toBeTruthy()

    wrapper.vm.keyword = 'bob'
    wrapper.vm.search()
    await flushPromises()
    expect(http.calls.at(-1).params).toEqual({ keyword: 'bob' })

    wrapper.vm.reset()
    await flushPromises()
    expect(wrapper.vm.keyword).toBe('')

    wrapper.vm.openCreate()
    expect(wrapper.vm.dialogVisible).toBe(true)
    expect(wrapper.vm.dialogTitle).toBe('新建用户')

    await wrapper.vm.openEdit(3)
    expect(wrapper.vm.form.username).toBe('bob')
    expect(wrapper.vm.form.deptId).toBe(11)
    expect(wrapper.vm.form.postIds).toEqual([301])
    expect(wrapper.vm.dialogTitle).toBe('编辑用户')

    wrapper.vm.form = { ...wrapper.vm.form, name: '鲍勃2' }
    await wrapper.vm.save()
    expect(sysUserApi.saveOrUpdate).toHaveBeenCalledWith(
      expect.objectContaining({ name: '鲍勃2', deptId: 11, postIds: [301] }),
    )
    expect(wrapper.vm.dialogVisible).toBe(false)

    await wrapper.vm.toggleStatus(3, true)
    expect(sysUserApi.updateStatus).toHaveBeenCalledWith(3, 0)
    await wrapper.vm.toggleStatus(3, false)
    expect(sysUserApi.updateStatus).toHaveBeenLastCalledWith(3, 1)

    await wrapper.vm.remove(3)
    expect(sysUserApi.removeById).toHaveBeenCalledWith(3)
  })

  it('filters by the left dept tree, resets to page one, and clears on a second click', async () => {
    const { pinia } = setupSession()
    const wrapper = mount(SysUser, realOptions(pinia, testRouter()))
    await flushPromises()

    // 左树带「全部」根节点；岗位选项来自用户页自己的只读端点。
    expect(wrapper.vm.deptTree[0]).toMatchObject({ id: 0, name: '全部' })
    expect(wrapper.vm.deptTree[0].children[0]).toMatchObject({ id: 10, name: '研发部' })
    expect(wrapper.vm.postOptions).toEqual([
      { id: 301, name: '经理' },
      { id: 302, name: '专员' },
    ])

    wrapper.vm.list.handlePageChange(2)
    await flushPromises()
    expect(wrapper.vm.view.page).toBe(2)

    wrapper.vm.handleDeptClick({ id: 10 })
    await flushPromises()
    expect(http.calls.at(-1).url).toMatch(/\/sysUser\/1\/10$/)
    expect(http.calls.at(-1).params).toEqual({ deptId: 10 })

    // 再点同一部门：清除过滤并回到全部。
    wrapper.vm.handleDeptClick({ id: 10 })
    await flushPromises()
    expect(http.calls.at(-1).params).toEqual({})
    expect(wrapper.vm.currentDeptId).toBe(0)

    // 重置按钮同时清部门与关键字。
    wrapper.vm.keyword = 'bob'
    wrapper.vm.handleDeptClick({ id: 10 })
    await flushPromises()
    wrapper.vm.reset()
    await flushPromises()
    expect(wrapper.vm.keyword).toBe('')
    expect(wrapper.vm.currentDeptId).toBe(0)
    expect(http.calls.at(-1).params).toEqual({})
  })

  it('exports with the current keyword, dept and status and downloads the blob', async () => {
    const { pinia } = setupSession({ permissions: ['system:user:export'] })
    const wrapper = mount(SysUser, realOptions(pinia, testRouter()))
    await flushPromises()

    const originalCreate = URL.createObjectURL
    const originalRevoke = URL.revokeObjectURL
    URL.createObjectURL = vi.fn(() => 'blob:export')
    URL.revokeObjectURL = vi.fn()
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    try {
      wrapper.vm.keyword = 'bob'
      wrapper.vm.statusFilter = 0
      wrapper.vm.handleDeptClick({ id: 10 })
      await flushPromises()

      await wrapper.vm.handleExport()

      expect(sysUserApi.exportUsers).toHaveBeenCalledWith({
        keyword: 'bob',
        deptId: 10,
        status: 0,
      })
      expect(URL.createObjectURL).toHaveBeenCalledTimes(1)
      expect(click).toHaveBeenCalledTimes(1)
    } finally {
      click.mockRestore()
      URL.createObjectURL = originalCreate
      URL.revokeObjectURL = originalRevoke
    }
  })

  it('shows the 导出 header action only with the export button point', async () => {
    const granted = setupSession({ permissions: ['system:user:export'] })
    const wrapper = mount(SysUser, realOptions(granted.pinia, testRouter()))
    await flushPromises()
    expect(wrapper.find('.page-actions').text()).toContain('导出')
    wrapper.unmount()

    const denied = setupSession({ permissions: [] })
    const deniedWrapper = mount(SysUser, realOptions(denied.pinia, testRouter()))
    await flushPromises()
    expect(deniedWrapper.find('.page-actions').text()).not.toContain('导出')
  })

  it('imports a xlsx file and renders the partial-success summary with failure rows', async () => {
    const { pinia } = setupSession({ permissions: ['system:user:import'] })
    const wrapper = mount(SysUser, realOptions(pinia, testRouter()))
    await flushPromises()

    const originalCreate = URL.createObjectURL
    const originalRevoke = URL.revokeObjectURL
    URL.createObjectURL = vi.fn(() => 'blob:import')
    URL.revokeObjectURL = vi.fn()
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    try {
      wrapper.vm.openImport()
      expect(wrapper.vm.importDialogVisible).toBe(true)

      await wrapper.vm.handleDownloadTemplate()
      expect(sysUserApi.downloadImportTemplate).toHaveBeenCalledTimes(1)
      expect(URL.createObjectURL).toHaveBeenCalledTimes(1)
      expect(click).toHaveBeenCalledTimes(1)

      // 本地预检与服务端同文案：csv、超 2MB 都在发请求前拒绝。
      wrapper.vm.handleImportFileChange({
        target: { files: [new File(['x'], 'users.csv')], value: 'users.csv' },
      })
      expect(wrapper.vm.importError).toBe('仅支持 xlsx 文件')
      expect(wrapper.vm.importFile).toBeNull()

      wrapper.vm.handleImportFileChange({
        target: {
          files: [new File([new Uint8Array(2 * 1024 * 1024 + 1)], 'big.xlsx')],
          value: 'big.xlsx',
        },
      })
      expect(wrapper.vm.importError).toBe('导入文件超过 2 MB 限制')
      expect(wrapper.vm.importFile).toBeNull()
      expect(sysUserApi.importUsers).not.toHaveBeenCalled()

      const file = new File(['PK'], 'users.xlsx')
      wrapper.vm.handleImportFileChange({ target: { files: [file], value: 'users.xlsx' } })
      expect(wrapper.vm.importFile).toBe(file)
      expect(wrapper.vm.importError).toBe('')

      const readsBefore = http.calls.length
      await wrapper.vm.submitImport()
      await flushPromises()

      expect(sysUserApi.importUsers).toHaveBeenCalledWith(file)
      expect(wrapper.vm.importResult).toEqual(
        expect.objectContaining({ total: 3, successCount: 2, failureCount: 1 }),
      )
      expect(wrapper.text()).toContain('成功 2 条，失败 1 条')
      expect(wrapper.text()).toContain('bob')
      expect(wrapper.text()).toContain('用户名已存在')
      // 有成功行时刷新列表，让新用户出现在当前页。
      expect(http.calls.length).toBeGreaterThan(readsBefore)
    } finally {
      click.mockRestore()
      URL.createObjectURL = originalCreate
      URL.revokeObjectURL = originalRevoke
    }
  })

  it('shows the 导入 header action only with the import button point', async () => {
    const granted = setupSession({ permissions: ['system:user:import'] })
    const wrapper = mount(SysUser, realOptions(granted.pinia, testRouter()))
    await flushPromises()
    expect(wrapper.find('.page-actions').text()).toContain('导入')
    wrapper.unmount()

    const denied = setupSession({ permissions: [] })
    const deniedWrapper = mount(SysUser, realOptions(denied.pinia, testRouter()))
    await flushPromises()
    expect(deniedWrapper.find('.page-actions').text()).not.toContain('导入')
  })

  it('blocks the create with local required messages before the request', async () => {
    const { pinia } = setupSession()
    const wrapper = mount(SysUser, realOptions(pinia, testRouter()))
    await flushPromises()

    wrapper.vm.openCreate()
    await flushPromises()
    await wrapper.vm.save()
    // Element Plus 用 100ms debounce 决定错误是否上屏，等过 debounce 再断言。
    await new Promise((resolve) => setTimeout(resolve, 150))
    await flushPromises()
    expect(sysUserApi.saveOrUpdate).not.toHaveBeenCalled()
    expect(wrapper.vm.dialogVisible).toBe(true)
    expect(wrapper.findAll('.el-form-item__error').map((node) => node.text())).toEqual([
      '用户名不能为空',
      '密码不能为空',
      '姓名不能为空',
    ])
  })

  it('shows the 重置密码 row action only with the resetPwd button point', async () => {
    const granted = setupSession({ permissions: ['system:user:resetPwd'] })
    const wrapper = mount(SysUser, realOptions(granted.pinia, testRouter()))
    await flushPromises()

    expect(
      wrapper
        .findAll('.el-table__row')[0]
        .findAll('button')
        .map((node) => node.text()),
    ).toContain('重置密码')
    wrapper.unmount()

    const denied = setupSession({ permissions: [] })
    const deniedWrapper = mount(SysUser, realOptions(denied.pinia, testRouter()))
    await flushPromises()

    expect(
      deniedWrapper
        .findAll('.el-table__row')[0]
        .findAll('button')
        .map((node) => node.text()),
    ).not.toContain('重置密码')
  })

  it('validates the reset dialog, submits the new password, closes and refreshes', async () => {
    const { pinia } = setupSession({ permissions: ['system:user:resetPwd'] })
    const wrapper = mount(SysUser, realOptions(pinia, testRouter()))
    await flushPromises()
    const listReads = () =>
      http.calls.filter((call) => /\/sysUser\/\d+\/\d+$/.test(call.url)).length
    const readsBefore = listReads()

    wrapper.vm.openResetPassword(3, 'bob')
    await flushPromises()
    expect(wrapper.vm.resetDialogVisible).toBe(true)
    expect(wrapper.vm.resetTarget).toEqual({ id: 3, username: 'bob' })
    const resetModel = () =>
      wrapper
        .findAllComponents({ name: 'ElForm' })
        .map((form) => form.props('model'))
        .find((model) => model && 'newPassword' in model)

    resetModel().newPassword = '12345'
    resetModel().confirmPassword = '12345'
    await flushPromises()
    await wrapper.vm.saveResetPassword()
    await new Promise((resolve) => setTimeout(resolve, 150))
    await flushPromises()
    expect(sysUserApi.resetPassword).not.toHaveBeenCalled()
    expect(wrapper.findAll('.el-form-item__error').map((node) => node.text())).toContain(
      '密码长度必须在 6 到 100 位之间',
    )

    resetModel().newPassword = '123456'
    resetModel().confirmPassword = '654321'
    await flushPromises()
    await wrapper.vm.saveResetPassword()
    await new Promise((resolve) => setTimeout(resolve, 150))
    await flushPromises()
    expect(sysUserApi.resetPassword).not.toHaveBeenCalled()
    expect(wrapper.findAll('.el-form-item__error').map((node) => node.text())).toContain(
      '两次输入的密码不一致',
    )

    resetModel().confirmPassword = '123456'
    await flushPromises()
    await wrapper.vm.saveResetPassword()
    await flushPromises()

    expect(sysUserApi.resetPassword).toHaveBeenCalledWith(3, '123456')
    expect(wrapper.vm.resetDialogVisible).toBe(false)
    expect(listReads()).toBeGreaterThan(readsBefore)
    expect(messageMock).toHaveBeenCalledWith(
      expect.objectContaining({ type: 'success', message: '重置密码成功' }),
    )
  })

  it('opens, reopens, and closes the assign-roles dialog', async () => {
    const { pinia, user } = setupSession()
    const wrapper = mount(SysUser, realOptions(pinia, testRouter()))
    await flushPromises()

    await wrapper.vm.openAssignRoles(3, 'bob')
    await flushPromises()
    expect(wrapper.vm.assignRolesVisible).toBe(true)
    expect(wrapper.vm.assignUser).toEqual({ id: 3, username: 'bob' })
    expect(http.calls.some((call) => String(call.url).includes('/sysRole/toAssign/3'))).toBe(true)

    await wrapper.vm.openAssignRoles(4, 'carol')
    await flushPromises()
    expect(wrapper.vm.assignUser).toEqual({ id: 4, username: 'carol' })

    user.token = 'rotated'
    await flushPromises()

    wrapper.vm.assignRolesVisible = false
    await flushPromises()
    expect(wrapper.vm.assignUser).toEqual({ id: 4, username: 'carol' })
  })

  it('surfaces pending assignments from the module', async () => {
    const { pinia } = setupSession()
    const wrapper = mount(SysUser, realOptions(pinia, testRouter()))
    await flushPromises()

    const dialog = wrapper.findComponent(AssignRolesDialog)
    expect(dialog.exists()).toBe(true)
    expect(dialog.props('modelValue')).toBe(false)

    wrapper.unmount()
  })
})

describe('AssignRolesDialog', () => {
  it('opens a lease for the user, selects roles, and closes on leave', async () => {
    const { pinia } = setupSession()
    const wrapper = mount(AssignRolesDialog, {
      props: { modelValue: true, user: { id: 3, username: 'bob' } },
      ...realOptions(pinia, testRouter()),
    })
    await flushPromises()

    expect(http.calls.some((call) => String(call.url).includes('/sysRole/toAssign/3'))).toBe(true)

    wrapper.vm.selectRoles([2])
    await flushPromises()
    expect(wrapper.vm.selectedRoleIds).toContain(2)

    await wrapper.vm.requestLeave()
    expect(wrapper.emitted('update:modelValue')[0]).toEqual([false])
  })

  it('closes without a lease when nothing is open', async () => {
    const { pinia } = setupSession()
    const wrapper = mount(AssignRolesDialog, {
      props: { modelValue: false, user: null },
      ...realOptions(pinia, testRouter()),
    })

    await expect(wrapper.vm.requestLeave()).resolves.toBe(true)
    expect(wrapper.emitted('update:modelValue')).toEqual([[false]])
  })
})

describe('AssignAuth page', () => {
  function treeStub() {
    return {
      template: '<div />',
      methods: {
        setCheckedKeys() {},
        getCheckedNodes() {
          return [{ id: 2 }, { id: 21 }]
        },
      },
    }
  }

  it('reads the granted menus, keeps their buttons, and saves every checked id', async () => {
    const { pinia, recognition } = setupSession({
      recognition: fakeRecognition({ resume: vi.fn(async () => {}) }),
    })
    const router = testRouter()
    await router.push({ path: PATHS.SYSTEM_ASSIGN_AUTH, query: { id: '3', roleName: '审批人' } })
    await router.isReady()
    const success = vi.fn()

    const options = mountOptions(pinia, router)
    options.global.stubs = { ...elementStubs, 'el-tree': treeStub() }
    options.global.mocks = { $message: { success } }
    const wrapper = mount(AssignAuth, options)
    await flushPromises()

    expect(wrapper.vm.roleName).toBe('审批人')
    expect(sysMenuApi.getByIdForAssign).toHaveBeenCalledWith('3')
    expect(wrapper.vm.fullTree).toHaveLength(1)
    expect(wrapper.vm.menuTree).toHaveLength(1)
    expect(wrapper.vm.menuTree[0].children[0].children[0]).toMatchObject({
      id: 21,
      type: 2,
      perms: 'system:user:add',
    })

    wrapper.vm.save()
    await flushPromises()

    expect(sysMenuApi.doAssign).toHaveBeenCalledWith('3', [2, 21])
    expect(success).toHaveBeenCalledWith('保存成功')
    expect(recognition.resume).toHaveBeenCalled()
    expect(wrapper.vm.saving).toBe(false)
  })

  it('does nothing without a role id in the route', async () => {
    const { pinia } = setupSession()
    const router = testRouter()
    await router.push(PATHS.SYSTEM_ASSIGN_AUTH)
    await router.isReady()

    const options = mountOptions(pinia, router)
    options.global.stubs = { ...elementStubs, 'el-tree': treeStub() }
    const wrapper = mount(AssignAuth, options)
    await flushPromises()

    wrapper.vm.save()
    await flushPromises()

    expect(sysMenuApi.getByIdForAssign).not.toHaveBeenCalled()
    expect(sysMenuApi.doAssign).not.toHaveBeenCalled()
    expect(wrapper.vm.saving).toBe(false)
  })
})
