import { ElMessage } from 'element-plus'

import request, { requestBinary } from '@/request/request'
import { useUserStore } from '@/store/modules/user'
import type { ProfileAvatarResult, ProfileRecord } from '@/types/domain'
import { createAvatarLoader } from './avatar'
import { createProfile } from './index'
import {
  avatarRequest,
  changePasswordRequest,
  profileRequest,
  updateProfileRequest,
  uploadAvatarRequest,
} from './config'

const DEFAULT_NOTICE_DURATION = 3000

/**
 * `/profile` 页面的浏览器接线（#254）：HTTP、头像 blob 生命周期、提示与改密后的
 * 退出跳转都收在这里；改密成功复用 user store 的 `logout`（服务端令牌已作废，
 * 请求失败也照样离开）→ 登录页。
 */
export function createProfilePage() {
  const userStore = useUserStore()
  const avatars = createAvatarLoader({
    load: (path) => requestBinary(avatarRequest(path), { notifyNormalErrors: false }),
    createObjectUrl: (blob) => URL.createObjectURL(blob),
    revokeObjectUrl: (url) => URL.revokeObjectURL(url),
  })

  const profile = createProfile({
    load: async () => (await request<ProfileRecord>(profileRequest())).data || {},
    save: async (payload) => {
      await request(updateProfileRequest(payload))
    },
    changePassword: async (payload) => {
      await request(changePasswordRequest(payload))
    },
    uploadAvatar: async (file): Promise<ProfileAvatarResult> => {
      const response = await request<ProfileAvatarResult>(uploadAvatarRequest(file))
      return response.data || {}
    },
    refreshUser: () => userStore.getInfo(),
    loadAvatar: (path) => avatars.read(path),
    releaseAvatar: (url) => avatars.release(url),
    notify: (message, type = 'success') =>
      ElMessage({ message, type, duration: DEFAULT_NOTICE_DURATION }),
    signOut: () => userStore.logout(),
  })

  function dispose(): void {
    profile.dispose()
    avatars.dispose()
  }

  return { ...profile, dispose }
}
