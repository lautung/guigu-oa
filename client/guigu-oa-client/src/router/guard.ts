import type { Pinia } from 'pinia'
import type { Router } from 'vue-router'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'
import { resolveAuthGuard } from '@/navigation'
import { ROUTES } from '@/navigation/constants'
import { useUserStore } from '@/store/modules/user'
import type { Recognition } from '@/recognition'

export function setupNavigation(router: Router, pinia: Pinia, recognition: Recognition): void {
  NProgress.configure({ showSpinner: false })
  router.beforeEach(async (to) => {
    NProgress.start()
    document.title = to.meta.title ? `${to.meta.title} - 云尚办公` : '云尚办公'
    const store = useUserStore(pinia)
    if (recognition.credentials().token && to.path !== ROUTES.LOGIN && !store.recognized) {
      try {
        await recognition.recognize(to.fullPath)
      } catch {
        NProgress.done()
        return false
      }
    }
    const decision = resolveAuthGuard({
      path: to.path,
      fullPath: to.fullPath,
      hasToken: !!recognition.credentials().token,
      menus: store.menus,
    })
    if (decision.action === 'redirect') {
      NProgress.done()
      return decision.to
    }
  })
  router.afterEach(() => NProgress.done())
}
