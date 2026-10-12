import { configPermission } from '@/config-permission'
import { resolveClientSurface } from '@/surface/client-surface'
import {
  AUTH_WHITELIST,
  DESKTOP_CONFIG_HINT,
  MOBILE_BREAKPOINT,
  ROUTES,
  WORK_BUILTIN_ITEMS,
  getDefaultPostLoginRoute,
  isMobileViewport,
} from './constants'
import type { MenuNode, SidebarGroup, SidebarItem } from '@/types/domain'

type GuardDecision = { action: 'allow' } | { action: 'redirect'; to: string }

export {
  AUTH_WHITELIST,
  DESKTOP_CONFIG_HINT,
  MOBILE_BREAKPOINT,
  ROUTES,
  WORK_BUILTIN_ITEMS,
  getDefaultPostLoginRoute,
  isMobileViewport,
}

export function buildLoginRedirectPath(intendedPath?: string): string {
  return intendedPath || getDefaultPostLoginRoute()
}

function mapMenuToSidebarItems(menus: SidebarItem[]): SidebarItem[] {
  return menus.map((menu) => {
    const item: SidebarItem = {
      path: menu.path,
      title: menu.title,
    }

    if (menu.children && menu.children.length > 0) {
      const children = mapMenuToSidebarItems(menu.children)
      if (children.length > 0) {
        item.children = children
      }
    }

    return item
  })
}

export function buildWorkSidebarGroup(): SidebarGroup {
  return {
    key: 'work',
    title: '办事',
    collapsed: false,
    hint: null,
    items: WORK_BUILTIN_ITEMS.map((item) => ({ ...item })),
  }
}

function configSidebarGroup(
  grantedConfigMenus: SidebarItem[],
  viewportWidth: number,
): SidebarGroup {
  const mobile = isMobileViewport(viewportWidth)

  return {
    key: 'config',
    title: '配置',
    collapsed: mobile,
    hint: mobile ? DESKTOP_CONFIG_HINT : null,
    items: mapMenuToSidebarItems(grantedConfigMenus),
  }
}

export function buildSidebarTree({
  menus = [],
  viewportWidth = MOBILE_BREAKPOINT + 1,
}: {
  menus?: MenuNode[]
  viewportWidth?: number
} = {}): SidebarGroup[] {
  const tree = [buildWorkSidebarGroup()]
  const permission = configPermission(menus)

  if (permission.hasConfigPermission) {
    tree.push(configSidebarGroup(permission.grantedConfigMenus, viewportWidth))
  }

  return tree
}

export function resolveAuthGuard({
  path,
  fullPath,
  hasToken,
}: {
  path: string
  fullPath?: string
  hasToken: boolean
}): GuardDecision {
  const targetPath = fullPath || path

  if (hasToken) {
    if (path === ROUTES.LOGIN) {
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

export function resolveNavigationGuard({
  path,
  fullPath,
  hasToken,
  viewportWidth,
  menus,
}: {
  path: string
  fullPath?: string
  hasToken: boolean
  viewportWidth: number
  menus?: MenuNode[]
}): GuardDecision {
  const authDecision = resolveAuthGuard({ path, fullPath, hasToken })

  if (authDecision.action === 'redirect') {
    return authDecision
  }

  if (!hasToken) {
    return authDecision
  }

  const occupancy = resolveClientSurface({
    path,
    viewportWidth,
    hasToken,
    menus,
  }).occupancy

  if (occupancy && occupancy.action === 'redirect') {
    return occupancy
  }

  return { action: 'allow' }
}
