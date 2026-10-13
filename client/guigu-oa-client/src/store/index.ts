import { createPinia } from 'pinia'
import type { App } from 'vue'

export { useUserStore } from './modules/user'

export function setupStore(app: App) {
  const pinia = createPinia()
  app.use(pinia)
  return pinia
}
