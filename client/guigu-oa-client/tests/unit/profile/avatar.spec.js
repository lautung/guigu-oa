import { describe, expect, it, vi } from 'vitest'

import { createAvatarLoader } from '@/profile/avatar'

function fakeLoader(overrides = {}) {
  return {
    load: vi.fn(async () => new Blob(['png'])),
    createObjectUrl: vi.fn(() => 'blob:avatar'),
    revokeObjectUrl: vi.fn(),
    ...overrides,
  }
}

describe('profile/avatar', () => {
  it('loads a protected avatar into a blob URL', async () => {
    const loader = fakeLoader()
    const source = createAvatarLoader(loader)

    expect(await source.read('/admin/system/profile/avatar/2')).toBe('blob:avatar')
    expect(loader.load).toHaveBeenCalledWith('/admin/system/profile/avatar/2')
    expect(loader.createObjectUrl).toHaveBeenCalledTimes(1)
  })

  it('revokes released and disposed URLs, and never loads after dispose', async () => {
    const loader = fakeLoader()
    const source = createAvatarLoader(loader)

    const url = await source.read('/admin/system/profile/avatar/2')
    source.release(url)
    expect(loader.revokeObjectUrl).toHaveBeenCalledWith('blob:avatar')

    await source.read('/admin/system/profile/avatar/3')
    source.dispose()
    expect(loader.revokeObjectUrl).toHaveBeenCalledTimes(2)

    loader.load.mockClear()
    expect(await source.read('/admin/system/profile/avatar/4')).toBe('')
    expect(loader.load).not.toHaveBeenCalled()
  })
})
