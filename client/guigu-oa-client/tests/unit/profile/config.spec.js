import { describe, expect, it } from 'vitest'

import {
  avatarRequest,
  changePasswordRequest,
  presentProfile,
  profileRequest,
  updateProfileRequest,
  uploadAvatarRequest,
} from '@/profile/config'

describe('profile/config', () => {
  it('builds the profile read/write/password requests', () => {
    expect(profileRequest()).toEqual({ method: 'get', url: '/admin/system/profile' })
    expect(updateProfileRequest({ name: '爱丽丝', phone: '138' })).toEqual({
      method: 'put',
      url: '/admin/system/profile',
      data: { name: '爱丽丝', phone: '138' },
    })
    expect(changePasswordRequest({ oldPassword: '111111', newPassword: '654321' })).toEqual({
      method: 'post',
      url: '/admin/system/profile/password',
      data: { oldPassword: '111111', newPassword: '654321' },
    })
  })

  it('uploads the avatar as multipart field `file` and reads it as a blob', () => {
    const file = new File(['x'], 'a.png', { type: 'image/png' })
    const config = uploadAvatarRequest(file)

    expect(config.method).toBe('post')
    expect(config.url).toBe('/admin/system/profile/avatar')
    expect(config.data).toBeInstanceOf(FormData)
    expect(config.data.get('file')).toBe(file)
    expect(avatarRequest('/admin/system/profile/avatar/2')).toEqual({
      method: 'get',
      url: '/admin/system/profile/avatar/2',
      responseType: 'blob',
    })
  })

  it('maps the server profile shape and treats missing fields as empty', () => {
    expect(
      presentProfile({ username: 'alice', name: '爱丽丝', phone: '138', avatar: '/avatar/2' }),
    ).toEqual({
      username: 'alice',
      name: '爱丽丝',
      phone: '138',
      avatarPath: '/avatar/2',
    })
    expect(presentProfile()).toEqual({ username: '', name: '', phone: '', avatarPath: '' })
  })
})
