import request from '@/request/request'
import type { ApiRequestConfig, RegisterPayload, RegisterSwitchRecord } from '@/types/domain'

const REGISTER_API = '/admin/system/index/register'

export function registerSwitchRequest(): ApiRequestConfig {
  return { method: 'get', url: `${REGISTER_API}/switch` }
}

export function registerRequest(payload: RegisterPayload): ApiRequestConfig {
  return { method: 'post', url: REGISTER_API, data: { ...payload } }
}

/** 登录页与注册页共用的一次探测：只有明确的 `enabled === true` 才算开放。 */
export async function loadRegisterSwitch(): Promise<boolean> {
  const response = await request<RegisterSwitchRecord>(registerSwitchRequest())
  return response.data?.enabled === true
}

export async function submitRegister(payload: RegisterPayload): Promise<void> {
  await request<void>(registerRequest(payload))
}
