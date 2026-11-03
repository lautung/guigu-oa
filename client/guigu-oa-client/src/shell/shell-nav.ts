import { configPermission } from '@/config/permission'
import { WORK_BUILTIN_ITEMS } from '@/navigation/constants'
import { PATHS, returnPathOfRoute } from '@/router/paths'
import { ROUTE_GROUP_ENTRIES } from '@/router/routes'
import type { BackofficeMenuEntry, SidebarItem } from '@/types/domain'

export type ShellModuleKey = 'workbench' | 'work' | 'system' | 'monitor' | 'tool'

export interface ShellNavLeaf {
  path: string
  title: string
  icon: string
}

export interface ShellNavGroup {
  title: string
  icon: string
  items: ShellNavLeaf[]
}

export interface ShellModule {
  key: ShellModuleKey
  title: string
  icon: string
  items: ShellNavLeaf[]
  groups: ShellNavGroup[]
  firstPath: string
}

export interface ShellModuleDefinition {
  key: ShellModuleKey
  title: string
  icon: string
  prefix: string
}

/** 五个一级模块定义（ADR 0059）。没有页面或授予的模块不进程导航，不显示死入口。 */
export const SHELL_MODULE_DEFINITIONS: readonly ShellModuleDefinition[] = [
  { key: 'workbench', title: '工作台', icon: 'HomeFilled', prefix: PATHS.WORKBENCH },
  { key: 'work', title: '办事', icon: 'Tickets', prefix: PATHS.WORK },
  { key: 'system', title: '系统管理', icon: 'Setting', prefix: PATHS.SYSTEM },
  { key: 'monitor', title: '系统监控', icon: 'Monitor', prefix: '/monitor' },
  { key: 'tool', title: '系统工具', icon: 'Tools', prefix: '/tool' },
] as const

export const DESKTOP_HINT = '建议桌面'

const WORK_ICONS: Record<string, string> = {
  [PATHS.WORK_START]: 'EditPen',
  [PATHS.WORK_PENDING]: 'AlarmClock',
  [PATHS.WORK_PROCESSED]: 'CircleCheck',
  [PATHS.WORK_STARTED]: 'Promotion',
  [PATHS.WORK_COPIED]: 'Share',
  [PATHS.WORK_PROFILE]: 'User',
}

function normalizePath(path: string | undefined): string {
  const value = (path || '').split('?')[0].split('#')[0]
  const withLeadingSlash = value.startsWith('/') ? value : `/${value}`
  return withLeadingSlash.replace(/\/+$/, '') || '/'
}

function leafOf(node: SidebarItem): ShellNavLeaf {
  return {
    path: normalizePath(node.path),
    title: node.title,
    icon: node.icon || 'Document',
  }
}

function leavesOf(node: SidebarItem): ShellNavLeaf[] {
  if (!node.children?.length) return [leafOf(node)]
  return node.children.flatMap((child) => leavesOf(child))
}

function workbenchModule(): ShellModule {
  const items: ShellNavLeaf[] = [
    { path: PATHS.WORKBENCH, title: '工作台', icon: 'HomeFilled' },
    { path: PATHS.NOTICE, title: '全部公告', icon: 'Bell' },
  ]
  return {
    key: 'workbench',
    title: '工作台',
    icon: 'HomeFilled',
    items,
    groups: [],
    firstPath: PATHS.WORKBENCH,
  }
}

function workModule(): ShellModule {
  const items = WORK_BUILTIN_ITEMS.map((item) => ({
    path: normalizePath(item.path),
    title: item.title,
    icon: WORK_ICONS[item.path] || 'Document',
  }))
  return {
    key: 'work',
    title: '办事',
    icon: 'Tickets',
    items,
    groups: [],
    firstPath: items[0]?.path || PATHS.WORK,
  }
}

