import axios from 'axios'
import { RecognitionFailure, type Recognition, type RequestContext } from '@/recognition'
import type { ApiResponse } from '@/types/domain'

/** Transport-only scope fields stamped onto axios configs; produce them through request-scope, never from domain code. */
export interface TransportScopeFields {
  _recognition?: RequestContext
  _isRelevant?: () => boolean
  _notifyNormalErrors?: boolean
  _authRefreshRetried?: boolean
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

/** 页面/按钮点拒绝（ADR 0052）：即使作用域关闭普通错误提示，也要把服务端文案提示一次。 */
export class PermissionRequestFailure extends BusinessRequestFailure {
  constructor(message = '没有操作权限') {
    super('forbidden', message, 205)
  }
}

/** 页面把未知失败投影成一行文案：只认非空字符串 message，其余由调用方兜底。 */
export function failureMessage(failure: unknown): string {
  if (!failure || typeof failure !== 'object' || !('message' in failure)) return ''
  const message = (failure as { message?: unknown }).message
  return typeof message === 'string' ? message : ''
}

export function businessFailure(
  response: Partial<ApiResponse<unknown>>,
  operation: 'request' | 'refresh' = 'request',
): RequestFailure {
  if (response.code === 205) {
    return new PermissionRequestFailure(response.message || '没有操作权限')
  }
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

export function transportFailure(
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

/**
 * 4xx 错误响应若带着业务信封，按业务失败而不是传输损失处理（#145）；refresh 的
 * 401+208/209 加入契约后（#155），两个入口共用这一个判定。
 */
export function businessEnvelope(error: unknown): ApiResponse<unknown> | undefined {
  if (!axios.isAxiosError(error) || !error.response) return undefined
  const { status, data } = error.response
  return status >= 400 &&
    status < 500 &&
    data &&
    typeof (data as ApiResponse<unknown>).code === 'number'
    ? (data as ApiResponse<unknown>)
    : undefined
}

/** 请求失败上报 recognition 的统一接线：按次相关性与错误策略从请求配置读取。 */
export function rejectRequest(
  recognition: Pick<Recognition, 'reject'>,
  config: Pick<TransportScopeFields, '_isRelevant' | '_notifyNormalErrors'>,
  context: RequestContext,
  error: unknown,
): RecognitionFailure {
  const silent = context.silent || config._isRelevant?.() === false
  const notifyNormal =
    config._notifyNormalErrors !== false || error instanceof PermissionRequestFailure
  return recognition.reject({ ...context, silent }, error, notifyNormal)
}

/** #155：refresh 失败以「原业务请求被明确拒绝」的形状上报，统一带上原始业务码。 */
export function refreshRefusal(error: RequestFailure, code = 208): RequestFailure {
  return new RequestFailure(error.kind, error.message, {
    ...error.detail,
    originalBusinessCode: code,
  })
}
