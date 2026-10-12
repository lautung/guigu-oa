import { isConfigClassMenu, isWorkMenu } from '@/config-permission'
import type { ApiRequestConfig, EntityId, MenuForm, MenuNode } from '@/types/domain'

const SYS_MENU_API = '/admin/system/sysMenu'

export function menuWriteValidationError(form: Partial<MenuNode> = {}): string | null {
  if (isWorkMenu(form)) {
    return '办事内置入口不在菜单管理中维护'
  }

  if (!isConfigClassMenu(form) && form.type !== 2) {
    return '只能维护配置类菜单，不能添加公众号、Dashboard、部门/岗位/日志或在线流程设计器'
  }

  return null
}

function presentMenuNode(menu: MenuNode = {}): MenuNode {
  return {
    id: menu.id,
    parentId: menu.parentId,
    name: menu.name,
    type: menu.type,
    path: menu.path,
    component: menu.component,
    perms: menu.perms || '',
    icon: menu.icon || '',
    sortValue: menu.sortValue,
    status: menu.status,
    select: !!(menu.select || menu.isSelect),
    children: [],
  }
}

function presentMenuTree(nodes: MenuNode[], keepNode: (node: MenuNode) => boolean): MenuNode[] {
  return nodes.filter(keepNode).map((node) => {
    const presented = presentMenuNode(node)
    presented.children = presentMenuTree(node.children || [], keepNode)
    return presented
  })
}

export function presentConfigMenuTree(nodes: MenuNode[] = []): MenuNode[] {
  return presentMenuTree(nodes, (node) => isConfigClassMenu(node) || node.type === 2)
}

export function presentAssignableConfigMenus(nodes: MenuNode[] = []): MenuNode[] {
  return presentMenuTree(nodes, isConfigClassMenu)
}

export function collectCheckedIds(nodes: MenuNode[] = []): EntityId[] {
  return nodes.reduce<EntityId[]>((ids, node) => {
    const children = node.children || []
    if (node.select && children.length === 0 && node.id != null) {
      ids.push(node.id)
    }
    return ids.concat(collectCheckedIds(children))
  }, [])
}

function collectSelectedIds(node: MenuNode, ids: EntityId[]): void {
  if (node.select && node.id != null) {
    ids.push(node.id)
  }
  ;(node.children || []).forEach((child) => collectSelectedIds(child, ids))
}

export function preservedOutOfSpecMenuIds(nodes: MenuNode[] = []): EntityId[] {
  const ids: EntityId[] = []

  function walk(list: MenuNode[]): void {
    list.forEach((node) => {
      if (!isConfigClassMenu(node)) {
        collectSelectedIds(node, ids)
      } else {
        walk(node.children || [])
      }
    })
  }

  walk(nodes)
  return ids
}

export function mergeAssignMenuIds(
  checkedConfigIds: EntityId[] = [],
  fullTree: MenuNode[] = [],
): EntityId[] {
  return Array.from(new Set(checkedConfigIds.concat(preservedOutOfSpecMenuIds(fullTree))))
}

export function findNodesRequest(): ApiRequestConfig {
  return {
    method: 'get',
    url: `${SYS_MENU_API}/findNodes`,
  }
}

export function toAssignRequest(roleId: EntityId): ApiRequestConfig {
  return {
    method: 'get',
    url: `${SYS_MENU_API}/toAssign/${roleId}`,
  }
}

export function doAssignRequest(roleId: EntityId, menuIdList: EntityId[]): ApiRequestConfig {
  return {
    method: 'post',
    url: `${SYS_MENU_API}/doAssign`,
    data: { roleId, menuIdList },
  }
}

export function removeRequest(id: EntityId): ApiRequestConfig {
  return {
    method: 'delete',
    url: `${SYS_MENU_API}/remove/${id}`,
  }
}

function menuPayload(form: MenuForm): Record<string, unknown> {
  const prepared = applyDirectoryComponent(form)
  const data: Record<string, unknown> = {
    parentId: prepared.parentId,
    name: prepared.name,
    type: prepared.type,
    path: prepared.path,
    component: prepared.component,
    perms: prepared.perms || '',
    icon: prepared.icon || '',
    sortValue: prepared.sortValue,
    status: prepared.status,
  }

  if (prepared.id) {
    data.id = prepared.id
  }

  return data
}

export function applyDirectoryComponent(form: MenuForm): MenuForm {
  if (form.type !== 0) {
    return form
  }
  if (form.parentId !== 0) {
    return { ...form, component: 'ParentView' }
  }
  return { ...form, component: form.component || 'Layout' }
}

export function writeRequest(form: MenuForm): ApiRequestConfig {
  if (form.id) {
    return {
      method: 'put',
      url: `${SYS_MENU_API}/update`,
      data: menuPayload(form),
    }
  }

  return {
    method: 'post',
    url: `${SYS_MENU_API}/save`,
    data: menuPayload(form),
  }
}

export function emptyForm(): MenuForm {
  return {
    id: '',
    parentId: 0,
    parentName: '',
    name: '',
    type: 0,
    path: '',
    component: 'Layout',
    perms: '',
    icon: '',
    sortValue: 1,
    status: 1,
  }
}

export function presentForm(record: MenuNode = {}): MenuForm {
  return {
    id: record.id || '',
    parentId: record.parentId == null ? 0 : record.parentId,
    parentName: record.parentName || '',
    name: record.name || '',
    type: record.type == null ? 0 : record.type,
    path: record.path || '',
    component: record.component || '',
    perms: record.perms || '',
    icon: record.icon || '',
    sortValue: record.sortValue == null ? 1 : record.sortValue,
    status: record.status == null ? 1 : record.status,
  }
}

export function formForCreate(parent?: MenuNode): MenuForm {
  const form = emptyForm()
  if (!parent) {
    return form
  }

  form.parentId = parent.id ?? 0
  form.parentName = parent.name || ''
  form.component = ''

  if (parent.type === 0) {
    form.type = 1
  } else if (parent.type === 1) {
    form.type = 2
  }

  return form
}
