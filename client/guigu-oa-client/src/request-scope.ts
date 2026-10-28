import type { Recognition } from '@/recognition'
import type { ApiRequestConfig, ApiResponse } from '@/types/domain'
import request, { type ScopedRequestConfig } from '@/utils/request'

export interface RequestScopeOptions {
  notifyNormalErrors?: boolean
}

export interface RequestScope {
  request<TData = unknown>(
    config: ApiRequestConfig,
    isRelevant?: () => boolean,
  ): Promise<ApiResponse<TData>>
  current(): boolean
}

export type ScopeRecognition = Pick<Recognition, 'capture' | 'assertCurrent'>
export type ScopeTransport = (config: ScopedRequestConfig) => Promise<ApiResponse<unknown>>

/** One opening owns its captured identity; relevance stays a per-request predicate. */
export function createRequestScope(
  recognition: ScopeRecognition,
  send: ScopeTransport,
  options: RequestScopeOptions = {},
): RequestScope {
  const context = recognition.capture()
  const notifyNormalErrors = options.notifyNormalErrors ?? true
  return {
    current() {
      try {
        recognition.assertCurrent(context)
        return true
      } catch {
        return false
      }
    },
    request<TData = unknown>(config: ApiRequestConfig, isRelevant?: () => boolean) {
      const scoped: ScopedRequestConfig = {
        ...config,
        _recognition: context,
        _notifyNormalErrors: notifyNormalErrors,
      }
      if (isRelevant) scoped._isRelevant = isRelevant
      return send(scoped) as Promise<ApiResponse<TData>>
    },
  }
}

export function openRequestScope(
  recognition: ScopeRecognition,
  options: RequestScopeOptions = {},
): RequestScope {
  return createRequestScope(recognition, (config) => request(config), options)
}
