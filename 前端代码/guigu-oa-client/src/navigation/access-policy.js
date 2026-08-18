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

export const WORK_ROUTE_PREFIX = '/work'

export const CONFIG_ROUTE_PREFIXES = ['/system', '/processSet', '/processMgr']

export const WORK_BUILTIN_ITEMS = [
  { path: ROUTES.START, title: '发起审批' },
  { path: ROUTES.PENDING, title: '待处理' },
  { path: ROUTES.PROCESSED, title: '已处理' },
  { path: ROUTES.STARTED, title: '已发起' },
  { path: ROUTES.PROFILE, title: '基本信息' }
]

const EXCLUDED_CONFIG_PATH_PATTERNS = [
  /^\/wechat/i,
  /^\/dashboard/i,
  /onlineProcessSet/i
]

const EXCLUDED_CONFIG_COMPONENTS = [
  'system/sysDept/list',
  'system/sysPost/list',
  'system/sysOperLog/list',
  'system/sysLoginLog/list'
]

const HIDDEN_CONFIG_PATH_FRAGMENTS = [
  'assignAuth',
  'templateSet',
  'onlineProcessSet'
]

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

function isExcludedConfigMenu(menu) {
  const path = normalizePath(menu.path || '')
  const component = menu.component || ''

  if (EXCLUDED_CONFIG_PATH_PATTERNS.some(pattern => pattern.test(path))) {
    return true
  }

  if (EXCLUDED_CONFIG_COMPONENTS.includes(component)) {
    return true
  }

  return false
}

function isConfigRoutePath(path) {
  const normalized = normalizePath(path)
  if (CONFIG_ROUTE_PREFIXES.some(prefix => normalized.startsWith(prefix))) {
    return true
  }
  return HIDDEN_CONFIG_PATH_FRAGMENTS.some(fragment => normalized.includes(fragment))
}

export function getDefaultPostLoginRoute() {
  return ROUTES.PENDING
}

export function buildLoginRedirectPath(intendedPath) {
  return intendedPath || getDefaultPostLoginRoute()
}

export function classifyRoute(path) {
  const normalized = normalizePath(path)

  if (normalized === ROUTES.LOGIN || normalized === ROUTES.MOBILE_CONFIG_BLOCK) {
    return 'public'
  }

  if (normalized.startsWith(WORK_ROUTE_PREFIX)) {
    return 'work'
  }

  if (isConfigRoutePath(normalized)) {
    return 'config'
  }

  return 'unknown'
}

export function isMobileViewport(viewportWidth) {
  return viewportWidth <= MOBILE_BREAKPOINT
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

export function resolveViewportPolicy({ path, viewportWidth }) {
  if (classifyRoute(path) !== 'config') {
    return { action: 'allow' }
  }

  if (isMobileViewport(viewportWidth)) {
    return {
      action: 'block',
      message: DESKTOP_CONFIG_HINT,
      redirectTo: ROUTES.MOBILE_CONFIG_BLOCK
    }
  }

  return { action: 'allow' }
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

  if (path === ROUTES.MOBILE_CONFIG_BLOCK) {
    return { action: 'allow' }
  }

  const viewportDecision = resolveViewportPolicy({ path, viewportWidth })

  if (viewportDecision.action === 'block') {
    return {
      action: 'redirect',
      to: viewportDecision.redirectTo
    }
  }

  if (classifyRoute(path) === 'config' && !hasConfigPermission(menus)) {
    return {
      action: 'redirect',
      to: getDefaultPostLoginRoute()
    }
  }

  return { action: 'allow' }
}
