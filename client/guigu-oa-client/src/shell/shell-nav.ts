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
  /** 一级入口落点；空串表示当前用户没有可进入的页面（顶栏置灰）。 */
  firstPath: string
  /** 侧栏空态文案；有页面时为空串。 */
  emptyHint: string
}

export interface ShellModuleDefinition {
  key: ShellModuleKey
  title: string
  icon: string
  prefix: string
  /** 暂无页面的模块：保留一级入口与空态，不渲染死链。 */
  placeholder?: boolean
}

/** 五个一级模块定义（ADR 0059）：顶部固定五项，不随权限增减。 */
export const SHELL_MODULE_DEFINITIONS: readonly ShellModuleDefinition[] = [
  { key: 'workbench', title: '工作台', icon: 'HomeFilled', prefix: PATHS.WORKBENCH },
  { key: 'work', title: '办事', icon: 'Tickets', prefix: PATHS.WORK },
  { key: 'system', title: '系统管理', icon: 'Setting', prefix: PATHS.SYSTEM },
  { key: 'monitor', title: '系统监控', icon: 'Monitor', prefix: PATHS.MONITOR },
  { key: 'tool', title: '系统工具', icon: 'Tools', prefix: PATHS.TOOL, placeholder: true },
] as const

const SYSTEM_EMPTY_HINT = '暂无可用页面'
const PLACEHOLDER_EMPTY_HINT = '页面将在后续版本提供'

const WORK_ICONS: Record<string, string> = {
  [PATHS.WORK_START]: 'EditPen',
  [PATHS.WORK_PENDING]: 'AlarmClock',
  [PATHS.WORK_PROCESSED]: 'CircleCheck',
  [PATHS.WORK_STARTED]: 'Promotion',
  [PATHS.WORK_COPIED]: 'Share',
  [PATHS.WORK_PROFILE]: 'User',
}

/** 配置页按真实菜单形态分组：这三段前缀成为嵌套分组标题，其余 `/system/*` 平铺。 */
const SYSTEM_GROUP_DEFINITIONS = [
  { title: '日志管理', prefix: `${PATHS.SYSTEM}/log/`, icon: 'Document' },
  { title: '审批设置', prefix: `${PATHS.PROCESS_SET}/`, icon: 'Setting' },
  { title: '审批管理', prefix: `${PATHS.PROCESS_MGR}/`, icon: 'Files' },
] as const

/** 目录本身不是页面：即使菜单授予里带目录行，也不在侧栏渲染成可点项。 */
const SYSTEM_DIRECTORY_ROOTS = new Set([
  PATHS.SYSTEM,
  `${PATHS.SYSTEM}/log`,
  PATHS.PROCESS_SET,
  PATHS.PROCESS_MGR,
])

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
    emptyHint: '',
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
    emptyHint: '',
  }
}

function systemModule(menus: readonly BackofficeMenuEntry[]): ShellModule {
  const permission = configPermission([...menus])
  const leaves = permission.grantedMenus
    .flatMap((root) => leavesOf(root))
    .filter((leaf) => leaf.path.startsWith(`${PATHS.SYSTEM}/`))
    .filter((leaf) => !SYSTEM_DIRECTORY_ROOTS.has(leaf.path))

  const items = leaves.filter(
    (leaf) => !SYSTEM_GROUP_DEFINITIONS.some((group) => leaf.path.startsWith(group.prefix)),
  )
  const groups: ShellNavGroup[] = SYSTEM_GROUP_DEFINITIONS.flatMap((definition) => {
    const groupItems = leaves.filter((leaf) => leaf.path.startsWith(definition.prefix))
    return groupItems.length
      ? [{ title: definition.title, icon: definition.icon, items: groupItems }]
      : []
  })

  const firstPath = items[0]?.path || groups[0]?.items[0]?.path || ''
  return {
    key: 'system',
    title: '系统管理',
    icon: 'Setting',
    items,
    groups,
    firstPath,
    emptyHint: firstPath ? '' : SYSTEM_EMPTY_HINT,
  }
}

function placeholderModule(definition: ShellModuleDefinition): ShellModule {
  return {
    key: definition.key,
    title: definition.title,
    icon: definition.icon,
    items: [],
    groups: [],
    firstPath: definition.prefix,
    emptyHint: PLACEHOLDER_EMPTY_HINT,
  }
}

/**
 * 客户端已落路由的监控页面（#239）：路由表里带 menuGroup 的 /monitor 子页。
 * 服务端菜单先行（V117+ 全部监控页面都已授予），客户端页面随各自票落地；
 * 还没有客户端路由的授予页不渲染成死链。
 */
function routedMonitorPages(): Set<string> {
  return new Set(
    ROUTE_GROUP_ENTRIES.filter(
      (entry) => entry.group === 'config' && entry.path.startsWith(`${PATHS.MONITOR}/`),
    ).map((entry) => entry.path),
  )
}

/** 系统监控（ADR 0060）：平铺二级项，firstPath 取第一个已授予且有客户端路由的页面。 */
function monitorModule(menus: readonly BackofficeMenuEntry[]): ShellModule {
  const routed = routedMonitorPages()
  const permission = configPermission([...menus])
  const items = permission.grantedMenus
    .flatMap((root) => leavesOf(root))
    .filter((leaf) => leaf.path.startsWith(`${PATHS.MONITOR}/`))
    .filter((leaf) => routed.has(leaf.path))

  const firstPath = items[0]?.path || ''
  return {
    key: 'monitor',
    title: '系统监控',
    icon: 'Monitor',
    items,
    groups: [],
    firstPath,
    emptyHint: firstPath ? '' : SYSTEM_EMPTY_HINT,
  }
}

/** 五个一级模块固定返回；系统管理与系统监控只由已授予页面填充，工具保留空态入口。 */
export function buildShellNav(menus: BackofficeMenuEntry[] = []): ShellModule[] {
  return SHELL_MODULE_DEFINITIONS.map((definition) => {
    if (definition.key === 'workbench') return workbenchModule()
    if (definition.key === 'work') return workModule()
    if (definition.key === 'system') return systemModule(menus)
    if (definition.key === 'monitor') return monitorModule(menus)
    return placeholderModule(definition)
  })
}

/** 当前路径属于哪个一级模块。路径优先，未知路径不属于任何模块。 */
export function activeModuleKey(path: string): ShellModuleKey | null {
  const normalized = normalizePath(path)
  if (normalized === PATHS.WORKBENCH || normalized === PATHS.NOTICE) return 'workbench'
  if (normalized.startsWith(`${PATHS.NOTICE}/`)) return 'workbench'
  if (normalized === PATHS.WORK || normalized.startsWith(`${PATHS.WORK}/`)) return 'work'
  if (normalized === PATHS.SYSTEM || normalized.startsWith(`${PATHS.SYSTEM}/`)) return 'system'
  if (normalized === PATHS.MONITOR || normalized.startsWith(`${PATHS.MONITOR}/`)) return 'monitor'
  if (normalized === PATHS.TOOL || normalized.startsWith(`${PATHS.TOOL}/`)) return 'tool'
  return null
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
