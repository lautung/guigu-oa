import { configPermission } from '@/config/permission'
import { translate as t } from '@/i18n'
import { WORK_BUILTIN_ITEMS } from '@/navigation/constants'
import { PATHS, returnPathOfRoute } from '@/router/paths'
import { ROUTE_GROUP_ENTRIES } from '@/router/routes'
import type { BackofficeMenuEntry, SidebarItem } from '@/types/domain'

export type ShellModuleKey = 'workbench' | 'work' | 'business' | 'system' | 'monitor' | 'tool'

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
}

/** 六个一级模块定义（ADR 0059，蓝图 §6）：顶部固定六项，不随权限增减。 */
const MODULE_DEFINITIONS = [
  {
    key: 'workbench',
    titleKey: 'shell.modules.workbench',
    icon: 'HomeFilled',
    prefix: PATHS.WORKBENCH,
  },
  { key: 'work', titleKey: 'shell.modules.work', icon: 'Tickets', prefix: PATHS.WORK },
  {
    key: 'business',
    titleKey: 'shell.modules.business',
    icon: 'Folder',
    prefix: PATHS.BUSINESS,
  },
  { key: 'system', titleKey: 'shell.modules.system', icon: 'Setting', prefix: PATHS.SYSTEM },
  { key: 'monitor', titleKey: 'shell.modules.monitor', icon: 'Monitor', prefix: PATHS.MONITOR },
  { key: 'tool', titleKey: 'shell.modules.tool', icon: 'Tools', prefix: PATHS.TOOL },
] as const

/** 标题用 getter 读当前 locale：面包屑与导航在同一次渲染里拿到同一种语言。 */
export const SHELL_MODULE_DEFINITIONS: readonly ShellModuleDefinition[] = MODULE_DEFINITIONS.map(
  (definition) => ({
    key: definition.key,
    get title() {
      return t(definition.titleKey)
    },
    icon: definition.icon,
    prefix: definition.prefix,
  }),
)

const WORK_ICONS: Record<string, string> = {
  [PATHS.WORK_START]: 'EditPen',
  [PATHS.WORK_PENDING]: 'AlarmClock',
  [PATHS.WORK_PROCESSED]: 'CircleCheck',
  [PATHS.WORK_STARTED]: 'Promotion',
  [PATHS.WORK_COPIED]: 'Share',
  [PATHS.WORK_MY_ATTENDANCE]: 'Clock',
  [PATHS.WORK_MY_ATTENDANCE_RECORD]: 'Calendar',
}

const WORK_PAGE_TITLE_KEYS: Record<string, string> = {
  [PATHS.WORK_START]: 'shell.workPages.start',
  [PATHS.WORK_PENDING]: 'shell.workPages.pending',
  [PATHS.WORK_PROCESSED]: 'shell.workPages.processed',
  [PATHS.WORK_STARTED]: 'shell.workPages.started',
  [PATHS.WORK_COPIED]: 'shell.workPages.copied',
  [PATHS.WORK_MY_ATTENDANCE]: 'shell.workPages.myAttendance',
  [PATHS.WORK_MY_ATTENDANCE_RECORD]: 'shell.workPages.myAttendanceRecord',
}

/** 内置办事页的标签由客户端拥有；面包屑与侧栏共用这一处投影。 */
export function workPageTitle(path: string, fallback = ''): string {
  const key = WORK_PAGE_TITLE_KEYS[normalizePath(path)]
  return key ? t(key) : fallback
}

/** 配置页按真实菜单形态分组：这三段前缀成为嵌套分组标题，其余 `/system/*` 平铺。 */
const SYSTEM_GROUP_DEFINITIONS = [
  { titleKey: 'shell.groups.logs', prefix: `${PATHS.SYSTEM}/log/`, icon: 'Document' },
  { titleKey: 'shell.groups.processSettings', prefix: `${PATHS.PROCESS_SET}/`, icon: 'Setting' },
  {
    titleKey: 'shell.groups.processManagement',
    prefix: `${PATHS.PROCESS_MGR}/`,
    icon: 'Files',
  },
] as const

/** 业务页按真实菜单形态分组：W3 只有考勤管理，W2 人事 / W4 报销在同一目录追加。 */
const BUSINESS_GROUP_DEFINITIONS = [
  {
    titleKey: 'shell.groups.attendance',
    prefix: `${PATHS.BUSINESS_ATTENDANCE}/`,
    icon: 'AlarmClock',
  },
] as const

