import {
  getDefaultPostLoginRoute,
  isMobileViewport,
  ROUTES,
  WORK_BUILTIN_ITEMS
} from '@/navigation/constants'

export const MENU_GROUPS = {
  WORK: 'work',
  CONFIG: 'config'
}

export const DENSITIES = {
  CONSTRAINED: 'constrained',
  WIDE: 'wide'
}

function densityForMenuGroup(menuGroup) {
  if (menuGroup === MENU_GROUPS.WORK) return DENSITIES.CONSTRAINED
  if (menuGroup === MENU_GROUPS.CONFIG) return DENSITIES.WIDE
  return null
}

const KNOWN_CONFIG_ENTRIES = [
  { path: '/system/sysUser', title: '用户管理' },
  { path: '/system/sysRole', title: '角色管理' },
  { path: '/system/sysMenu', title: '菜单管理' },
  { path: '/system/assignAuth', title: '菜单授权', hidden: true, parentPath: '/system/sysRole' },
  { path: '/processSet/processType', title: '审批类型' },
  { path: '/processSet/processTemplate', title: '审批模板' },
  { path: '/processSet/templateSet', title: '审批模板设置', hidden: true, parentPath: '/processSet/processTemplate' },
  { path: '/processMgr/process', title: '审批实例查询' }
]

function normalizePath(path) {
  if (!path) return ''
  const withSlash = path.startsWith('/') ? path : `/${path}`
  return withSlash.replace(/\/$/, '') || '/'
}

function joinPath(base, segment) {
  const normalizedBase = normalizePath(base).replace(/\/$/, '')
  const normalizedSegment = (segment || '').replace(/^\//, '')
  if (!normalizedSegment) return normalizedBase
  return `${normalizedBase}/${normalizedSegment}`
}

function pathMatchesEntry(path, entryPath) {
  return path === entryPath || path.startsWith(`${entryPath}/`)
}

function findBestEntry(path, entries) {
  const normalized = normalizePath(path)
  return entries
    .filter(item => pathMatchesEntry(normalized, item.path))
    .sort((left, right) => right.path.length - left.path.length)[0] || null
}

function flattenConfigEntries(menus = [], parentPath = '', visibleParentPath = '') {
  return menus.flatMap(menu => {
    const segment = menu.path || ''
    const path = segment.startsWith('/')
      ? normalizePath(segment)
      : joinPath(parentPath, segment)
    const hidden = !!menu.hidden
    const entry = {
      path,
      title: (menu.meta && menu.meta.title) || menu.title || menu.name || path,
      hidden,
      parentPath: visibleParentPath || undefined,
      group: MENU_GROUPS.CONFIG
    }
    const nextVisibleParent = hidden ? visibleParentPath : path
    const children = flattenConfigEntries(menu.children || [], path, nextVisibleParent)
    return [entry, ...children]
  })
}

function workEntries() {
  return WORK_BUILTIN_ITEMS.map(item => ({
    ...item,
    group: MENU_GROUPS.WORK
  }))
}

function configEntries(menus = []) {
  const fromMenus = flattenConfigEntries(menus)
  const fromCatalog = KNOWN_CONFIG_ENTRIES.map(item => ({
    ...item,
    group: MENU_GROUPS.CONFIG
  }))
  const seen = new Set()
  return [...fromMenus, ...fromCatalog].filter(entry => {
    if (seen.has(entry.path)) return false
    seen.add(entry.path)
    return true
  })
}

function findMenuEntry(path, menus = []) {
  const workEntry = findBestEntry(path, workEntries())
  if (workEntry) return workEntry

  const entries = configEntries(menus)
  const configEntry = findBestEntry(path, entries)
  if (configEntry) return configEntry

  const normalized = normalizePath(path)
  const descendant = entries.find(item => item.path.startsWith(`${normalized}/`))
  if (descendant) {
    return {
      path: normalized,
      title: descendant.title,
      group: MENU_GROUPS.CONFIG
    }
  }

  return null
}

const WORK_LIST_ENTRY_PATHS = [ROUTES.PENDING, ROUTES.PROCESSED, ROUTES.STARTED]

function occupancyFor({ menuGroup, viewportWidth, hasToken, configEntryAuthorized }) {
  if (!hasToken || menuGroup !== MENU_GROUPS.CONFIG) {
    return { action: 'allow' }
  }

  if (isMobileViewport(viewportWidth) || !configEntryAuthorized) {
    return {
      action: 'redirect',
      to: getDefaultPostLoginRoute()
    }
  }

  return { action: 'allow' }
}

function visibleMenuEntry(entry, menus) {
  if (!entry) return null
  if (entry.hidden && entry.parentPath) {
    return findMenuEntry(entry.parentPath, menus) || entry
  }
  return entry
}

export function resolveClientSurface({
  path,
  viewportWidth,
  hasToken,
  menus = [],
  configEntryAuthorized
} = {}) {
  if (normalizePath(path) === ROUTES.LOGIN) {
    return {
      showSurface: false,
      menuGroup: null,
      density: null,
      occupancy: { action: 'allow' }
    }
  }

  const entry = findMenuEntry(path, menus)
  if (!entry) {
    return {
      showSurface: false,
      menuGroup: null,
      density: null,
      occupancy: { action: 'allow' }
    }
  }

  const owner = visibleMenuEntry(entry, menus)

  return {
    showSurface: !!hasToken,
    menuGroup: owner.group,
    density: densityForMenuGroup(owner.group),
    occupancy: occupancyFor({
      menuGroup: owner.group,
      viewportWidth,
      hasToken,
      configEntryAuthorized
    }),
    hasWorkListPerspective: WORK_LIST_ENTRY_PATHS.includes(owner.path),
    returnPath: owner.path
  }
}

export function loadClientSurfaceHost() {
  return import('./ClientSurfaceHost.vue')
}
