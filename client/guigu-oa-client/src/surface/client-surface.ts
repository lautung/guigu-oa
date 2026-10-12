import { configPermission } from '@/config-permission'
import {
  getDefaultPostLoginRoute,
  isMobileViewport,
  ROUTES,
  WORK_BUILTIN_ITEMS,
} from '@/navigation/constants'
import type { MenuNode } from '@/types/domain'

export const MENU_GROUPS = {
  WORK: 'work',
  CONFIG: 'config',
} as const

export const DENSITIES = {
  CONSTRAINED: 'constrained',
  WIDE: 'wide',
} as const

type MenuGroup = (typeof MENU_GROUPS)[keyof typeof MENU_GROUPS]
type Density = (typeof DENSITIES)[keyof typeof DENSITIES]
type Occupancy = { action: 'allow' } | { action: 'redirect'; to: string }

interface MenuEntry {
  path: string
  title: string
  hidden?: boolean
  parentPath?: string
  group: MenuGroup
}

export interface ClientSurfaceInput {
  path?: string
  viewportWidth?: number
  hasToken?: boolean
  menus?: MenuNode[]
}

interface ClientSurfaceResolution {
  showSurface: boolean
  menuGroup: MenuGroup | null
  density: Density | null
  occupancy: Occupancy
  hasWorkListPerspective?: boolean
  returnPath?: string
}

function densityForMenuGroup(menuGroup: MenuGroup | null): Density | null {
  if (menuGroup === MENU_GROUPS.WORK) return DENSITIES.CONSTRAINED
  if (menuGroup === MENU_GROUPS.CONFIG) return DENSITIES.WIDE
  return null
}

const KNOWN_CONFIG_ENTRIES: Omit<MenuEntry, 'group'>[] = [
  { path: '/system/sysUser', title: '用户管理' },
  { path: '/system/sysRole', title: '角色管理' },
  { path: '/system/sysMenu', title: '菜单管理' },
  { path: '/system/assignAuth', title: '菜单授权', hidden: true, parentPath: '/system/sysRole' },
  { path: '/processSet/processType', title: '审批类型' },
  { path: '/processSet/processTemplate', title: '审批模板' },
  {
    path: '/processSet/templateSet',
    title: '审批模板设置',
    hidden: true,
    parentPath: '/processSet/processTemplate',
  },
  { path: '/processMgr/process', title: '审批实例查询' },
]

function normalizePath(path: string | undefined): string {
  if (!path) return ''
  const withSlash = path.startsWith('/') ? path : `/${path}`
  return withSlash.replace(/\/$/, '') || '/'
}

