import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { ref, shallowRef } from 'vue'

const { holder } = vi.hoisted(() => ({ holder: {} }))

vi.mock('@/config/assign-roles-browser', () => ({
  useAssignRoles: () => holder.module,
}))

import AssignRolesDialog from '@/views/config/sysUser/AssignRolesDialog.vue'

const buttonStub = {
  name: 'ElButton',
  props: ['disabled', 'loading', 'type'],
  template: '<button :disabled="disabled"><slot /></button>',
}
const dialogStub = {
  name: 'ElDialog',
  props: ['modelValue'],
  template: '<div><slot /><slot name="footer" /></div>',
}
const checkboxGroupStub = {
  name: 'ElCheckboxGroup',
  props: ['modelValue', 'disabled'],
  emits: ['update:model-value'],
  template: '<div class="group" :data-disabled="disabled"><slot /></div>',
}
const checkboxStub = {
  name: 'ElCheckbox',
  props: ['value'],
  template: '<label class="role"><slot /></label>',
}
const stubs = {
  'el-button': buttonStub,
  'el-dialog': dialogStub,
  'el-checkbox': checkboxStub,
  'el-checkbox-group': checkboxGroupStub,
}

function view(overrides = {}) {
  return {
    userId: 3,
    username: 'bob',
    phase: 'ready',
    roles: [],
    selectedRoleIds: [],
    submittedRoleIds: [],
    submittedRoles: [],
    latest: null,
    reading: false,
    error: '',
    canSelect: true,
    canSave: true,
    canReRead: true,
    canEnd: true,
    ...overrides,
  }
}

function makeModule(initial = view()) {
  const viewRef = shallowRef(initial)
  const released = ref(false)
  const lease = {
    view: viewRef,
    released,
    select: vi.fn(),
    save: vi.fn(),
    reload: vi.fn(),
    endAssignment: vi.fn(),
    requestLeave: vi.fn(async () => true),
    environmentChanged: vi.fn(async () => {}),
    dispose: vi.fn(),
  }
  return { lease, released, view: viewRef, open: vi.fn(() => lease) }
}

function mountDialog(props = { modelValue: true, user: { id: 3, username: 'bob' } }) {
  return mount(AssignRolesDialog, {
    props,
    global: { stubs },
  })
}

beforeEach(() => {
  holder.module = makeModule()
})

