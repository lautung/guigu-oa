export const MOBILE_BREAKPOINT = 768

export const DESKTOP_CONFIG_HINT = '请在电脑浏览器使用'

export const ROUTES = {
  LOGIN: '/login',
  MOBILE_CONFIG_BLOCK: '/mobile-config-block',
  START: '/work/start',
  PENDING: '/work/pending',
  PROCESSED: '/work/processed',
  STARTED: '/work/started',
  PROFILE: '/work/profile'
}

export const AUTH_WHITELIST = [ROUTES.LOGIN, ROUTES.MOBILE_CONFIG_BLOCK]

export const WORK_BUILTIN_ITEMS = [
  { path: ROUTES.START, title: '发起审批' },
  { path: ROUTES.PENDING, title: '待处理' },
  { path: ROUTES.PROCESSED, title: '已处理' },
  { path: ROUTES.STARTED, title: '已发起' },
  { path: ROUTES.PROFILE, title: '基本信息' }
]

export function getDefaultPostLoginRoute() {
  return ROUTES.PENDING
}

export function isMobileViewport(viewportWidth) {
  return viewportWidth <= MOBILE_BREAKPOINT
}
