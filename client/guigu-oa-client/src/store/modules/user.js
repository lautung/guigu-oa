import { defineStore } from 'pinia'
import {
  getToken,
  getRefreshToken,
  setToken,
  setRefreshToken,
  removeRefreshToken,
  removeToken
} from '@/utils/auth'
import { login, getInfo } from '@/api/user'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: getToken() || '',
    refreshToken: getRefreshToken() || '',
    name: '',
    avatar: '',
    menus: []
  }),

  actions: {
    async login(userInfo) {
      const { username, password } = userInfo
      const response = await login({ username: username.trim(), password })
      const { data } = response
      this.token = data.token
      this.refreshToken = data.refreshToken || ''
      setToken(data.token)
      if (data.refreshToken) {
        setRefreshToken(data.refreshToken)
      } else {
        removeRefreshToken()
      }
    },

    async getInfo() {
      const response = await getInfo()
      const { data } = response
      if (!data) {
        throw new Error('验证失败，请重新登录')
      }
      this.name = data.name
      this.avatar = data.avatar || ''
      this.menus = data.routers || []
      return data
    },

    async logout() {
      this.resetToken()
    },

    resetToken() {
      removeToken()
      this.token = ''
      this.refreshToken = ''
      this.name = ''
      this.avatar = ''
      this.menus = []
    }
  }
})
