import { enableAutoUnmount } from '@vue/test-utils'
import { afterEach, vi } from 'vitest'

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
