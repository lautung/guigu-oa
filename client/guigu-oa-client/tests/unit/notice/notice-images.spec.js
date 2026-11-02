import { describe, expect, it, vi } from 'vitest'

import { createNoticeImageHydrator, noticeImageId } from '@/notice/notice-images'

function imageElement(src) {
  const img = document.createElement('img')
  img.setAttribute('src', src)
  return img
}

function harness(options = {}) {
  const created = []
  const revoked = []
  const loadImage = options.loadImage || vi.fn(async () => new Blob(['png'], { type: 'image/png' }))
  const hydrator = createNoticeImageHydrator({
    loadImage,
    createObjectUrl: (blob) => {
      const url = `blob:notice-${created.length + 1}`
      created.push({ blob, url })
      return url
    },
    revokeObjectUrl: (url) => revoked.push(url),
  })
  return { hydrator, loadImage, created, revoked }
}

describe('公告图片水合', () => {
  it('只认同源公告图片路径', () => {
    expect(noticeImageId('/admin/notices/images/12')).toBe(12)
    expect(noticeImageId('/admin/notices/images/12?x=1')).toBeNull()
    expect(noticeImageId('https://evil.test/a.png')).toBeNull()
    expect(noticeImageId('/admin/notices/images/1.svg')).toBeNull()
    expect(noticeImageId('data:image/png;base64,AAAA')).toBeNull()
    expect(noticeImageId(null)).toBeNull()
  })

  it('把公告图片换成带认证取回的 blob 地址，其他图片不动', async () => {
    const { hydrator, loadImage, created } = harness()
    const root = document.createElement('div')
    const local = imageElement('/admin/notices/images/9')
    const other = imageElement('/avatar.svg')
    root.append(local, other)

    hydrator.hydrate(root)
    await vi.waitFor(() => expect(local.getAttribute('src')).toMatch(/^blob:/))

    expect(loadImage).toHaveBeenCalledWith(9)
    expect(loadImage).toHaveBeenCalledTimes(1)
    expect(created).toHaveLength(1)
    expect(other.getAttribute('src')).toBe('/avatar.svg')
    hydrator.dispose()
  })

  it('同一张图只取一次，重复水合复用同一个对象地址', async () => {
    const { hydrator, loadImage } = harness()
    const root = document.createElement('div')
    const first = imageElement('/admin/notices/images/3')
    const second = imageElement('/admin/notices/images/3')
    root.append(first, second)

    hydrator.hydrate(root)
    await vi.waitFor(() => expect(first.getAttribute('src')).toMatch(/^blob:/))
    await vi.waitFor(() => expect(second.getAttribute('src')).toMatch(/^blob:/))

    expect(loadImage).toHaveBeenCalledTimes(1)
    expect(second.getAttribute('src')).toBe(first.getAttribute('src'))
    hydrator.dispose()
  })

  it('内容更新与卸载时撤销对象地址', async () => {
    const { hydrator, revoked } = harness()
    const root = document.createElement('div')
    const local = imageElement('/admin/notices/images/4')
    root.append(local)

    hydrator.hydrate(root)
    await vi.waitFor(() => expect(local.getAttribute('src')).toMatch(/^blob:/))
    const url = local.getAttribute('src')

    hydrator.revokeAll()
    expect(revoked).toEqual([url])

    const fresh = imageElement('/admin/notices/images/4')
    const nextRoot = document.createElement('div')
    nextRoot.append(fresh)
    hydrator.hydrate(nextRoot)
    await vi.waitFor(() => expect(fresh.getAttribute('src')).toMatch(/^blob:/))
    expect(fresh.getAttribute('src')).not.toBe(url)

    const secondUrl = fresh.getAttribute('src')
    hydrator.dispose()
    expect(revoked).toEqual([url, secondUrl])
  })

  it('卸载后迟到的图片结果不再创建对象地址', async () => {
    let resolveLoad
    const { hydrator, created } = harness({
      loadImage: () =>
        new Promise((resolve) => {
          resolveLoad = resolve
        }),
    })
    const root = document.createElement('div')
    const local = imageElement('/admin/notices/images/5')
    root.append(local)

    hydrator.hydrate(root)
    hydrator.dispose()
    resolveLoad(new Blob(['png'], { type: 'image/png' }))
    await Promise.resolve()
    await Promise.resolve()

    expect(created).toEqual([])
    expect(local.getAttribute('src')).toBe('/admin/notices/images/5')
  })

  it('图片取回失败时保留原 src，不创建对象地址', async () => {
    const { hydrator, created } = harness({
      loadImage: vi.fn(async () => {
        throw new Error('401')
      }),
    })
    const root = document.createElement('div')
    const local = imageElement('/admin/notices/images/6')
    root.append(local)

    hydrator.hydrate(root)
    await vi.waitFor(() => expect(created).toEqual([]))
    expect(local.getAttribute('src')).toBe('/admin/notices/images/6')
    hydrator.dispose()
  })
})
