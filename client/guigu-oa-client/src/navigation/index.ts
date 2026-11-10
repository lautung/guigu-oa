import { configPermission } from '@/config/permission'
import { AUTH_WHITELIST, ROUTES, getDefaultPostLoginRoute } from './constants'
import type { BackofficeMenuEntry } from '@/types/domain'

type GuardDecision = { action: 'allow' } | { action: 'redirect'; to: string }

export function buildLoginRedirectPath(intendedPath?: string): string {
  return intendedPath || getDefaultPostLoginRoute()
}

export function resolveAuthGuard({
  path,
  fullPath,
  hasToken,
  menus = [],
}: {
  path: string
  fullPath?: string
  hasToken: boolean
  menus?: BackofficeMenuEntry[]
}): GuardDecision {
  const targetPath = fullPath || path

  if (hasToken) {
    if (path === ROUTES.LOGIN || path === ROUTES.REGISTER) {
      return { action: 'redirect', to: getDefaultPostLoginRoute() }
    }
    const permission = configPermission(menus)
    const entry = permission.entryOf(path)
    // 后台页面授予守卫：config（系统管理/监控/工具）与 business（业务）同档，
    // 未授予回落工作台；work 组是登录即有的办事面，不套页面授予（蓝图 §2）。
    if (entry && entry.group !== 'work' && !permission.coversPage(path)) {
      return { action: 'redirect', to: getDefaultPostLoginRoute() }
    }
    return { action: 'allow' }
  }

  if (AUTH_WHITELIST.some((whiteListedPath) => whiteListedPath === path)) {
    return { action: 'allow' }
  }

  return {
    action: 'redirect',
    to: `${ROUTES.LOGIN}?redirect=${encodeURIComponent(targetPath)}`,
  }
}
