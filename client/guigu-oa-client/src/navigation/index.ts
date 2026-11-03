import { configPermission } from '@/config/permission'
import {
  AUTH_WHITELIST,
  DESKTOP_CONFIG_HINT,
  MOBILE_BREAKPOINT,
  ROUTES,
  WORK_BUILTIN_ITEMS,
  getDefaultPostLoginRoute,
  isMobileViewport,
} from './constants'
import type { BackofficeMenuEntry, SidebarGroup, SidebarItem } from '@/types/domain'

type GuardDecision = { action: 'allow' } | { action: 'redirect'; to: string }

export function buildLoginRedirectPath(intendedPath?: string): string {
  return intendedPath || getDefaultPostLoginRoute()
}

function buildWorkSidebarGroup(): SidebarGroup {
  return {
    key: 'work',
    title: '办事',
    collapsed: false,
    hint: null,
    items: WORK_BUILTIN_ITEMS.map((item) => ({ ...item })),
  }
}

function configSidebarGroup(grantedMenus: SidebarItem[], viewportWidth: number): SidebarGroup {
  const mobile = isMobileViewport(viewportWidth)

  return {
    key: 'config',
    title: '配置',
    collapsed: mobile,
    hint: mobile ? DESKTOP_CONFIG_HINT : null,
    items: grantedMenus,
  }
}

export function buildSidebarTree({
  menus = [],
  viewportWidth = MOBILE_BREAKPOINT + 1,
}: {
  menus?: BackofficeMenuEntry[]
  viewportWidth?: number
} = {}): SidebarGroup[] {
  const tree = [buildWorkSidebarGroup()]
  const permission = configPermission(menus)

  if (permission.hasConfigPermission) {
    tree.push(configSidebarGroup(permission.grantedMenus, viewportWidth))
  }

  return tree
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
    if (path === ROUTES.LOGIN) {
      return { action: 'redirect', to: getDefaultPostLoginRoute() }
    }
    const permission = configPermission(menus)
    const entry = permission.entryOf(path)
    if (entry?.group === 'config' && !permission.coversPage(path)) {
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
