import axios from 'axios'
import { Message } from 'element-ui'
import store from '@/store'
import { getToken } from '@/utils/auth'
import { ROUTES } from '@/navigation/access-policy'
import router from '@/router'

const service = axios.create({
  baseURL: process.env.VUE_APP_BASE_API || '/dev-api',
  timeout: 15000
})

service.interceptors.request.use(
  config => {
    if (store.getters.token) {
      config.headers['token'] = getToken()
    }
    return config
  },
  error => Promise.reject(error)
)

service.interceptors.response.use(
  response => {
    const res = response.data
    if (res.code !== 200) {
      Message({
        message: res.message || '请求失败',
        type: 'error',
        duration: 5 * 1000
      })

      if (res.code === 208) {
        store.dispatch('user/resetToken').then(() => {
          router.replace(`${ROUTES.LOGIN}?redirect=${encodeURIComponent(router.currentRoute.fullPath)}`)
        })
      }
      return Promise.reject(new Error(res.message || 'Error'))
    }
    return res
  },
  error => {
    Message({
      message: error.message,
      type: 'error',
      duration: 5 * 1000
    })
    return Promise.reject(error)
  }
)

export default service
