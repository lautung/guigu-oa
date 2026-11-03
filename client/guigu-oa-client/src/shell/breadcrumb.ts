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

export interface BreadcrumbInput {
  path: string
  title?: string
  menus?: BackofficeMenuEntry[]
}

/**
 * 面包屑是「首页 / 一级 / 二级 / 页面」。详情与隐藏页回退到列表/可见父级 trail；
 * 一级用模块标题（系统管理），不跟随菜单根的名称。
 */
export function buildBreadcrumb({ path, title, menus = [] }: BreadcrumbInput): string[] {
  const current = normalizePath(path)
  const target = normalizePath(returnPathOfRoute(path))
  const moduleKey = activeModuleKey(target)
  const moduleTitle = moduleKey ? moduleTitleOf(moduleKey) : ''
  const currentTitle = current === target ? title || '' : ''

  if (!moduleKey) return currentTitle ? [HOME, currentTitle] : [HOME]

  if (moduleKey === 'workbench') {
    if (target === PATHS.WORKBENCH) return [HOME, moduleTitle]
    if (target === PATHS.NOTICE) return [HOME, moduleTitle, NOTICE_TITLE]
    return [HOME, moduleTitle]
  }

  if (moduleKey === 'work') {
    const pageTitle = BUILTIN_TITLES[target] || currentTitle || target
    return [HOME, moduleTitle, pageTitle]
  }

  const chain = menuChain(menus, target)
  const trail = [HOME, moduleTitle]

  chain.forEach((entry) => {
    const entryPath = normalizePath(entry.path)
    if (entryPath === PATHS.SYSTEM) return
    if (trail[trail.length - 1] !== entry.title) trail.push(entry.title)
  })

  if (currentTitle && trail[trail.length - 1] !== currentTitle) {
    trail.push(currentTitle)
  }

  return trail
}
