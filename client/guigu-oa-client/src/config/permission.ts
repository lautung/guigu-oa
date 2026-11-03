import { ROUTE_GROUP_ENTRIES, type RouteGroupEntry } from '@/router/routes'
import type { BackofficeMenuEntry, SidebarItem } from '@/types/domain'

/**
 * 配置授予的客户端投影：组归属来自路由表，授予内容来自 /info 的 menus。
 * 不认按钮，不认用户 id，不再维护产品目录。
 */

export type MenuGroup = 'work' | 'config'

function normalizePath(path: string | undefined): string {
  if (!path) return ''
  const withLeadingSlash = path.startsWith('/') ? path : `/${path}`
  return withLeadingSlash.replace(/\/$/, '') || '/'
}

function pathMatches(path: string, entryPath: string): boolean {
  return path === entryPath || path.startsWith(`${entryPath}/`)
}

function bestMatch<T extends { path: string }>(path: string, entries: readonly T[]): T | null {
  const matches = entries
    .filter((entry) => pathMatches(path, entry.path))
    .sort((left, right) => right.path.length - left.path.length)
  return matches[0] || null
}

function routeEntryOf(path: string): RouteGroupEntry | null {
  const route = bestMatch(path, ROUTE_GROUP_ENTRIES)
  if (route) return route

  // 只挂着子页的父目录仍属于该组。
  const descendant = ROUTE_GROUP_ENTRIES.find((entry) => entry.path.startsWith(`${path}/`))
  return descendant ? { path, group: descendant.group } : null
}

function grantedEntryOf(
  path: string,
  entries: readonly BackofficeMenuEntry[],
): BackofficeMenuEntry | null {
  const match = bestMatch(path, entries)
  if (match) return match
  return entries.find((entry) => normalizePath(entry.path).startsWith(`${path}/`)) || null
}

function publicGrantedEntry(
  entry: BackofficeMenuEntry | null,
): { path: string; group: MenuGroup } | null {
  if (!entry) return null
  const path = entry.hidden && entry.parentPath ? entry.parentPath : entry.path
  return { path: normalizePath(path), group: 'config' }
}

function grantedPages(entries: readonly BackofficeMenuEntry[]): string[] {
  const visiblePages = entries
    .filter((entry) => entry.type === 1 && !entry.hidden)
    .map((entry) => normalizePath(entry.path))
    .filter(Boolean)
  const visible = new Set(visiblePages)
  const hiddenPages = entries
    .filter(
      (entry) =>
        entry.type === 1 &&
        entry.hidden &&
        !!entry.parentPath &&
        visible.has(normalizePath(entry.parentPath)),
    )
    .map((entry) => normalizePath(entry.path))
    .filter(Boolean)
  return [...visiblePages, ...hiddenPages]
}

function pathCoveredByGrant(path: string, pages: readonly string[]): boolean {
  return pages.some(
    (page) => path === page || path.startsWith(`${page}/`) || page.startsWith(`${path}/`),
  )
}

function sidebarEntries(entries: readonly BackofficeMenuEntry[]): BackofficeMenuEntry[] {
  const byPath = new Map(entries.map((entry) => [normalizePath(entry.path), entry]))
  const keep = new Set<string>()

  entries
    .filter((entry) => entry.type === 1 && !entry.hidden)
    .forEach((page) => {
      let current: BackofficeMenuEntry | undefined = page
      while (current) {
        const path = normalizePath(current.path)
        if (keep.has(path)) break
        keep.add(path)
        current = current.parentPath ? byPath.get(normalizePath(current.parentPath)) : undefined
      }
    })

  return entries.filter((entry) => !entry.hidden && keep.has(normalizePath(entry.path)))
}

function buildSidebar(entries: readonly BackofficeMenuEntry[]): SidebarItem[] {
  const byParent = new Map<string, BackofficeMenuEntry[]>()
  sidebarEntries(entries).forEach((entry) => {
    const parentPath = entry.parentPath ? normalizePath(entry.parentPath) : ''
    const siblings = byParent.get(parentPath) || []
    siblings.push(entry)
    byParent.set(parentPath, siblings)
  })

  const build = (parentPath: string): SidebarItem[] =>
    (byParent.get(parentPath) || [])
      .slice()
      .sort(
        (left, right) =>
          (left.sort ?? 0) - (right.sort ?? 0) || left.path.localeCompare(right.path),
      )
      .map((entry) => {
        const path = normalizePath(entry.path)
        const children = build(path)
        return {
          path,
          title: entry.title,
          ...(entry.icon ? { icon: entry.icon } : {}),
          ...(children.length > 0 ? { children } : {}),
        }
      })

  return build('')
}

export function configPermission(menus: BackofficeMenuEntry[] = []) {
  const pages = grantedPages(menus)

  return {
    hasConfigPermission: pages.length > 0,
    grantedMenus: buildSidebar(menus),
    /** 页面授予：精确页面、目录下的已授页面，或已授页面下的详情子路径。 */
    coversPage(path: string): boolean {
      const normalized = normalizePath(path)
      if (!normalized) return false
      return pathCoveredByGrant(normalized, pages)
    },
    entryOf(path: string): { path: string; group: MenuGroup } | null {
      const normalized = normalizePath(path)
      if (!normalized) return null

      const route = routeEntryOf(normalized)
      if (route) {
        return {
          path: route.hiddenParentPath || route.path,
          group: route.group,
        }
      }

      return publicGrantedEntry(grantedEntryOf(normalized, menus))
    },
    canOccupy({ path }: { path: string }): boolean {
      const normalized = normalizePath(path)
      if (!normalized) return false
      return pathCoveredByGrant(normalized, pages)
    },
  }
}
