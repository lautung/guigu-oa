import axios from 'axios'
import { ElMessage } from 'element-plus'
import {
  getToken,
  getRefreshToken,
  setToken,
  setRefreshToken,
  removeToken
} from '@/utils/auth'
import { ROUTES } from '@/navigation/constants'

const baseURL = import.meta.env.VITE_APP_BASE_API || '/dev-api'
const refreshUrl = `${baseURL.replace(/\/$/, '')}/admin/system/index/refresh`
const service = axios.create({
  baseURL,
  timeout: 15000
})

let refreshPromise = null

service.interceptors.request.use(
  config => {
    const token = getToken()
    if (token) {
      config.headers['token'] = token
    }
    return config
  },
  error => Promise.reject(error)
)

service.interceptors.response.use(
  response => handleResponse(response),
  error => {
    ElMessage({
      message: error.message,
      type: 'error',
      duration: 5 * 1000
    })
    return Promise.reject(error)
  }
)

async function handleResponse(response) {
  const res = response.data
  if (res.code === 200) {
    return res
  }

  const config = response.config
  const isExpiredAccessToken = res.code === 208
  const canRefresh = isExpiredAccessToken
    && config
    && !config._authRefreshRetried
    && !isAuthenticationEndpoint(config.url)
    && getRefreshToken()

  if (canRefresh) {
    config._authRefreshRetried = true
    try {
      const token = await refreshAccessToken()
      config.headers = config.headers || {}
      config.headers.token = token
      return service(config)
    } catch (error) {
      showError(error.message || '登录已过期，请重新登录')
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

function refreshAccessToken() {
  if (!refreshPromise) {
    const refreshToken = getRefreshToken()
    refreshPromise = axios.post(refreshUrl, { refreshToken }, { timeout: 15000 })
      .then(response => {
        const res = response.data
        if (res.code !== 200 || !res.data?.token || !res.data?.refreshToken) {
          throw new Error(res.message || '登录已过期，请重新登录')
        }
        setToken(res.data.token)
        setRefreshToken(res.data.refreshToken)
        return res.data.token
      })
      .finally(() => {
        refreshPromise = null
      })
  }
  return refreshPromise
}

function isAuthenticationEndpoint(url = '') {
  return /\/admin\/system\/index\/(login|refresh)(?:[/?]|$)/.test(url)
}

function showError(message) {
  ElMessage({
    message,
    type: 'error',
    duration: 5 * 1000
  })
}

function clearLoginStateAndRedirect() {
  removeToken()
  import('@/store/modules/user').then(({ useUserStore }) => {
    useUserStore().resetToken()
    return import('@/router')
  }).then(module => {
    const router = module.default
    if (router.currentRoute.value.path !== ROUTES.LOGIN) {
      router.replace(`${ROUTES.LOGIN}?redirect=${encodeURIComponent(router.currentRoute.value.fullPath)}`)
    }
  })
}

export default service
