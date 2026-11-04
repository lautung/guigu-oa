import { enableAutoUnmount } from '@vue/test-utils'
import { afterEach, beforeEach, vi } from 'vitest'

import { DEFAULT_LOCALE, setLocale } from '@/i18n'

/**
 * Global test environment.
 *
 * NProgress schedules a timer that touches `document` after jsdom is torn down, which surfaces
 * as an unhandled error in longer suites. Keep it inert for every test file.
 */
vi.mock('nprogress', () => ({
  default: { start: vi.fn(), done: vi.fn(), configure: vi.fn(), remove: vi.fn() },
}))

/** A mounted component left behind re-renders against a stale route and fails the run. */
enableAutoUnmount(afterEach)

/** 单测默认中文（票面：现有断言不变）；切换 locale 的用例在自己的 afterEach 复位。 */
beforeEach(() => setLocale(DEFAULT_LOCALE))
