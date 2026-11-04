import { defineStore } from 'pinia'
import { translate as t } from '@/i18n'
import { openRequestScope } from '@/request/request-scope'
import { failureMessage } from '@/request/request-failure'
import { useUserStore } from '@/store/modules/user'

/**
 * 锁屏（#255）：手动触发的本地遮罩，不是退出、停用或登录失败限制。
 *
 * 锁状态保存在 sessionStorage：刷新后同一标签页仍锁着，关闭标签页即解除。
 * 解锁只拿本人密码问一次 `verifyPassword`：错误口令是业务失败（201），不写
 * 审计、不计登录失败；连续 5 次错误则退出登录（复用 user store 的 `logout`）。
 */
export const LOCK_SCREEN_STORAGE_KEY = 'guigu_oa_client_locked'
export const MAX_UNLOCK_FAILURES = 5

interface LockScreenState {
  locked: boolean
  failures: number
  error: string
}

function readLocked(): boolean {
  return sessionStorage.getItem(LOCK_SCREEN_STORAGE_KEY) === '1'
}

function isWrongPassword(error: unknown): boolean {
  const detail = (error as { detail?: { businessCode?: number } } | null)?.detail
  return detail?.businessCode === 201
}

export const useLockScreenStore = defineStore('lock-screen', {
  state: (): LockScreenState => ({
    locked: readLocked(),
    failures: 0,
    error: '',
  }),
  actions: {
    lock(): void {
      this.locked = true
      this.failures = 0
      this.error = ''
      sessionStorage.setItem(LOCK_SCREEN_STORAGE_KEY, '1')
    },
    isLocked(): boolean {
      return this.locked
    },
    async unlock(password: string): Promise<boolean> {
      const user = useUserStore()
      const recognition = user.recognition
      if (!recognition) throw new Error('认出 module 尚未接入')
      this.error = ''
      try {
        await openRequestScope(recognition, { notifyNormalErrors: false }).request({
          url: '/admin/system/index/verifyPassword',
          method: 'post',
          data: { password },
        })
      } catch (error) {
        if (!isWrongPassword(error)) {
          this.error = failureMessage(error) || t('shell.lockScreen.unlockFailed')
          return false
        }
        this.failures += 1
        if (this.failures >= MAX_UNLOCK_FAILURES) {
          this.failures = 0
          sessionStorage.removeItem(LOCK_SCREEN_STORAGE_KEY)
          try {
            await user.logout()
          } finally {
            this.locked = false
            this.error = ''
          }
          return false
        }
        this.error = failureMessage(error) || t('shell.lockScreen.wrongPassword')
        return false
      }
      this.locked = false
      this.failures = 0
      this.error = ''
      sessionStorage.removeItem(LOCK_SCREEN_STORAGE_KEY)
      return true
    },
  },
})
