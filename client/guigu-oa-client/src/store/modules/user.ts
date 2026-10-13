import { defineStore } from 'pinia'
import { getToken, getRefreshToken } from '@/utils/auth'
import { login } from '@/api/user'
import type { Recognition, RecoveryState } from '@/recognition'
import type { LoginCredentials, MenuNode, UserInfo } from '@/types/domain'

interface UserState {
  token: string
  refreshToken: string
  name: string
  avatar: string
  menus: MenuNode[]
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
      this.recognition?.exit()
    },
    resetToken() {
      this.recognition?.forget()
    },
  },
})
