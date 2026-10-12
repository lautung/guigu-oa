import { createPinia } from 'pinia'

export { useUserStore } from './modules/user'

export function setupStore(app) {
  const pinia = createPinia()
  app.use(pinia)
}
