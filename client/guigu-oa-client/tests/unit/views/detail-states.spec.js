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
      perspective: { kind: 'pending' },
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
    showWithdraw: false,
    canWithdraw: false,
    withdrawing: false,
    showUrge: false,
    canUrge: false,
    urgeBusy: false,
    urgeDone: false,
    showTransfer: false,
    canTransfer: false,
    transferring: false,
    showAddSign: false,
    canAddSign: false,
    addingSign: false,
    actionTargets: [],
    actionTargetsLoading: false,
    actionTargetsError: '',
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
    withdraw: vi.fn(),
    urge: vi.fn(),
    transfer: vi.fn(),
    addSign: vi.fn(),
    loadActionTargets: vi.fn(),
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

  it('renders applicant actions only in started contexts and drives them through the opening', async () => {
    holder.opening.state.showWithdraw = true
    holder.opening.state.canWithdraw = true
    holder.opening.state.showUrge = true
    holder.opening.state.canUrge = true
    const wrapper = await mountDetail()

    const withdraw = wrapper.findAll('button').find((button) => button.text() === '撤回审批')
    expect(withdraw).toBeDefined()
    expect(withdraw.attributes('disabled')).toBeUndefined()
    await withdraw.trigger('click')
    expect(holder.opening.withdraw).toHaveBeenCalled()

    const urge = wrapper.findAll('button').find((button) => button.text() === '催办')
    expect(urge.attributes('disabled')).toBeUndefined()
    await urge.trigger('click')
    expect(holder.opening.urge).toHaveBeenCalled()
  })

  it('hides applicant actions in pending and disables them with the urge-done hint', async () => {
    const pending = await mountDetail()
    expect(pending.findAll('button').some((button) => button.text() === '撤回审批')).toBe(false)
    expect(pending.findAll('button').some((button) => button.text() === '催办')).toBe(false)
    pending.unmount()

    holder.opening = makeOpening()
    holder.opening.state.showWithdraw = true
    holder.opening.state.showUrge = true
    holder.opening.state.canWithdraw = false
    holder.opening.state.canUrge = false
    holder.opening.state.urgeDone = true
    const wrapper = await mountDetail()
    expect(
      wrapper
        .findAll('button')
        .find((button) => button.text() === '撤回审批')
        .attributes('disabled'),
    ).toBeDefined()
    expect(
      wrapper
        .findAll('button')
        .find((button) => button.text() === '催办')
        .attributes('disabled'),
    ).toBeDefined()
    expect(wrapper.text()).toContain('已提醒当前处理人')
  })

  it('renders the withdraw recovery with its own action label', async () => {
    holder.opening.state.recoveries = [
      { operationId: 'op-w', processId: 'p-1', action: 'withdraw' },
    ]
    const wrapper = await mountDetail()

    expect(wrapper.text()).toContain('所选动作：撤回')
    const end = wrapper
      .findAll('button')
      .find((button) => button.text() === '我已核实，结束本次记录')
    await end.trigger('click')
    expect(holder.opening.endRecovery).toHaveBeenCalledWith('op-w')
  })

  it('keeps the pending-only return button out of started contexts after ending a record', async () => {
    holder.opening.state.ended = true
    holder.opening.state.detail.perspective = { kind: 'started' }
    const wrapper = await mountDetail()

    expect(wrapper.text()).toContain('本次记录已结束')
    expect(wrapper.text()).toContain('请刷新详情或返回列表核实当前状态')
    expect(wrapper.findAll('button').some((button) => button.text() === '返回最新待处理')).toBe(
      false,
    )
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

describe('PendingDetail 转办/加签动作面（#288）', () => {
  it('挂起行、候选人行与催办标记在详情头部呈现', async () => {
    holder.opening.state.detail.pendingRow = { actionKind: 'APPROVE', suspended: true, urged: true }
    const wrapper = await mountDetail()

    expect(wrapper.text()).toContain('等待加签人处理')
    expect(wrapper.text()).toContain('已催办')
    expect(wrapper.text()).not.toContain('候选人之一，处理即认领')

    holder.opening.state.detail.pendingRow = {
      actionKind: 'CANDIDATE',
      suspended: false,
      urged: false,
    }
    const candidate = await mountDetail()
    expect(candidate.text()).toContain('候选人之一，处理即认领')
  })

  it('转办/加签按钮随资格出现，点击打开弹层并读取候选目标', async () => {
    holder.opening.state.detail.canApprove = true
    holder.opening.state.detail.pendingRow = { actionKind: 'APPROVE', suspended: false }
    holder.opening.state.showTransfer = true
    holder.opening.state.showAddSign = true
    holder.opening.state.canTransfer = true
    holder.opening.state.canAddSign = true
    const wrapper = await mountDetail()

    const transfer = wrapper.findAll('button').find((button) => button.text() === '转办')
    expect(transfer).toBeDefined()
    expect(transfer.attributes('disabled')).toBeUndefined()
    await transfer.trigger('click')
    expect(holder.opening.loadActionTargets).toHaveBeenCalled()

    const addSign = wrapper.findAll('button').find((button) => button.text() === '加签')
    expect(addSign).toBeDefined()
    await addSign.trigger('click')
    expect(holder.opening.loadActionTargets).toHaveBeenCalledTimes(2)
  })

  it('挂起行与加签行不出现转办/加签按钮，动作按钮按资格停用', async () => {
    holder.opening.state.detail.canApprove = false
    holder.opening.state.detail.pendingRow = { actionKind: 'APPROVE', suspended: true }
    const suspended = await mountDetail()
    expect(suspended.findAll('button').some((button) => button.text() === '转办')).toBe(false)
    expect(suspended.findAll('button').some((button) => button.text() === '加签')).toBe(false)
    suspended.unmount()

    holder.opening = makeOpening()
    holder.opening.state.detail.canApprove = true
    holder.opening.state.detail.pendingRow = { actionKind: 'ADD_SIGN', suspended: false }
    holder.opening.state.showTransfer = false
    holder.opening.state.showAddSign = false
    const signer = await mountDetail()
    expect(signer.findAll('button').some((button) => button.text() === '转办')).toBe(false)
    expect(signer.findAll('button').some((button) => button.text() === '加签')).toBe(false)
    signer.unmount()

    holder.opening = makeOpening()
    holder.opening.state.detail.canApprove = true
    holder.opening.state.detail.pendingRow = { actionKind: 'APPROVE', suspended: false }
    holder.opening.state.showTransfer = true
    holder.opening.state.showAddSign = true
    holder.opening.state.canTransfer = false
    holder.opening.state.canAddSign = false
    const disabled = await mountDetail()
    expect(
      disabled
        .findAll('button')
        .find((button) => button.text() === '转办')
        .attributes('disabled'),
    ).toBeDefined()
    expect(
      disabled
        .findAll('button')
        .find((button) => button.text() === '加签')
        .attributes('disabled'),
    ).toBeDefined()
  })

  it('转办/加签恢复记录显示对应动作文案；提交在途时面板让位给提交提示', async () => {
    holder.opening.state.recoveries = [
      { operationId: 'op-t', processId: 'p-1', action: 'transfer', targetUserId: 5 },
      { operationId: 'op-a', processId: 'p-1', action: 'addSign', signType: 1, assigneeIds: [4] },
    ]
    const wrapper = await mountDetail()

    expect(wrapper.text()).toContain('所选动作：转办')
    expect(wrapper.text()).toContain('所选动作：加签')
    wrapper.unmount()

    holder.opening = makeOpening()
    holder.opening.state.recoveries = [
      { operationId: 'op-t', processId: 'p-1', action: 'transfer', targetUserId: 5 },
    ]
    holder.opening.state.transferring = true
    const inFlight = await mountDetail()
    expect(inFlight.text()).not.toContain('处理结果待确认')
    expect(inFlight.text()).toContain('可以直接离开；离开不会撤销已提交的处理。')
  })
})
