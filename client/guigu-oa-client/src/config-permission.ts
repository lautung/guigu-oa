import { isMobileViewport, WORK_BUILTIN_ITEMS } from '@/navigation/constants'
import type { MenuNode, SidebarItem } from '@/types/domain'

/**
 * 配置权限：有没有、授了哪些配置类菜单、某地址属于哪组、这页能不能占用。
 * 输入是 info 给来的菜单树。不认用户 id，不认按钮，不认认证状态。
 * 菜单树是授予权威；产品目录只兜底当前菜单树里没有的已知配置页。
 * 菜单管理可以问什么算配置类菜单；勾选和增删改仍是配置页的动作。
 */

export type MenuGroup = 'work' | 'config'

interface MenuEntry {
  path: string
  group: MenuGroup
  hidden?: boolean
  hiddenParentPath?: string
}

export const KNOWN_CONFIG_PAGES: readonly { path: string; hiddenParentPath?: string }[] = [
  { path: '/system/sysUser' },
  { path: '/system/sysRole' },
  { path: '/system/sysMenu' },
  { path: '/system/assignAuth', hiddenParentPath: '/system/sysRole' },
  { path: '/processSet/processType' },
  { path: '/processSet/processTemplate' },
  { path: '/processSet/templateSet', hiddenParentPath: '/processSet/processTemplate' },
  { path: '/processMgr/process' },
]

const EXCLUDED_PATH_PATTERNS = [/wechat/i, /dashboard/i, /onlineProcessSet/i]

const EXCLUDED_COMPONENTS = [
  'system/sysDept/list',
  'system/sysPost/list',
  'system/sysOperLog/list',
  'system/sysLoginLog/list',
]

function isExcludedConfigMenu(menu: MenuNode = {}): boolean {
  const path = menu.path || ''
  const component = menu.component || ''

  if (EXCLUDED_PATH_PATTERNS.some((pattern) => pattern.test(path) || pattern.test(component))) {
    return true
  }

  return EXCLUDED_COMPONENTS.includes(component)
}

function normalizePath(path: string | undefined): string {
  if (!path) return ''
  const withLeadingSlash = path.startsWith('/') ? path : `/${path}`
  return withLeadingSlash.replace(/\/$/, '') || '/'
}

