import { defineStore } from 'pinia'
import { translate as t } from '@/i18n'
import { openRequestScope } from '@/request/request-scope'
import { BusinessRequestFailure, failureMessage } from '@/request/request-failure'
import { useUserStore } from '@/store/modules/user'

/**
 * 锁屏（#255）：手动触发的本地遮罩，不是退出、停用或登录失败限制。
 *
 * 锁状态保存在 sessionStorage：刷新后同一标签页仍锁着，关闭标签页即解除。
 * 解锁只拿本人密码问一次 `verifyPassword`：错误口令是业务失败（201），不写
 * 审计、不计登录失败；连续 5 次错误则退出登录（复用 user store 的 `logout`）。
 */
export const LOCK_SCREEN_STORAGE_KEY = 'guigu_oa_client_locked'
export const LOCK_SCREEN_FAILURES_STORAGE_KEY = 'guigu_oa_client_lock_failures'
export const MAX_UNLOCK_FAILURES = 5

interface LockScreenState {
  locked: boolean
  failures: number
  error: string
  loggingOut: boolean
}

function readLocked(): boolean {
  return sessionStorage.getItem(LOCK_SCREEN_STORAGE_KEY) === '1'
}

function readFailures(): number {
  const stored = Number(sessionStorage.getItem(LOCK_SCREEN_FAILURES_STORAGE_KEY))
  if (!Number.isFinite(stored) || stored <= 0) return 0
  // 历史值超过阈值说明上次退出没走完；保留到「下一次错误即退出」，不提前解锁。
  return Math.min(Math.floor(stored), MAX_UNLOCK_FAILURES - 1)
}

function writeFailures(failures: number): void {
  sessionStorage.setItem(LOCK_SCREEN_FAILURES_STORAGE_KEY, String(failures))
}

function clearLockState(): void {
  sessionStorage.removeItem(LOCK_SCREEN_STORAGE_KEY)
  sessionStorage.removeItem(LOCK_SCREEN_FAILURES_STORAGE_KEY)
}

function isWrongPassword(error: unknown): boolean {
  return error instanceof BusinessRequestFailure && error.businessCode === 201
}

export const useLockScreenStore = defineStore('lock-screen', {
  state: (): LockScreenState => ({
    locked: readLocked(),
    failures: readFailures(),
    error: '',
    loggingOut: false,
  }),
  actions: {
    lock(): void {
      this.locked = true
      this.failures = 0
      this.error = ''
      this.loggingOut = false
      sessionStorage.setItem(LOCK_SCREEN_STORAGE_KEY, '1')
      sessionStorage.removeItem(LOCK_SCREEN_FAILURES_STORAGE_KEY)
    },
    isLocked(): boolean {
      return this.locked
    },
    async unlock(password: string): Promise<boolean> {
      if (this.loggingOut) return false
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
          await this.exitAfterFailures(user)
          return false
        }
        writeFailures(this.failures)
        this.error = failureMessage(error) || t('shell.lockScreen.wrongPassword')
        return false
      }
      this.locked = false
      this.failures = 0
      this.error = ''
      clearLockState()
      return true
    },
    /**
     * 第 5 次失败后的退出：锁标记保留到 logout 完成，刷新落在这个窗口也仍然锁着；
     * logout 失败保持锁定与标记，下一次提交继续尝试。
     */
    async exitAfterFailures(user: ReturnType<typeof useUserStore>): Promise<void> {
      this.loggingOut = true
      this.error = ''
      try {
        await user.logout()
        this.locked = false
        this.failures = 0
        clearLockState()
      } catch (error) {
        this.failures = MAX_UNLOCK_FAILURES
        writeFailures(this.failures)
        this.error = failureMessage(error) || t('shell.lockScreen.unlockFailed')
      } finally {
        this.loggingOut = false
      }
    },
  },
})
