import axios from 'axios'
import { ElMessage } from 'element-plus'
import { getToken } from '@/utils/auth'
import { ROUTES } from '@/navigation/access-policy'

const service = axios.create({
  baseURL: import.meta.env.VITE_APP_BASE_API || '/dev-api',
  timeout: 15000
})

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
  response => {
    const res = response.data
    if (res.code !== 200) {
      ElMessage({
        message: res.message || '请求失败',
        type: 'error',
        duration: 5 * 1000
      })

      if (res.code === 208) {
        // 动态导入避免循环依赖
        import('@/store/modules/user').then(({ useUserStore }) => {
          const userStore = useUserStore()
          userStore.resetToken()
          const router = import('@/router').then(m => m.default)
          router.then(r => {
            r.replace(`${ROUTES.LOGIN}?redirect=${encodeURIComponent(r.currentRoute.value.fullPath)}`)
          })
        })
      }
      return Promise.reject(new Error(res.message || 'Error'))
    }
    return res
  },
  error => {
    ElMessage({
      message: error.message,
      type: 'error',
      duration: 5 * 1000
    })
    return Promise.reject(error)
  }
)

export default service
