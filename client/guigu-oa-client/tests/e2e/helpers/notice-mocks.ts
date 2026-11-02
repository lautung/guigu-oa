import type { Route } from '@playwright/test'

const json = (route: Route, data: unknown) =>
  route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code: 200, data }) })

/**
 * 顶栏铃铛在进入外壳后立即读一次未读（ADR 0055），之后每 60 秒与 focus 时再读。
 * 页面用例的严格 mock 把未知请求当失败，所以统一在这里给空结果；
 * 需要断言铃铛行为的用例自己先拦截这两个端点。
 */
export async function fulfillNoticeUnread(route: Route, path: string): Promise<boolean> {
  if (path.endsWith('/admin/notices/unread-count')) {
    await json(route, 0)
    return true
  }
  if (path.endsWith('/admin/notices/unread')) {
    await json(route, [])
    return true
  }
  return false
}
