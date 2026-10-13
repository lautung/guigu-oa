import axios, { type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import { getToken, getRefreshToken, setToken, setRefreshToken, removeToken } from '@/utils/auth'
import { ROUTES } from '@/navigation/constants'
import type { ApiClient, ApiResponse, LoginTokens } from '@/types/domain'

const baseURL = import.meta.env.VITE_APP_BASE_API || '/dev-api'
const refreshUrl = `${baseURL.replace(/\/$/, '')}/admin/system/index/refresh`
const service = axios.create({
  baseURL,
  timeout: 15000,
})

type RefreshableRequestConfig = InternalAxiosRequestConfig & {
  _authRefreshRetried?: boolean
  _authSessionRefreshToken?: string
}

let refreshFlight: { refreshToken: string; promise: Promise<string> } | null = null

service.interceptors.request.use(
  (config) => {
    const token = getToken()
    const authConfig = config as RefreshableRequestConfig
    authConfig._authSessionRefreshToken = getRefreshToken() || ''
    if (token) {
      setRequestToken(config, token)
    }
    return config
  },
  (error: unknown) => Promise.reject(error),
)

service.interceptors.response.use(
  (response) =>
    handleResponse(response as AxiosResponse<ApiResponse<unknown>>) as unknown as AxiosResponse,
  (error: unknown) => {
    showError(errorMessage(error))
    return Promise.reject(error)
  },
)

async function handleResponse(
  response: AxiosResponse<ApiResponse<unknown>>,
): Promise<ApiResponse<unknown>> {
  const res = response.data
  const config = response.config as RefreshableRequestConfig
  const sessionChanged =
    config._authSessionRefreshToken !== undefined &&
    config._authSessionRefreshToken !== (getRefreshToken() || '')
  if (sessionChanged && !isAuthenticationEndpoint(config.url)) {
    return Promise.reject(new Error('请求所属的登录状态已变化'))
  }

  if (res.code === 200) {
    return res
  }

  const isExpiredAccessToken = res.code === 208

  const latestToken = getToken()
  const requestToken = getRequestToken(config)
  const requestAlreadyHasRefreshedToken =
    isExpiredAccessToken &&
    !config._authRefreshRetried &&
    !sessionChanged &&
    !!requestToken &&
    !!latestToken &&
    requestToken !== latestToken

  if (requestAlreadyHasRefreshedToken) {
    config._authRefreshRetried = true
    setRequestToken(config, latestToken)
    return (await service(config)) as unknown as ApiResponse<unknown>
  }

  const canRefresh =
    isExpiredAccessToken &&
    !config._authRefreshRetried &&
    !sessionChanged &&
    !isAuthenticationEndpoint(config.url) &&
    getRefreshToken()

  if (canRefresh) {
    config._authRefreshRetried = true
    try {
      const token = await refreshAccessToken(
        config._authSessionRefreshToken || getRefreshToken() || '',
      )
      setRequestToken(config, token)
      return (await service(config)) as unknown as ApiResponse<unknown>
    } catch (error: unknown) {
      if (isRequestSessionCurrent(config)) {
        showError(errorMessage(error) || '登录已过期，请重新登录')
        clearLoginStateAndRedirect(config._authSessionRefreshToken)
      }
      return Promise.reject(error)
    }
  }

  const error = new Error(res.message || '请求失败')
  if (isExpiredAccessToken || res.code === 209) {
    if (res.code === 209) {
      showError(res.message || '账号已停用')
    }
    clearLoginStateAndRedirect()
  } else {
    showError(error.message)
  }
  return Promise.reject(error)
}

function refreshAccessToken(expectedRefreshToken: string): Promise<string> {
  const refreshToken = getRefreshToken() || ''
  if (!refreshToken || refreshToken !== expectedRefreshToken) {
    return Promise.reject(new Error('请求所属的登录状态已变化'))
  }
  if (refreshFlight?.refreshToken === refreshToken) {
    return refreshFlight.promise
  }

  const promise = axios
    .post<ApiResponse<LoginTokens>>(refreshUrl, { refreshToken }, { timeout: 15000 })
    .then(({ data: response }) => {
      if ((getRefreshToken() || '') !== refreshToken) {
        throw new Error('请求所属的登录状态已变化')
      }
      const tokens = response.data
      if (response.code !== 200 || !tokens?.token || !tokens.refreshToken) {
        throw new Error(response.message || '登录已过期，请重新登录')
      }
      setToken(tokens.token)
      setRefreshToken(tokens.refreshToken)
      return tokens.token
    })
    .finally(() => {
      if (refreshFlight?.promise === promise) {
        refreshFlight = null
      }
    })
  refreshFlight = { refreshToken, promise }
  return promise
}

function isAuthenticationEndpoint(url = ''): boolean {
  return /\/admin\/system\/index\/(login|refresh)(?:[/?]|$)/.test(url)
}

function setRequestToken(config: InternalAxiosRequestConfig, token: string): void {
  if (typeof config.headers.set === 'function') {
    config.headers.set('token', token)
  } else {
    Object.assign(config.headers, { token })
  }
}

function getRequestToken(config: InternalAxiosRequestConfig): string {
  const headers = config.headers as unknown as { get?: (name: string) => unknown; token?: unknown }
  const value = typeof headers.get === 'function' ? headers.get('token') : headers.token
  return typeof value === 'string' ? value : ''
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : '请求失败'
}

function showError(message: string): void {
  ElMessage({
    message,
    type: 'error',
    duration: 5 * 1000,
  })
}

function isRequestSessionCurrent(config: RefreshableRequestConfig): boolean {
  return (
    config._authSessionRefreshToken === undefined ||
    config._authSessionRefreshToken === (getRefreshToken() || '')
  )
}

function clearLoginStateAndRedirect(expectedRefreshToken?: string): void {
  const currentRefreshToken = getRefreshToken() || ''
  if (expectedRefreshToken !== undefined && expectedRefreshToken !== currentRefreshToken) {
    return
  }
  removeToken()
  import('@/store/modules/user')
    .then(({ useUserStore }) => {
      if (getToken() || getRefreshToken()) {
        return null
      }
      useUserStore().resetToken()
      return import('@/router')
    })
    .then((routerModule) => {
      if (!routerModule || getToken() || getRefreshToken()) {
        return
      }
      const router = routerModule.default
      if (router.currentRoute.value.path !== ROUTES.LOGIN) {
        router.replace(
          `${ROUTES.LOGIN}?redirect=${encodeURIComponent(router.currentRoute.value.fullPath)}`,
        )
      }
    })
}

export default service as unknown as ApiClient
