import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import {
  LOCK_SCREEN_STORAGE_KEY,
  MAX_UNLOCK_FAILURES,
  useLockScreenStore,
} from '@/shell/lock-screen'
import { fakeRecognition, installHttp, ok, setupSession } from '../support/harness'

function freshSession(options = {}) {
  const session = setupSession(options)
  return { ...session, lockScreen: useLockScreenStore(session.pinia) }
}

function wrongPassword() {
  return { code: 201, message: '密码错误' }
}

describe('锁屏状态', () => {
  let http

  beforeEach(() => {
    sessionStorage.clear()
  })

  afterEach(() => {
    http?.restore()
    http = undefined
    sessionStorage.clear()
  })

  it('locks the tab and keeps that state in sessionStorage', () => {
    const { lockScreen } = freshSession()

    expect(lockScreen.isLocked()).toBe(false)
    lockScreen.lock()

    expect(lockScreen.isLocked()).toBe(true)
    expect(sessionStorage.getItem(LOCK_SCREEN_STORAGE_KEY)).toBe('1')
  })

  it('restores a locked tab after a reload and starts unlocked without the marker', () => {
    sessionStorage.setItem(LOCK_SCREEN_STORAGE_KEY, '1')
    const restored = freshSession()
    expect(restored.lockScreen.isLocked()).toBe(true)

    sessionStorage.clear()
    const fresh = freshSession()
    expect(fresh.lockScreen.isLocked()).toBe(false)
  })

  it('unlocks with the current password, clears the marker and keeps the session', async () => {
    http = installHttp(() => ok(null))
    const recognition = fakeRecognition({ exit: vi.fn() })
    const { lockScreen } = freshSession({ recognition })
    lockScreen.lock()

    await expect(lockScreen.unlock('111111')).resolves.toBe(true)

    expect(lockScreen.isLocked()).toBe(false)
    expect(lockScreen.failures).toBe(0)
    expect(sessionStorage.getItem(LOCK_SCREEN_STORAGE_KEY)).toBeNull()
    expect(http.calls.map((call) => call.url)).toEqual(['/admin/system/index/verifyPassword'])
    expect(JSON.parse(http.calls[0].data)).toEqual({ password: '111111' })
    expect(recognition.exit).not.toHaveBeenCalled()
  })

  it('stays locked and counts each wrong password', async () => {
    http = installHttp(wrongPassword)
    const { lockScreen } = freshSession()
    lockScreen.lock()

    for (let attempt = 1; attempt < MAX_UNLOCK_FAILURES; attempt += 1) {
      await expect(lockScreen.unlock(`wrong-${attempt}`)).resolves.toBe(false)
      expect(lockScreen.isLocked()).toBe(true)
      expect(lockScreen.failures).toBe(attempt)
    }

    expect(lockScreen.error).toBe('密码错误')
    expect(sessionStorage.getItem(LOCK_SCREEN_STORAGE_KEY)).toBe('1')
  })

  it('exits the login after five consecutive wrong passwords', async () => {
    http = installHttp((config) => (config.url.includes('/logout') ? ok(null) : wrongPassword()))
    const recognition = fakeRecognition({ exit: vi.fn() })
    const { lockScreen } = freshSession({ recognition })
    lockScreen.lock()

    for (let attempt = 1; attempt <= MAX_UNLOCK_FAILURES; attempt += 1) {
      await lockScreen.unlock(`wrong-${attempt}`)
    }

    expect(lockScreen.isLocked()).toBe(false)
    expect(lockScreen.failures).toBe(0)
    expect(sessionStorage.getItem(LOCK_SCREEN_STORAGE_KEY)).toBeNull()
    expect(recognition.exit).toHaveBeenCalledTimes(1)
    expect(http.calls.some((call) => call.url.includes('/logout'))).toBe(true)
  })

  it('does not count a transport failure as a wrong password', async () => {
    http = installHttp(() => {
      throw new Error('offline')
    })
    const { lockScreen } = freshSession()
    lockScreen.lock()

    await expect(lockScreen.unlock('111111')).resolves.toBe(false)

    expect(lockScreen.isLocked()).toBe(true)
    expect(lockScreen.failures).toBe(0)
    expect(lockScreen.error).toBeTruthy()
    expect(sessionStorage.getItem(LOCK_SCREEN_STORAGE_KEY)).toBe('1')
  })
})
