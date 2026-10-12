import router from './router'
import { getToken } from '@/utils/auth'
import { ElMessage } from 'element-plus'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'
import { resolveNavigationGuard } from '@/navigation'
import { useUserStore } from '@/store/modules/user'

NProgress.configure({ showSpinner: false })

function getViewportWidth() {
  return typeof window !== 'undefined' ? window.innerWidth : 1024
}

router.beforeEach(async (to, from, next) => {
  NProgress.start()
  document.title = to.meta.title ? `${to.meta.title} - 云尚办公` : '云尚办公'

  const hasToken = !!getToken()
  const userStore = useUserStore()
  let menus = userStore.menus || []

  if (hasToken && to.path !== '/login' && !userStore.name) {
    try {
      await userStore.getInfo()
      menus = userStore.menus || []
    } catch (error: unknown) {
      userStore.resetToken()
      ElMessage.error(error instanceof Error ? error.message : '获取用户信息失败')
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
    menus,
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
