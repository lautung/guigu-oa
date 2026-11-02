/**
 * 公告正文图片的认证取回（#219）：正文里的 `<img src="/admin/notices/images/{id}">`
 * 是浏览器原生请求，带不上 axios 的 `token` 头；这里用同一套请求客户端取 Blob，
 * 再把 `src` 换成 `blob:` 对象地址（CSP 的 `img-src` 已放行 `blob:`，ADR 0026）。
 *
 * 只认同源路径 `/admin/notices/images/{id}`——与服务端消毒的白名单一致（ADR 0055）。
 * 外部图、`data:`、SVG 都不碰，保持原 `src`。取回失败保留原 `src`，不弹提示。
 *
 * 复用面：`NoticeContent.vue` 是当前唯一调用方；#220 的 Quill 预览可以直接用
 * `createNoticeImageHydrator` 走同一条路径。
 */

export interface NoticeImageHydrationOptions {
  loadImage: (id: number) => Promise<Blob>
  createObjectUrl: (blob: Blob) => string
  revokeObjectUrl: (url: string) => void
}

export interface NoticeImageHydrator {
  /** 扫描根节点内的公告图片；已水合的复用对象地址，同一 id 只取一次。 */
  hydrate(root: ParentNode | null | undefined): void
  /** 内容变化时撤销本批对象地址；下一次 hydrate 会重新取回。 */
  revokeAll(): void
  /** 卸载：撤销并把在途结果作废。 */
  dispose(): void
}

const NOTICE_IMAGE_PATH = /^\/admin\/notices\/images\/(\d+)$/

/** 正文图片地址 → 图片 id；不是同源公告图片路径时返回 null。 */
export function noticeImageId(src: string | null | undefined): number | null {
  if (!src) return null
  const match = NOTICE_IMAGE_PATH.exec(src)
  if (!match) return null
  const id = Number(match[1])
  return Number.isSafeInteger(id) && id > 0 ? id : null
}

export function createNoticeImageHydrator(
  options: NoticeImageHydrationOptions,
): NoticeImageHydrator {
  const urls = new Map<number, string>()
  const pending = new Set<number>()
  let generation = 0
  let disposed = false

  function apply(root: ParentNode): void {
    for (const element of root.querySelectorAll('img')) {
      const id = noticeImageId(element.getAttribute('src'))
      if (id == null) continue
      const url = urls.get(id)
      if (url) element.setAttribute('src', url)
    }
  }

  function hydrate(root: ParentNode | null | undefined): void {
    if (disposed || !root) return
    const current = generation
    apply(root)
    for (const element of root.querySelectorAll('img')) {
      const id = noticeImageId(element.getAttribute('src'))
      if (id == null || urls.has(id) || pending.has(id)) continue
      pending.add(id)
      options
        .loadImage(id)
        .then((blob) => {
          pending.delete(id)
          if (disposed || current !== generation) return
          const url = options.createObjectUrl(blob)
          urls.set(id, url)
          apply(root)
        })
        .catch(() => {
          pending.delete(id)
        })
    }
  }

  function revokeAll(): void {
    generation += 1
    for (const url of urls.values()) options.revokeObjectUrl(url)
    urls.clear()
  }

  return {
    hydrate,
    revokeAll,
    dispose() {
      disposed = true
      revokeAll()
    },
  }
}
