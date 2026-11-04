import type {
  ApiRequestConfig,
  DeptRecord,
  DeptSelectNode,
  DeptTreeNode,
  EntityId,
  PostOption,
  PostRecord,
  UserForm,
  UserListItem,
  UserRecord,
} from '@/types/domain'
import { lengthRule, requiredRule, type LengthRule, type RequiredRule } from './form-rules'
import { presentDeptTree } from './sys-dept'

/** 用户页的独立动作按钮点（ADR 0052/0056）：重置密码不复用编辑点。 */
export const SYS_USER_PERMISSIONS = {
  resetPassword: 'system:user:resetPwd',
} as const

export const RESET_PASSWORD_MIN_LENGTH = 6

export const RESET_PASSWORD_MAX_LENGTH = 100

export const RESET_PASSWORD_LENGTH_MESSAGE = '密码长度必须在 6 到 100 位之间'

export interface ResetPasswordForm {
  newPassword: string
  confirmPassword: string
}

export interface MatchRule {
  validator(rule: unknown, value: unknown, callback: (error?: Error) => void): void
}

export type ResetPasswordRule = RequiredRule | LengthRule | MatchRule

export function presentListRecord(record: UserRecord = {}): UserListItem {
  return {
    id: record.id || '',
    username: record.username,
    name: record.name,
    phone: record.phone,
    status: record.status,
    statusEnabled: record.status === 1,
    createTime: record.createTime,
  }
}

export function presentListPage(
  page: { records?: UserRecord[]; total?: number; pages?: number } = {},
) {
  return {
    records: (page.records || []).map(presentListRecord),
    total: page.total || 0,
    pages: page.pages || 0,
  }
}

const SYS_USER_API = '/admin/system/sysUser'

/** 左树「全部」节点的 id：0 保留给「不过滤」，部门主键从 1 起。 */
export const ALL_DEPTS_ID = 0

/**
 * 用户页的只读部门树（#249）：走用户页自己的端点，只判 `/system/sysUser` 页面授予，
 * 不要求部门管理页的授予；写入口仍在部门管理页。
 */
export function deptTreeRequest(): ApiRequestConfig {
  return {
    method: 'get',
    url: `${SYS_USER_API}/depts`,
  }
}

/** 表单岗位多选的只读选项（#249）：同样只判用户页授予，服务端只回启用岗位。 */
export function postOptionsRequest(): ApiRequestConfig {
  return {
    method: 'get',
    url: `${SYS_USER_API}/posts`,
  }
}

/** 左树：prepend「全部」根节点，部门保持服务端的嵌套与排序。 */
export function presentDeptFilterTree(nodes: DeptRecord[] = []): DeptSelectNode[] {
  return [{ id: ALL_DEPTS_ID, name: '全部', children: presentDeptTree(nodes) }]
}

function toSelectNode(node: DeptTreeNode): DeptSelectNode {
  return {
    ...node,
    // 停用部门仍出现在左侧过滤树（历史用户仍归属它），但表单不能新选。
    disabled: node.status !== 1,
    children: node.children.map(toSelectNode),
  }
}

/** 表单的部门树：停用部门置 disabled，服务端仍会再用 201 拒绝。 */
export function presentDeptFormTree(nodes: DeptRecord[] = []): DeptSelectNode[] {
  return presentDeptTree(nodes).map(toSelectNode)
}

export function presentPostOptions(records: PostRecord[] = []): PostOption[] {
  return records
    .filter((record) => record.id !== undefined && record.id !== '')
    .map((record) => ({ id: record.id as EntityId, name: record.name || '' }))
}

export function listRequest(
  page: number,
  limit: number,
  searchObj: Record<string, string | number> = {},
): ApiRequestConfig {
  return {
    method: 'get',
    url: `${SYS_USER_API}/${page}/${limit}`,
    params: searchObj,
  }
}

export function getRequest(id: EntityId): ApiRequestConfig {
  return {
    method: 'get',
    url: `${SYS_USER_API}/get/${id}`,
  }
}

