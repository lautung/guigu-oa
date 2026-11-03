import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'

const { messageMock, confirmMock } = vi.hoisted(() => ({
  messageMock: Object.assign(vi.fn(), { error: vi.fn(), success: vi.fn(), close: vi.fn() }),
  confirmMock: vi.fn(),
}))

vi.mock('element-plus', () => ({
  ElMessage: messageMock,
  ElMessageBox: { confirm: confirmMock, close: vi.fn() },
}))

import { RecognitionFailure } from '@/recognition'
import { PATHS } from '@/router/paths'
import { WORK_LIST_KINDS } from '@/router/paths'
import { getDefaultPostLoginRoute } from '@/navigation/constants'
import Login from '@/views/login/index.vue'
import WorkListPage from '@/views/work/WorkListPage.vue'
import Profile from '@/views/work/profile/index.vue'
import WorkStart from '@/views/work/start/index.vue'
import {
  XSS_PAYLOAD,
  createTestRouter,
  fakeRecognition,
  installHttp,
  mountOptions,
  ok,
  paged,
  setupSession,
} from '../support/harness'

const workRecord = {
  id: 'p-1',
  taskId: 't-1',
  title: '张三的请假',
  name: '张三',
  processTypeName: '请假审批',
  createTime: '2026-01-02 10:00:00',
  status: 1,
  formValues: JSON.stringify({ formShowData: { days: 2, reason: '家中有事' } }),
}

function workRouter() {
  return createTestRouter([
    {
      path: PATHS.WORK_PENDING,
      component: WorkListPage,
      meta: { workListKind: WORK_LIST_KINDS.PENDING },
    },
    {
      path: PATHS.WORK_PROCESSED,
      component: WorkListPage,
      meta: { workListKind: WORK_LIST_KINDS.PROCESSED },
    },
    { path: PATHS.WORK_STARTED, component: { template: '<div />' } },
    { path: PATHS.WORK_PROFILE, component: { template: '<div />' } },
    { path: PATHS.WORKBENCH, component: { template: '<div />' } },
    { path: '/login', component: { template: '<div />' } },
    { path: '/work/start/:processTemplateId', component: { template: '<div />' } },
  ])
}

let http
beforeEach(() => {
  vi.clearAllMocks()
  messageMock.mockReset()
  confirmMock.mockReset().mockResolvedValue('confirm')
  http = installHttp((config) => {
    const url = config.url || ''
    if (/findPending|findProcessed/.test(url)) return paged([workRecord])
    if (url.includes('/admin/process/getCurrentUser')) {
      return ok({ name: '爱丽丝', phone: '13900000000' })
    }
    if (url.includes('/admin/process/findStartable')) {
      return ok([
        {
          id: 2,
          name: '请假',
          processTemplateList: [
            { id: 5, name: '请假模板', status: 1, iconUrl: '/icon.png' },
            { id: 6, name: '未发布模板', status: 0 },
          ],
        },
      ])
    }
    if (url.includes('/admin/system/index/login')) {
      return ok({ token: 'token', refreshToken: 'refresh' })
    }
    return ok(null)
  })
})

afterEach(() => http.restore())

describe('WorkListPage', () => {
  it('presents the list for the route kind and opens a detail', async () => {
    const { pinia } = setupSession()
    const router = workRouter()
    await router.push(PATHS.WORK_PENDING)
    await router.isReady()
    const wrapper = mount(WorkListPage, mountOptions(pinia, router))
    await flushPromises()

    expect(wrapper.vm.definition.title).toBeTruthy()
    expect(wrapper.vm.rows[0]).toMatchObject({
      title: '张三的请假',
      statusLabel: '审批中',
      relationLabel: expect.any(String),
    })

    const push = vi.spyOn(router, 'push').mockResolvedValue(undefined)
    wrapper.vm.openDetail(wrapper.vm.rows[0])
    await flushPromises()
    expect(push).toHaveBeenCalledWith('/work/pending/p-1/t-1')
  })

  it('shows a summary field that looks like HTML as text, never as markup', async () => {
    http.restore()
    http = installHttp((config) => {
      const url = config.url || ''
      if (/findPending/.test(url)) {
        return paged([
          {
            ...workRecord,
            formValues: JSON.stringify({ formShowData: { 请假原因: XSS_PAYLOAD } }),
          },
        ])
      }
      return ok(null)
    })
    const { pinia } = setupSession()
    const router = workRouter()
    await router.push(PATHS.WORK_PENDING)
    await router.isReady()
    const wrapper = mount(WorkListPage, mountOptions(pinia, router))
    await flushPromises()

    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.text()).toContain(XSS_PAYLOAD)
  })

  it('reopens the list when the route kind changes', async () => {
    const { pinia } = setupSession()
    const router = workRouter()
    await router.push(PATHS.WORK_PENDING)
    await router.isReady()
    const wrapper = mount(WorkListPage, mountOptions(pinia, router))
    await flushPromises()
    const pendingTitle = wrapper.vm.definition.title

    await router.push(PATHS.WORK_PROCESSED)
    await flushPromises()

    expect(wrapper.vm.definition.title).not.toBe(pendingTitle)
    expect(http.calls.some((call) => String(call.url).includes('findProcessed'))).toBe(true)
  })
})

