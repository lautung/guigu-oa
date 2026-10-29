/**
 * 兼容性矩阵里的已知浏览器噪声（#117）：WebKit 在容器尺寸连续变化时会以 `error` 事件上报
 * ResizeObserver 循环，Chromium 不上报；浏览器会自行节流，不影响页面功能。
 * 只过滤这一条，其他 pageerror 仍然照常失败。
 */
export function isKnownBrowserNoise(message: string): boolean {
  return message.includes('ResizeObserver loop completed with undelivered notifications')
}
