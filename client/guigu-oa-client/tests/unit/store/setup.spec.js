import { describe, expect, it } from 'vitest'
import { createApp } from 'vue'

import { setupStore, useUserStore } from '@/store'

describe('setupStore', () => {
  it('creates a pinia instance, installs it, and re-exports the user store', () => {
    const app = createApp({ render: () => null })
    const pinia = setupStore(app)

    expect(app.config.globalProperties.$pinia).toBe(pinia)
    expect(useUserStore(pinia).$id).toBe('user')
  })
})
