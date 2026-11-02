import axios, { type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import type { Recognition, RequestContext } from '@/recognition'
import {
  businessEnvelope,
  businessFailure,
  rejectRequest,
  transportFailure,
  type TransportScopeFields,
} from '@/request/request-failure'
import {
  createRenewal,
  isAuthentication,
  setRequestToken,
  type RenewableRequestConfig,
} from '@/request/renewal'
import type { ApiClient, ApiRequestConfig, ApiResponse } from '@/types/domain'

const baseURL = import.meta.env.VITE_APP_BASE_API || '/dev-api'

export interface ScopedRequestConfig extends ApiRequestConfig, TransportScopeFields {}

type RefreshableRequestConfig = InternalAxiosRequestConfig & TransportScopeFields

/**
 * 业务信封的统一处理（#145 后两个入口共用）：HTTP 200 响应体与 401/404/405 等
 * 错误响应体都是 {code,message,data} 信封。成功 200 直通；208 交给续期 module
 * （ADR 0048）裁决重放；其余按业务码拒绝。只有没有信封体的传输错误才走
 * transportFailure。
 */
export function createRequestClient(recognition: Recognition): ApiClient {
  const service = axios.create({ baseURL, timeout: 15000 })
  const resend = (config: RenewableRequestConfig) =>
    service(config) as unknown as Promise<ApiResponse<unknown>>
  const renewal = createRenewal(recognition, resend)
  service.interceptors.request.use((config: RefreshableRequestConfig) => {
    config._recognition ??= recognition.capture(isInfo(config.url) || isAuthentication(config.url))
    recognition.assertCurrent(config._recognition)
    const token = recognition.credentials().token
    if (token) setRequestToken(config, token)
    return config
  })

  async function handleResponse(response: AxiosResponse<ApiResponse<unknown>>) {
    const config = response.config as RefreshableRequestConfig
    const context = config._recognition || recognition.capture(isInfo(config.url))
    return processEnvelope(config, context, response.data)
  }

  async function processEnvelope(
    config: RefreshableRequestConfig,
    context: RequestContext,
    res: ApiResponse<unknown> | undefined,
  ) {
    recognition.assertCurrent(context)
    if (res?.code === 200) return res
    if (res?.code === 208) {
      const replay = renewal.onExpired(config, context)
      if (replay) return replay
    }
    throw rejectRequest(recognition, config, context, businessFailure(res || {}))
  }

  service.interceptors.response.use(
    (response) => handleResponse(response) as unknown as AxiosResponse,
    (error: unknown) => {
      const config = axios.isAxiosError(error)
        ? (error.config as RefreshableRequestConfig)
        : undefined
      const envelope = businessEnvelope(error)
      // #145：认证失败 401 + code 208/209、容器级 404/405 + code 201 仍带业务信封，
      // 按 body.code 走业务分支，不降级成传输错误。5xx（网关/服务器故障）即使碰巧
      // 带了 JSON 体也保持传输失败语义，由上层保留待确认。
      if (config && envelope) {
        const context = config._recognition || recognition.capture(isInfo(config.url))
        return processEnvelope(config, context, envelope) as unknown as AxiosResponse
      }
      throw rejectRequest(
        recognition,
        config || ({} as RefreshableRequestConfig),
        config?._recognition || recognition.capture(),
        transportFailure(error),
      )
    },
  )
  return service as unknown as ApiClient
}

function isInfo(url = ''): boolean {
  return /\/admin\/system\/index\/info(?:[/?]|$)/.test(url)
}

let client: ApiClient | undefined
export function configureRequest(recognition: Recognition): void {
  client = createRequestClient(recognition)
}
const request: ApiClient = (config) => {
  if (!client) throw new Error('认出 module 尚未接入')
  return client(config)
}
export default request
