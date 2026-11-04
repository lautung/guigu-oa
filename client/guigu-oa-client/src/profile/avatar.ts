/**
 * 头像 blob 的认证取回（#254）：同源 `<img>` 拿不到认证头，先经 requestBinary 取回，
 * 再换成 `blob:` 显示。URL 的生命周期由本模块拥有：release 单个、dispose 全部。
 */
export interface AvatarLoader {
  load(path: string): Promise<Blob>
  createObjectUrl(blob: Blob): string
  revokeObjectUrl(url: string): void
}

export function createAvatarLoader(loader: AvatarLoader) {
  const urls = new Set<string>()
  let disposed = false

  async function read(path: string): Promise<string> {
    if (!path || disposed) return ''
    const blob = await loader.load(path)
    if (disposed) return ''
    const url = loader.createObjectUrl(blob)
    urls.add(url)
    return url
  }

  function release(url: string): void {
    if (url && urls.delete(url)) loader.revokeObjectUrl(url)
  }

  function dispose(): void {
    disposed = true
    urls.forEach((url) => loader.revokeObjectUrl(url))
    urls.clear()
  }

  return { read, release, dispose }
}
