import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'

import AppShell from '@/shell/AppShell.vue'
import { LOCK_SCREEN_STORAGE_KEY } from '@/shell/lock-screen'
import { elementStubs, installHttp, mountOptions, ok, setupSession } from '../support/harness'

const page = (name) => ({ template: `<div class="page-${name}">${name}</div>` })

function shellRouter() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/workbench', component: page('workbench'), meta: { title: '工作台' } },
      { path: '/work/pending', component: page('pending'), meta: { title: '待处理' } },
      { path: '/work/start', component: page('start'), meta: { title: '发起审批' } },
      { path: '/work/processed', component: page('processed'), meta: { title: '已处理' } },
      { path: '/work/started', component: page('started'), meta: { title: '已发起' } },
      { path: '/work/copied', component: page('copied'), meta: { title: '抄送我的' } },
      { path: '/profile', component: page('profile'), meta: { title: '个人中心' } },
    ],
  })
  router.push('/work/pending')
  return router
}

function shellOptions(pinia, router) {
  const options = mountOptions(pinia, router)
  return {
    global: {
      ...options.global,
      stubs: { ...elementStubs, NoticeBell: true },
    },
  }
}

async function lockedShell() {
  sessionStorage.setItem(LOCK_SCREEN_STORAGE_KEY, '1')
  const { pinia } = setupSession({ name: 'Alice' })
  const router = shellRouter()
  await router.isReady()
  return mount(AppShell, shellOptions(pinia, router))
}

describe('锁屏遮罩', () => {
  let http

  beforeEach(() => {
    sessionStorage.clear()
  })

  afterEach(() => {
    http?.restore()
    http = undefined
    sessionStorage.clear()
  })

  it('covers the shell and stops rendering business content until unlocked', async () => {
    const wrapper = await lockedShell()

    expect(wrapper.find('.lock-screen').exists()).toBe(true)
    expect(wrapper.find('.page-pending').exists()).toBe(false)
    expect(wrapper.find('.app-shell').attributes('inert')).toBeDefined()
    expect(wrapper.find('.lock-screen').attributes('aria-modal')).toBe('true')
  })

  it('unlocks with the current password and restores the page', async () => {
    http = installHttp(() => ok(null))
    const wrapper = await lockedShell()

    await wrapper.find('.lock-password').setValue('111111')
    await wrapper.find('.lock-screen form').trigger('submit')
    await flushPromises()

    expect(wrapper.find('.lock-screen').exists()).toBe(false)
    expect(wrapper.find('.page-pending').exists()).toBe(true)
    expect(wrapper.find('.app-shell').attributes('inert')).toBeUndefined()
    expect(sessionStorage.getItem(LOCK_SCREEN_STORAGE_KEY)).toBeNull()
  })

  it('shows the server message and stays locked after a wrong password', async () => {
    http = installHttp(() => ({ code: 201, message: '密码错误' }))
    const wrapper = await lockedShell()

    await wrapper.find('.lock-password').setValue('wrong-password')
    await wrapper.find('.lock-screen form').trigger('submit')
    await flushPromises()

    expect(wrapper.find('.lock-error').text()).toBe('密码错误')
    expect(wrapper.find('.lock-screen').exists()).toBe(true)
    expect(wrapper.find('.page-pending').exists()).toBe(false)
    expect(sessionStorage.getItem(LOCK_SCREEN_STORAGE_KEY)).toBe('1')
  })
})
