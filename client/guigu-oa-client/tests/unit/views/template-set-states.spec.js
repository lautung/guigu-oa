import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { reactive } from 'vue'

const { holder, messageMock, confirmMock } = vi.hoisted(() => ({
  holder: {},
  messageMock: Object.assign(vi.fn(), { error: vi.fn(), success: vi.fn(), close: vi.fn() }),
  confirmMock: vi.fn(),
}))

vi.mock('element-plus', () => ({
  ElMessage: messageMock,
  ElMessageBox: { confirm: confirmMock, close: vi.fn() },
}))

vi.mock('@/config/template-editor', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    createTemplateEditor: () => holder.editor,
  }
})

import { PATHS } from '@/router/paths'
import TemplateSet from '@/views/config/processTemplate/templateSet.vue'
import { createTestRouter, elementStubs, mountOptions, setupSession } from '../support/harness'

const buttonStub = {
  name: 'ElButton',
  props: ['disabled', 'loading', 'type', 'link'],
  template: '<button :disabled="disabled"><slot /></button>',
}

const formStub = {
  name: 'ElForm',
  props: ['model', 'rules', 'disabled', 'labelWidth'],
  template: '<form><slot /></form>',
}
const formItemStub = {
  name: 'ElFormItem',
  props: ['label', 'prop', 'for'],
  template: '<div><slot /></div>',
}

function state(overrides = {}) {
  return reactive({
    phase: 'ready',
    step: 0,
    form: {
      id: '8',
      processTypeId: 2,
      name: '请假模板',
      iconUrl: '',
      description: '',
      formProps: '[]',
      formOptions: '{}',
    },
    types: [{ id: 2, name: '请假' }],
    error: '',
    uploadError: '',
    zipStatus: 'empty',
    zip: null,
    approvers: [],
    approverStatus: 'ready',
    approverError: '',
    approverSaving: false,
    approverSaveError: '',
    approverDirty: false,
    approverRoles: [],
    approverPosts: [],
    approverOptionsError: '',
    staleApproverKeys: [],
    zipUnparsable: false,
    canSaveApprovers: false,
    dirty: false,
    capturing: false,
    leaving: false,
    snapshot: null,
    inspectionDesign: null,
    canEdit: true,
    canNavigateSteps: true,
    canSave: true,
    canRestoreZip: false,
    ...overrides,
  })
}

function makeEditor(overrides = {}) {
  const opening = {
    state: state(overrides),
    updateBasic: vi.fn(),
    next: vi.fn(),
    previous: vi.fn(),
    back: vi.fn(),
    reload: vi.fn(),
    retryDesigner: vi.fn(),
    selectZip: vi.fn(),
    removeZip: vi.fn(),
    restoreZip: vi.fn(),
    retryUpload: vi.fn(),
    updateApprover: vi.fn(),
    saveApprovers: vi.fn(),
    retryApprovers: vi.fn(),
    endEditing: vi.fn(),
    openList: vi.fn(),
    requestLeave: vi.fn(async () => true),
    environmentChanged: vi.fn(async () => {}),
    dispose: vi.fn(),
    open: vi.fn(function () {
      return this
    }),
  }
  return { opening, editor: { open: vi.fn(() => opening) } }
}

function templateSetRouter() {
  return createTestRouter([
    { path: PATHS.PROCESS_SET_TEMPLATE_SET, component: TemplateSet },
    { path: PATHS.PROCESS_SET_PROCESS_TEMPLATE, component: { template: '<div />' } },
  ])
}

async function mountTemplateSet(query = { id: '8' }) {
  const { pinia } = setupSession()
  const router = templateSetRouter()
  await router.push({ path: PATHS.PROCESS_SET_TEMPLATE_SET, query })
  await router.isReady()
  const options = mountOptions(pinia, router)
  options.global.stubs = { ...elementStubs, 'el-button': buttonStub }
  const wrapper = mount(TemplateSet, options)
  await flushPromises()
  return wrapper
}

beforeEach(() => {
  holder.current = makeEditor()
  holder.editor = holder.current.editor
})

