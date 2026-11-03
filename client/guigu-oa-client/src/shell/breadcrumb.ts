import { WORK_BUILTIN_ITEMS } from '@/navigation/constants'
import { PATHS, returnPathOfRoute } from '@/router/paths'
import type { BackofficeMenuEntry } from '@/types/domain'
import { activeModuleKey, moduleTitleOf } from './shell-nav'

const HOME = '首页'
const NOTICE_TITLE = '全部公告'

const BUILTIN_TITLES: Record<string, string> = {
  [PATHS.WORKBENCH]: '工作台',
  [PATHS.NOTICE]: NOTICE_TITLE,
  ...Object.fromEntries(WORK_BUILTIN_ITEMS.map((item) => [item.path, item.title])),
}

export interface BreadcrumbNode {
  title: string
  /** 只有真实页面（列表页/可见父级）才有路径；目录与当前页不可点。 */
  path?: string
}

function normalizePath(path: string | undefined): string {
  const value = (path || '').split('?')[0].split('#')[0]
  const withLeadingSlash = value.startsWith('/') ? value : `/${value}`
  return withLeadingSlash.replace(/\/+$/, '') || '/'
}

function menuChain(menus: readonly BackofficeMenuEntry[], target: string): BackofficeMenuEntry[] {
  const byPath = new Map(menus.map((entry) => [normalizePath(entry.path), entry]))
  const chain: BackofficeMenuEntry[] = []
  let current = byPath.get(target)

  while (current && chain.length < 20) {
    chain.unshift(current)
    current = current.parentPath ? byPath.get(normalizePath(current.parentPath)) : undefined
  }

  return chain
}

function homeNode(current: string): BreadcrumbNode {
  return current === normalizePath(PATHS.WORKBENCH)
    ? { title: HOME }
    : { title: HOME, path: PATHS.WORKBENCH }
}

/** 中间层级指向真实页面；当前页只显示文字，不做自链接。 */
function pageNode(title: string, path: string, current: string): BreadcrumbNode {
  const target = normalizePath(path)
  return target === current ? { title } : { title, path: target }
}

export interface BreadcrumbInput {
  path: string
  title?: string
  menus?: BackofficeMenuEntry[]
}

/**
 * 面包屑是「首页 / 一级 / 二级 / 页面」。详情与隐藏页回退到列表/可见父级 trail；
 * 一级用模块标题（系统管理），不跟随菜单根的名称。有 path 的层级可点，目录与当前页不可点。
 */
export function buildBreadcrumb({ path, title, menus = [] }: BreadcrumbInput): BreadcrumbNode[] {
  const current = normalizePath(path)
  const target = normalizePath(returnPathOfRoute(path))
  const moduleKey = activeModuleKey(target)
  const moduleTitle = moduleKey ? moduleTitleOf(moduleKey) : ''
  const currentTitle = current === target ? title || '' : ''

  if (!moduleKey)
    return currentTitle ? [homeNode(current), { title: currentTitle }] : [homeNode(current)]

  if (moduleKey === 'workbench') {
    if (target === PATHS.WORKBENCH) return [homeNode(current), { title: moduleTitle }]
    if (target === PATHS.NOTICE) {
      return [
        homeNode(current),
        { title: moduleTitle },
        pageNode(NOTICE_TITLE, PATHS.NOTICE, current),
      ]
    }
    return [homeNode(current), { title: moduleTitle }]
  }

  if (moduleKey === 'work') {
    const pageTitle = BUILTIN_TITLES[target] || currentTitle || target
    return [homeNode(current), { title: moduleTitle }, pageNode(pageTitle, target, current)]
  }

  const chain = menuChain(menus, target)
  const trail: BreadcrumbNode[] = [homeNode(current), { title: moduleTitle }]

  chain.forEach((entry) => {
    const entryPath = normalizePath(entry.path)
    if (entryPath === PATHS.SYSTEM) return
    const node =
      entry.type === 1 ? pageNode(entry.title, entryPath, current) : { title: entry.title }
    if (trail[trail.length - 1].title !== node.title) trail.push(node)
  })

  if (currentTitle && trail[trail.length - 1].title !== currentTitle) {
    trail.push({ title: currentTitle })
  }

  return trail
}