describe('Profile page', () => {
  it('loads the current user into the profile card', async () => {
    const { pinia } = setupSession()
    const wrapper = mount(Profile, mountOptions(pinia, workRouter()))
    await flushPromises()

    expect(wrapper.vm.loading).toBe(false)
    expect(wrapper.vm.profile.name).toBe('爱丽丝')
    expect(wrapper.text()).toContain('爱丽丝')
  })

  it('stops loading when the read fails', async () => {
    http.restore()
    http = installHttp(() => ({ code: 500, message: '不可用' }))
    const { pinia } = setupSession()
    const wrapper = mount(Profile, mountOptions(pinia, workRouter()))
    await flushPromises()

    expect(wrapper.vm.loading).toBe(false)
    expect(wrapper.vm.profile.name).toBe('')
  })
})

describe('WorkStart page', () => {
  it('renders startable groups and opens the apply page', async () => {
    const { pinia } = setupSession()
    const router = workRouter()
    await router.push(PATHS.WORK_STARTED)
    await router.isReady()
    const wrapper = mount(WorkStart, mountOptions(pinia, router))
    await flushPromises()

    expect(wrapper.vm.loading).toBe(false)
    expect(wrapper.vm.groups).toHaveLength(1)
    expect(wrapper.vm.groups[0].templates).toHaveLength(1)

    wrapper.vm.openApply(undefined)
    await flushPromises()
    expect(router.currentRoute.value.path).toBe(PATHS.WORK_STARTED)

    wrapper.vm.openApply(5)
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/work/start/5')
  })
})

describe('Login page', () => {
  function formStub(valid = true) {
    return defineComponent({
      name: 'ElForm',
      setup(_, { slots, expose }) {
        expose({
          validate(callback) {
            callback(valid)
          },
        })
        return () => h('form', slots.default ? slots.default() : [])
      },
    })
  }

  function mountLogin(pinia, router, valid = true) {
    const options = mountOptions(pinia, router)
    options.global.stubs['el-form'] = formStub(valid)
    return mount(Login, options)
  }

  it('reads the redirect, validates, logs in, and navigates', async () => {
    const { pinia, recognition } = setupSession({
      recognition: fakeRecognition({ establish: vi.fn() }),
    })
    const router = workRouter()
    await router.push('/login?redirect=/work/start')
    await router.isReady()
    const wrapper = mountLogin(pinia, router)
    await flushPromises()

    expect(wrapper.vm.redirect).toBe('/work/start')

    wrapper.vm.loginForm.username = 'alice'
    wrapper.vm.loginForm.password = '111111'
    await wrapper.vm.handleLogin()
    await flushPromises()

    expect(http.calls.some((call) => String(call.url).includes('/index/login'))).toBe(true)
    expect(recognition.establish).toHaveBeenCalled()
    expect(router.currentRoute.value.path).toBe('/work/start')
    expect(wrapper.vm.loading).toBe(false)
  })

  it('falls back to the default route when no redirect is present', async () => {
    const { pinia } = setupSession()
    const router = workRouter()
    await router.push('/login')
    await router.isReady()
    const wrapper = mountLogin(pinia, router)
    await flushPromises()

    expect(wrapper.vm.redirect).toBeUndefined()

    await wrapper.vm.handleLogin()
    await flushPromises()
    expect(router.currentRoute.value.path).toBe(getDefaultPostLoginRoute())
  })

  it('does not call the API when validation fails', async () => {
    const { pinia } = setupSession()
    const router = workRouter()
    await router.push('/login')
    await router.isReady()
    const wrapper = mountLogin(pinia, router, false)
    await flushPromises()

    await wrapper.vm.handleLogin()
    expect(http.calls).toHaveLength(0)
    expect(wrapper.vm.loading).toBe(false)
  })

  it('reports login failures except a superseded recognition', async () => {
    const failing = vi.fn(() => {
      throw new Error('密码错误')
    })
    const { pinia, recognition } = setupSession({
      recognition: fakeRecognition({ establish: failing }),
    })
    const router = workRouter()
    await router.push('/login')
    await router.isReady()
    const wrapper = mountLogin(pinia, router)
    await flushPromises()

    await wrapper.vm.handleLogin()
    await flushPromises()
    expect(messageMock.error).toHaveBeenCalledWith('密码错误')
    expect(wrapper.vm.loading).toBe(false)

    recognition.establish = vi.fn(() => {
      throw new RecognitionFailure('superseded', '已被新的登录取代')
    })
    await wrapper.vm.handleLogin()
    await flushPromises()
    expect(messageMock.error).toHaveBeenCalledTimes(1)
  })
})
