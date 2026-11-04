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

/** 二进制读的入口（公告图片）：响应体直通 Blob，不经过业务信封。 */
export type BinaryRequestConfig = ApiRequestConfig & { responseType: 'blob' }

export type BinaryClient = (config: ScopedRequestConfig & { responseType: 'blob' }) => Promise<Blob>

export interface BinaryRequestOptions {
  /** 背景取图失败不弹全局提示；默认与普通请求一致。 */
  notifyNormalErrors?: boolean
}

type RefreshableRequestConfig = InternalAxiosRequestConfig & TransportScopeFields

interface RequestTransport {
  client: ApiClient
  binary: BinaryClient
}

/**
 * 业务信封的统一处理（#145 后两个入口共用）：HTTP 200 响应体与 401/404/405 等
 * 错误响应体都是 {code,message,data} 信封。成功 200 直通；208 交给续期 module
 * （ADR 0048）裁决重放；其余按业务码拒绝。只有没有信封体的传输错误才走
 * transportFailure。
 *
 * 二进制读（`responseType: 'blob'`）：成功时直通 Blob；但 gate 拒绝是 HTTP 200 + JSON
 * 信封（导出/模板下载的 205），所以 Content-Type 为 JSON 时按信封分类，只有真正的
 * 二进制才直通。4xx 的 JSON 错误体同样被 axios 包成 Blob（401+208 的续期拒绝、
 * 205/201 的命名失败），错误拦截器先把 Blob 解成信封再交给既有分类；真正的二进制
 * 4xx/5xx 仍走传输失败（图片失败不弹全局提示，由调用方保持原 `src`）。续期状态机与
 * JSON 入口共用同一个 transport。
 */
const JSON_CONTENT_TYPE = /(?:application|text)\/json/i

/** blob 响应的 Content-Type（或 Blob 自身类型）为 JSON 时，body 其实是业务信封。 */
function isJsonPayload(response: AxiosResponse, data: unknown): data is Blob {
  if (!(data instanceof Blob)) return false
  const headerType = String(response.headers?.['content-type'] || '')
  return JSON_CONTENT_TYPE.test(headerType) || JSON_CONTENT_TYPE.test(data.type)
}

/** 只认带数字 code 的 JSON 信封；解析失败/普通 JSON 仍按二进制直通。 */
async function readEnvelope(data: Blob): Promise<ApiResponse<unknown> | undefined> {
  try {
    const text = await readBlobText(data)
    if (!text.trim()) return undefined
    const parsed = JSON.parse(text) as ApiResponse<unknown>
    return parsed && typeof parsed.code === 'number' ? parsed : undefined
  } catch {
    return undefined
  }
}

/** 现代浏览器有 Blob.text；jsdom/旧环境退回 FileReader。 */
function readBlobText(data: Blob): Promise<string> {
  if (typeof data.text === 'function') return data.text()
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result || ''))
    reader.onerror = () => reject(reader.error)
    reader.readAsText(data)
  })
}

function createTransport(recognition: Recognition): RequestTransport {
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
    if (config.responseType === 'blob') {
      recognition.assertCurrent(context)
      if (isJsonPayload(response, response.data)) {
        const envelope = await readEnvelope(response.data)
        if (envelope) return processEnvelope(config, context, envelope) as unknown as AxiosResponse
      }
      return response.data as unknown as AxiosResponse
    }
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

  /**
   * #259 二轮：`responseType: 'blob'` 的错误响应体也会被 axios 包成 Blob，
   * `businessEnvelope` 读不到 code 就会把 401+208 降级成传输失败（续期不触发、会话
   * 不结束）。先从 Blob 解析出信封并写回 `error.response.data`，后续分类与 JSON 入口一致。
   * 解析失败/真实二进制保持原样。
   */
  async function normalizeBlobError(error: unknown): Promise<void> {
    if (!axios.isAxiosError(error) || !error.response) return
    const config = error.config as RefreshableRequestConfig | undefined
    if (config?.responseType !== 'blob') return
    const data = error.response.data
    if (!isJsonPayload(error.response, data)) return
    const envelope = await readEnvelope(data)
    if (envelope) error.response.data = envelope
  }

  service.interceptors.response.use(
    (response) => handleResponse(response) as unknown as AxiosResponse,
    async (error: unknown) => {
      await normalizeBlobError(error)
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
  return {
    client: service as unknown as ApiClient,
    binary: ((config: ScopedRequestConfig & { responseType: 'blob' }) =>
      service(config)) as unknown as BinaryClient,
  }
}

export function createRequestClient(recognition: Recognition): ApiClient {
  return createTransport(recognition).client
}

function isInfo(url = ''): boolean {
  return /\/admin\/system\/index\/info(?:[/?]|$)/.test(url)
}

let client: ApiClient | undefined
let binary: BinaryClient | undefined
export function configureRequest(recognition: Recognition): void {
  const transport = createTransport(recognition)
  client = transport.client
  binary = transport.binary
}
const request: ApiClient = (config) => {
  if (!client) throw new Error('认出 module 尚未接入')
  return client(config)
}

/** 二进制读的模块级入口：与 JSON 入口共用同一个 transport（含认证头与续期）。 */
export function requestBinary(
  config: BinaryRequestConfig,
  options: BinaryRequestOptions = {},
): Promise<Blob> {
  if (!binary) throw new Error('认出 module 尚未接入')
  return binary({ ...config, _notifyNormalErrors: options.notifyNormalErrors })
}

export default request
