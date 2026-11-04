import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { ref, shallowRef } from 'vue'

const { holder } = vi.hoisted(() => ({ holder: {} }))

vi.mock('@/config/role-users-browser', () => ({
  useRoleUsers: () => holder.module,
}))

import RoleUsersDrawer from '@/views/config/sysRole/RoleUsersDrawer.vue'

const buttonStub = {
  name: 'ElButton',
  props: ['disabled', 'link', 'type'],
  emits: ['click'],
  template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>',
}
const drawerStub = {
  name: 'ElDrawer',
  props: ['modelValue', 'title', 'size'],
  template: '<div><slot /><slot name="footer" /></div>',
}
const inputStub = {
  name: 'ElInput',
  props: ['modelValue', 'disabled'],
  emits: ['update:modelValue', 'keyup'],
  template:
    '<input :value="modelValue" :disabled="disabled" @input="$emit(\'update:modelValue\', $event.target.value)" />',
}
const tagStub = {
  name: 'ElTag',
  template: '<span class="tag"><slot /></span>',
}
const stubs = {
  'el-drawer': drawerStub,
  'el-input': inputStub,
  'el-button': buttonStub,
  'el-tag': tagStub,
}

function view(overrides = {}) {
  return {
    roleId: 7,
    roleName: '审批人',
    phase: 'ready',
    members: [{ id: 11, username: 'alice', name: '爱丽丝' }],
    candidates: [{ id: 13, username: 'carol', name: '卡罗尔' }],
    keyword: '',
    page: 1,
    size: 10,
    total: 1,
    submittedUserIds: [],
    submittedUsers: [],
    latest: null,
    reading: false,
    error: '',
    canSearch: true,
    canMove: true,
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
    add: vi.fn(() => true),
    remove: vi.fn(() => true),
    search: vi.fn(async () => {}),
    goPage: vi.fn(async () => {}),
    save: vi.fn(async () => {}),
    reload: vi.fn(async () => {}),
    endAssignment: vi.fn(async () => true),
    requestLeave: vi.fn(async () => true),
    environmentChanged: vi.fn(async () => {}),
    dispose: vi.fn(),
  }
  return { lease, released, view: viewRef, open: vi.fn(() => lease) }
}

function mountDrawer(props = {}) {
  return mount(RoleUsersDrawer, {
    props: {
      modelValue: true,
      role: { id: 7, roleName: '审批人' },
      ...props,
    },
    global: { stubs },
  })
}

beforeEach(() => {
  holder.module = makeModule()
})

describe('RoleUsersDrawer 双列表', () => {
  it('renders the opening state before a lease exists', () => {
    const wrapper = mountDrawer({ modelValue: false, role: null })
    expect(wrapper.text()).toContain('正在打开分配用户…')
  })

  it('renders both sides and wires add, remove, search and save', async () => {
    const wrapper = mountDrawer()

    expect(wrapper.text()).toContain('未选用户')
    expect(wrapper.text()).toContain('已选用户（1）')
    expect(wrapper.text()).toContain('alice（爱丽丝）')
    expect(wrapper.text()).toContain('carol（卡罗尔）')

    const add = wrapper.findAll('button').find((button) => button.text() === '添加')
    await add?.trigger('click')
    expect(holder.module.lease.add).toHaveBeenCalledWith([13])

    const remove = wrapper.findAll('button').find((button) => button.text() === '移除')
    await remove?.trigger('click')
    expect(holder.module.lease.remove).toHaveBeenCalledWith([11])

    const input = wrapper.find('input')
    await input.setValue('car')
    const search = wrapper.findAll('button').find((button) => button.text() === '搜索')
    await search?.trigger('click')
    expect(holder.module.lease.search).toHaveBeenCalledWith('car')

    const save = wrapper.findAll('button').find((button) => button.text() === '保存')
    await save?.trigger('click')
    expect(holder.module.lease.save).toHaveBeenCalledTimes(1)
  })

  it('renders the pending panel with submitted and latest members', async () => {
    holder.module = makeModule(
      view({
        phase: 'unconfirmed',
        members: [{ id: 11, username: 'alice' }],
        submittedUsers: [{ id: 11, username: 'alice' }],
        submittedUserIds: [11],
        canSave: false,
        canMove: false,
        canSearch: false,
        latest: { members: [{ id: 12, username: 'bob' }] },
      }),
    )
    const wrapper = mountDrawer()

    expect(wrapper.text()).toContain('分配结果待确认')
    expect(wrapper.text()).toContain('本次提交')
    expect(wrapper.text()).toContain('最新已保存成员')
    expect(wrapper.text()).toContain('bob')

    const reread = wrapper.findAll('button').find((button) => button.text() === '重新读取当前成员')
    await reread?.trigger('click')
    expect(holder.module.lease.reload).toHaveBeenCalledTimes(1)

    const end = wrapper.findAll('button').find((button) => button.text() === '结束本次分配')
    await end?.trigger('click')
    expect(holder.module.lease.endAssignment).toHaveBeenCalledTimes(1)
  })

  it('shows the read error with a retry command and disables save', async () => {
    holder.module = makeModule(
      view({ phase: 'read-error', error: '成员与用户读取失败，请重试', canSave: false }),
    )
    const wrapper = mountDrawer()

    expect(wrapper.text()).toContain('成员与用户读取失败，请重试')
    const retry = wrapper.findAll('button').find((button) => button.text() === '重试读取')
    await retry?.trigger('click')
    expect(holder.module.lease.reload).toHaveBeenCalledTimes(1)
    const save = wrapper.findAll('button').find((button) => button.text() === '保存')
    expect(save?.attributes('disabled')).toBeDefined()
  })
})
