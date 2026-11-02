import { onBeforeUnmount } from 'vue'
import { openRequestScope } from '@/request/request-scope'
import { useUserStore } from '@/store/modules/user'
import { createNoticeUnreadSource, createUnreadPolling, type UnreadPolling } from '@/notice/unread'

/**
 * 顶栏铃铛的浏览器接线：每个外壳一个轮询 opening，作用域每次刷新重新打开
 * （登录身份变化后不再拿旧身份读数）。作用域关闭普通错误提示——60 秒一次的
 * 后台轮询失败不该反复弹 toast。
 *
 * 详情页写回执成功后调 `refreshNoticeUnread()`，角标立即归零，不必等下一拍。
 * #219 只有铃铛是唯一实例；多个实例时全部刷新。
 */

const active = new Set<UnreadPolling>()

export function useNoticeUnread(): UnreadPolling {
  const user = useUserStore()
  const recognition = user.recognition
  if (!recognition) throw new Error('认出 module 尚未接入')
  const polling = createUnreadPolling({
    source: createNoticeUnreadSource((config) =>
      openRequestScope(recognition, { notifyNormalErrors: false }).request(config),
    ),
  })
  active.add(polling)
  onBeforeUnmount(() => {
    active.delete(polling)
    polling.stop()
  })
  return polling
}

export function refreshNoticeUnread(): void {
  for (const polling of active) void polling.refresh()
}
