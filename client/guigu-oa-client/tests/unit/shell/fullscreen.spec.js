import { afterEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

import FullscreenButton from '@/shell/FullscreenButton.vue'
import { fullscreenSupported, toggleFullscreen } from '@/shell/fullscreen'
import { elementStubs } from '../support/harness'

function fakeDocument({ request, exit, element = null } = {}) {
  return {
    fullscreenElement: element,
    documentElement: { requestFullscreen: request },
    exitFullscreen: exit,
  }
}

describe('全屏 API', () => {
  it('is supported only when both enter and exit APIs exist', () => {
    const request = vi.fn(() => Promise.resolve())
    const exit = vi.fn(() => Promise.resolve())

    expect(fullscreenSupported(fakeDocument({ request, exit }))).toBe(true)
    expect(fullscreenSupported(fakeDocument({ request }))).toBe(false)
    expect(fullscreenSupported(fakeDocument({ exit }))).toBe(false)
    expect(fullscreenSupported(null)).toBe(false)
  })

  it('enters fullscreen when idle and exits when already fullscreen', async () => {
    const request = vi.fn(() => Promise.resolve())
    const exit = vi.fn(() => Promise.resolve())
    const idle = fakeDocument({ request, exit })

    await expect(toggleFullscreen(idle)).resolves.toBe(true)
    expect(request).toHaveBeenCalledTimes(1)
    expect(exit).not.toHaveBeenCalled()

    const activeRequest = vi.fn(() => Promise.resolve())
    const activeExit = vi.fn(() => Promise.resolve())
    const active = fakeDocument({
      request: activeRequest,
      exit: activeExit,
      element: { tag: 'html' },
    })
    await expect(toggleFullscreen(active)).resolves.toBe(false)
    expect(activeRequest).not.toHaveBeenCalled()
    expect(activeExit).toHaveBeenCalledTimes(1)
  })

  it('does nothing when the browser does not support fullscreen', async () => {
    await expect(toggleFullscreen(fakeDocument())).resolves.toBe(false)
  })
})

describe('FullscreenButton', () => {
  afterEach(() => {
    delete document.documentElement.requestFullscreen
    delete document.exitFullscreen
    delete document.fullscreenElement
  })

  it('is hidden when the fullscreen API is missing', () => {
    const wrapper = mount(FullscreenButton, { global: { stubs: elementStubs } })

    expect(wrapper.find('.fullscreen-btn').exists()).toBe(false)
  })

  it('toggles fullscreen and reflects the current state', async () => {
    const request = vi.fn(() => Promise.resolve())
    const exit = vi.fn(() => Promise.resolve())
    Object.defineProperty(document.documentElement, 'requestFullscreen', {
      configurable: true,
      value: request,
    })
    Object.defineProperty(document, 'exitFullscreen', { configurable: true, value: exit })

    const wrapper = mount(FullscreenButton, { global: { stubs: elementStubs } })
    const button = wrapper.find('.fullscreen-btn')
    expect(button.attributes('title')).toBe('全屏')

    await button.trigger('click')
    await flushPromises()
    expect(request).toHaveBeenCalledTimes(1)

    Object.defineProperty(document, 'fullscreenElement', {
      configurable: true,
      value: document.documentElement,
    })
    document.dispatchEvent(new Event('fullscreenchange'))
    await flushPromises()
    expect(wrapper.find('.fullscreen-btn').attributes('title')).toBe('退出全屏')

    await wrapper.find('.fullscreen-btn').trigger('click')
    await flushPromises()
    expect(exit).toHaveBeenCalledTimes(1)

    Object.defineProperty(document, 'fullscreenElement', { configurable: true, value: null })
    document.dispatchEvent(new Event('fullscreenchange'))
    await flushPromises()
    expect(wrapper.find('.fullscreen-btn').attributes('title')).toBe('全屏')
  })
})
