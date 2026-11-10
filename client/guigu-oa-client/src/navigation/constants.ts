import { PATHS, ROUTES } from '@/router/paths'

export { ROUTES }

export const AUTH_WHITELIST = [ROUTES.LOGIN, ROUTES.REGISTER] as const

export const WORK_BUILTIN_ITEMS = [
  { path: ROUTES.START, title: '发起审批' },
  { path: ROUTES.PENDING, title: '待处理' },
  { path: ROUTES.PROCESSED, title: '已处理' },
  { path: ROUTES.STARTED, title: '已发起' },
  { path: ROUTES.COPIED, title: '抄送我的' },
  { path: ROUTES.MY_ATTENDANCE, title: '打卡' },
] as const

/** 登录落点统一为工作台（ADR 0051/0059）：不按权限分流，也不落介绍页。 */
export function getDefaultPostLoginRoute(): string {
  return PATHS.WORKBENCH
}