function joinPath(base: string, segment: string | undefined): string {
  const normalizedBase = normalizePath(base).replace(/\/$/, '')
  const normalizedSegment = (segment || '').replace(/^\//, '')
  if (!normalizedSegment) return normalizedBase
  return `${normalizedBase}/${normalizedSegment}`
}

function pathMatchesEntry(path: string, entryPath: string): boolean {
  return path === entryPath || path.startsWith(`${entryPath}/`)
}

function findBestEntry(path: string, entries: MenuEntry[]): MenuEntry | null {
  const normalized = normalizePath(path)
  return (
    entries
      .filter((item) => pathMatchesEntry(normalized, item.path))
      .sort((left, right) => right.path.length - left.path.length)[0] || null
  )
}

function flattenConfigEntries(
  menus: MenuNode[],
  parentPath = '',
  visibleParentPath = '',
): MenuEntry[] {
  return menus.flatMap((menu) => {
    const segment = menu.path || ''
    const path = segment.startsWith('/') ? normalizePath(segment) : joinPath(parentPath, segment)
    const hidden = !!menu.hidden
    const entry: MenuEntry = {
      path,
      title: menu.meta?.title || menu.title || menu.name || path,
      hidden,
      parentPath: visibleParentPath || undefined,
      group: MENU_GROUPS.CONFIG,
    }
    const nextVisibleParent = hidden ? visibleParentPath : path
    const children = flattenConfigEntries(menu.children || [], path, nextVisibleParent)
    return [entry, ...children]
  })
}

function workEntries(): MenuEntry[] {
  return WORK_BUILTIN_ITEMS.map((item) => ({
    ...item,
    group: MENU_GROUPS.WORK,
  }))
}

function configEntries(menus: MenuNode[] = []): MenuEntry[] {
  const fromMenus = flattenConfigEntries(menus)
  const fromCatalog = KNOWN_CONFIG_ENTRIES.map((item) => ({
    ...item,
    group: MENU_GROUPS.CONFIG,
  }))
  const seen = new Set<string>()
  return [...fromMenus, ...fromCatalog].filter((entry) => {
    if (seen.has(entry.path)) return false
    seen.add(entry.path)
    return true
  })
}

function findMenuEntry(path: string, menus: MenuNode[] = []): MenuEntry | null {
  const workEntry = findBestEntry(path, workEntries())
  if (workEntry) return workEntry

  const entries = configEntries(menus)
  const configEntry = findBestEntry(path, entries)
  if (configEntry) return configEntry

  const normalized = normalizePath(path)
  const descendant = entries.find((item) => item.path.startsWith(`${normalized}/`))
  if (descendant) {
    return {
      path: normalized,
      title: descendant.title,
      group: MENU_GROUPS.CONFIG,
    }
  }

  return null
}

const WORK_LIST_ENTRY_PATHS: readonly string[] = [ROUTES.PENDING, ROUTES.PROCESSED, ROUTES.STARTED]

function occupancyFor({
  menuGroup,
  viewportWidth,
  hasToken,
  path,
  menus,
}: Required<Pick<ClientSurfaceInput, 'path' | 'viewportWidth' | 'hasToken' | 'menus'>> & {
  menuGroup: MenuGroup | null
}): Occupancy {
  if (!hasToken || menuGroup !== MENU_GROUPS.CONFIG) {
    return { action: 'allow' }
  }

  if (isMobileViewport(viewportWidth) || !configPermission(menus).canOccupy(path)) {
    return {
      action: 'redirect',
      to: getDefaultPostLoginRoute(),
    }
  }

  return { action: 'allow' }
}

function visibleMenuEntry(entry: MenuEntry | null, menus: MenuNode[]): MenuEntry | null {
  if (!entry) return null
  if (entry.hidden && entry.parentPath) {
    return findMenuEntry(entry.parentPath, menus) || entry
  }
  return entry
}

export function resolveClientSurface({
  path = '',
  viewportWidth = 1024,
  hasToken = false,
  menus = [],
}: ClientSurfaceInput = {}): ClientSurfaceResolution {
  if (normalizePath(path) === ROUTES.LOGIN) {
    return {
      showSurface: false,
      menuGroup: null,
      density: null,
      occupancy: { action: 'allow' },
    }
  }

  const entry = findMenuEntry(path, menus)
  if (!entry) {
    return {
      showSurface: false,
      menuGroup: null,
      density: null,
      occupancy: { action: 'allow' },
    }
  }

  const owner = visibleMenuEntry(entry, menus) || entry

  return {
    showSurface: hasToken,
    menuGroup: owner.group,
    density: densityForMenuGroup(owner.group),
    occupancy: occupancyFor({ menuGroup: owner.group, viewportWidth, hasToken, path, menus }),
    hasWorkListPerspective: WORK_LIST_ENTRY_PATHS.includes(owner.path),
    returnPath: owner.path,
  }
}

export function createViewportOccupancyHandler({
  getSurfaceInput,
  goTo,
}: {
  getSurfaceInput: () => ClientSurfaceInput
  goTo: (path: string) => void
}): (viewportWidth: number) => void {
  return function handleViewportWidth(viewportWidth: number): void {
    const input = getSurfaceInput()
    const occupancy = resolveClientSurface({ ...input, viewportWidth }).occupancy
    if (occupancy.action === 'redirect' && occupancy.to !== input.path) {
      goTo(occupancy.to)
    }
  }
}

export function loadClientSurfaceHost(): Promise<typeof import('./ClientSurfaceHost.vue')> {
  return import('./ClientSurfaceHost.vue')
}
