import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

const { messageMock, confirmMock } = vi.hoisted(() => ({
  messageMock: Object.assign(vi.fn(), { error: vi.fn(), success: vi.fn(), close: vi.fn() }),
  confirmMock: vi.fn(),
}))

vi.mock('element-plus', () => ({
  ElMessage: messageMock,
  ElMessageBox: { confirm: confirmMock, close: vi.fn() },
}))

import ApprovalStartAttachments from '@/work/ApprovalStartAttachments.vue'
import { PATHS, WORK_LIST_KINDS } from '@/router/paths'
import Apply from '@/views/work/start/apply.vue'
import PendingDetail from '@/views/work/pending/detail.vue'
import TemplateSet from '@/views/config/processTemplate/templateSet.vue'
import {
  createTestRouter,
  elementStubs,
  installHttp,
  mountOptions,
  ok,
  setupSession,
} from '../support/harness'

const template = {
  id: 5,
  processTypeId: 2,
  name: '请假模板',
  status: 1,
  availableForInitiation: true,
  formProps: JSON.stringify([{ field: 'days', title: '天数' }]),
  formOptions: JSON.stringify({}),
}

function fileEvent(files) {
  return { currentTarget: { files, value: '' } }
}

let http
beforeEach(() => {
  vi.clearAllMocks()
  messageMock.mockReset()
  confirmMock.mockReset().mockResolvedValue('confirm')
  http = installHttp((config) => {
    const url = config.url || ''
    if (/processTemplate\/get\/5$/.test(url)) return ok(template)
    if (url.includes('/carbonCopyRecipients'))
      return ok([{ id: 9, name: '卡罗尔', username: 'carol' }])
    if (url.includes('/admin/attachments/drafts/')) return ok([])
    if (url.includes('/admin/process/startUp')) return ok(null)
    if (url.includes('/admin/process/show/')) {
      return ok({ process: { id: 'p-1', title: '张三的请假' }, isApprove: true, records: [] })
    }
    if (url.includes('/admin/attachments/processes/'))
      return ok({ attachments: [], canUpload: true })
    if (url.includes('/processType/findAll')) return ok([{ id: 2, name: '请假' }])
    if (/processTemplate\/get\/8$/.test(url)) return ok({ ...template, id: 8, status: 0 })
    return ok(null)
  })
})

afterEach(() => http.restore())

describe('Apply page', () => {
  function applyRouter() {
    return createTestRouter([
      { path: PATHS.WORK_START, component: { template: '<div />' } },
      { path: PATHS.WORK_STARTED, component: { template: '<div />' } },
      { path: PATHS.WORK_PENDING, component: { template: '<div />' } },
      { path: '/work/start/:processTemplateId', component: Apply },
    ])
  }

  it('loads the template, recipients, and attachments for the route id', async () => {
    const { pinia } = setupSession()
    const router = applyRouter()
    await router.push('/work/start/5')
    await router.isReady()
    const wrapper = mount(Apply, mountOptions(pinia, router))
    await flushPromises()

    expect(wrapper.vm.view.template.name).toBe('请假模板')
    expect(wrapper.vm.view.recipients).toHaveLength(1)
    expect(wrapper.vm.view.attachmentsReady).toBe(true)
    expect(wrapper.vm.rule).toEqual([{ field: 'days', title: '天数' }])
    expect(wrapper.vm.option.form).toMatchObject({ labelPosition: 'top' })

    wrapper.vm.formData = { days: 2 }
    expect(wrapper.vm.formData).toEqual({ days: 2 })
    wrapper.vm.ccUserIds = [9]
    expect(wrapper.vm.ccUserIds).toEqual([9])
  })

  it('submits the draft and navigates to pending', async () => {
    const { pinia } = setupSession()
    const router = applyRouter()
    await router.push('/work/start/5')
    await router.isReady()
    const wrapper = mount(Apply, mountOptions(pinia, router))
    await flushPromises()

    const push = vi.spyOn(router, 'push')
    wrapper.vm.formData = { days: 2 }
    wrapper.vm.start.submit({ days: 2 })
    await flushPromises()

    const startCall = http.calls.find((call) => String(call.url).includes('/startUp'))
    expect(startCall).toBeDefined()
    expect(JSON.parse(startCall.data).formValues).toContain('"天数":2')
    expect(push).toHaveBeenCalledWith(PATHS.WORK_STARTED)
  })

  it('reloads, goes back, and forwards attachment picking to the module', async () => {
    const { pinia } = setupSession()
    const router = applyRouter()
    await router.push('/work/start/5')
    await router.isReady()
    const wrapper = mount(Apply, mountOptions(pinia, router))
    await flushPromises()

    wrapper.vm.start.reload()
    await flushPromises()
    expect(
      http.calls.filter((call) => String(call.url).includes('/processTemplate/get/5')),
    ).toHaveLength(2)

    const attachments = wrapper.findComponent(ApprovalStartAttachments)
    expect(attachments.vm.formatSize(512)).toBe('512 B')
    expect(attachments.vm.formatSize(2048)).toBe('2.0 KB')
    expect(attachments.vm.formatSize(2 * 1048576)).toBe('2.0 MB')

    attachments.vm.onFilesSelected(fileEvent([]))
    attachments.vm.replaceUnavailable(fileEvent([]), 3)
    attachments.vm.retryInterrupted(fileEvent([]), 'interrupted-1')
    await flushPromises()

    wrapper.vm.goBack()
    await flushPromises()
    expect(router.currentRoute.value.path).toBe(PATHS.WORK_START)
  })
})

