/**
 * 配置权限：有没有、授了哪些配置类菜单、这页能不能占用。
 * 输入是 info 给来的菜单树。不认用户 id，不认按钮。
 * 菜单管理可以问什么算配置类菜单；勾选和增删改仍是配置页的动作。
 */

const EXCLUDED_PATH_PATTERNS = [
  /wechat/i,
  /dashboard/i,
  /onlineProcessSet/i
]

const EXCLUDED_COMPONENTS = [
  'system/sysDept/list',
  'system/sysPost/list',
  'system/sysOperLog/list',
  'system/sysLoginLog/list'
]

function isExcludedConfigMenu(menu = {}) {
  const path = menu.path || ''
  const component = menu.component || ''

  if (EXCLUDED_PATH_PATTERNS.some(pattern => pattern.test(path) || pattern.test(component))) {
    return true
  }

  return EXCLUDED_COMPONENTS.includes(component)
}

function normalizePath(path) {
  if (!path) return ''
  return path.startsWith('/') ? path : `/${path}`
}

function joinPath(base, segment) {
  const normalizedBase = normalizePath(base).replace(/\/$/, '')
  const normalizedSegment = (segment || '').replace(/^\//, '')
  if (!normalizedSegment) return normalizedBase
  return `${normalizedBase}/${normalizedSegment}`
}

function resolveMenuPath(menu, parentPath = '') {
  const segment = menu.path || ''
  if (segment.startsWith('/')) {
    return normalizePath(segment)
  }
  return joinPath(parentPath, segment)
}

function titleOf(menu) {
  return (menu.meta && menu.meta.title) || menu.title || menu.name || ''
}

export function isWorkMenu(menu = {}) {
  const path = menu.path || ''
  return path === 'work' || /^\/work/i.test(path) || /^work\//i.test(path)
}

function isButtonMenu(menu = {}) {
  return menu.type === 2
}

export function isConfigClassMenu(menu = {}) {
  return !isButtonMenu(menu) && !isWorkMenu(menu) && !isExcludedConfigMenu(menu)
}

function grantedConfigMenusFrom(menus = [], parentPath = '') {
  const granted = []

  menus.forEach(menu => {
    if (menu.hidden || !isConfigClassMenu(menu)) {
      return
    }

    const path = resolveMenuPath(menu, parentPath)
    const pageChildren = (menu.children || []).filter(child => !isButtonMenu(child))
    const children = grantedConfigMenusFrom(pageChildren, path)
    if (pageChildren.length > 0 && children.length === 0) {
      return
    }

    const node = {
      path,
      title: titleOf(menu)
    }
    if (children.length > 0) {
      node.children = children
    }
    granted.push(node)
  })

  return granted
}

function collectGrantedLeaves(nodes, leaves = []) {
  nodes.forEach(node => {
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
  { path: '/processSet/templateSet', parentPath: '/processSet/processTemplate' }
]

function pathCanOccupy(path, leaves) {
  const normalized = normalizePath(path)
  if (leaves.some(leaf => (
    normalized === leaf
    || normalized.startsWith(`${leaf}/`)
    || leaf.startsWith(`${normalized}/`)
  ))) {
    return true
  }

  return HIDDEN_CONFIG_PAGES.some(hidden => (
    (normalized === hidden.path || normalized.startsWith(`${hidden.path}/`))
    && leaves.includes(hidden.parentPath)
  ))
}

export function configPermission(menus = []) {
  const grantedConfigMenus = grantedConfigMenusFrom(menus)
  const grantedLeaves = collectGrantedLeaves(grantedConfigMenus)

  return {
    hasConfigPermission: grantedLeaves.length > 0,
    grantedConfigMenus,
    canOccupy(path) {
      return pathCanOccupy(path, grantedLeaves)
    }
  }
}
