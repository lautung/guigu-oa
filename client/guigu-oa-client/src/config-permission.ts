import type { MenuNode, SidebarItem } from '@/types/domain'

/**
 * 配置权限：有没有、授了哪些配置类菜单、这页能不能占用。
 * 输入是 info 给来的菜单树。不认用户 id，不认按钮。
 * 菜单管理可以问什么算配置类菜单；勾选和增删改仍是配置页的动作。
 */

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
  return path.startsWith('/') ? path : `/${path}`
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

const HIDDEN_CONFIG_PAGES = [
  { path: '/system/assignAuth', parentPath: '/system/sysRole' },
  { path: '/processSet/templateSet', parentPath: '/processSet/processTemplate' },
]

function pathCanOccupy(path: string, leaves: string[]): boolean {
  const normalized = normalizePath(path)
  if (
    leaves.some(
      (leaf) =>
        normalized === leaf ||
        normalized.startsWith(`${leaf}/`) ||
        leaf.startsWith(`${normalized}/`),
    )
  ) {
    return true
  }

  return HIDDEN_CONFIG_PAGES.some(
    (hidden) =>
      (normalized === hidden.path || normalized.startsWith(`${hidden.path}/`)) &&
      leaves.includes(hidden.parentPath),
  )
}

export function configPermission(menus: MenuNode[] = []) {
  const grantedConfigMenus = grantedConfigMenusFrom(menus)
  const grantedLeaves = collectGrantedLeaves(grantedConfigMenus)

  return {
    hasConfigPermission: grantedLeaves.length > 0,
    grantedConfigMenus,
    canOccupy(path: string): boolean {
      return pathCanOccupy(path, grantedLeaves)
    },
  }
}
