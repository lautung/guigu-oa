import { isExcludedConfigMenu } from '@/config/sys-menu'
import { resolveClientSurface } from '@/surface/client-surface'
import {
  AUTH_WHITELIST,
  DESKTOP_CONFIG_HINT,
  MOBILE_BREAKPOINT,
  ROUTES,
  WORK_BUILTIN_ITEMS,
  getDefaultPostLoginRoute,
  isMobileViewport
} from './constants'

export {
  AUTH_WHITELIST,
  DESKTOP_CONFIG_HINT,
  MOBILE_BREAKPOINT,
  ROUTES,
  WORK_BUILTIN_ITEMS,
  getDefaultPostLoginRoute,
  isMobileViewport
}

const HIDDEN_CONFIG_PATH_FRAGMENTS = [
  'assignAuth',
  'templateSet',
  'onlineProcessSet'
]

const HIDDEN_CONFIG_PARENT_PATHS = {
  assignAuth: '/system/sysRole',
  templateSet: '/processSet/processTemplate'
}

function normalizePath(path) {
  if (!path) return ''
  return path.startsWith('/') ? path : `/${path}`
}

function joinPath(base, segment) {
  const normalizedBase = normalizePath(base).replace(/\/$/, '')
  const normalizedSegment = (segment || '').replace(/^\//, '')
  if (!normalizedSegment) return normalizedBase
  return `${normalizedBase}/${normalizedSegment}`
}

function resolveMenuPath(menu, parentPath = '') {
  const segment = menu.path || ''
  if (segment.startsWith('/')) {
    return normalizePath(segment)
  }
  return joinPath(parentPath, segment)
}

const CONFIG_ROUTE_PREFIXES = ['/system', '/processSet', '/processMgr']

function isConfigRoutePath(path) {
  const normalized = normalizePath(path)
  if (CONFIG_ROUTE_PREFIXES.some(prefix => normalized.startsWith(prefix))) {
    return true
  }
  return HIDDEN_CONFIG_PATH_FRAGMENTS.some(fragment => normalized.includes(fragment))
}

export function buildLoginRedirectPath(intendedPath) {
  return intendedPath || getDefaultPostLoginRoute()
}

export function filterConfigMenus(menus = [], parentPath = '') {
  return menus
    .filter(menu => !isExcludedConfigMenu(menu))
    .map(menu => {
      const currentPath = resolveMenuPath(menu, parentPath)
      const node = {
        path: currentPath,
        title: menu.meta && menu.meta.title ? menu.meta.title : menu.name || currentPath,
        hidden: !!menu.hidden
      }

      if (menu.children && menu.children.length > 0) {
        node.children = filterConfigMenus(menu.children, currentPath)
      }

      return node
    })
    .filter(menu => {
      if (menu.hidden) {
        return true
      }
      if (menu.children && menu.children.length > 0) {
        return menu.children.some(child => !child.hidden || (child.children && child.children.length > 0))
      }
      return true
    })
}

function countVisibleConfigEntries(menus = []) {
  return menus.reduce((count, menu) => {
    if (menu.hidden) {
      return count
    }
    if (menu.children && menu.children.length > 0) {
      return count + countVisibleConfigEntries(menu.children)
    }
    return count + 1
  }, 0)
}

export function hasConfigPermission(menus = []) {
  const filtered = filterConfigMenus(menus)
  return countVisibleConfigEntries(filtered) > 0
}

function collectLeafAndHiddenConfigPaths(menus = [], paths = []) {
  menus.forEach(menu => {
    const children = menu.children || []
    const visibleChildren = children.filter(child => !child.hidden)
    if (menu.hidden) {
      paths.push(menu.path)
    } else if (visibleChildren.length === 0 && children.length === 0) {
      paths.push(menu.path)
    }
    collectLeafAndHiddenConfigPaths(children, paths)
  })
  return paths
}

export function isAuthorizedConfigPath(path, menus = []) {
  const normalized = normalizePath(path)
  if (!isConfigRoutePath(normalized)) {
    return true
  }

  const granted = collectLeafAndHiddenConfigPaths(filterConfigMenus(menus))
    .map(item => normalizePath(item))
    .filter(Boolean)

  if (granted.some(authorized => (
    normalized === authorized
    || normalized.startsWith(`${authorized}/`)
    || authorized.startsWith(`${normalized}/`)
  ))) {
    return true
  }

  return Object.entries(HIDDEN_CONFIG_PARENT_PATHS).some(([fragment, parentPath]) => (
    normalized.includes(fragment) && granted.includes(parentPath)
  ))
}

function mapMenuToSidebarItems(menus = []) {
  return menus
    .filter(menu => !menu.hidden)
    .map(menu => {
      const item = {
        path: menu.path,
        title: menu.title
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

export function buildWorkSidebarGroup() {
  return {
    key: 'work',
    title: '办事',
    collapsed: false,
    hint: null,
    items: WORK_BUILTIN_ITEMS.map(item => ({ ...item }))
  }
}

export function buildConfigSidebarGroup(menus = [], viewportWidth) {
  const mobile = isMobileViewport(viewportWidth)

  return {
    key: 'config',
    title: '配置',
    collapsed: mobile,
    hint: mobile ? DESKTOP_CONFIG_HINT : null,
    items: mapMenuToSidebarItems(filterConfigMenus(menus))
  }
}

export function buildSidebarTree({ menus = [], viewportWidth = MOBILE_BREAKPOINT + 1 }) {
  const tree = [buildWorkSidebarGroup()]

  if (hasConfigPermission(menus)) {
    tree.push(buildConfigSidebarGroup(menus, viewportWidth))
  }

  return tree
}

export function resolveAuthGuard({ path, fullPath, hasToken }) {
  const targetPath = fullPath || path

  if (hasToken) {
    if (path === ROUTES.LOGIN) {
      return { action: 'redirect', to: getDefaultPostLoginRoute() }
    }
    return { action: 'allow' }
  }

  if (AUTH_WHITELIST.includes(path)) {
    return { action: 'allow' }
  }

  return {
    action: 'redirect',
    to: `${ROUTES.LOGIN}?redirect=${encodeURIComponent(targetPath)}`
  }
}

export function resolveNavigationGuard({
  path,
  fullPath,
  hasToken,
  viewportWidth,
  menus
}) {
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
    configEntryAuthorized: isAuthorizedConfigPath(path, menus)
  }).occupancy

  if (occupancy && occupancy.action === 'redirect') {
    return occupancy
  }

  return { action: 'allow' }
}
