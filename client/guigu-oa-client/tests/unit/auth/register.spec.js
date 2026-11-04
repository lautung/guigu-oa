import { describe, expect, it, vi } from 'vitest'

import { REGISTER_TEXT, createRegister, validateRegister } from '@/auth/register'
import { BusinessRequestFailure } from '@/request/request-failure'

function fakeAdapter(overrides = {}) {
  return {
    loadSwitch: vi.fn(async () => true),
    submit: vi.fn(async () => undefined),
    notify: vi.fn(),
    goToLogin: vi.fn(),
    ...overrides,
  }
}

function failure(message, code = 201) {
  return new BusinessRequestFailure('retryable', message, code)
}

function filled(register, overrides = {}) {
  Object.assign(register.state, {
    username: 'reg-alice',
    password: 'secret1',
    confirm: 'secret1',
    name: '爱丽丝',
    phone: '13800001111',
    ...overrides,
  })
  return register
}

describe('auth/register module', () => {
  it('is a closed page when the switch is off and never calls submit', async () => {
    const adapter = fakeAdapter({ loadSwitch: vi.fn(async () => false) })
    const register = createRegister(adapter)

    expect(await register.loadSwitch()).toBe(false)
    expect(register.state.loaded).toBe(true)
    expect(register.state.enabled).toBe(false)

    expect(await register.submit()).toBe(false)
    expect(register.state.formError).toBe(REGISTER_TEXT.closed)
    expect(adapter.submit).not.toHaveBeenCalled()
  })

  it('fails closed when the switch probe itself fails', async () => {
    const adapter = fakeAdapter({
      loadSwitch: vi.fn(async () => Promise.reject(new Error('down'))),
    })
    const register = createRegister(adapter)

    expect(await register.loadSwitch()).toBe(false)
    expect(register.state.enabled).toBe(false)
    expect(register.state.loading).toBe(false)
  })

  it('validates username/password/confirm/name with the server wording', () => {
    expect(
      validateRegister({ username: '', password: 'secret1', confirm: 'secret1', name: '爱丽丝' }),
    ).toBe('用户名不能为空')
    expect(
      validateRegister({
        username: 'u'.repeat(21),
        password: 'secret1',
        confirm: 'secret1',
        name: '爱丽丝',
      }),
    ).toBe('用户名过长')
    expect(validateRegister({ username: 'reg-a', password: '', confirm: '', name: '爱丽丝' })).toBe(
      '密码不能为空',
    )
    expect(
      validateRegister({ username: 'reg-a', password: '12345', confirm: '12345', name: '爱丽丝' }),
    ).toBe('密码长度必须在 6 到 100 位之间')
    expect(
      validateRegister({ username: 'reg-a', password: 'secret1', confirm: '', name: '爱丽丝' }),
    ).toBe('确认密码不能为空')
    expect(
      validateRegister({ username: 'reg-a', password: 'secret1', confirm: 'secret1', name: '  ' }),
    ).toBe('姓名不能为空')
    expect(
      validateRegister({
        username: 'reg-a',
        password: 'secret1',
        confirm: 'secret1',
        name: '名'.repeat(21),
      }),
    ).toBe('姓名过长')
    expect(
      validateRegister({
        username: 'reg-a',
        password: 'secret1',
        confirm: 'secret2',
        name: '爱丽丝',
      }),
    ).toBe('两次输入的密码不一致')
    // 手机可选：空值合法。
    expect(
      validateRegister({
        username: 'reg-a',
        password: 'secret1',
        confirm: 'secret1',
        name: '爱丽丝',
      }),
    ).toBe('')
  })

  it('rejects a mismatch before calling the adapter', async () => {
    const adapter = fakeAdapter()
    const register = createRegister(adapter)
    await register.loadSwitch()
    filled(register, { confirm: 'secret2' })

    expect(await register.submit()).toBe(false)
    expect(register.state.formError).toBe('两次输入的密码不一致')
    expect(adapter.submit).not.toHaveBeenCalled()
  })

  it('submits trimmed fields and goes to login with the username, without auto login', async () => {
    const adapter = fakeAdapter()
    const register = createRegister(adapter)
    await register.loadSwitch()
    filled(register, { username: '  reg-alice  ', name: '  爱丽丝  ', phone: ' 13800001111 ' })

    expect(await register.submit()).toBe(true)

    expect(adapter.submit).toHaveBeenCalledWith({
      username: 'reg-alice',
      password: 'secret1',
      confirm: 'secret1',
      name: '爱丽丝',
      phone: '13800001111',
    })
    expect(adapter.notify).toHaveBeenCalledWith('注册成功，请登录', 'success')
    expect(adapter.goToLogin).toHaveBeenCalledWith('reg-alice')
    expect(register.state.submitting).toBe(false)
    expect(register.state.password).toBe('')
    expect(register.state.confirm).toBe('')
  })

  it('shows the server conflict message and keeps the user on the page', async () => {
    const adapter = fakeAdapter({
      submit: vi.fn(async () => Promise.reject(failure('用户名已存在'))),
    })
    const register = createRegister(adapter)
    await register.loadSwitch()
    filled(register)

    expect(await register.submit()).toBe(false)
    expect(register.state.formError).toBe('用户名已存在')
    expect(adapter.goToLogin).not.toHaveBeenCalled()
    expect(register.state.submitting).toBe(false)
  })

  it('shows the rate-limit message when the server refuses the attempt', async () => {
    const adapter = fakeAdapter({
      submit: vi.fn(async () => Promise.reject(failure('注册尝试过于频繁，请稍后再试'))),
    })
    const register = createRegister(adapter)
    await register.loadSwitch()
    filled(register)

    expect(await register.submit()).toBe(false)
    expect(register.state.formError).toBe('注册尝试过于频繁，请稍后再试')
  })
})
