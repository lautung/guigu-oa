import { reactive } from 'vue'

import { failureMessage } from '@/request/request-failure'
import type { RegisterPayload } from '@/types/domain'

/**
 * 注册页的状态机（#253，ADR 0062）：只依赖三个适配器动作——探测开关、提交、跳登录。
 *
 * <p>开关关闭时整页是关闭态：`loadSwitch` 把 `enabled` 置 false，`submit` 直接拒绝且不调
 * 适配器。成功注册**不自动登录**，由适配器带着用户名跳登录页；校验文案逐字镜像服务端
 * `RegisterVo`，真正的判定仍在服务端。
 */
export interface RegisterAdapter {
  loadSwitch(): Promise<boolean>
  submit(payload: RegisterPayload): Promise<unknown>
  notify(message: string, type?: 'success' | 'error'): void
  /** 成功后的落点：登录页并用 username 预填。 */
  goToLogin(username: string): void | Promise<void>
}

export interface RegisterState {
  loading: boolean
  loaded: boolean
  enabled: boolean
  submitting: boolean
  username: string
  password: string
  confirm: string
  name: string
  phone: string
  formError: string
}

export const REGISTER_TEXT = {
  closed: '注册未开放',
  passwordMismatch: '两次输入的密码不一致',
  success: '注册成功，请登录',
} as const

function emptyState(): RegisterState {
  return {
    loading: false,
    loaded: false,
    enabled: false,
    submitting: false,
    username: '',
    password: '',
    confirm: '',
    name: '',
    phone: '',
    formError: '',
  }
}

/** 与服务端 RegisterVo 同文案、同顺序；空手机合法。 */
export function validateRegister(payload: RegisterPayload): string {
  const username = (payload.username || '').trim()
  const name = (payload.name || '').trim()
  const phone = (payload.phone || '').trim()
  if (!username) return '用户名不能为空'
  if (username.length > 20) return '用户名过长'
  if (!payload.password) return '密码不能为空'
  if (payload.password.length < 6 || payload.password.length > 100) {
    return '密码长度必须在 6 到 100 位之间'
  }
  if (!payload.confirm) return '确认密码不能为空'
  if (!name) return '姓名不能为空'
  if (name.length > 20) return '姓名过长'
  if (phone.length > 20) return '手机号过长'
  if (payload.password !== payload.confirm) return REGISTER_TEXT.passwordMismatch
  return ''
}

export function createRegister(adapter: RegisterAdapter) {
  const state = reactive<RegisterState>(emptyState())

  /** 登录页与注册页共用的一次探测；失败按关闭处理（fail closed）。 */
  async function loadSwitch(): Promise<boolean> {
    state.loading = true
    try {
      state.enabled = await adapter.loadSwitch()
    } catch {
      state.enabled = false
    } finally {
      state.loading = false
      state.loaded = true
    }
    return state.enabled
  }

  async function submit(): Promise<boolean> {
    if (!state.enabled) {
      state.formError = REGISTER_TEXT.closed
      return false
    }
    const payload: RegisterPayload = {
      username: state.username.trim(),
      password: state.password,
      confirm: state.confirm,
      name: state.name.trim(),
      phone: state.phone.trim(),
    }
    const invalid = validateRegister(payload)
    if (invalid) {
      state.formError = invalid
      return false
    }

    state.submitting = true
    state.formError = ''
    try {
      await adapter.submit(payload)
      state.password = ''
      state.confirm = ''
      adapter.notify(REGISTER_TEXT.success, 'success')
      await adapter.goToLogin(payload.username)
      return true
    } catch (error) {
      state.formError = failureMessage(error) || '注册失败'
      return false
    } finally {
      state.submitting = false
    }
  }

  return { state, loadSwitch, submit }
}