/** 目录本身不是页面：/business 与 /business/attendance 只做侧栏分组，不渲染成可点项。 */
const BUSINESS_DIRECTORY_ROOTS = new Set<string>([PATHS.BUSINESS, PATHS.BUSINESS_ATTENDANCE])

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
    { path: PATHS.WORKBENCH, title: t('shell.items.workbench'), icon: 'HomeFilled' },
    { path: PATHS.NOTICE, title: t('shell.items.notice'), icon: 'Bell' },
  ]
  return {
    key: 'workbench',
    title: t('shell.modules.workbench'),
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
    title: workPageTitle(item.path, item.title),
    icon: WORK_ICONS[item.path] || 'Document',
  }))
  return {
    key: 'work',
    title: t('shell.modules.work'),
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
      ? [{ title: t(definition.titleKey), icon: definition.icon, items: groupItems }]
      : []
  })

  const firstPath = items[0]?.path || groups[0]?.items[0]?.path || ''
  return {
    key: 'system',
    title: t('shell.modules.system'),
    icon: 'Setting',
    items,
    groups,
    firstPath,
    emptyHint: firstPath ? '' : t('shell.empty.noPages'),
  }
}

/**
 * 客户端已落路由的目录子页（#239/#246）：路由表里带 menuGroup 的 `<prefix>/*`。
 * 服务端菜单先行（监控 V117+ 全部页面都已授予），客户端页面随各自票落地；
 * 还没有客户端路由的授予页不渲染成死链。
 */
function routedPagesUnder(prefix: string, group: 'config' | 'business' = 'config'): Set<string> {
  return new Set(
    ROUTE_GROUP_ENTRIES.filter(
      (entry) => entry.group === group && entry.path.startsWith(`${prefix}/`),
    ).map((entry) => entry.path),
  )
}

/**
 * 业务一级模块（蓝图 §6）：已授予且已落路由的页面按二级目录分组。
 * 与系统管理同款：目录行不渲染成可点项，firstPath 取第一个可进入的页面。
 */
function businessModule(menus: readonly BackofficeMenuEntry[]): ShellModule {
  const routed = routedPagesUnder(PATHS.BUSINESS, 'business')
  const permission = configPermission([...menus])
  const leaves = permission.grantedMenus
    .flatMap((root) => leavesOf(root))
    .filter((leaf) => leaf.path.startsWith(`${PATHS.BUSINESS}/`))
    .filter((leaf) => !BUSINESS_DIRECTORY_ROOTS.has(leaf.path))
    .filter((leaf) => routed.has(leaf.path))

  const items = leaves.filter(
    (leaf) => !BUSINESS_GROUP_DEFINITIONS.some((group) => leaf.path.startsWith(group.prefix)),
  )
  const groups: ShellNavGroup[] = BUSINESS_GROUP_DEFINITIONS.flatMap((definition) => {
    const groupItems = leaves.filter((leaf) => leaf.path.startsWith(definition.prefix))
    return groupItems.length
      ? [{ title: t(definition.titleKey), icon: definition.icon, items: groupItems }]
      : []
  })

  const firstPath = items[0]?.path || groups[0]?.items[0]?.path || ''
  return {
    key: 'business',
    title: t('shell.modules.business'),
    icon: 'Folder',
    items,
    groups,
    firstPath,
    emptyHint: firstPath ? '' : t('shell.empty.noPages'),
  }
}

/** 平铺二级项的目录模块（系统监控 ADR 0060、系统工具 ADR 0061）：firstPath 取第一个已授予且有客户端路由的页面。 */
function flatDirectoryModule(
  definition: ShellModuleDefinition,
  menus: readonly BackofficeMenuEntry[],
): ShellModule {
  const routed = routedPagesUnder(definition.prefix)
  const permission = configPermission([...menus])
  const items = permission.grantedMenus
    .flatMap((root) => leavesOf(root))
    .filter((leaf) => leaf.path.startsWith(`${definition.prefix}/`))
    .filter((leaf) => routed.has(leaf.path))

  const firstPath = items[0]?.path || ''
  return {
    key: definition.key,
    title: definition.title,
    icon: definition.icon,
    items,
    groups: [],
    firstPath,
    emptyHint: firstPath ? '' : t('shell.empty.noPages'),
  }
}

/** 六个一级模块固定返回；业务、系统管理、系统监控与系统工具只由已授予且已落路由的页面填充。 */
export function buildShellNav(menus: BackofficeMenuEntry[] = []): ShellModule[] {
  return SHELL_MODULE_DEFINITIONS.map((definition) => {
    if (definition.key === 'workbench') return workbenchModule()
    if (definition.key === 'work') return workModule()
    if (definition.key === 'business') return businessModule(menus)
    if (definition.key === 'system') return systemModule(menus)
    return flatDirectoryModule(definition, menus)
  })
}

/** 当前路径属于哪个一级模块。路径优先，未知路径不属于任何模块。 */
export function activeModuleKey(path: string): ShellModuleKey | null {
  const normalized = normalizePath(path)
  if (normalized === PATHS.WORKBENCH || normalized === PATHS.NOTICE) return 'workbench'
  if (normalized.startsWith(`${PATHS.NOTICE}/`)) return 'workbench'
  if (normalized === PATHS.WORK || normalized.startsWith(`${PATHS.WORK}/`)) return 'work'
  if (normalized === PATHS.BUSINESS || normalized.startsWith(`${PATHS.BUSINESS}/`)) {
    return 'business'
  }
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
