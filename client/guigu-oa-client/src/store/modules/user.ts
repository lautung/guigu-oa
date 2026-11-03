import { defineStore } from 'pinia'
import { getToken, getRefreshToken } from '@/utils/auth'
import { login, logout as logoutRequest } from '@/api/user'
import { getDefaultPostLoginRoute } from '@/navigation/constants'
import type { Recognition, RecoveryState } from '@/recognition'
import type { BackofficeMenuEntry, LoginCredentials, UserInfo } from '@/types/domain'

interface UserState {
  token: string
  refreshToken: string
  name: string
  avatar: string
  roles: string[]
  permissions: string[]
  menus: BackofficeMenuEntry[]
  recognized: boolean
  recovery: RecoveryState
  recognition: Recognition | null
}

export const useUserStore = defineStore('user', {
  state: (): UserState => ({
    token: getToken() || '',
    refreshToken: getRefreshToken() || '',
    name: '',
    avatar: '',
    roles: [],
    permissions: [],
    menus: [],
    recognized: false,
    recovery: { status: 'idle', target: '' },
    recognition: null,
  }),
  actions: {
    async login(userInfo: LoginCredentials): Promise<void> {
      if (!this.recognition) throw new Error('认出 module 尚未接入')
      const response = await login({
        username: userInfo.username.trim(),
        password: userInfo.password,
      })
      this.recognition.establish(response.data)
    },
    async getInfo(): Promise<UserInfo> {
      if (!this.recognition) throw new Error('认出 module 尚未接入')
      return this.recognition.recognize()
    },
    async logout() {
      const recognition = this.recognition
      if (recognition) {
        try {
          // 让服务端作废这台客户端手里的凭证；失败也要离开。
          await logoutRequest(recognition.credentials().refreshToken || '')
        } catch {
          // 本地退出优先：网络或服务端拒绝都不留在已进入状态。
        }
      }
      recognition?.exit(getDefaultPostLoginRoute())
    },
    resetToken() {
      this.recognition?.forget()
    },
  },
})
