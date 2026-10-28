import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { computed, reactive, ref } from 'vue'

const { holder } = vi.hoisted(() => ({ holder: {} }))

vi.mock('@/work/approval-start-browser', () => ({
  useApprovalStart: () => holder.start,
}))

import ApprovalStartAttachments from '@/work/ApprovalStartAttachments.vue'
import { PATHS } from '@/router/paths'
import Apply from '@/views/work/start/apply.vue'
import { createTestRouter, mountOptions, setupSession } from '../support/harness'

const buttonStub = {
  name: 'ElButton',
  props: ['disabled', 'link', 'type'],
  template: '<button :disabled="disabled"><slot /></button>',
}

function viewState(overrides = {}) {
  return reactive({
    template: null,
    recipients: [],
    attachments: [],
    interrupted: [],
    loading: false,
    reading: false,
    uploading: 0,
    deleting: [],
    attachmentsReady: true,
    locked: false,
    attachmentLocked: false,
    phase: 'editing',
    error: '',
    ...overrides,
  })
}

function makeStart(overrides = {}) {
  const state = viewState(overrides)
  return {
    state,
    view: computed(() => state),
    formData: ref({}),
    ccUserIds: ref([]),
    rule: ref([]),
    option: computed(() => ({ form: {}, submitBtn: {} })),
    submit: vi.fn(),
    reload: vi.fn(),
    add: vi.fn(),
    replaceUnavailable: vi.fn(),
    retryInterrupted: vi.fn(),
    remove: vi.fn(),
    omitUnavailable: vi.fn(),
    omitInterrupted: vi.fn(),
    finishFoundApproval: vi.fn(),
    prepareStartAgain: vi.fn(),
  }
}

function applyRouter() {
  return createTestRouter([
    { path: PATHS.WORK_START, component: { template: '<div />' } },
    { path: PATHS.WORK_STARTED, component: { template: '<div />' } },
    { path: '/work/start/:processTemplateId', component: Apply },
  ])
}

async function mountApply() {
  const { pinia } = setupSession()
  const router = applyRouter()
  await router.push('/work/start/5')
  await router.isReady()
  const options = mountOptions(pinia, router)
  options.global.stubs = { ...options.global.stubs, 'el-button': buttonStub }
  const wrapper = mount(Apply, options)
  await flushPromises()
  return { wrapper, router }
}

beforeEach(() => {
  holder.start = makeStart()
})

describe('Apply states', () => {
  it('falls back to the generic title without a template', async () => {
    const { wrapper } = await mountApply()

    expect(wrapper.text()).toContain('发起审批')
    expect(wrapper.findComponent(ApprovalStartAttachments).exists()).toBe(false)
  })

  it('shows a retry for an editing error', async () => {
    holder.start.state.error = '模板读取失败'
    const { wrapper } = await mountApply()

    expect(wrapper.vm.view.error).toBe('模板读取失败')
    const retry = wrapper.findAll('button').find((button) => button.text() === '重试读取')
    await retry.trigger('click')
    expect(holder.start.reload).toHaveBeenCalled()
  })

  it('renders the template and its recipients', async () => {
    holder.start.state.template = { id: 5, name: '请假模板', rule: [], option: {} }
    holder.start.state.recipients = [{ id: 9, name: '卡罗尔' }]
    const { wrapper } = await mountApply()

    expect(wrapper.text()).toContain('请假模板')
    expect(wrapper.vm.view.recipients).toHaveLength(1)
    expect(wrapper.findComponent(ApprovalStartAttachments).exists()).toBe(true)
  })

  it('renders the unconfirmed section and its verification actions', async () => {
    holder.start.state.template = { id: 5, name: '请假模板', rule: [], option: {} }
    holder.start.state.phase = 'unconfirmed'
    const { wrapper } = await mountApply()

    expect(wrapper.text()).toContain('发起结果待确认')

    const finished = wrapper
      .findAll('button')
      .find((button) => button.text() === '已找到，结束本次')
    await finished.trigger('click')
    expect(holder.start.finishFoundApproval).toHaveBeenCalled()

    const again = wrapper.findAll('button').find((button) => button.text() === '仍未找到，重新发起')
    await again.trigger('click')
    expect(holder.start.prepareStartAgain).toHaveBeenCalled()
  })

  it('renders the completed section and returns to the start list', async () => {
    holder.start.state.template = { id: 5, name: '请假模板', rule: [], option: {} }
    holder.start.state.phase = 'completed'
    const { wrapper, router } = await mountApply()

    expect(wrapper.text()).toContain('本次已发起')

    wrapper.vm.goBack()
    await flushPromises()
    expect(router.currentRoute.value.path).toBe(PATHS.WORK_START)
  })
})