describe('Pending detail page', () => {
  function detailRouter() {
    return createTestRouter([
      {
        path: '/work/pending/:id/:taskId',
        component: PendingDetail,
        meta: { workListKind: WORK_LIST_KINDS.PENDING },
      },
      {
        path: '/work/pending/:id',
        component: PendingDetail,
        meta: { workListKind: WORK_LIST_KINDS.PENDING },
      },
      { path: PATHS.WORK_PENDING, component: { template: '<div />' } },
      { path: PATHS.WORK_PROCESSED, component: { template: '<div />' } },
      { path: '/work/pending/p-1', component: { template: '<div />' } },
    ])
  }

  it('loads the detail and exposes reload and back', async () => {
    const { pinia } = setupSession()
    const router = detailRouter()
    await router.push('/work/pending/p-1/t-1')
    await router.isReady()
    const wrapper = mount(PendingDetail, mountOptions(pinia, router))
    await flushPromises()

    expect(wrapper.vm.detail.title).toBe('张三的请假')
    expect(wrapper.vm.detailSections).toEqual(['form', 'records'])

    wrapper.vm.opening.reload()
    await flushPromises()
    expect(
      http.calls.filter((call) => String(call.url).includes('/admin/process/show/p-1')),
    ).toHaveLength(2)

    wrapper.vm.addFiles(fileEvent([]))
    await flushPromises()

    wrapper.vm.opening.back()
    await flushPromises()
    expect(router.currentRoute.value.path).toBe(PATHS.WORK_PENDING)
  })

  it('stays empty without a process id', async () => {
    const { pinia } = setupSession()
    const router = detailRouter()
    await router.push(PATHS.WORK_PENDING)
    await router.isReady()
    const wrapper = mount(PendingDetail, mountOptions(pinia, router))
    await flushPromises()

    expect(wrapper.vm.state).toBeUndefined()
    expect(wrapper.vm.detail).toBeUndefined()
  })
})

describe('Template set page', () => {
  function designerStub() {
    let rule = []
    let option = {}
    return {
      template: '<div />',
      methods: {
        getRule: () => rule,
        setRule: (value) => {
          rule = value
        },
        getOption: () => option,
        setOption: (value) => {
          option = value
        },
      },
    }
  }

  function templateSetRouter() {
    return createTestRouter([
      { path: PATHS.PROCESS_SET_TEMPLATE_SET, component: TemplateSet },
      { path: PATHS.PROCESS_SET_PROCESS_TEMPLATE, component: { template: '<div />' } },
    ])
  }

  it('loads an unpublished template into the editor', async () => {
    const { pinia } = setupSession()
    const router = templateSetRouter()
    await router.push({ path: PATHS.PROCESS_SET_TEMPLATE_SET, query: { id: '8' } })
    await router.isReady()
    const options = mountOptions(pinia, router)
    options.global.stubs = { ...elementStubs, 'fc-designer': designerStub() }
    const wrapper = mount(TemplateSet, options)
    await flushPromises()

    expect(wrapper.vm.state.phase).toBe('ready')
    expect(wrapper.vm.state.form.name).toBe('请假模板')
    expect(wrapper.vm.state.types).toHaveLength(1)
    expect(wrapper.vm.wizardSteps).toHaveLength(3)

    wrapper.vm.edit.updateBasic({ name: '改过的模板' })
    expect(wrapper.vm.state.form.name).toBe('改过的模板')

    wrapper.vm.selectZip({ target: { files: [], value: '' } })
    wrapper.vm.environmentChanged()
    await flushPromises()
  })

  it('opens a blank editor when the route carries no id', async () => {
    const { pinia } = setupSession()
    const router = templateSetRouter()
    await router.push(PATHS.PROCESS_SET_TEMPLATE_SET)
    await router.isReady()
    const options = mountOptions(pinia, router)
    options.global.stubs = { ...elementStubs, 'fc-designer': designerStub() }
    const wrapper = mount(TemplateSet, options)
    await flushPromises()

    expect(wrapper.vm.state.phase).toBe('ready')
    expect(wrapper.vm.state.form.id).toBe('')
  })
})
