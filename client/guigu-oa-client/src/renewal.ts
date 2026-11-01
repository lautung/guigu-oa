import axios, { type InternalAxiosRequestConfig } from 'axios'
import type { Recognition, RequestContext } from '@/recognition'
import {
  BusinessRequestFailure,
  RequestFailure,
  businessEnvelope,
  businessFailure,
  refreshRefusal,
  rejectRequest,
  transportFailure,
  type TransportScopeFields,
} from '@/request-failure'
import type { ApiResponse, LoginTokens } from '@/types/domain'

const baseURL = import.meta.env.VITE_APP_BASE_API || '/dev-api'
const refreshUrl = `${baseURL.replace(/\/$/, '')}/admin/system/index/refresh`

/** Renewal 只读写重放标记与按次相关性；其余传输 scope 字段归 request.ts。 */
export type RenewableRequestConfig = InternalAxiosRequestConfig &
  Pick<TransportScopeFields, '_authRefreshRetried' | '_isRelevant'>

/** 认证端点不重放：login 208 是凭证错，refresh 208 是续期本身，logout 是主动退出。 */
export function isAuthentication(url = ''): boolean {
  return /\/admin\/system\/index\/(login|refresh|logout)(?:[/?]|$)/.test(url)
}

export function setRequestToken(config: InternalAxiosRequestConfig, token: string): void {
  if (typeof config.headers.set === 'function') config.headers.set('token', token)
  else Object.assign(config.headers, { token })
}
function getRequestToken(config: InternalAxiosRequestConfig): string {
  const headers = config.headers as unknown as { get?: (name: string) => unknown; token?: unknown }
  const value = typeof headers.get === 'function' ? headers.get('token') : headers.token
  return typeof value === 'string' ? value : ''
}

export type RenewalResend = (config: RenewableRequestConfig) => Promise<ApiResponse<unknown>>

/**
 * 续期状态机的唯一 owner（ADR 0048）：同一进入结果同一时刻只飞一次 refresh，
 * 成功换新头重放被 208 拦下的原请求；refresh 失败被服务端判为认证失败（#155）
 * 才按身份失效上报，临时故障只作可重试失败。
 */
export function createRenewal(recognition: Recognition, resend: RenewalResend) {
  let refreshFlight: { context: RequestContext; promise: Promise<string> } | null = null

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
        // #155：refresh 失败与登录同族（HTTP 401 + 业务信封 208/209），4xx 带信封按业务
        // 失败分类——降级成 transport 会把认证失败归为 retryable，会话不结束。
        const envelope = businessEnvelope(error)
        const reason = envelope
          ? businessFailure(envelope, 'refresh')
          : transportFailure(error, 'refresh')
        if (reason instanceof RequestFailure) {
          throw refreshRefusal(reason)
        }
        throw reason
      })
      .finally(() => {
        if (refreshFlight?.promise === promise) refreshFlight = null
      })
    refreshFlight = { context, promise }
    return promise
  }

  /**
   * 208 的决策与执行：豁免（认证端点、已重试过、不相关）返回 null 交回调用方按业务
   * 失败拒绝；并发已刷新则直接换头重放；否则单飞 refresh 后换头重放。refresh 失败
   * 先按上下文上报 recognition，再以原请求被明确拒绝的形状抛出。
   */
  function onExpired(
    config: RenewableRequestConfig,
    context: RequestContext,
  ): Promise<ApiResponse<unknown>> | null {
    if (
      isAuthentication(config.url) ||
      config._authRefreshRetried ||
      config._isRelevant?.() === false
    )
      return null
    config._authRefreshRetried = true
    const latestToken = recognition.credentials().token
    if (latestToken && getRequestToken(config) !== latestToken) {
      setRequestToken(config, latestToken)
      return resend(config)
    }
    if (!recognition.credentials().refreshToken) return null
    return refreshAndReplay(config, context)
  }

  async function refreshAndReplay(
    config: RenewableRequestConfig,
    context: RequestContext,
  ): Promise<ApiResponse<unknown>> {
    try {
      const token = await refreshAccessToken(context)
      setRequestToken(config, token)
    } catch (error: unknown) {
      const reason = rejectRequest(recognition, config, context, error)
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
    return resend(config)
  }

  return { onExpired }
}
