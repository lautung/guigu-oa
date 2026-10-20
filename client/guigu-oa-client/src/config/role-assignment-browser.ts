import { ElMessage, ElMessageBox } from 'element-plus'
import type { Recognition } from '@/recognition'
import request from '@/utils/request'
import { createRoleAssignment, type RoleAssignment } from './role-assignment'

// The application instance outlives a user-page mount; only a real reload loses live requests.
const assignments = new WeakMap<Recognition, RoleAssignment>()
export function useRoleAssignment(recognition: Recognition): RoleAssignment {
  const existing = assignments.get(recognition)
  if (existing) return existing
  let confirming = false
  let confirmationId = 0
  const assignment = createRoleAssignment({
    recognition,
    request,
    records: {
      load(key) {
        const value = sessionStorage.getItem(key)
        return value === null ? null : JSON.parse(value)
      },
      save: (key, record) => sessionStorage.setItem(key, JSON.stringify(record)),
      remove: (key) => sessionStorage.removeItem(key),
    },
    async confirm(message, kind) {
      const ending = kind === 'end-assignment'
      const owner = ++confirmationId
      confirming = true
      try {
        await ElMessageBox.confirm(message, ending ? '结束本次分配' : '离开分配角色', {
          type: 'warning',
          confirmButtonText: ending ? '结束本次分配' : '确认离开',
          cancelButtonText: ending ? '保留本次分配' : '继续分配',
        })
        return true
      } catch {
        return false
      } finally {
        if (owner === confirmationId) confirming = false
      }
    },
    cancelConfirmation() {
      confirmationId++
      if (confirming) {
        confirming = false
        ElMessageBox.close()
      }
    },
    notify: (message) => {
      ElMessage.success(message)
    },
  })
  assignments.set(recognition, assignment)
  return assignment
}
