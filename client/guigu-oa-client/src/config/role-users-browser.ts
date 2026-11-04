import { ElMessage, ElMessageBox } from 'element-plus'
import router from '@/router'
import { openRequestScope } from '@/request/request-scope'
import { useUserStore } from '@/store/modules/user'
import { getEntryId } from '@/utils/auth'
import { classifySubmissionFailure } from '@/request/submission-outcome'
import type { Recognition } from '@/recognition'
import type { EntityId, RoleUserRecord } from '@/types/domain'
import { assignRoleUsersRequest, roleUsersRequest } from './sys-role'
import { createRoleUsers, type RoleUser } from './role-users'

const modules = new WeakMap<Recognition, ReturnType<typeof createRoleUsers>>()

/** 成员右侧列表要完整，不跟候选分页走；单页上限取服务端 PageWindow 的 500。 */
const MEMBER_PAGE_SIZE = 500

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

function toRoleUser(record: RoleUserRecord): RoleUser {
  const id = record.id as EntityId
  return {
    id,
    username: record.username || String(id),
    name: record.name,
    phone: record.phone,
  }
}

export function useRoleUsers() {
  const userStore = useUserStore()
  const recognition = userStore.recognition
  if (!recognition) throw new Error('认出 module 尚未接入')
  let module = modules.get(recognition)
  if (!module) {
    const openScope = () => openRequestScope(recognition, { notifyNormalErrors: false })
    module = createRoleUsers({
      entryId: () => getEntryId() || '',
      capture: () => {
        const context = recognition.capture()
        return { generation: context.generation, entryId: context.entryId }
      },
      storage: storage(),
      readMembers: async (roleId) => {
        // 多页成员读共用一次身份捕获，翻页途中换身份不会拼出两份人的列表。
        const scope = openScope()
        const members: RoleUser[] = []
        for (let page = 1; page <= 100; page += 1) {
          const response = await scope.request<{
            records?: RoleUserRecord[]
            total?: number
          }>(roleUsersRequest(roleId, { page, limit: MEMBER_PAGE_SIZE, assigned: true }))
          const records = response.data?.records || []
          members.push(...records.map(toRoleUser))
          const total = response.data?.total || 0
          if (!records.length || members.length >= total) break
        }
        return members
      },
      readCandidates: async (roleId, query) => {
        const response = await openScope().request<{
          records?: RoleUserRecord[]
          total?: number
        }>(
          roleUsersRequest(roleId, {
            page: query.page,
            limit: query.size,
            keyword: query.keyword,
            assigned: false,
          }),
        )
        const records = response.data?.records || []
        return {
          users: records.map(toRoleUser),
          total: response.data?.total || records.length,
        }
      },
      assign: async (roleId, userIds) => {
        try {
          await openScope().request(assignRoleUsersRequest(roleId, userIds))
          return { kind: 'saved' }
        } catch (error) {
          if (classifySubmissionFailure(error, 'assign-role-users') === 'refused')
            return {
              kind: 'rejected',
              message: error instanceof Error ? error.message : '分配用户未获接受',
            }
          return { kind: 'unconfirmed' }
        }
      },
      refreshOperator: () => recognition.recognize().then(() => undefined),
      confirm: async (message, kind) => {
        const ending = kind === 'end-assignment'
        try {
          await ElMessageBox.confirm(message, ending ? '结束本次分配' : '关闭分配用户', {
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

export type RoleUsersFacade = ReturnType<typeof useRoleUsers>
