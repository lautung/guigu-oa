import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

const registerApi = vi.hoisted(() => ({
  loadRegisterSwitch: vi.fn(),
  submitRegister: vi.fn(),
}))

vi.mock('@/api/register', () => registerApi)

import ElementPlus from 'element-plus'

import { PATHS } from '@/router/paths'
import LoginPage from '@/views/login/index.vue'
import RegisterPage from '@/views/register/index.vue'
import { createTestRouter, setupSession } from '../support/harness'

const routes = [
  { path: PATHS.LOGIN, component: { template: '<div />' } },
  { path: PATHS.REGISTER, component: { template: '<div />' } },
  { path: PATHS.WORKBENCH, component: { template: '<div />' } },
]

async function mountAt(component, path, query = {}) {
  const { pinia } = setupSession({ token: '' })
  const router = createTestRouter(routes)
  await router.push({ path, query })
  await router.isReady()
  const wrapper = mount(component, {
    global: { plugins: [pinia, router, ElementPlus] },
  })
  await flushPromises()
  return wrapper
}

describe('注册入口', () => {
  beforeEach(() => {
    registerApi.loadRegisterSwitch.mockReset()
    registerApi.submitRegister.mockReset()
  })

  it('hides the login-page register entry while the switch is off', async () => {
    registerApi.loadRegisterSwitch.mockResolvedValue(false)

    const wrapper = await mountAt(LoginPage, PATHS.LOGIN)

    expect(wrapper.find('.register-entry').exists()).toBe(false)
    expect(registerApi.loadRegisterSwitch).toHaveBeenCalledTimes(1)
  })

  it('shows the register entry only when the switch is open', async () => {
    registerApi.loadRegisterSwitch.mockResolvedValue(true)

    const wrapper = await mountAt(LoginPage, PATHS.LOGIN)
    const entry = wrapper.find('.register-entry a')

    expect(entry.exists()).toBe(true)
    expect(entry.attributes('href')).toBe(PATHS.REGISTER)
  })

  it('keeps the entry hidden when the probe fails', async () => {
    registerApi.loadRegisterSwitch.mockRejectedValue(new Error('network'))

    const wrapper = await mountAt(LoginPage, PATHS.LOGIN)

    expect(wrapper.find('.register-entry').exists()).toBe(false)
  })

  it('prefills the username returned from registration', async () => {
    registerApi.loadRegisterSwitch.mockResolvedValue(true)

    const wrapper = await mountAt(LoginPage, PATHS.LOGIN, { username: 'reg-alice' })

    expect(wrapper.find('input').element.value).toBe('reg-alice')
  })

  it('renders the closed state instead of the form when the switch is off', async () => {
    registerApi.loadRegisterSwitch.mockResolvedValue(false)

    const wrapper = await mountAt(RegisterPage, PATHS.REGISTER)

    expect(wrapper.find('.register-closed').exists()).toBe(true)
    expect(wrapper.text()).toContain('注册未开放')
    expect(wrapper.find('.register-form').exists()).toBe(false)
  })

  it('renders the register form when the switch is open', async () => {
    registerApi.loadRegisterSwitch.mockResolvedValue(true)

    const wrapper = await mountAt(RegisterPage, PATHS.REGISTER)

    expect(wrapper.find('.register-closed').exists()).toBe(false)
    expect(wrapper.find('.register-form').exists()).toBe(true)
    expect(wrapper.findAll('input')).toHaveLength(5)
  })

  it('navigates to login with the username after a successful registration', async () => {
    registerApi.loadRegisterSwitch.mockResolvedValue(true)
    registerApi.submitRegister.mockResolvedValue(undefined)

    const { pinia } = setupSession({ token: '' })
    const router = createTestRouter(routes)
    await router.push(PATHS.REGISTER)
    await router.isReady()
    const wrapper = mount(RegisterPage, { global: { plugins: [pinia, router, ElementPlus] } })
    await flushPromises()

    const inputs = wrapper.findAll('input')
    await inputs[0].setValue('reg-alice')
    await inputs[1].setValue('secret1')
    await inputs[2].setValue('secret1')
    await inputs[3].setValue('爱丽丝')
    await wrapper.find('.register-btn').trigger('click')
    await flushPromises()

    expect(registerApi.submitRegister).toHaveBeenCalledWith({
      username: 'reg-alice',
      password: 'secret1',
      confirm: 'secret1',
      name: '爱丽丝',
      phone: '',
    })
    expect(router.currentRoute.value.path).toBe(PATHS.LOGIN)
    expect(router.currentRoute.value.query.username).toBe('reg-alice')
  })
})
