import axios, { type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { RecognitionFailure, type Recognition, type RequestContext } from '@/recognition'
import type { ApiClient, ApiResponse, LoginTokens } from '@/types/domain'

const baseURL = import.meta.env.VITE_APP_BASE_API || '/dev-api'
const refreshUrl = `${baseURL.replace(/\/$/, '')}/admin/system/index/refresh`
type RefreshableRequestConfig = InternalAxiosRequestConfig & {
  _authRefreshRetried?: boolean
  _recognition?: RequestContext
}

function businessFailure(response: Partial<ApiResponse<unknown>>): RecognitionFailure {
  const kind =
    response.code === 208
      ? 'invalid'
      : response.code === 209
        ? 'disabled'
        : response.code === 403
          ? 'forbidden'
          : 'retryable'
  return new RecognitionFailure(kind, response.message || '请求失败')
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
        if (!response || response.code !== 200) throw businessFailure(response || {})
        if (!response.data?.token || !response.data.refreshToken)
          throw new RecognitionFailure('retryable', '暂时无法验证用户，请重试')
        recognition.commitRefresh(context, response.data)
        return response.data.token
      })
      .finally(() => {
        if (refreshFlight?.promise === promise) refreshFlight = null
      })
    refreshFlight = { context, promise }
    return promise
  }

  async function handleResponse(response: AxiosResponse<ApiResponse<unknown>>) {
    const config = response.config as RefreshableRequestConfig
    const context = config._recognition || recognition.capture(isInfo(config.url))
    recognition.assertCurrent(context)
    const res = response.data
    if (res?.code === 200) return res
    if (res?.code === 208 && !config._authRefreshRetried && !isAuthentication(config.url)) {
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
          throw recognition.reject(context, error)
        }
        return service(config)
      }
    }
    throw recognition.reject(context, businessFailure(res || {}))
  }

  service.interceptors.response.use(
    (response) => handleResponse(response) as unknown as AxiosResponse,
    (error: unknown) => {
      const config = axios.isAxiosError(error)
        ? (error.config as RefreshableRequestConfig)
        : undefined
      throw recognition.reject(config?._recognition || recognition.capture(), error)
    },
  )
  return service as unknown as ApiClient
}

function isInfo(url = ''): boolean {
  return /\/admin\/system\/index\/info(?:[/?]|$)/.test(url)
}
function isAuthentication(url = ''): boolean {
  return /\/admin\/system\/index\/(login|refresh)(?:[/?]|$)/.test(url)
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
