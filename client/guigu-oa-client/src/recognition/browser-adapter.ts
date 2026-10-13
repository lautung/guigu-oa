import { markRaw } from 'vue'
import type { Pinia } from 'pinia'
import type { Router } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/modules/user'
import { getInfo } from '@/api/user'
import { configureRequest } from '@/utils/request'
import {
  getToken,
  getRefreshToken,
  getEntryId,
  setEntryId,
  setToken,
  setRefreshToken,
  removeToken,
} from '@/utils/auth'
import { ROUTES } from '@/navigation/constants'
import { createRecognition } from './index'

export function setupRecognition(pinia: Pinia, router: Router) {
  const store = useUserStore(pinia)
  function newEntryId(): string {
    return Array.from(crypto.getRandomValues(new Uint32Array(4)), (part) =>
      part.toString(16).padStart(8, '0'),
    ).join('')
  }
  // Existing token-only entries acquire the same origin-wide marker once at startup.
  if (getToken() && !getEntryId()) setEntryId(newEntryId())
  const recognition = createRecognition({
    credentials: () => ({ token: getToken() || '', refreshToken: getRefreshToken() }),
    entryId: () => getEntryId() || '',
    writeTokens(tokens) {
      const entryId = getEntryId() || newEntryId()
      removeToken()
      setToken(tokens.token)
      if (tokens.refreshToken) setRefreshToken(tokens.refreshToken)
      setEntryId(entryId)
      store.token = tokens.token
      store.refreshToken = tokens.refreshToken || ''
    },
    clearUser() {
      removeToken()
      store.$patch({
        token: '',
        refreshToken: '',
        name: '',
        avatar: '',
        menus: [],
        recognized: false,
      })
    },
    hasUser: () => store.recognized,
    writeUser(user) {
      store.$patch({
        name: user.name,
        avatar: user.avatar || '',
        menus: user.routers || [],
        recognized: true,
      })
    },
    recovery: () => store.recovery,
    showRecovery: (state) => {
      store.recovery = state
    },
    loadUser: async () => (await getInfo()).data,
    currentPath: () => router.currentRoute.value.fullPath,
    goTo: (path) => {
      void router.replace(path)
    },
    goToLogin: (target) => {
      if (router.currentRoute.value.path !== ROUTES.LOGIN) {
        void router.replace(`${ROUTES.LOGIN}?redirect=${encodeURIComponent(target)}`)
      }
    },
    notify: (message) => {
      ElMessage({ message, type: 'error', duration: 5000 })
    },
  })
  store.recognition = markRaw(recognition)
  configureRequest(recognition)
  return recognition
}
