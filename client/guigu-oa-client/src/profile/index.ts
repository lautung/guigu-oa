import { reactive } from 'vue'

import { failureMessage } from '@/request/request-failure'
import type {
  ProfileAvatarResult,
  ProfilePasswordPayload,
  ProfileRecord,
  ProfileWritePayload,
} from '@/types/domain'
import { presentProfile, validateAvatarFile, validatePassword, validateProfile } from './config'

export interface ProfileAdapter {
  load(): Promise<ProfileRecord>
  save(payload: ProfileWritePayload): Promise<unknown>
  changePassword(payload: ProfilePasswordPayload): Promise<unknown>
  uploadAvatar(file: File): Promise<ProfileAvatarResult>
  /** 认证取回头像字节并换成可显示的 blob URL。 */
  loadAvatar(path: string): Promise<string>
  releaseAvatar(url: string): void
  notify(message: string, type?: 'success' | 'error'): void
  /** 改密成功后作废本地进入结果并跳登录（ADR 0029）。 */
  signOut(): void | Promise<void>
}

export interface ProfileState {
  loading: boolean
  loaded: boolean
  saving: boolean
  uploading: boolean
  changingPassword: boolean
  username: string
  name: string
  phone: string
  avatarPath: string
  avatarUrl: string
  oldPassword: string
  newPassword: string
  confirmPassword: string
  formError: string
  avatarError: string
  passwordError: string
}

function emptyState(): ProfileState {
  const view = presentProfile()
  return {
    loading: false,
    loaded: false,
    saving: false,
    uploading: false,
    changingPassword: false,
    username: view.username,
    name: view.name,
    phone: view.phone,
    avatarPath: view.avatarPath,
    avatarUrl: '',
    oldPassword: '',
    newPassword: '',
    confirmPassword: '',
    formError: '',
    avatarError: '',
    passwordError: '',
  }
}

/**
 * 个人中心的状态机（#254）：资料读写、头像上传与改密。页面只做绑定；校验文案逐字
 * 镜像服务端 DTO，失败文案取服务端 201/205 的业务消息。
 */
export function createProfile(adapter: ProfileAdapter) {
  const state = reactive<ProfileState>(emptyState())
  let avatarSequence = 0

  function setAvatarUrl(url: string): void {
    if (state.avatarUrl && state.avatarUrl !== url) adapter.releaseAvatar(state.avatarUrl)
    state.avatarUrl = url
  }

  async function hydrateAvatar(path: string): Promise<void> {
    const sequence = ++avatarSequence
    if (!path) {
      setAvatarUrl('')
      return
    }
    try {
      const url = await adapter.loadAvatar(path)
      if (sequence !== avatarSequence) {
        if (url) adapter.releaseAvatar(url)
        return
      }
      setAvatarUrl(url)
    } catch {
      // 头像读失败是背景失败：保留默认图，不打断资料编辑。
      if (sequence === avatarSequence) setAvatarUrl('')
    }
  }

  async function load(): Promise<void> {
    state.loading = true
    state.formError = ''
    try {
      const record = await adapter.load()
      const view = presentProfile(record)
      state.username = view.username
      state.name = view.name
      state.phone = view.phone
      state.avatarPath = view.avatarPath
      await hydrateAvatar(view.avatarPath)
      state.loaded = true
    } catch (error) {
      state.formError = failureMessage(error) || '资料加载失败'
    } finally {
      state.loading = false
    }
  }

  async function save(): Promise<boolean> {
    const payload: ProfileWritePayload = { name: state.name.trim(), phone: state.phone.trim() }
    const invalid = validateProfile(payload)
    if (invalid) {
      state.formError = invalid
      return false
    }
    state.saving = true
    state.formError = ''
    try {
      await adapter.save(payload)
      adapter.notify('资料已保存', 'success')
      return true
    } catch (error) {
      state.formError = failureMessage(error) || '保存失败'
      return false
    } finally {
      state.saving = false
    }
  }

  async function uploadAvatar(file: File): Promise<boolean> {
    const invalid = validateAvatarFile(file)
    if (invalid) {
      state.avatarError = invalid
      return false
    }
    state.uploading = true
    state.avatarError = ''
    try {
      const result = await adapter.uploadAvatar(file)
      state.avatarPath = result.url || ''
      await hydrateAvatar(state.avatarPath)
      adapter.notify('头像已更新', 'success')
      return true
    } catch (error) {
      state.avatarError = failureMessage(error) || '头像上传失败'
      return false
    } finally {
      state.uploading = false
    }
  }

  async function changePassword(): Promise<boolean> {
    const payload: ProfilePasswordPayload = {
      oldPassword: state.oldPassword,
      newPassword: state.newPassword,
    }
    const invalid = validatePassword(payload, state.confirmPassword)
    if (invalid) {
      state.passwordError = invalid
      return false
    }
    state.changingPassword = true
    state.passwordError = ''
    try {
      await adapter.changePassword(payload)
      state.oldPassword = ''
      state.newPassword = ''
      state.confirmPassword = ''
      adapter.notify('密码已修改，请重新登录', 'success')
      await adapter.signOut()
      return true
    } catch (error) {
      state.passwordError = failureMessage(error) || '修改密码失败'
      return false
    } finally {
      state.changingPassword = false
    }
  }

  function dispose(): void {
    avatarSequence += 1
    setAvatarUrl('')
  }

  return { state, load, save, uploadAvatar, changePassword, dispose }
}
