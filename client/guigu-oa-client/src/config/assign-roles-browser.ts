import { ElMessage, ElMessageBox } from 'element-plus'
import router from '@/router'
import { openRequestScope } from '@/request/request-scope'
import { resolveClientSurface } from '@/surface/client-surface'
import { useUserStore } from '@/store/modules/user'
import { getEntryId } from '@/utils/auth'
import { classifySubmissionFailure } from '@/request/submission-outcome'
import type { Recognition } from '@/recognition'
import type { RoleAssignmentLists } from './sys-role'
import { assignRolesRequest, toAssignRolesRequest } from './sys-role'
import { createAssignRoles } from './assign-roles'

const modules = new WeakMap<Recognition, ReturnType<typeof createAssignRoles>>()

function storage(): {
  getItem: (key: string) => string | null
  setItem: (key: string, value: string) => void
  removeItem: (key: string) => void
  keys: () => string[]
} {
  return {
    getItem: (key) => sessionStorage.getItem(key),
    setItem: (key, value) => sessionStorage.setItem(key, value),
    removeItem: (key) => sessionStorage.removeItem(key),
    keys: () =>
      Array.from({ length: sessionStorage.length }, (_, index) => sessionStorage.key(index) || ''),
  }
}

export function useAssignRoles() {
  const userStore = useUserStore()
  const recognition = userStore.recognition
  if (!recognition) throw new Error('认出 module 尚未接入')
  let module = modules.get(recognition)
  if (!module) {
    const openScope = () => openRequestScope(recognition, { notifyNormalErrors: false })
    module = createAssignRoles({
      entryId: () => getEntryId() || '',
      capture: () => {
        const context = recognition.capture()
        return { generation: context.generation, entryId: context.entryId }
      },
      storage: storage(),
      read: async (userId) => {
        const response = await openScope().request<RoleAssignmentLists>(
          toAssignRolesRequest(userId),
        )
        const allRolesList = response.data?.allRolesList || []
        const assginRoleList = response.data?.assginRoleList || []
        return {
          roles: allRolesList.map((role) => ({
            id: role.id,
            roleName: role.roleName || String(role.id),
          })),
          assignedRoleIds: assginRoleList.flatMap((role) => (role.id == null ? [] : [role.id])),
        }
      },
      assign: async (userId, roleIds) => {
        try {
          await openScope().request(assignRolesRequest(userId, roleIds))
          return { kind: 'saved' }
        } catch (error) {
          if (classifySubmissionFailure(error, 'assign-roles') === 'refused')
            return {
              kind: 'rejected',
              message: error instanceof Error ? error.message : '分配角色未获接受',
            }
          return { kind: 'unconfirmed' }
        }
      },
      refreshOperator: () => recognition.recognize().then(() => undefined),
      occupancy: () =>
        resolveClientSurface({
          path: router.currentRoute.value.path,
          hasToken: !!userStore.token,
          menus: userStore.menus,
        }).occupancy,
      confirm: async (message, kind) => {
        const ending = kind === 'end-assignment'
        try {
          await ElMessageBox.confirm(message, ending ? '结束本次分配' : '关闭分配角色', {
            type: 'warning',
            confirmButtonText: ending ? '结束本次分配' : '确认关闭',
            cancelButtonText: ending ? '保留待确认' : '继续分配',
          })
          return true
        } catch {
          return false
        }
      },
      dismissConfirm: () => {
        ElMessageBox.close()
      },
      notify: (message, kind) => {
        ElMessage({ message, type: kind, duration: 5000 })
      },
      replace: (path) => {
        void router.replace(path)
      },
    })
    modules.set(recognition, module)
  }
  return module
}

export type AssignRolesFacade = ReturnType<typeof useAssignRoles>
