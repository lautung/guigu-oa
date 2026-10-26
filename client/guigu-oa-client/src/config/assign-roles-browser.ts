import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/store/modules/user'
import { getEntryId } from '@/utils/auth'
import request, { RequestFailure } from '@/utils/request'
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

function rejectedBeforeWrite(error: RequestFailure): boolean {
  const detail = error.detail
  if (detail.operation === 'refresh') return true
  if (detail.source === 'business' && [208, 209, 403].includes(detail.businessCode || 0))
    return true
  return detail.source === 'http' && [401, 403].includes(detail.httpStatus || 0)
}

export function useAssignRoles() {
  const recognition = useUserStore().recognition
  if (!recognition) throw new Error('认出 module 尚未接入')
  let module = modules.get(recognition)
  if (!module) {
    module = createAssignRoles({
      entryId: () => getEntryId() || '',
      capture: () => {
        const context = recognition.capture()
        return { generation: context.generation, entryId: context.entryId }
      },
      storage: storage(),
      read: async (userId) => {
        const response = await request<RoleAssignmentLists>({
          ...toAssignRolesRequest(userId),
          normalErrorNotification: 'caller',
        })
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
          await request({
            ...assignRolesRequest(userId, roleIds),
            normalErrorNotification: 'caller',
          })
          return { kind: 'saved' }
        } catch (error) {
          if (error instanceof RequestFailure && rejectedBeforeWrite(error))
            return { kind: 'rejected', message: error.message }
          return { kind: 'unconfirmed' }
        }
      },
      refreshOperator: () => recognition.recognize().then(() => undefined),
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
      notify: (message, kind) => {
        ElMessage({ message, type: kind, duration: 5000 })
      },
    })
    modules.set(recognition, module)
  }
  return module
}

export type AssignRolesFacade = ReturnType<typeof useAssignRoles>