describe('ApprovalStartAttachments states', () => {
  function attachmentStart(overrides = {}) {
    const state = viewState({ attachmentsReady: true, ...overrides })
    return {
      state,
      view: computed(() => state),
      add: vi.fn(),
      replaceUnavailable: vi.fn(),
      retryInterrupted: vi.fn(),
      remove: vi.fn(),
      reload: vi.fn(),
      omitUnavailable: vi.fn(),
      omitInterrupted: vi.fn(),
    }
  }

  it('renders reading, not-ready, and empty states', async () => {
    const reading = attachmentStart({ reading: true })
    const first = mount(ApprovalStartAttachments, { props: { start: reading } })
    expect(first.text()).toContain('正在读取附件…')

    const notReady = attachmentStart({ attachmentsReady: false })
    const second = mount(ApprovalStartAttachments, { props: { start: notReady } })
    expect(second.text()).toContain('附件读取未完成')
    await second.find('button').trigger('click')
    expect(notReady.reload).toHaveBeenCalled()

    const empty = attachmentStart()
    const third = mount(ApprovalStartAttachments, { props: { start: empty } })
    expect(third.text()).toContain('暂无附件')
  })

  it('renders available attachments with formatted sizes and removes them', async () => {
    const start = attachmentStart({
      attachments: [
        { id: 1, fileName: 'a.pdf', availability: 'available', sizeBytes: 512 },
        { id: 2, fileName: 'b.pdf', availability: 'available', sizeBytes: 2048 },
      ],
    })
    const wrapper = mount(ApprovalStartAttachments, { props: { start } })

    expect(wrapper.text()).toContain('a.pdf')
    expect(wrapper.text()).toContain('512 B')
    expect(wrapper.text()).toContain('2.0 KB')

    const remove = wrapper.findAll('button').find((button) => button.text() === '删除')
    await remove.trigger('click')
    expect(start.remove).toHaveBeenCalledWith(1)
  })

  it('renders unavailable and interrupted attachments with their recovery actions', async () => {
    const start = attachmentStart({
      attachments: [{ id: 3, fileName: 'c.pdf', availability: 'unavailable', sizeBytes: 1 }],
      interrupted: [{ id: 'i-1', name: 'd.pdf' }],
    })
    const wrapper = mount(ApprovalStartAttachments, { props: { start } })

    expect(wrapper.text()).toContain('已不可用于本次发起')
    expect(wrapper.text()).toContain('d.pdf')

    const omit = wrapper.findAll('button').find((button) => button.text() === '不再附加')
    await omit.trigger('click')
    expect(start.omitUnavailable).toHaveBeenCalledWith(3)

    const ignore = wrapper.findAll('button').find((button) => button.text() === '忽略未完成文件')
    await ignore.trigger('click')
    expect(start.omitInterrupted).toHaveBeenCalledWith('i-1')
  })

  it('forwards picked files to the module', async () => {
    const start = attachmentStart()
    const wrapper = mount(ApprovalStartAttachments, { props: { start } })
    const file = new File(['x'], 'x.pdf')

    wrapper.vm.onFilesSelected({ currentTarget: { files: [file], value: '' } })
    expect(start.add).toHaveBeenCalledWith([file])

    wrapper.vm.replaceUnavailable({ currentTarget: { files: [file], value: '' } }, 3)
    expect(start.replaceUnavailable).toHaveBeenCalledWith(3, [file])

    wrapper.vm.retryInterrupted({ currentTarget: { files: [file], value: '' } }, 'i-1')
    expect(start.retryInterrupted).toHaveBeenCalledWith('i-1', [file])

    expect(wrapper.vm.formatSize(512)).toBe('512 B')
    expect(wrapper.vm.formatSize(2048)).toBe('2.0 KB')
    expect(wrapper.vm.formatSize(2097152)).toBe('2.0 MB')
  })
})
