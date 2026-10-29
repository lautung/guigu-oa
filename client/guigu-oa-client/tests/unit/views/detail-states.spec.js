import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { reactive } from 'vue'

const { holder } = vi.hoisted(() => ({ holder: {} }))

vi.mock('@/work/approval-detail', () => ({ createApprovalDetail: () => holder.module }))
vi.mock('@/work/approval-detail-http', () => ({ createHttpDetailAdapter: () => ({}) }))

import { PATHS, WORK_LIST_KINDS } from '@/router/paths'
import PendingDetail from '@/views/work/pending/detail.vue'
import { XSS_PAYLOAD, createTestRouter, mountOptions, setupSession } from '../support/harness'

const buttonStub = {
  name: 'ElButton',
  props: ['disabled', 'type', 'link'],
  template: '<button :disabled="disabled"><slot /></button>',
}
const alertStub = {
  name: 'ElAlert',
  props: ['title', 'description', 'type'],
  template: '<div class="alert">{{ title }} {{ description }}<slot /></div>',
}
const tagStub = { name: 'ElTag', template: '<span class="tag"><slot /></span>' }

function state(overrides = {}) {
  return reactive({
    loading: false,
    detail: {
      title: '张三的请假',
      statusLabel: '审批中',
      statusTagType: 'warning',
      createTime: '2026-01-01',
      progressLabel: '流转中',
      recordsFirst: false,
      formFields: [],
      records: [
        { operateUser: '张三', description: '提交申请', createTime: '2026-01-01', status: 1 },
      ],
    },
    canSubmit: false,
    error: '',
    stale: false,
    submitting: false,
    actionError: '',
    recordError: '',
    recoveries: [],
    ended: false,
    attachments: [],
    attachmentLoading: false,
    attachmentBusy: false,
    attachmentError: '',
    attachmentUploadAllowed: false,
    canAddAttachments: false,
    downloadingId: null,
    returnPath: '/work/pending',
    ...overrides,
  })
}

function makeOpening(overrides = {}) {
  const stateRef = state(overrides)
  return {
    state: stateRef,
    release: vi.fn(),
    back: vi.fn(),
    reload: vi.fn(),
    addAttachments: vi.fn(),
    canEndRecovery: vi.fn(() => true),
    endRecovery: vi.fn(),
    viewProcessed: vi.fn(),
    returnPending: vi.fn(),
  }
}

function detailRouter() {
  return createTestRouter([
    {
      path: '/work/pending/:id/:taskId',
      component: PendingDetail,
      meta: { workListKind: WORK_LIST_KINDS.PENDING },
    },
    { path: PATHS.WORK_PENDING, component: { template: '<div />' } },
    { path: PATHS.WORK_PROCESSED, component: { template: '<div />' } },
  ])
}

async function mountDetail() {
  const { pinia } = setupSession()
  const router = detailRouter()
  await router.push('/work/pending/p-1/t-1')
  await router.isReady()
  const options = mountOptions(pinia, router)
  options.global.stubs = {
    ...options.global.stubs,
    'el-button': buttonStub,
    'el-alert': alertStub,
    'el-tag': tagStub,
  }
  const wrapper = mount(PendingDetail, options)
  await flushPromises()
  return wrapper
}

beforeEach(() => {
  holder.opening = makeOpening()
  holder.module = { open: vi.fn(() => holder.opening) }
})

describe('PendingDetail states', () => {
  it('renders the loaded detail with records', async () => {
    const wrapper = await mountDetail()

    expect(wrapper.text()).toContain('张三的请假')
    expect(wrapper.text()).toContain('审批中')
    expect(wrapper.text()).toContain('流转中')
    expect(wrapper.text()).toContain('张三')
    expect(wrapper.vm.detail.records[0].description).toBe('提交申请')
  })

  it('shows a form field value that looks like HTML as text, never as markup', async () => {
    holder.opening.state.detail.formFields = [{ key: '请假原因', value: XSS_PAYLOAD }]
    const wrapper = await mountDetail()

    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.text()).toContain(XSS_PAYLOAD)
  })

  it('renders loading and disables the refresh button', async () => {
    holder.opening.state.loading = true
    const wrapper = await mountDetail()

    expect(wrapper.text()).toContain('正在读取详情…')
    const refresh = wrapper.findAll('button').find((button) => button.text() === '刷新详情')
    expect(refresh.attributes('disabled')).toBeDefined()
  })

  it('distinguishes a failed read from a stale read', async () => {
    holder.opening.state.error = '网络故障'
    const wrapper = await mountDetail()
    expect(wrapper.text()).toContain('详情读取失败')

    holder.opening.state.stale = true
    await flushPromises()
    expect(wrapper.text()).toContain('详情尚未更新')
  })

  it('renders record errors and the submitting notice', async () => {
    holder.opening.state.recordError = '记录损坏'
    const wrapper = await mountDetail()
    expect(wrapper.text()).toContain('处理恢复记录故障，已暂停新的处理')

    holder.opening.state.submitting = true
    await flushPromises()
    expect(wrapper.text()).toContain('正在提交处理')
  })

  it('renders recovery records and lets the user end a verifiable one', async () => {
    holder.opening.state.recoveries = [
      { operationId: 'op-1', processId: 'p-1', taskId: 't-1', status: 1 },
      { operationId: 'op-2', processId: 'p-1', taskId: 't-1', status: 0 },
    ]
    const wrapper = await mountDetail()

    expect(wrapper.text()).toContain('处理结果待确认')
    expect(wrapper.text()).toContain('所选动作：通过')
    expect(wrapper.text()).toContain('所选动作：拒绝')

    const end = wrapper
      .findAll('button')
      .find((button) => button.text() === '我已核实，结束本次记录')
    await end.trigger('click')
    expect(holder.opening.endRecovery).toHaveBeenCalledWith('op-1')

    const processed = wrapper.findAll('button').find((button) => button.text() === '查看已处理')
    await processed.trigger('click')
    expect(holder.opening.viewProcessed).toHaveBeenCalled()

    const pending = wrapper.findAll('button').find((button) => button.text() === '返回待处理')
    await pending.trigger('click')
    expect(holder.opening.returnPending).toHaveBeenCalled()
  })

  it('hides the end action when the record is no longer verifiable', async () => {
    holder.opening.canEndRecovery.mockReturnValue(false)
    holder.opening.state.recoveries = [
      { operationId: 'op-1', processId: 'p-1', taskId: 't-1', status: 1 },
    ]
    const wrapper = await mountDetail()

    expect(
      wrapper.findAll('button').some((button) => button.text() === '我已核实，结束本次记录'),
    ).toBe(false)
  })

  it('renders action errors and the ended hint', async () => {
    holder.opening.state.actionError = '提交失败'
    holder.opening.state.ended = true
    const wrapper = await mountDetail()

    expect(wrapper.text()).toContain('提交失败')
    expect(wrapper.text()).toContain('返回最新待处理')
  })

  it('drives the toolbar and attachment input through the opening', async () => {
    const wrapper = await mountDetail()

    const back = wrapper.findAll('button').find((button) => button.text() === '返回')
    await back.trigger('click')
    expect(holder.opening.back).toHaveBeenCalled()

    const refresh = wrapper.findAll('button').find((button) => button.text() === '刷新详情')
    await refresh.trigger('click')
    expect(holder.opening.reload).toHaveBeenCalled()

    wrapper.vm.addFiles({ currentTarget: { files: [], value: '' } })
    expect(holder.opening.addAttachments).toHaveBeenCalledWith([])

    wrapper.unmount()
    expect(holder.opening.release).toHaveBeenCalled()
  })
})
