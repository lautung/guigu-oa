import type { Directive } from 'vue'
import { useUserStore } from '@/store/modules/user'

/** 按钮点判定（ADR 0052/0058）：授予内容来自 /info 的 permissions，服务端 gate 是唯一强制点。 */
export type PermissionCollection = readonly string[] | ReadonlySet<string> | null | undefined

export function permissionSet(permissions: PermissionCollection): Set<string> {
  return permissions ? new Set(Array.from(permissions)) : new Set()
}

export function isPermitted(
  permissions: PermissionCollection,
  permission: string | null | undefined,
): boolean {
  return !!permission && permissionSet(permissions).has(permission)
}

/** 脚本条件的权限判定：读取当前用户 store，模板或渲染函数里调用即随 permissions 更新。 */
export function hasPermission(permission: string): boolean {
  return isPermitted(useUserStore().permissions, permission)
}

/** 无权限移除 DOM，不是禁用或隐藏；mounted 与 updated 都重判。 */
export function createHasPermi(
  read: () => PermissionCollection = () => useUserStore().permissions,
): Directive<HTMLElement, string> {
  function apply(el: HTMLElement, permission: string) {
    if (!isPermitted(read(), permission)) el.remove()
  }
  return {
    mounted(el, binding) {
      apply(el, binding.value)
    },
    updated(el, binding) {
      apply(el, binding.value)
    },
  }
}

export const hasPermi = createHasPermi()