function systemModule(menus: readonly BackofficeMenuEntry[]): ShellModule | null {
  const permission = configPermission([...menus])
  if (!permission.hasConfigPermission) return null

  const items: ShellNavLeaf[] = []
  const groups: ShellNavGroup[] = []

  function addNode(node: SidebarItem, depth: number): void {
    const path = normalizePath(node.path)

    if (!node.children?.length) {
      if (depth === 0 && path === PATHS.SYSTEM) return
      items.push(leafOf(node))
      return
    }

    if (depth === 0 && path === PATHS.SYSTEM) {
      node.children.forEach((child) => addNode(child, 1))
      return
    }

    const leaves = node.children.flatMap((child) => leavesOf(child))
    if (!leaves.length) return
    groups.push({ title: node.title, icon: node.icon || 'Folder', items: leaves })
  }

  permission.grantedMenus.forEach((root) => addNode(root, 0))

  const firstPath = items[0]?.path || groups[0]?.items[0]?.path || ''
  if (!firstPath) return null

  return {
    key: 'system',
    title: '系统管理',
    icon: 'Setting',
    items,
    groups,
    firstPath,
  }
}

/** 监控与工具暂无页面；有静态路由后再按同一形状派生，绝不渲染空模块。 */
function staticModule(definition: ShellModuleDefinition): ShellModule | null {
  const hasPages = ROUTE_GROUP_ENTRIES.some(
    (entry) => entry.path === definition.prefix || entry.path.startsWith(`${definition.prefix}/`),
  )
  if (!hasPages) return null

  return {
    key: definition.key,
    title: definition.title,
    icon: definition.icon,
    items: [],
    groups: [],
    firstPath: '',
  }
}

/** 从静态路由与 `/info.menus` 派生当前可进入的一级模块。 */
export function buildShellNav(menus: BackofficeMenuEntry[] = []): ShellModule[] {
  const modules = new Map<ShellModuleKey, ShellModule>()
  modules.set('workbench', workbenchModule())
  modules.set('work', workModule())

  const system = systemModule(menus)
  if (system) modules.set('system', system)

  for (const definition of SHELL_MODULE_DEFINITIONS) {
    if (modules.has(definition.key)) continue
    const module = staticModule(definition)
    if (module?.firstPath) modules.set(definition.key, module)
  }

  return SHELL_MODULE_DEFINITIONS.flatMap((definition) => {
    const module = modules.get(definition.key)
    return module ? [module] : []
  })
}

/** 当前路径属于哪个一级模块。路径优先于机构，未知路径不属于任何模块。 */
export function activeModuleKey(path: string): ShellModuleKey | null {
  const normalized = normalizePath(path)
  if (normalized === PATHS.WORKBENCH || normalized === PATHS.NOTICE) return 'workbench'
  if (normalized.startsWith(`${PATHS.NOTICE}/`)) return 'workbench'
  if (normalized === PATHS.WORK || normalized.startsWith(`${PATHS.WORK}/`)) return 'work'
  if (normalized === PATHS.SYSTEM || normalized.startsWith(`${PATHS.SYSTEM}/`)) return 'system'
  if (normalized === '/monitor' || normalized.startsWith('/monitor/')) return 'monitor'
  if (normalized === '/tool' || normalized.startsWith('/tool/')) return 'tool'
  return null
}

/** 标签落在哪个模块；切回一级时据此找回该模块最近的标签。 */
export function tabModuleKey(path: string): ShellModuleKey | null {
  return activeModuleKey(path)
}

export function moduleTitleOf(key: ShellModuleKey): string {
  return SHELL_MODULE_DEFINITIONS.find((definition) => definition.key === key)?.title || ''
}

export function lastTabPathForModule(
  tabs: readonly { path: string }[],
  key: ShellModuleKey,
): string | null {
  for (let index = tabs.length - 1; index >= 0; index -= 1) {
    if (activeModuleKey(tabs[index].path) === key) return tabs[index].path
  }
  return null
}

/** 侧栏激活项：自身、详情子路径，以及回到列表/可见父级的隐藏页。 */
export function isShellLeafActive(currentPath: string, leafPath: string): boolean {
  const current = normalizePath(currentPath)
  const leaf = normalizePath(leafPath)
  if (current === leaf || current.startsWith(`${leaf}/`)) return true
  return returnPathOfRoute(current) === leaf
}
