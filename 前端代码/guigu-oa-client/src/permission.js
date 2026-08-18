import router from './router'
import store from './store'
import { getToken } from '@/utils/auth'
import { Message } from 'element-ui'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'
import { resolveAuthGuard } from '@/navigation/access-policy'

NProgress.configure({ showSpinner: false })

router.beforeEach(async(to, from, next) => {
  NProgress.start()
  document.title = to.meta.title ? `${to.meta.title} - 云尚办公` : '云尚办公'

  const hasToken = !!getToken()
  const decision = resolveAuthGuard({ path: to.path, fullPath: to.fullPath, hasToken })

  if (decision.action === 'redirect') {
    next(decision.to)
    NProgress.done()
    return
  }

  if (hasToken && to.path !== '/login') {
    const hasUserInfo = store.getters.name
    if (!hasUserInfo) {
      try {
        await store.dispatch('user/getInfo')
        next({ ...to, replace: true })
      } catch (error) {
        await store.dispatch('user/resetToken')
        Message.error(error || '获取用户信息失败')
        next(`/login?redirect=${encodeURIComponent(to.fullPath)}`)
        NProgress.done()
        return
      }
    } else {
      next()
    }
  } else {
    next()
  }
})

router.afterEach(() => {
  NProgress.done()
})