export function removeRequest(id: EntityId): ApiRequestConfig {
  return {
    method: 'delete',
    url: `${SYS_USER_API}/remove/${id}`,
  }
}

export function updateStatusRequest(id: EntityId, status: number): ApiRequestConfig {
  return {
    method: 'put',
    url: `${SYS_USER_API}/updateStatus/${id}/${status}`,
  }
}

/** 管理员重置他人的密码（#250）：请求体只收新密码，不需要旧密码。 */
export function resetPasswordRequest(id: EntityId, newPassword: string): ApiRequestConfig {
  return {
    method: 'post',
    url: `${SYS_USER_API}/${id}/resetPassword`,
    data: { newPassword },
  }
}

export function emptyResetPasswordForm(): ResetPasswordForm {
  return {
    newPassword: '',
    confirmPassword: '',
  }
}

/** 必填、长度与两次一致都镜像服务端 SysUserResetPasswordRequest（ADR 0038）。 */
export function resetPasswordRules(
  readForm: () => ResetPasswordForm,
): Record<'newPassword' | 'confirmPassword', ResetPasswordRule[]> {
  return {
    newPassword: [
      requiredRule('密码不能为空'),
      lengthRule(
        RESET_PASSWORD_MIN_LENGTH,
        RESET_PASSWORD_MAX_LENGTH,
        RESET_PASSWORD_LENGTH_MESSAGE,
      ),
    ],
    confirmPassword: [
      requiredRule('确认密码不能为空'),
      {
        validator(_rule, value, callback) {
          if (value !== readForm().newPassword) {
            callback(new Error('两次输入的密码不一致'))
            return
          }
          callback()
        },
      },
    ],
  }
}

function userPayload(form: UserForm | UserRecord, { includePassword = false } = {}) {
  const data: {
    id?: EntityId
    username?: string
    name?: string
    phone?: string
    status: number
    deptId?: EntityId
    postIds: EntityId[]
    password?: string
  } = {
    username: form.username,
    name: form.name,
    phone: form.phone,
    status: form.status == null ? 1 : form.status,
    // update 空列表=清空、未传=保留；表单总是带上当前选择（含空数组），意图明确。
    postIds: form.postIds || [],
  }

  // 空部门不发送：save 表示不归属，update 表示不覆盖（ADR 0034 的空字段语义）。
  if (form.deptId !== undefined && form.deptId !== null && form.deptId !== '') {
    data.deptId = form.deptId
  }

  if (form.id) {
    data.id = form.id
  }

  if (includePassword) {
    data.password = form.password
  }

  return data
}

export function writeRequest(form: UserForm | UserRecord): ApiRequestConfig {
  if (form.id) {
    return {
      method: 'put',
      url: `${SYS_USER_API}/update`,
      data: userPayload(form),
    }
  }

  return {
    method: 'post',
    url: `${SYS_USER_API}/save`,
    data: userPayload(form, { includePassword: true }),
  }
}

export function emptyForm(): UserForm {
  return {
    id: '',
    username: '',
    password: '',
    name: '',
    phone: '',
    status: 1,
    deptId: '',
    postIds: [],
  }
}

export function presentForm(record: UserRecord = {}): UserForm {
  return {
    id: record.id || '',
    username: record.username || '',
    password: '',
    name: record.name || '',
    phone: record.phone || '',
    status: record.status == null ? 1 : record.status,
    deptId: record.deptId == null ? '' : record.deptId,
    postIds: (record.postIds || []).filter((id) => id !== undefined && id !== null && id !== ''),
  }
}

/** 必填与文案镜像 SysUserSaveRequest(ADR 0034);phone 选填。 */
export function formRules(): Record<'username' | 'password' | 'name', RequiredRule[]> {
  return {
    username: [requiredRule('用户名不能为空')],
    password: [requiredRule('密码不能为空')],
    name: [requiredRule('姓名不能为空')],
  }
}
