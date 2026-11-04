import { describe, expect, it, vi } from 'vitest'

import { createProfile } from '@/profile'
import { BusinessRequestFailure } from '@/request/request-failure'

function fakeAdapter(overrides = {}) {
  return {
    load: vi.fn(async () => ({
      username: 'alice',
      name: '爱丽丝',
      phone: '13800001111',
      avatar: '',
    })),
    save: vi.fn(async () => undefined),
    changePassword: vi.fn(async () => undefined),
    uploadAvatar: vi.fn(async () => ({ url: '/admin/system/profile/avatar/2' })),
    refreshUser: vi.fn(async () => undefined),
    loadAvatar: vi.fn(async () => 'blob:avatar'),
    releaseAvatar: vi.fn(),
    notify: vi.fn(),
    signOut: vi.fn(),
    ...overrides,
  }
}

function failure(message, code = 201) {
  return new BusinessRequestFailure('retryable', message, code)
}

describe('profile module', () => {
  it('loads the owner profile and hydrates the avatar through the adapter', async () => {
    const adapter = fakeAdapter({
      load: async () => ({
        username: 'alice',
        name: '爱丽丝',
        phone: '13800001111',
        avatar: '/admin/system/profile/avatar/2',
      }),
    })
    const profile = createProfile(adapter)

    await profile.load()

    expect(profile.state.username).toBe('alice')
    expect(profile.state.name).toBe('爱丽丝')
    expect(profile.state.phone).toBe('13800001111')
    expect(profile.state.avatarPath).toBe('/admin/system/profile/avatar/2')
    expect(adapter.loadAvatar).toHaveBeenCalledWith('/admin/system/profile/avatar/2')
    expect(profile.state.avatarUrl).toBe('blob:avatar')
    expect(profile.state.loaded).toBe(true)
  })

  it('shows the server message when the profile read fails', async () => {
    const profile = createProfile(
      fakeAdapter({ load: async () => Promise.reject(failure('用户不存在')) }),
    )

    await profile.load()

    expect(profile.state.formError).toBe('用户不存在')
    expect(profile.state.loaded).toBe(false)
  })

  it('validates 姓名/手机 before calling save', async () => {
    const adapter = fakeAdapter()
    const profile = createProfile(adapter)

    profile.state.name = '   '
    expect(await profile.save()).toBe(false)
    expect(profile.state.formError).toBe('姓名不能为空')

    profile.state.name = '名'.repeat(21)
    expect(await profile.save()).toBe(false)
    expect(profile.state.formError).toBe('姓名过长')

    profile.state.name = '爱丽丝'
    profile.state.phone = '1'.repeat(21)
    expect(await profile.save()).toBe(false)
    expect(profile.state.formError).toBe('手机号过长')

    expect(adapter.save).not.toHaveBeenCalled()
  })

  it('saves a trimmed name and phone and never the read-only username', async () => {
    const adapter = fakeAdapter()
    const profile = createProfile(adapter)
    profile.state.username = 'alice'
    profile.state.name = '  爱丽丝  '
    profile.state.phone = ' 13800001111 '

    expect(await profile.save()).toBe(true)

    expect(adapter.save).toHaveBeenCalledWith({ name: '爱丽丝', phone: '13800001111' })
    expect(adapter.notify).toHaveBeenCalledWith('资料已保存', 'success')
  })

  it('shows the server message when the profile save fails', async () => {
    const profile = createProfile(
      fakeAdapter({ save: async () => Promise.reject(failure('用户不存在')) }),
    )
    profile.state.name = '爱丽丝'

    expect(await profile.save()).toBe(false)

    expect(profile.state.formError).toBe('用户不存在')
    expect(profile.state.saving).toBe(false)
  })

  it('rejects unsupported or oversized avatars before uploading', async () => {
    const adapter = fakeAdapter()
    const profile = createProfile(adapter)

    expect(await profile.uploadAvatar(new File(['x'], 'a.gif', { type: 'image/gif' }))).toBe(false)
    expect(profile.state.avatarError).toBe('头像类型不支持')

    const big = new File(['x'], 'a.png', { type: 'image/png' })
    Object.defineProperty(big, 'size', { value: 2 * 1024 * 1024 + 1 })
    expect(await profile.uploadAvatar(big)).toBe(false)
    expect(profile.state.avatarError).toBe('头像超过 2 MB 限制')

    expect(adapter.uploadAvatar).not.toHaveBeenCalled()
  })

  it('shows the upload failure and keeps the previous avatar', async () => {
    const adapter = fakeAdapter({
      load: async () => ({
        username: 'alice',
        name: '爱丽丝',
        phone: '',
        avatar: '/admin/system/profile/avatar/2',
      }),
      loadAvatar: vi.fn(async () => 'blob:old'),
      uploadAvatar: vi.fn(async () => Promise.reject(failure('头像类型不支持'))),
    })
    const profile = createProfile(adapter)
    await profile.load()

    const file = new File(['x'], 'a.png', { type: 'image/png' })
    expect(await profile.uploadAvatar(file)).toBe(false)

    expect(profile.state.avatarError).toBe('头像类型不支持')
    expect(profile.state.avatarUrl).toBe('blob:old')
    expect(profile.state.uploading).toBe(false)
  })

  it('replaces the avatar and releases the previous blob URL', async () => {
    const adapter = fakeAdapter({
      load: async () => ({
        username: 'alice',
        name: '爱丽丝',
        phone: '',
        avatar: '/admin/system/profile/avatar/old',
      }),
      loadAvatar: vi.fn(async (path) => (path.endsWith('/old') ? 'blob:old' : 'blob:new')),
      uploadAvatar: vi.fn(async () => ({ url: '/admin/system/profile/avatar/old' })),
    })
    const profile = createProfile(adapter)
    await profile.load()
    adapter.uploadAvatar.mockResolvedValue({ url: '/admin/system/profile/avatar/new' })

    expect(await profile.uploadAvatar(new File(['x'], 'a.png', { type: 'image/png' }))).toBe(true)

    expect(adapter.releaseAvatar).toHaveBeenCalledWith('blob:old')
    expect(profile.state.avatarPath).toBe('/admin/system/profile/avatar/new')
    expect(profile.state.avatarUrl).toBe('blob:new')
    // 顶栏头像读 store：上传成功后必须刷新 /info，而不是让顶栏停在旧图。
    expect(adapter.refreshUser).toHaveBeenCalledTimes(1)
  })

  it('keeps the upload successful when the header refresh fails', async () => {
    const adapter = fakeAdapter({
      refreshUser: vi.fn(async () => Promise.reject(new Error('info down'))),
    })
    const profile = createProfile(adapter)

    expect(await profile.uploadAvatar(new File(['x'], 'a.png', { type: 'image/png' }))).toBe(true)

    expect(adapter.refreshUser).toHaveBeenCalledTimes(1)
    expect(adapter.notify).toHaveBeenCalledWith('头像已更新', 'success')
    expect(profile.state.avatarError).toBe('')
  })

  it('validates the old/new/confirm passwords before calling changePassword', async () => {
    const adapter = fakeAdapter()
    const profile = createProfile(adapter)
    profile.state.newPassword = '654321'
    profile.state.confirmPassword = '654321'

    expect(await profile.changePassword()).toBe(false)
    expect(profile.state.passwordError).toBe('旧密码不能为空')

    profile.state.oldPassword = '111111'
    profile.state.newPassword = '12345'
    profile.state.confirmPassword = '12345'
    expect(await profile.changePassword()).toBe(false)
    expect(profile.state.passwordError).toBe('密码长度必须在 6 到 100 位之间')

    profile.state.newPassword = '654321'
    profile.state.confirmPassword = '654322'
    expect(await profile.changePassword()).toBe(false)
    expect(profile.state.passwordError).toBe('两次输入的密码不一致')

    expect(adapter.changePassword).not.toHaveBeenCalled()
  })

  it('signs out after a successful password change (all sessions are retired)', async () => {
    const adapter = fakeAdapter()
    const profile = createProfile(adapter)
    profile.state.oldPassword = '111111'
    profile.state.newPassword = '654321'
    profile.state.confirmPassword = '654321'

    expect(await profile.changePassword()).toBe(true)

    expect(adapter.changePassword).toHaveBeenCalledWith({
      oldPassword: '111111',
      newPassword: '654321',
    })
    expect(adapter.notify).toHaveBeenCalledWith('密码已修改，请重新登录', 'success')
    expect(adapter.signOut).toHaveBeenCalledTimes(1)
    expect(profile.state.oldPassword).toBe('')
    expect(profile.state.newPassword).toBe('')
    expect(profile.state.confirmPassword).toBe('')
  })

  it('shows 旧密码错误 and stays signed in when the old password is wrong', async () => {
    const adapter = fakeAdapter({
      changePassword: async () => Promise.reject(failure('旧密码错误')),
    })
    const profile = createProfile(adapter)
    profile.state.oldPassword = '000000'
    profile.state.newPassword = '654321'
    profile.state.confirmPassword = '654321'

    expect(await profile.changePassword()).toBe(false)

    expect(profile.state.passwordError).toBe('旧密码错误')
    expect(adapter.signOut).not.toHaveBeenCalled()
    expect(profile.state.changingPassword).toBe(false)
  })
})