function joinPath(base: string, segment: string | undefined): string {
  const normalizedBase = normalizePath(base).replace(/\/$/, '')
  const normalizedSegment = (segment || '').replace(/^\//, '')
  if (!normalizedSegment) return normalizedBase
  return `${normalizedBase}/${normalizedSegment}`
}

function resolveMenuPath(menu: MenuNode, parentPath = ''): string {
  const segment = menu.path || ''
  if (segment.startsWith('/')) {
    return normalizePath(segment)
  }
  return joinPath(parentPath, segment)
}

function titleOf(menu: MenuNode): string {
  return menu.meta?.title || menu.title || menu.name || ''
}

export function isWorkMenu(menu: MenuNode = {}): boolean {
  const path = menu.path || ''
  return path === 'work' || /^\/work/i.test(path) || /^work\//i.test(path)
}

function isButtonMenu(menu: MenuNode): boolean {
  return menu.type === 2
}

export function isConfigClassMenu(menu: MenuNode): boolean {
  return !isButtonMenu(menu) && !isWorkMenu(menu) && !isExcludedConfigMenu(menu)
}

function grantedConfigMenusFrom(menus: MenuNode[], parentPath = ''): SidebarItem[] {
  const granted: SidebarItem[] = []

  menus.forEach((menu) => {
    if (menu.hidden || !isConfigClassMenu(menu)) {
      return
    }

    const path = resolveMenuPath(menu, parentPath)
    const pageChildren = (menu.children || []).filter((child) => !isButtonMenu(child))
    const children = grantedConfigMenusFrom(pageChildren, path)
    if (pageChildren.length > 0 && children.length === 0) {
      return
    }

    const node: SidebarItem = {
      path,
      title: titleOf(menu),
    }
    if (children.length > 0) {
      node.children = children
    }
    granted.push(node)
  })

  return granted
}

function collectGrantedLeaves(nodes: SidebarItem[], leaves: string[] = []): string[] {
  nodes.forEach((node) => {
    if (node.children && node.children.length > 0) {
      collectGrantedLeaves(node.children, leaves)
    } else {
      leaves.push(node.path)
    }
  })
  return leaves
}

// 菜单组分类先覆盖菜单树里的所有条目；某个菜单是否算被授予的配置权限仍由 isConfigClassMenu 决定。
function flattenMenuEntries(
  menus: MenuNode[],
  parentPath = '',
  visibleParentPath = '',
): MenuEntry[] {
  return menus.flatMap((menu) => {
    const path = resolveMenuPath(menu, parentPath)
    const hidden = !!menu.hidden
    const entry: MenuEntry = {
      path,
      group: 'config',
      ...(hidden ? { hidden: true, hiddenParentPath: visibleParentPath || undefined } : {}),
    }
    const nextVisibleParent = hidden ? visibleParentPath : path
    const children = flattenMenuEntries(menu.children || [], path, nextVisibleParent)
    return [entry, ...children]
  })
}

function catalogEntries(): MenuEntry[] {
  return KNOWN_CONFIG_PAGES.map((page) => ({
    path: page.path,
    group: 'config',
    ...(page.hiddenParentPath ? { hidden: true, hiddenParentPath: page.hiddenParentPath } : {}),
  }))
}

function configEntriesFrom(menus: MenuNode[]): MenuEntry[] {
  const fromMenus = flattenMenuEntries(menus)
  const seenPaths = new Set(fromMenus.map((entry) => entry.path))
  const fromCatalog = catalogEntries().filter((entry) => !seenPaths.has(entry.path))
  return [...fromMenus, ...fromCatalog]
}

function workEntries(): MenuEntry[] {
  return WORK_BUILTIN_ITEMS.map((item) => ({ path: item.path, group: 'work' }))
}

function pathMatchesEntry(path: string, entryPath: string): boolean {
  return path === entryPath || path.startsWith(`${entryPath}/`)
}

function findBestEntry(path: string, entries: MenuEntry[]): MenuEntry | null {
  const matches = entries
    .filter((entry) => pathMatchesEntry(path, entry.path))
    .sort((left, right) => right.path.length - left.path.length)
  return matches[0] || null
}

function findEntry(path: string, work: MenuEntry[], config: MenuEntry[]): MenuEntry | null {
  const normalized = normalizePath(path)
  const workEntry = findBestEntry(normalized, work)
  if (workEntry) return workEntry

  const configEntry = findBestEntry(normalized, config)
  if (configEntry) return configEntry

  // 只挂着子页的父目录仍属于配置组。
  const descendant = config.find((entry) => entry.path.startsWith(`${normalized}/`))
  if (descendant) {
    return { path: normalized, group: 'config' }
  }

  return null
}

function findEntryOf(path: string, work: MenuEntry[], config: MenuEntry[]): MenuEntry | null {
  const entry = findEntry(path, work, config)
  if (!entry || !entry.hidden || !entry.hiddenParentPath) {
    return entry
  }

  return findEntry(entry.hiddenParentPath, work, config) || entry
}

function publicEntry(entry: MenuEntry | null): { path: string; group: MenuGroup } | null {
  if (!entry) return null
  return { path: entry.path, group: entry.group }
}

function pathCoveredByGrant(path: string, leaves: string[]): boolean {
  return leaves.some(
    (leaf) => path === leaf || path.startsWith(`${leaf}/`) || leaf.startsWith(`${path}/`),
  )
}

// 隐藏页的入口父级和占用父级同源：菜单树里有该页时取菜单树里的可见父级，
// 只有当前菜单树里没有的已知隐藏页才用目录里的父级映射兜底。
function hiddenParentOf(path: string, config: MenuEntry[]): string | undefined {
  return findBestEntry(normalizePath(path), config)?.hiddenParentPath
}

function pathCanOccupy(
  path: string,
  viewportWidth: number,
  config: MenuEntry[],
  leaves: string[],
): boolean {
  if (isMobileViewport(viewportWidth)) return false

  const normalized = normalizePath(path)
  if (pathCoveredByGrant(normalized, leaves)) return true

  const hiddenParentPath = hiddenParentOf(normalized, config)
  return hiddenParentPath !== undefined && pathCoveredByGrant(hiddenParentPath, leaves)
}

interface CanOccupyInput {
  path: string
  viewportWidth: number
}

export function configPermission(menus: MenuNode[] = []) {
  const grantedConfigMenus = grantedConfigMenusFrom(menus)
  const grantedLeaves = collectGrantedLeaves(grantedConfigMenus)
  const work = workEntries()
  const config = configEntriesFrom(menus)

  return {
    hasConfigPermission: grantedLeaves.length > 0,
    grantedConfigMenus,
    entryOf(path: string): { path: string; group: 'work' | 'config' } | null {
      return publicEntry(findEntryOf(path, work, config))
    },
    canOccupy({ path, viewportWidth }: CanOccupyInput): boolean {
      return pathCanOccupy(path, viewportWidth, config, grantedLeaves)
    },
  }
}
