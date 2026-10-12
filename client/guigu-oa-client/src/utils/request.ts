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
}

let refreshPromise: Promise<string> | null = null

service.interceptors.request.use(
  (config) => {
    const token = getToken()
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
  if (res.code === 200) {
    return res
  }

  const config = response.config as RefreshableRequestConfig
  const isExpiredAccessToken = res.code === 208
  const canRefresh =
    isExpiredAccessToken &&
    !config._authRefreshRetried &&
    !isAuthenticationEndpoint(config.url) &&
    getRefreshToken()

  if (canRefresh) {
    config._authRefreshRetried = true
    try {
      const token = await refreshAccessToken()
      setRequestToken(config, token)
      return (await service(config)) as unknown as ApiResponse<unknown>
    } catch (error: unknown) {
      showError(errorMessage(error) || '登录已过期，请重新登录')
      clearLoginStateAndRedirect()
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

function refreshAccessToken(): Promise<string> {
  if (!refreshPromise) {
    const refreshToken = getRefreshToken()
    refreshPromise = axios
      .post<ApiResponse<LoginTokens>>(refreshUrl, { refreshToken }, { timeout: 15000 })
      .then(({ data: response }) => {
        const tokens = response.data
        if (response.code !== 200 || !tokens?.token || !tokens.refreshToken) {
          throw new Error(response.message || '登录已过期，请重新登录')
        }
        setToken(tokens.token)
        setRefreshToken(tokens.refreshToken)
        return tokens.token
      })
      .finally(() => {
        refreshPromise = null
      })
  }
  return refreshPromise
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

function clearLoginStateAndRedirect(): void {
  removeToken()
  import('@/store/modules/user')
    .then(({ useUserStore }) => {
      useUserStore().resetToken()
      return import('@/router')
    })
    .then(({ default: router }) => {
      if (router.currentRoute.value.path !== ROUTES.LOGIN) {
        router.replace(
          `${ROUTES.LOGIN}?redirect=${encodeURIComponent(router.currentRoute.value.fullPath)}`,
        )
      }
    })
}

export default service as unknown as ApiClient
