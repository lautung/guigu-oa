import type {
  ApiRequestConfig,
  ProfilePasswordPayload,
  ProfileRecord,
  ProfileView,
  ProfileWritePayload,
} from '@/types/domain'

/** 个人中心（#254）：登录即有，不是后台页，不带页面授予/按钮点。 */
export const PROFILE_API = '/admin/system/profile'
export const PROFILE_AVATAR_MAX_BYTES = 2 * 1024 * 1024
export const PROFILE_AVATAR_TYPES = ['image/jpeg', 'image/png', 'image/webp'] as const

export function profileRequest(): ApiRequestConfig {
  return { method: 'get', url: PROFILE_API }
}

export function updateProfileRequest(payload: ProfileWritePayload): ApiRequestConfig {
  return { method: 'put', url: PROFILE_API, data: { ...payload } }
}

export function changePasswordRequest(payload: ProfilePasswordPayload): ApiRequestConfig {
  return { method: 'post', url: `${PROFILE_API}/password`, data: { ...payload } }
}

/** 上传走服务端转存（multipart 字段 `file`），固定键覆盖式写入。 */
export function uploadAvatarRequest(file: File): ApiRequestConfig {
  const data = new FormData()
  data.append('file', file)
  return { method: 'post', url: `${PROFILE_API}/avatar`, data }
}

/** 头像读是已认证同源二进制端点；路径由服务端下发（/info.avatar 或资料读的 avatar）。 */
export function avatarRequest(path: string): ApiRequestConfig & { responseType: 'blob' } {
  return { method: 'get', url: path, responseType: 'blob' }
}

export function presentProfile(record: Partial<ProfileRecord> = {}): ProfileView {
  return {
    username: record.username || '',
    name: record.name || '',
    phone: record.phone || '',
    avatarPath: record.avatar || '',
  }
}

export function validateProfile(payload: ProfileWritePayload): string {
  if (!payload.name.trim()) return '姓名不能为空'
  if (payload.name.length > 20) return '姓名过长'
  if (payload.phone.length > 20) return '手机号过长'
  return ''
}

export function validatePassword(payload: ProfilePasswordPayload, confirmPassword: string): string {
  if (!payload.oldPassword) return '旧密码不能为空'
  if (!payload.newPassword) return '新密码不能为空'
  if (payload.newPassword.length < 6 || payload.newPassword.length > 100) {
    return '密码长度必须在 6 到 100 位之间'
  }
  if (payload.newPassword !== confirmPassword) return '两次输入的密码不一致'
  return ''
}

/** 本地预检与服务端同文案；真正的判定仍在服务端（类型/魔数/2MB）。 */
export function validateAvatarFile(file: File): string {
  if (!(PROFILE_AVATAR_TYPES as readonly string[]).includes(file.type)) return '头像类型不支持'
  if (file.size > PROFILE_AVATAR_MAX_BYTES) return '头像超过 2 MB 限制'
  return ''
}
