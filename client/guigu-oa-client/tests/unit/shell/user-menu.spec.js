import { afterEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'

import UserMenu from '@/shell/UserMenu.vue'
import { useShellStore } from '@/shell/shell-state'
import { SHELL_DENSITY_KEY, SHELL_THEME_KEY } from '@/shell/shell-preferences'
import { fakeRecognition, installHttp, mountOptions, ok, setupSession } from '../support/harness'

function menuRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: { template: '<div />' } },
      { path: '/work/profile', component: { template: '<div />' } },
    ],
  })
}

describe('UserMenu', () => {
  let http

  afterEach(() => {
    http?.restore()
    http = undefined
    localStorage.clear()
    document.documentElement.classList.remove('dark')
  })

  it('shows the user, opens 个人中心 and has no fake lock-screen entry', async () => {
    const { pinia } = setupSession({ name: 'Alice' })
    const router = menuRouter()
    const wrapper = mount(UserMenu, mountOptions(pinia, router))

    expect(wrapper.find('.username').text()).toBe('Alice')
    expect(wrapper.text()).not.toContain('锁屏')

    await wrapper.find('.user-menu-profile').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/work/profile')
  })

  it('writes theme and density to the shell store and localStorage', async () => {
    const { pinia } = setupSession({ name: 'Alice' })
    const shell = useShellStore(pinia)
    const wrapper = mount(UserMenu, mountOptions(pinia, menuRouter()))

    await wrapper.find('[data-theme="dark"]').trigger('click')
    expect(shell.theme).toBe('dark')
    expect(localStorage.getItem(SHELL_THEME_KEY)).toBe('dark')
    expect(document.documentElement.classList.contains('dark')).toBe(true)

    await wrapper.find('[data-density="large"]').trigger('click')
    expect(shell.density).toBe('large')
    expect(localStorage.getItem(SHELL_DENSITY_KEY)).toBe('large')
    expect(wrapper.find('[data-density="large"]').classes()).toContain('active')

    await wrapper.find('[data-theme="light"]').trigger('click')
    expect(shell.theme).toBe('light')
    expect(document.documentElement.classList.contains('dark')).toBe(false)
  })

  it('logs out through the existing user store action', async () => {
    http = installHttp(() => ok(null))
    const recognition = fakeRecognition({ exit: vi.fn() })
    const { pinia } = setupSession({ name: 'Alice', recognition })
    const wrapper = mount(UserMenu, mountOptions(pinia, menuRouter()))

    await wrapper.find('.logout-btn').trigger('click')
    await flushPromises()

    expect(recognition.exit).toHaveBeenCalled()
    expect(http.calls.some((call) => call.url.includes('/logout'))).toBe(true)
  })
})
