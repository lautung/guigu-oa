import router from './router'
import store from './store'
import { getToken } from '@/utils/auth'
import { Message } from 'element-ui'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'
import { resolveNavigationGuard } from '@/navigation/access-policy'

NProgress.configure({ showSpinner: false })

function getViewportWidth() {
  return typeof window !== 'undefined' ? window.innerWidth : 1024
}

router.beforeEach(async(to, from, next) => {
  NProgress.start()
  document.title = to.meta.title ? `${to.meta.title} - 云尚办公` : '云尚办公'

  const hasToken = !!getToken()
  let menus = store.getters.menus || []

  if (hasToken && to.path !== '/login' && !store.getters.name) {
    try {
      await store.dispatch('user/getInfo')
      menus = store.getters.menus || []
    } catch (error) {
      await store.dispatch('user/resetToken')
      Message.error(error || '获取用户信息失败')
      next(`/login?redirect=${encodeURIComponent(to.fullPath)}`)
      NProgress.done()
      return
    }
  }

  const decision = resolveNavigationGuard({
    path: to.path,
    fullPath: to.fullPath,
    hasToken,
    viewportWidth: getViewportWidth(),
    menus
  })

  if (decision.action === 'redirect') {
    next(decision.to)
    NProgress.done()
    return
  }

  next()
})

router.afterEach(() => {
  NProgress.done()
})
