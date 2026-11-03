/**
 * 全屏能力只认标准 API（#230）：requestFullscreen 与 exitFullscreen 同时存在才显示按钮。
 * 不支持时由调用方隐藏入口，不做 vendor 前缀分支。
 */
export interface FullscreenDocument {
  fullscreenElement: Element | null
  documentElement: { requestFullscreen?: () => Promise<void> }
  exitFullscreen?: () => Promise<void>
}

export function fullscreenSupported(doc: FullscreenDocument | null | undefined): boolean {
  return (
    !!doc &&
    typeof doc.documentElement?.requestFullscreen === 'function' &&
    typeof doc.exitFullscreen === 'function'
  )
}

/** 返回操作后的全屏状态；不支持时保持 false。 */
export async function toggleFullscreen(
  doc: FullscreenDocument | null | undefined,
): Promise<boolean> {
  if (!fullscreenSupported(doc)) return false

  if (doc!.fullscreenElement) {
    await doc!.exitFullscreen!()
    return false
  }

  await doc!.documentElement.requestFullscreen!()
  return true
}
