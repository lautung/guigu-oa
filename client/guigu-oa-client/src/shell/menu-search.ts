import type { BackofficeMenuEntry } from '@/types/domain'
import { buildBreadcrumb } from './breadcrumb'
import { buildShellNav } from './shell-nav'

/**
 * 菜单搜索的索引与匹配（#230，ADR 0059）：把已授权页面与内置办事页拍平成
 * 「标题 + 面包屑」，只做不区分大小写的包含匹配；不引 fuse，不搜按钮点。
 * 索引只来自 buildShellNav，也就是 /info.menus 已按权限过滤后的结果。
 */
export interface MenuSearchEntry {
  title: string
  path: string
  trail: string[]
}

export const MENU_SEARCH_LIMIT = 8

export function buildMenuSearch(menus: BackofficeMenuEntry[] = []): MenuSearchEntry[] {
  const entries: MenuSearchEntry[] = []
  const seen = new Set<string>()

  buildShellNav(menus).forEach((module) => {
    const leaves = [...module.items, ...module.groups.flatMap((group) => group.items)]
    leaves.forEach((leaf) => {
      if (seen.has(leaf.path)) return
      seen.add(leaf.path)
      entries.push({
        title: leaf.title,
        path: leaf.path,
        trail: buildBreadcrumb({ path: leaf.path, title: leaf.title, menus }).map(
          (node) => node.title,
        ),
      })
    })
  })

  return entries
}

function normalize(value: string): string {
  return value.trim().toLowerCase()
}

/** 标题命中优先于仅面包屑命中；同级保持导航顺序。 */
function matchRank(entry: MenuSearchEntry, query: string): number {
  const title = normalize(entry.title)
  if (title === query) return 1
  if (title.startsWith(query)) return 2
  if (title.includes(query)) return 3
  if (entry.trail.some((part) => normalize(part).includes(query))) return 4
  return 0
}

export function searchMenus(
  entries: readonly MenuSearchEntry[],
  query: string,
  limit = MENU_SEARCH_LIMIT,
): MenuSearchEntry[] {
  const normalized = normalize(query)
  if (!normalized) return []

  return entries
    .map((entry, index) => ({ entry, index, rank: matchRank(entry, normalized) }))
    .filter((candidate) => candidate.rank > 0)
    .sort((left, right) => left.rank - right.rank || left.index - right.index)
    .slice(0, limit)
    .map((candidate) => candidate.entry)
}
