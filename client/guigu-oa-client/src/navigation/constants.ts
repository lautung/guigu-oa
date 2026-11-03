import { ROUTES } from '@/router/paths'

export { ROUTES }

export const AUTH_WHITELIST = [ROUTES.LOGIN] as const

export const WORK_BUILTIN_ITEMS = [
  { path: ROUTES.START, title: '发起审批' },
  { path: ROUTES.PENDING, title: '待处理' },
  { path: ROUTES.PROCESSED, title: '已处理' },
  { path: ROUTES.STARTED, title: '已发起' },
  { path: ROUTES.COPIED, title: '抄送我的' },
  { path: ROUTES.PROFILE, title: '基本信息' },
] as const

export function getDefaultPostLoginRoute(): string {
  return ROUTES.PENDING
}
