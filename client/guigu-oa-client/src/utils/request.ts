import axios, { type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { RecognitionFailure, type Recognition, type RequestContext } from '@/recognition'
import type { ApiClient, ApiRequestConfig, ApiResponse, LoginTokens } from '@/types/domain'

const baseURL = import.meta.env.VITE_APP_BASE_API || '/dev-api'
const refreshUrl = `${baseURL.replace(/\/$/, '')}/admin/system/index/refresh`

/** Transport-only scope fields; produce them through request-scope, never from domain code. */
export interface ScopedRequestConfig extends ApiRequestConfig {
  _recognition?: RequestContext
  _isRelevant?: () => boolean
  _notifyNormalErrors?: boolean
}

type RefreshableRequestConfig = InternalAxiosRequestConfig & {
  _authRefreshRetried?: boolean
  _recognition?: RequestContext
  _isRelevant?: () => boolean
  _notifyNormalErrors?: boolean
}

export interface RequestFailureDetail {
  source: 'business' | 'http' | 'timeout' | 'network' | 'unexpected'
  operation: 'request' | 'refresh'
  businessCode?: number
  httpStatus?: number
  originalBusinessCode?: number
}

export class RequestFailure extends RecognitionFailure {
  constructor(
    kind: ConstructorParameters<typeof RecognitionFailure>[0],
    message: string,
    readonly detail: RequestFailureDetail,
  ) {
    super(kind, message)
    this.name = 'RequestFailure'
  }
}

export class BusinessRequestFailure extends RequestFailure {
  constructor(
    kind: ConstructorParameters<typeof RecognitionFailure>[0],
    message: string,
    readonly businessCode?: number,
    detail: RequestFailureDetail = {
      source: businessCode === undefined ? 'unexpected' : 'business',
      operation: 'request',
      businessCode,
    },
  ) {
    super(kind, message, detail)
  }
}

function businessFailure(
  response: Partial<ApiResponse<unknown>>,
  operation: 'request' | 'refresh' = 'request',
): RequestFailure {
  const kind =
    response.code === 208
      ? 'invalid'
      : response.code === 209
        ? 'disabled'
        : response.code === 403
          ? 'forbidden'
          : 'retryable'
  return new BusinessRequestFailure(
    kind,
    response.message || '请求失败',
    [201, 208, 209, 403].includes(response.code || 0) ? response.code : undefined,
    {
      source: typeof response.code === 'number' ? 'business' : 'unexpected',
      operation,
      businessCode: response.code,
    },
  )
}

function transportFailure(
  error: unknown,
  operation: 'request' | 'refresh' = 'request',
): RecognitionFailure {
  if (error instanceof RecognitionFailure) return error
  const status = axios.isAxiosError(error) ? error.response?.status : undefined
  const timeout =
    axios.isAxiosError(error) && ['ECONNABORTED', 'ETIMEDOUT'].includes(error.code || '')
  return new RequestFailure('retryable', error instanceof Error ? error.message : '请求失败', {
    source: status
      ? 'http'
      : timeout
        ? 'timeout'
        : axios.isAxiosError(error)
          ? 'network'
          : 'unexpected',
    operation,
    httpStatus: status,
  })
}

export function createRequestClient(recognition: Recognition): ApiClient {
  const service = axios.create({ baseURL, timeout: 15000 })
  let refreshFlight: { context: RequestContext; promise: Promise<string> } | null = null
  service.interceptors.request.use((config: RefreshableRequestConfig) => {
    config._recognition ??= recognition.capture(isInfo(config.url) || isAuthentication(config.url))
    recognition.assertCurrent(config._recognition)
    const token = recognition.credentials().token
    if (token) setRequestToken(config, token)
    return config
  })

  function refreshAccessToken(context: RequestContext): Promise<string> {
    recognition.assertCurrent(context)
    if (
      refreshFlight?.context.generation === context.generation &&
      refreshFlight.context.entryId === context.entryId
    )
      return refreshFlight.promise
    const refreshToken = recognition.credentials().refreshToken
    const promise = axios
      .post<ApiResponse<LoginTokens>>(refreshUrl, { refreshToken }, { timeout: 15000 })
      .then(({ data: response }) => {
        recognition.assertCurrent(context)
        if (!response || response.code !== 200) throw businessFailure(response || {}, 'refresh')
        if (!response.data?.token || !response.data.refreshToken)
          throw new RequestFailure('retryable', '暂时无法验证用户，请重试', {
            source: 'unexpected',
            operation: 'refresh',
          })
        recognition.commitRefresh(context, response.data)
        return response.data.token
      })
      .catch((error: unknown) => {
        const reason = transportFailure(error, 'refresh')
        if (reason instanceof RequestFailure) {
          throw new RequestFailure(reason.kind, reason.message, {
            ...reason.detail,
            originalBusinessCode: 208,
          })
        }
        throw reason
      })
      .finally(() => {
        if (refreshFlight?.promise === promise) refreshFlight = null
      })
    refreshFlight = { context, promise }
    return promise
  }

  function rejectRequest(
    config: RefreshableRequestConfig,
    context: RequestContext,
    error: unknown,
  ) {
    const silent = context.silent || config._isRelevant?.() === false
    return recognition.reject({ ...context, silent }, error, config._notifyNormalErrors !== false)
  }

  async function handleResponse(response: AxiosResponse<ApiResponse<unknown>>) {
    const config = response.config as RefreshableRequestConfig
    const context = config._recognition || recognition.capture(isInfo(config.url))
    return processEnvelope(config, context, response.data)
  }

  /**
   * 业务信封的统一处理（#145 后两个入口共用）：HTTP 200 响应体与 401/404/405 等
   * 错误响应体都是 {code,message,data} 信封。成功 200 直通；208 触发一次刷新重试；
   * 其余按业务码拒绝。只有没有信封体的传输错误才走 transportFailure。
   */
  async function processEnvelope(
    config: RefreshableRequestConfig,
    context: RequestContext,
    res: ApiResponse<unknown> | undefined,
  ) {
    recognition.assertCurrent(context)
    if (res?.code === 200) return res
    if (
      res?.code === 208 &&
      !config._authRefreshRetried &&
      !isAuthentication(config.url) &&
      config._isRelevant?.() !== false
    ) {
      config._authRefreshRetried = true
      const latestToken = recognition.credentials().token
      if (latestToken && getRequestToken(config) !== latestToken) {
        setRequestToken(config, latestToken)
        return service(config)
      }
      if (recognition.credentials().refreshToken) {
        try {
          const token = await refreshAccessToken(context)
          setRequestToken(config, token)
        } catch (error: unknown) {
          const reason = rejectRequest(config, context, error)
          // The original business request was explicitly refused before authentication recovery.
          throw new BusinessRequestFailure(
            reason.kind,
            reason.message,
            208,
            reason instanceof RequestFailure
              ? reason.detail
              : { source: 'unexpected', operation: 'refresh', originalBusinessCode: 208 },
          )
        }
        return service(config)
      }
    }
    throw rejectRequest(config, context, businessFailure(res || {}))
  }

  service.interceptors.response.use(
    (response) => handleResponse(response) as unknown as AxiosResponse,
    (error: unknown) => {
      const config = axios.isAxiosError(error)
        ? (error.config as RefreshableRequestConfig)
        : undefined
      const status = axios.isAxiosError(error) ? error.response?.status : undefined
      const body = axios.isAxiosError(error) ? error.response?.data : undefined
      // #145：认证失败 401 + code 208/209、容器级 404/405 + code 201 仍带业务信封，
      // 按 body.code 走业务分支，不降级成传输错误。5xx（网关/服务器故障）即使碰巧
      // 带了 JSON 体也保持传输失败语义，由上层保留待确认。
      if (
        config &&
        status !== undefined &&
        status >= 400 &&
        status < 500 &&
        body &&
        typeof (body as ApiResponse<unknown>).code === 'number'
      ) {
        const context = config._recognition || recognition.capture(isInfo(config.url))
        return processEnvelope(
          config,
          context,
          body as ApiResponse<unknown>,
        ) as unknown as AxiosResponse
      }
      throw rejectRequest(
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
function isAuthentication(url = ''): boolean {
  return /\/admin\/system\/index\/(login|refresh|logout)(?:[/?]|$)/.test(url)
}
function setRequestToken(config: InternalAxiosRequestConfig, token: string): void {
  if (typeof config.headers.set === 'function') config.headers.set('token', token)
  else Object.assign(config.headers, { token })
}
function getRequestToken(config: InternalAxiosRequestConfig): string {
  const headers = config.headers as unknown as { get?: (name: string) => unknown; token?: unknown }
  const value = typeof headers.get === 'function' ? headers.get('token') : headers.token
  return typeof value === 'string' ? value : ''
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