describe('AssignRolesDialog phases', () => {
  it('renders the opening state before a lease exists', () => {
    const wrapper = mountDialog({ modelValue: false, user: null })
    expect(wrapper.text()).toContain('正在打开分配角色…')
  })

  it('renders the loading phase', () => {
    holder.module = makeModule(view({ phase: 'loading' }))
    const wrapper = mountDialog()

    expect(wrapper.text()).toContain('正在读取角色…')
  })

  it('renders a read error with a retry action', async () => {
    holder.module = makeModule(view({ phase: 'read-error', error: '读取失败' }))
    const wrapper = mountDialog()
    await flushPromises()

    expect(wrapper.text()).toContain('读取失败')
    const retry = wrapper.findAll('button').find((button) => button.text() === '重试读取')
    await retry.trigger('click')
    expect(holder.module.lease.reload).toHaveBeenCalled()
  })

  it('renders the unconfirmed panel with empty submitted roles and no latest read', async () => {
    holder.module = makeModule(
      view({ phase: 'unconfirmed', canReRead: false, canEnd: false, error: '网络中断' }),
    )
    const wrapper = mountDialog()
    await flushPromises()

    expect(wrapper.text()).toContain('分配结果待确认')
    expect(wrapper.text()).toContain('空角色列表')
    expect(wrapper.text()).toContain('尚未重新读取。')
    expect(wrapper.text()).toContain('网络中断')

    const end = wrapper.findAll('button').find((button) => button.text() === '结束本次分配')
    expect(end.attributes('disabled')).toBeDefined()

    const reread = wrapper.findAll('button').find((button) => button.text() === '重新读取当前角色')
    expect(reread.attributes('disabled')).toBeDefined()
  })

  it('renders submitted and latest roles with the reread action', async () => {
    holder.module = makeModule(
      view({
        phase: 'unconfirmed',
        submittedRoles: [{ id: 1, roleName: '审批人' }],
        latest: {
          roleIds: [1, 9],
          roles: [{ id: 1, roleName: '审批人' }],
          processTemplateId: null,
          assignedRoleIds: [1, 9],
        },
      }),
    )
    const wrapper = mountDialog()
    await flushPromises()

    expect(wrapper.text()).toContain('审批人')
    expect(wrapper.text()).toContain('9')
    const reread = wrapper.findAll('button').find((button) => button.text() === '重新读取当前角色')
    expect(reread.attributes('disabled')).toBeUndefined()
    await reread.trigger('click')
    expect(holder.module.lease.reload).toHaveBeenCalled()
  })

  it('renders the empty role picker and disables saving', () => {
    holder.module = makeModule(view({ canSave: false }))
    const wrapper = mountDialog()

    expect(wrapper.text()).toContain('当前没有可分配的角色')
    const save = wrapper.findAll('button').find((button) => button.text() === '保存')
    expect(save.attributes('disabled')).toBeDefined()
  })

  it('renders selectable roles and forwards selection and saving', async () => {
    holder.module = makeModule(
      view({
        roles: [
          { id: 1, roleName: '审批人' },
          { id: 2, roleName: '办事员' },
        ],
        selectedRoleIds: [1],
      }),
    )
    const wrapper = mountDialog()

    expect(wrapper.text()).toContain('审批人')
    expect(wrapper.findAll('.role')).toHaveLength(2)

    wrapper.findComponent(checkboxGroupStub).vm.$emit('update:model-value', [2])
    expect(holder.module.lease.select).toHaveBeenCalledWith([2])

    const save = wrapper.findAll('button').find((button) => button.text() === '保存')
    await save.trigger('click')
    expect(holder.module.lease.save).toHaveBeenCalled()
  })

  it('shows the saving status and an assign error', () => {
    holder.module = makeModule(view({ phase: 'saving', error: '保存被拒绝', canSave: false }))
    const wrapper = mountDialog()

    expect(wrapper.text()).toContain('正在保存角色分配')
    expect(wrapper.text()).toContain('保存被拒绝')
    const save = wrapper.findAll('button').find((button) => button.text() === '保存')
    expect(save.attributes('disabled')).toBeDefined()
  })

  it('closes through requestLeave or the update handler', async () => {
    holder.module = makeModule()
    const wrapper = mountDialog()

    await wrapper.vm.requestLeave()
    expect(wrapper.emitted('update:modelValue')).toContainEqual([false])

    const close = wrapper.findAll('button').find((button) => button.text() === '取消')
    await close.trigger('click')
    await flushPromises()
    expect(holder.module.lease.requestLeave).toHaveBeenCalled()
  })

  it('closes when the lease releases or reaches a terminal phase', async () => {
    holder.module = makeModule()
    mountDialog()
    await flushPromises()

    holder.module.released.value = true
    await flushPromises()
    expect(holder.module.lease.dispose).toHaveBeenCalled()

    const second = makeModule()
    holder.module = second
    const reopened = mountDialog()
    await flushPromises()
    second.view.value = view({ phase: 'saved' })
    await flushPromises()
    expect(second.lease.dispose).toHaveBeenCalled()

    expect(reopened.emitted('update:modelValue')).toContainEqual([false])
  })

  it('forwards environment changes and disposes on unmount', async () => {
    const wrapper = mountDialog()
    await flushPromises()

    wrapper.vm.environmentChanged()
    expect(holder.module.lease.environmentChanged).toHaveBeenCalled()

    wrapper.unmount()
    expect(holder.module.lease.dispose).toHaveBeenCalled()
  })
})