describe('TemplateSet states', () => {
  it('renders the basic step with the selected type and name', async () => {
    const wrapper = await mountTemplateSet()
    const stateRef = holder.current.opening.state

    expect(wrapper.vm.state.form.name).toBe('请假模板')
    expect(wrapper.vm.state.types[0].name).toBe('请假')
    expect(wrapper.vm.wizardSteps).toHaveLength(3)

    stateRef.canEdit = false
    await flushPromises()
    expect(wrapper.vm.state.canEdit).toBe(false)
  })

  it('shows the load error and retries', async () => {
    holder.current = makeEditor({ phase: 'load-error', error: '模板读取失败' })
    holder.editor = holder.current.editor
    const wrapper = await mountTemplateSet()

    expect(wrapper.text()).toContain('模板读取失败')
    const retry = wrapper.findAll('button').find((button) => button.text() === '重试读取')
    await retry.trigger('click')
    expect(holder.current.opening.reload).toHaveBeenCalled()
  })

  it('shows the design error and disables retry while capturing', async () => {
    holder.current = makeEditor({ phase: 'design-error', error: '设计器不可用', capturing: true })
    holder.editor = holder.current.editor
    const wrapper = await mountTemplateSet()

    expect(wrapper.text()).toContain('设计器不可用')
    const retry = wrapper.findAll('button').find((button) => button.text() === '重试表单设计')
    expect(retry.attributes('disabled')).toBeDefined()
  })

  it('shows the uncertain panel and offers verification actions', async () => {
    holder.current = makeEditor({ phase: 'uncertain', error: '保存结果未知' })
    holder.editor = holder.current.editor
    const wrapper = await mountTemplateSet()

    expect(wrapper.text()).toContain('模板保存结果待确认')

    const openList = wrapper
      .findAll('button')
      .find((button) => button.text() === '另开模板列表核实')
    await openList.trigger('click')
    expect(holder.current.opening.openList).toHaveBeenCalled()

    const end = wrapper.findAll('button').find((button) => button.text() === '结束本次编辑')
    await end.trigger('click')
    expect(holder.current.opening.endEditing).toHaveBeenCalled()
  })

  it('shows the saving status', async () => {
    holder.current = makeEditor({
      phase: 'saving',
      step: 2,
      canNavigateSteps: false,
      canSave: false,
    })
    holder.editor = holder.current.editor
    const wrapper = await mountTemplateSet()

    expect(wrapper.text()).toContain('正在保存审批模板')
    const submit = wrapper.findAll('button').find((button) => button.text().includes('提交保存'))
    expect(submit.attributes('disabled')).toBeDefined()
  })

  it('renders the ready zip and upload errors', async () => {
    holder.current = makeEditor({ phase: 'design-error', error: '', uploadError: '上传失败' })
    holder.current.opening.state.zipStatus = 'ready'
    holder.current.opening.state.zip = { name: 'leave.zip', path: 'processes/sha256/a.zip' }
    holder.current.opening.state.canRestoreZip = true
    holder.editor = holder.current.editor

    const wrapper = await mountTemplateSet()

    expect(wrapper.text()).toContain('leave.zip')
    expect(wrapper.text()).toContain('上传失败')
  })

  it('renders the readonly design preview', async () => {
    holder.current = makeEditor({
      phase: 'readonly',
      canEdit: false,
      inspectionDesign: {
        rule: [{ field: 'days', title: '天数' }],
        option: { form: {}, submitBtn: false },
      },
    })
    holder.editor = holder.current.editor
    const wrapper = await mountTemplateSet()

    expect(wrapper.vm.inspectionDesign).toBeTruthy()
  })

  it('drives step navigation and zip selection through the editor', async () => {
    const wrapper = await mountTemplateSet()
    wrapper.vm.state.step = 1
    await flushPromises()

    const previous = wrapper.findAll('button').find((button) => button.text() === '上一步')
    await previous.trigger('click')
    expect(holder.current.opening.previous).toHaveBeenCalled()

    wrapper.vm.state.step = 2
    await flushPromises()
    const submit = wrapper.findAll('button').find((button) => button.text().includes('提交保存'))
    await submit.trigger('click')
    expect(holder.current.opening.next).toHaveBeenCalled()

    const back = wrapper.findAll('button').find((button) => button.text() === '返回')
    await back.trigger('click')
    expect(holder.current.opening.back).toHaveBeenCalled()

    wrapper.vm.selectZip({ target: { files: [], value: '' } })
    expect(holder.current.opening.selectZip).not.toHaveBeenCalled()

    const file = new File(['zip'], 'leave.zip')
    wrapper.vm.selectZip({ target: { files: [file], value: '' } })
    expect(holder.current.opening.selectZip).toHaveBeenCalledWith(file)

    wrapper.vm.environmentChanged()
    expect(holder.current.opening.environmentChanged).toHaveBeenCalled()
  })

  it('disposes the editor on unmount', async () => {
    const wrapper = await mountTemplateSet()
    wrapper.unmount()
    expect(holder.current.opening.dispose).toHaveBeenCalled()
  })

  it('renders approver rows on the zip step and drives the config save through the editor', async () => {
    holder.current = makeEditor({
      step: 2,
      zipStatus: 'ready',
      zip: { name: 'leave.zip', path: 'processes/a.zip', key: 'a' },
      canSaveApprovers: true,
      approvers: [
        { key: 'approve', name: '部门审批', assignee: 'lisi', sourceType: 'ROLE', targetId: 7 },
        { key: 'manager', name: '经理审批', assignee: '', sourceType: '', targetId: '' },
      ],
      approverRoles: [{ id: 7, name: '审批主管' }],
    })
    holder.editor = holder.current.editor
    const wrapper = await mountTemplateSet()

    expect(wrapper.text()).toContain('此处规则将覆盖流程 zip 中的 assignee')
    expect(wrapper.text()).toContain('部门审批')
    expect(wrapper.text()).toContain('经理审批')
    expect(wrapper.text()).toContain('缺少审批人来源')

    const save = wrapper.findAll('button').find((button) => button.text() === '保存审批人配置')
    await save.trigger('click')
    expect(holder.current.opening.saveApprovers).toHaveBeenCalledTimes(1)
  })

  it('shows the publish-blocked hint when the adopted zip parses no BPMN nodes', async () => {
    holder.current = makeEditor({
      step: 2,
      zipStatus: 'ready',
      zip: { name: 'empty.zip', path: 'processes/e.zip', key: 'e' },
      zipUnparsable: true,
    })
    holder.editor = holder.current.editor
    const wrapper = await mountTemplateSet()

    expect(wrapper.text()).toContain('流程 zip 解析不出 BPMN 节点')
    expect(wrapper.text()).toContain('不可发布')
  })

  it('warns about saved rules that no longer match the adopted zip', async () => {
    holder.current = makeEditor({
      step: 2,
      zipStatus: 'ready',
      staleApproverKeys: ['approve'],
    })
    holder.editor = holder.current.editor
    const wrapper = await mountTemplateSet()

    expect(wrapper.text()).toContain('不存在的节点')
    expect(wrapper.text()).toContain('approve')
  })

  it('offers the retry read when the approver config read fails', async () => {
    holder.current = makeEditor({
      step: 2,
      approverStatus: 'error',
      approverError: '审批人配置读取失败',
    })
    holder.editor = holder.current.editor
    const wrapper = await mountTemplateSet()

    expect(wrapper.text()).toContain('审批人配置读取失败')
    const retry = wrapper.findAll('button').find((button) => button.text() === '重试读取')
    await retry.trigger('click')
    expect(holder.current.opening.retryApprovers).toHaveBeenCalledTimes(1)
  })

  it('tells new templates to save before configuring approvers', async () => {
    holder.current = makeEditor({ step: 2, zipStatus: 'ready' })
    holder.current.opening.state.form.id = ''
    holder.editor = holder.current.editor
    const wrapper = await mountTemplateSet()

    expect(wrapper.text()).toContain('先提交保存，再从模板列表打开编辑后配置审批人')
    expect(wrapper.find('.approver-row').exists()).toBe(false)
  })

  it('wires the basic step to the local required rules', async () => {
    const { pinia } = setupSession()
    const router = templateSetRouter()
    await router.push({ path: PATHS.PROCESS_SET_TEMPLATE_SET, query: { id: '8' } })
    await router.isReady()
    const options = mountOptions(pinia, router)
    options.global.stubs = {
      ...elementStubs,
      'el-button': buttonStub,
      'el-form': formStub,
      'el-form-item': formItemStub,
    }
    const wrapper = mount(TemplateSet, options)
    await flushPromises()

    const itemProps = wrapper
      .findAllComponents({ name: 'ElFormItem' })
      .map((item) => item.props('prop'))
    expect(itemProps).toContain('processTypeId')
    expect(itemProps).toContain('name')

    const rules = wrapper.findComponent({ name: 'ElForm' }).props('rules')
    expect(rules.name[0].required).toBe(true)
    expect(rules.processTypeId[0].required).toBe(true)
  })
})
