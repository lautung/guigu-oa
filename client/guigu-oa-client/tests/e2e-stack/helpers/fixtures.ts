import { test as base, expect } from '@playwright/test'

import { Oracle } from './oracle'

export const test = base.extend<{ oracle: Oracle }>({
  oracle: async ({ request }, use) => {
    await use(new Oracle(request))
  },
})

export { expect }
